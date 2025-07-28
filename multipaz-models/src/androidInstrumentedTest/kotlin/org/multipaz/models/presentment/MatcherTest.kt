package org.multipaz.models.presentment

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert
import org.junit.Test
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509CertChain
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.models.digitalcredentials.DigitalCredentials
import org.multipaz.models.digitalcredentials.calculateCredentialDatabase
import org.multipaz.models.openid.OpenID4VP
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.trustmanagement.TrustManagerLocal
import org.multipaz.util.toBase64Url
import kotlin.random.Random

// Tests for the matched in multipaz-models/src/androidMain/matcher ...
class MatcherTest {
    companion object {
        private const val TAG = "MatcherTest"

        private const val CLIENT_ID = "x509_san_dns:verifier.multipaz.org"
        private const val ORIGIN = "https://verifier.multipaz.org"
    }

    init {
        System.loadLibrary("MatcherTest")
    }

    external fun runMatcher(
        request: ByteArray,
        credentialDatabase: ByteArray,
    ): String

    suspend fun testMatcherDcql(
        version: OpenID4VP.Version,
        signRequest: Boolean,
        encryptionKey: EcPrivateKey?,
        dcql: String,
    ): String {
        val harness = DocumentStoreTestHarness()
        harness.initialize()

        val readerTrustManager = TrustManagerLocal(EphemeralStorage())
        val presentmentSource = SimplePresentmentSource(
            documentStore = harness.documentStore,
            documentTypeRepository = harness.documentTypeRepository,
            readerTrustManager = readerTrustManager,
            preferSignatureToKeyAgreement = true,
            domainMdocSignature = "mdoc",
            domainKeyBoundSdJwt = "sdjwt",
        )

        val nonce = Random.Default.nextBytes(16).toBase64Url()

        val (readerAuthKey, readerAuthCert) = if (signRequest) {
            val key = Crypto.createEcPrivateKey(EcCurve.P256)
            val cert = MdocUtil.generateReaderCertificate(
                readerRootCert = harness.readerRootCert,
                readerRootKey = harness.readerRootKey,
                readerKey = key.publicKey,
                subject = X500Name.fromName("CN=Multipaz Reader Cert Single-Use key"),
                serial = ASN1Integer.fromRandom(128),
                validFrom = harness.readerRootCert.validityNotBefore,
                validUntil = harness.readerRootCert.validityNotAfter
            )
            Pair(key, cert)
        } else {
            Pair(null, null)
        }

        val requestData = OpenID4VP.generateRequest(
            version = version,
            origin = ORIGIN,
            clientId = CLIENT_ID,
            nonce = nonce,
            responseEncryptionKey = encryptionKey?.publicKey,
            requestSigningKey = readerAuthKey,
            requestSigningKeyCertification = readerAuthCert?.let {
                X509CertChain(listOf(it, harness.readerRootCert))
            },
            responseMode = OpenID4VP.ResponseMode.DC_API,
            responseUri = null,
            dclqQuery = Json.decodeFromString(JsonObject.serializer(), dcql)
        )
        val protocolName = when (version) {
            OpenID4VP.Version.DRAFT_24 -> "openid4vp"
            OpenID4VP.Version.DRAFT_29 -> if (signRequest) "openid4vp-v1-signed" else "openid4vp-v1-unsigned"
        }

        val credentialDatabase = calculateCredentialDatabase(
            appName = "Test App",
            selectedProtocols = DigitalCredentials.Default.supportedProtocols,
            stores = listOf(Pair(harness.documentStore, harness.documentTypeRepository))
        )

        var result = runMatcher(
            request = buildJsonObject {
                putJsonArray("requests") {
                    addJsonObject {
                        put("protocol", protocolName)
                        put("data", requestData)
                    }
                }
            }.toString().encodeToByteArray(),
            credentialDatabase = Cbor.encode(credentialDatabase)
        )
        // To get stable output, replace all document IDs with displayName
        for (docId in harness.documentStore.listDocuments()) {
            val doc = harness.documentStore.lookupDocument(docId)!!
            result = result.replace(docId, "__${doc.metadata.displayName!!}__")
        }
        return result
    }

    // TODO: add tests for more complicated DCQL, including requests for multiple credentials...
    @Test
    fun testMatcher_OpenID4VP_mDL_simple() = runTest {
        val matcherResult = testMatcherDcql(
            version = OpenID4VP.Version.DRAFT_29,
            signRequest = true,
            encryptionKey = null,
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
        )
        Assert.assertEquals(
                """
                    Entry
                      cred_id openid4vp-v1-signed __mDL__
                      Older Than 21 Years: true
                      Photo of Holder: 5318 bytes
                """.trimIndent().trim() + "\n",
            matcherResult
        )
    }

    @Test
    fun testMatcher_OpenID4VP_sdjwt_simple() = runTest {
        val matcherResult = testMatcherDcql(
            version = OpenID4VP.Version.DRAFT_29,
            signRequest = true,
            encryptionKey = null,
            dcql =
                """
                    {
                      "credentials": [
                        {
                          "id": "pid",
                          "format": "dc+sd-jwt",
                          "meta": {
                            "vct_values": [
                              "urn:eudi:pid:1"
                            ]
                          },
                          "claims": [
                            {
                              "path": [
                                "age_equal_or_over",
                                "18"
                              ]
                            },
                            {
                              "path": [
                                "picture"
                              ]
                            }
                          ]
                        }
                      ]
                    }
                """.trimIndent().trim(),
        )
        Assert.assertEquals(
            """
                    Entry
                      cred_id openid4vp-v1-signed __EU PID__
                      Older Than 18: true
                      Photo of Holder: Image (5318 bytes)
                """.trimIndent().trim() + "\n",
            matcherResult
        )
    }
}
