package org.multipaz.models.presentment

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.cbor.Simple
import org.multipaz.cbor.addCborArray
import org.multipaz.cbor.buildCborArray
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.JsonWebEncryption
import org.multipaz.crypto.JsonWebSignature
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509CertChain
import org.multipaz.document.Document
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.request.Request
import org.multipaz.sdjwt.SdJwtKb
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class DigitalCredentialsPresentmentTest {
    companion object {
        private const val TAG = "DigitalCredentialsPresentmentTest"

        private const val CLIENT_ID = "x509_san_dns:verifier.multipaz.org"
        private const val ORIGIN = "https://verifier.multipaz.org"
        private const val APP_ID = "org.multipaz.testApp"
    }

    val documentStoreTestHarness = DocumentStoreTestHarness()

    class TestPresentmentMechanism(
        protocol: String,
        data: String,
        document: Document?,
        var response: String? = null,
        var closed: Boolean = false
    ): DigitalCredentialsPresentmentMechanism(
        appId = APP_ID,
        webOrigin = ORIGIN,
        protocol = protocol,
        data = data,
        document = document,
    ) {
        override fun sendResponse(response: String) {
            this.response = response
        }

        override fun close() {
            closed = true
        }
    }

    private data class ShownConsentPrompt(
        val document: Document,
        val request: Request,
        val trustPoint: TrustPoint?
    )

    private data class TestOpenID4VPResponse(
        val shownConsentPrompts: List<ShownConsentPrompt>,
        val vpToken: Map<String, List<String>>,
        val nonce: String,
        val origin: String,
        val clientId: String,
    )

    private suspend fun testOpenID4VP(
        version: Int,
        signRequest: Boolean,
        encryptResponse: Boolean,
        dcql: JsonObject
    ): TestOpenID4VPResponse {
        require(version == 24) { "Only OpenID4VP draft 24 is currently supported" }

        documentStoreTestHarness.initialize()

        val presentmentModel = PresentmentModel()

        val encryptionKey = Crypto.createEcPrivateKey(EcCurve.P256)

        val readerTrustManager = TrustManager()
        val presentmentSource = SimplePresentmentSource(
            documentStore = documentStoreTestHarness.documentStore,
            documentTypeRepository = documentStoreTestHarness.documentTypeRepository,
            readerTrustManager = readerTrustManager,
            preferSignatureToKeyAgreement = true,
            domainMdocSignature = "mdoc",
            domainKeyBoundSdJwt = "sdjwt",
        )

        val nonce = Random.Default.nextBytes(16).toBase64Url()

        // TODO: factor OpenID4VP request building to library to share this with verifier code e.g. VerifierServlet.kt
        val requestObject = buildJsonObject {
            put("response_type", "vp_token")
            put("response_mode", if (encryptResponse) "dc_api.jwt" else "dc_api")
            if (signRequest) {
                put("client_id", CLIENT_ID)
                putJsonArray("expected_origins") {
                    add(ORIGIN)
                }
            }
            put("dcql_query", dcql)
            put("nonce", nonce)
            putJsonObject("client_metadata") {
                put("vp_formats", buildJsonObject {
                    putJsonObject("mso_mdoc") {
                        putJsonArray("alg") {
                            add("ES256")
                        }
                    }
                    putJsonObject("dc+sd-jwt") {
                        putJsonArray("sd-jwt_alg_values") {
                            add("ES256")
                        }
                        putJsonArray("kb-jwt_alg_values") {
                            add("ES256")
                        }
                    }
                })
                if (encryptResponse) {
                    put("authorization_encrypted_response_alg", "ECDH-ES")
                    put("authorization_encrypted_response_enc", "A128GCM")
                    putJsonObject("jwks") {
                        putJsonArray("keys") {
                            add(encryptionKey.publicKey.toJwk())
                        }
                    }
                }
            }
        }

        val readerAuthKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val readerAuthCert = MdocUtil.generateReaderCertificate(
            readerRootCert = documentStoreTestHarness.readerRootCert,
            readerRootKey = documentStoreTestHarness.readerRootKey,
            readerKey = readerAuthKey.publicKey,
            subject = X500Name.fromName("CN=Multipaz Reader Cert Single-Use key"),
            serial = ASN1Integer.fromRandom(128),
            validFrom = documentStoreTestHarness.readerRootCert.validityNotBefore,
            validUntil = documentStoreTestHarness.readerRootCert.validityNotAfter
        )

        val request = if (signRequest) {
            buildJsonObject {
                put(
                    "request",
                    JsonWebSignature.sign(
                        key = readerAuthKey,
                        signatureAlgorithm = readerAuthKey.curve.defaultSigningAlgorithmFullySpecified,
                        claimsSet = requestObject,
                        type = "oauth-authz-req+jwt",
                        x5c = X509CertChain(listOf(readerAuthCert, documentStoreTestHarness.readerRootCert))
                    )
                )
            }.toString()
        } else {
            requestObject.toString()
        }

        val presentmentMechanism = TestPresentmentMechanism(
            protocol = "openid4vp",
            data = request,
            document = null,
        )

        val shownConsentPrompts = mutableListOf<ShownConsentPrompt>()

        val dismissable = MutableStateFlow<Boolean>(true)
        digitalCredentialsPresentment(
            documentTypeRepository = documentStoreTestHarness.documentTypeRepository,
            source = presentmentSource,
            model = presentmentModel,
            mechanism = presentmentMechanism,
            dismissable = dismissable,
            showConsentPrompt = { document, request, trustPoint ->
                shownConsentPrompts.add(ShownConsentPrompt(document, request, trustPoint))
                true
            }
        )
        val dcResponseObject = Json.decodeFromString(JsonObject.serializer(), presentmentMechanism.response!!)
        val decryptedDcResponse = if (encryptResponse) {
            JsonWebEncryption.decrypt(
                dcResponseObject["response"]!!.jsonPrimitive.content,
                encryptionKey
            )
        } else {
            dcResponseObject
        }

        // In OpenID4VP 1.0 this is a response of the form.
        //
        //  {
        //    "vp_token": {
        //      "<cred1>": ["<cred1response1>", "<cred1response2>", ...],
        //      "<cred2>": ["<cred2response1>", "<cred2response2">, ...],
        //      [...]
        //    }
        // }
        //
        // and in OpenID4VP Draft 24 it's of the form
        //
        //  {
        //    "vp_token": {
        //      "<cred1>": "<cred1response>",
        //      "<cred2>": "<cred2response>",
        //      [...]
        //    }
        //  }
        //
        val vpToken = mutableMapOf<String, List<String>>()
        for ((credId, result) in decryptedDcResponse["vp_token"]!!.jsonObject) {
            when {
                result is JsonPrimitive -> vpToken[credId] = listOf(result.jsonPrimitive.content)
                result is JsonArray -> vpToken[credId] = result.jsonArray.toList().map { it.jsonPrimitive.content }
                else -> throw IllegalStateException("Unexpected value $result in vpToken")
            }
        }


        return TestOpenID4VPResponse(
            shownConsentPrompts = shownConsentPrompts,
            vpToken = vpToken,
            nonce = nonce,
            origin = ORIGIN,
            clientId = CLIENT_ID,
        )
    }

    suspend fun test_OpenID4VP_mdoc(
        version: Int,
        signRequest: Boolean,
        encryptResponse: Boolean,
        dcql: String,
        expectedMdocResponse: String
    ) {
        val response = testOpenID4VP(
            version = version,
            signRequest = signRequest,
            encryptResponse = encryptResponse,
            dcql = Json.decodeFromString(JsonObject.serializer(), dcql)
        )
        assertEquals(1, response.vpToken.keys.size)
        val credId = response.vpToken.keys.first()
        val encodedDeviceResponse = response.vpToken[credId]!![0].fromBase64Url()

        val handoverInfo = Cbor.encode(
            buildCborArray {
                add(response.origin)
                add(response.clientId)
                add(response.nonce)
            }
        )
        val encodedSessionTranscript = Cbor.encode(
            buildCborArray {
                add(Simple.NULL) // DeviceEngagementBytes
                add(Simple.NULL) // EReaderKeyBytes
                addCborArray {
                    add("OpenID4VPDCAPIHandover")
                    add(Crypto.digest(Algorithm.SHA256, handoverInfo))
                }
            }
        )
        assertEquals(
            expectedMdocResponse,
            DeviceResponseParser(encodedDeviceResponse, encodedSessionTranscript).parse().prettyPrint().trim()
        )
    }

    suspend fun test_OpenID4VP_sdJwt(
        version: Int,
        signRequest: Boolean,
        encryptResponse: Boolean,
        dcql: String,
        expectedSdJwtResponse: String
    ) {
        val response = testOpenID4VP(
            version = version,
            signRequest = signRequest,
            encryptResponse = encryptResponse,
            dcql = Json.decodeFromString(JsonObject.serializer(), dcql)
        )
        assertEquals(1, response.vpToken.keys.size)
        val credId = response.vpToken.keys.first()
        val compactSerialization = response.vpToken[credId]!![0]
        val sdJwtKb = SdJwtKb(compactSerialization)
        val processedJwt = sdJwtKb.verify(
            issuerKey = documentStoreTestHarness.dsKey.publicKey,
            checkNonce = { nonce -> nonce == response.nonce },
            checkAudience = { audience ->
                if (signRequest) {
                    audience == CLIENT_ID
                } else {
                    audience == "web-origin:$ORIGIN"
                }
            },
            checkCreationTime = { creationTime -> true },
        ).filterKeys { key -> !setOf("iat", "nbf", "exp", "cnf").contains(key) }  // filter out variable claims
        assertEquals(
            expectedSdJwtResponse,
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }.encodeToString(processedJwt)
        )
    }

    suspend fun test_OID4VP_mDL(
        version: Int,
        signRequest: Boolean,
        encryptResponse: Boolean,
    ) {
        test_OpenID4VP_mdoc(
            version = version,
            signRequest = signRequest,
            encryptResponse = encryptResponse,
            dcql =
                """
                    {
                      "credentials": [{
                          "id": "mDL",
                          "format": "mso_mdoc",
                          "meta": { "doctype_value": "org.iso.18013.5.1.mDL" },
                          "claims": [
                            { "path": ["org.iso.18013.5.1", "age_over_21"] },
                            { "path": ["org.iso.18013.5.1", "portrait"] }
                    ]}]}                
                """.trimIndent().trim(),
            expectedMdocResponse =
                """
                    Document 0:
                      DocType: org.iso.18013.5.1.mDL
                      IssuerSigned:
                        org.iso.18013.5.1:
                          age_over_21: true
                          portrait: 5318 bytes
                """.trimIndent().trim(),
        )
    }

    suspend fun test_OID4VP_SDJWT(
        version: Int,
        signRequest: Boolean,
        encryptResponse: Boolean,
    ) {
        test_OpenID4VP_sdJwt(
            version = version,
            signRequest = signRequest,
            encryptResponse = encryptResponse,
            dcql =
                """
                    {
                      "credentials": [{
                          "id": "pid",
                          "format": "dc+sd-jwt",
                          "meta": { "vct_values": [ "urn:eudi:pid:1" ] },
                          "claims": [
                            { "path": ["age_equal_or_over", "18"] },
                            { "path": ["given_name"] },
                            { "path": ["family_name"] }
                    ]}]}                
                """.trimIndent().trim(),
            expectedSdJwtResponse =
                """
                    {
                      "iss": "https://example-issuer.com",
                      "vct": "urn:eudi:pid:1",
                      "family_name": "Mustermann",
                      "given_name": "Erika",
                      "age_equal_or_over": {
                        "18": true
                      }
                    }
                """.trimIndent().trim(),
        )
    }

    @Test fun OID4VP_24_NoSignedRequest_NoEncryptedResponse_mDL() = runTest { test_OID4VP_mDL(24, false, false) }
    @Test fun OID4VP_24_NoSignedRequest_EncryptedResponse_mDL() = runTest { test_OID4VP_mDL(24, false, true) }
    @Test fun OID4VP_24_SignedRequest_NoEncryptedResponse_mDL() = runTest { test_OID4VP_mDL(24, true, false) }
    @Test fun OID4VP_24_SignedRequest_EncryptedResponse_mDL() = runTest { test_OID4VP_mDL(24, true, true) }

    @Test fun OID4VP_24_NoSignedRequest_NoEncryptedResponse_SDJWT() = runTest { test_OID4VP_SDJWT(24, false, false) }
    @Test fun OID4VP_24_NoSignedRequest_EncryptedResponse_SDJWT() = runTest { test_OID4VP_SDJWT(24, false, true) }
    @Test fun OID4VP_24_SignedRequest_NoEncryptedResponse_SDJWT() = runTest { test_OID4VP_SDJWT(24, true, false) }
    @Test fun OID4VP_24_SignedRequest_EncryptedResponse_SDJWT() = runTest { test_OID4VP_SDJWT(24, true, true) }

}

private fun DeviceResponseParser.DeviceResponse.prettyPrint(): String {
    val diagOptions = setOf(DiagnosticOption.BSTR_PRINT_LENGTH)
    val sb = StringBuilder()
    for (n in documents.indices) {
        val doc = documents[n]
        sb.appendLine("Document $n:")
        sb.appendLine("  DocType: ${doc.docType}")
        sb.appendLine("  IssuerSigned:")
        for (namespaceName in doc.issuerNamespaces) {
            sb.appendLine("    $namespaceName:")
            for (dataElementName in doc.getIssuerEntryNames(namespaceName)) {
                val encodedValue = doc.getIssuerEntryData(namespaceName, dataElementName)
                sb.appendLine("      $dataElementName: ${Cbor.toDiagnostics(encodedValue, diagOptions)}")
            }
        }

    }
    return sb.toString()
}
