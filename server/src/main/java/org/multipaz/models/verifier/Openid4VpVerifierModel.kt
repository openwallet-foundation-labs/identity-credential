package org.multipaz.models.verifier

import io.ktor.util.encodeBase64
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.Simple
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.cbor.buildCborArray
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.JsonWebEncryption
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.DocumentCannedRequest
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.sdjwt.presentation.SdJwtVerifiablePresentation
import org.multipaz.sdjwt.util.JsonWebKey
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlin.random.Random

/**
 * Object that holds data necessary to verify Openid4VP credential presentation.
 *
 * This class is designed to work in the environment where its state may need to be serialized
 * between the time that the presentation request is generated (see [makeRequest]) and the
 * device response is verified (see [processResponse]), as is typically the case on the
 * server.
 *
 * This class creates requests for and parses only encrypted presentations (using either
 * "dc_api.jwt" or "direst_post.jwt" response mode) and it requires compliance with draft 28
 * of the Openid4VP spec.
 */
@CborSerializable
class Openid4VpVerifierModel(
    val clientId: String,
    val nonce: ByteString = ByteString(Random.nextBytes(15)),
    val ephemeralPrivateKey: EcPrivateKey = Crypto.createEcPrivateKey(EcCurve.P256),
    val requestedFormats: MutableMap<String, String> = mutableMapOf()
) {
    /**
     * Creates an Openid4VP presentation request for credentials (typically just one) in
     * [requests] parameter.
     */
    fun makeRequest(
        state: String,
        responseMode: String,
        requests: Map<String, DocumentCannedRequest>,
        expectedOrigins: List<String> = listOf(),
        responseUri: String? = null,
    ): String {
        val publicKey = ephemeralPrivateKey.publicKey
        val signingAlgorithm = publicKey.curve.defaultSigningAlgorithmFullySpecified
        val header = buildJsonObject {
            put("typ", JsonPrimitive("oauth-authz-req+jwt"))
            put("alg", JsonPrimitive(signingAlgorithm.joseAlgorithmIdentifier))
            put("x5c", buildJsonArray {
                for (cert in createCertificateChain().certificates) {
                    // NOT Base64Url for this parameter!
                    add(cert.encodedCertificate.encodeBase64())
                }
            })
        }.toString().toByteArray().toBase64Url()

        val bodyJson = buildJsonObject {
            put("client_id", clientId)
            if (responseUri != null) {
                put("response_uri", responseUri)
            }
            put("response_type", "vp_token")
            put("response_mode", responseMode)
            put("nonce", nonce.toByteArray().toBase64Url())
            put("client_metadata", buildClientMetadata())
            put("state", state)

            if (expectedOrigins.isNotEmpty()) {
                putJsonArray("expected_origins") {
                    expectedOrigins.forEach { add(it) }
                }
            }

            putJsonObject("dcql_query") {
                putJsonArray("credentials") {
                    for ((id, request) in requests) {
                        addJsonObject {
                            put("id", id)
                            if (request.mdocRequest == null) {
                                requestedFormats[id] = "dc+sd-jwt"
                                put("format", JsonPrimitive("dc+sd-jwt"))
                                putJsonObject("meta") {
                                    put("vct_values",
                                        buildJsonArray {
                                            add(JsonPrimitive(request.vcRequest!!.vct))
                                        }
                                    )
                                }
                                putJsonArray("claims") {
                                    for (claim in request.vcRequest!!.claimsToRequest) {
                                        addJsonObject {
                                            putJsonArray("path") {
                                                add(JsonPrimitive(claim.identifier))
                                            }
                                        }
                                    }
                                }
                            } else {
                                requestedFormats[id] = "mso_mdoc"
                                put("format", JsonPrimitive("mso_mdoc"))
                                putJsonObject("meta") {
                                    put(
                                        "doctype_value",
                                        JsonPrimitive(request.mdocRequest!!.docType)
                                    )
                                }
                                putJsonArray("claims") {
                                    for (ns in request.mdocRequest!!.namespacesToRequest) {
                                        for ((de, intentToRetain) in ns.dataElementsToRequest) {
                                            addJsonObject {
                                                putJsonArray("path") {
                                                    add(JsonPrimitive(ns.namespace))
                                                    add(JsonPrimitive(de.attribute.identifier))
                                                }
                                                put(
                                                    "intent_to_retain",
                                                    JsonPrimitive(intentToRetain)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val body = bodyJson.toString().toByteArray().toBase64Url()

        val message = "$header.$body"
        val signature = Crypto.sign(ephemeralPrivateKey, signingAlgorithm, message.toByteArray())
            .toCoseEncoded().toBase64Url()

        return "$message.$signature"
    }

    /**
     * Parses and validates credential presentation for the request that was previously created
     * using [makeRequest].
     */
    fun processResponse(
        responseUri: String,
        response: String
    ): Map<String, Presentation> {
        val decrypted = JsonWebEncryption.decrypt(JsonPrimitive(response), ephemeralPrivateKey)
        val header = Json.parseToJsonElement(
            response.substring(0, response.indexOf('.')).fromBase64Url().decodeToString()
        ).jsonObject
        val sessionTranscript = createSessionTranscript(
            clientId = clientId,
            responseUri = responseUri,
            authorizationRequestNonce = header["apv"]!!.jsonPrimitive.content,
            mdocGeneratedNonce = header["apu"]!!.jsonPrimitive.content,
        )
        val vpToken = decrypted["vp_token"]!!.jsonObject
        return vpToken.mapValues { (id, encoded) ->
            val presentationText = encoded.jsonPrimitive.content
            when (requestedFormats[id]) {
                "mso_mdoc" -> {
                    val parser = DeviceResponseParser(
                        presentationText.fromBase64Url(),
                        sessionTranscript
                    )
                    MdocPresentation(parser.parse())
                }
                "dc+sd-jwt" -> {
                    SdJwtPresentation(SdJwtVerifiablePresentation.fromString(presentationText))
                }
                else -> throw IllegalStateException("Unknown format for id '$id'")
            }
        }
    }

    private fun createCertificateChain(): X509CertChain {
        val now = Clock.System.now()
        val validFrom = now.plus(DateTimePeriod(minutes = -10), TimeZone.currentSystemDefault())
        val validUntil = now.plus(DateTimePeriod(minutes = 10), TimeZone.currentSystemDefault())
        val readerKey = ephemeralPrivateKey
        val readerKeySubject = "CN=OWF IC Online Verifier Single-Use Reader Key"

        val readerKeyCertificate = MdocUtil.generateReaderCertificate(
            readerRootCert = owfIcReaderCert,
            readerRootKey = owfIcReaderRoot,
            readerKey = readerKey.publicKey,
            subject = X500Name.fromName(readerKeySubject),
            serial = ASN1Integer(1L),
            validFrom = validFrom,
            validUntil = validUntil
        )

        return X509CertChain(listOf(readerKeyCertificate, owfIcReaderCert))
    }

    private fun buildClientMetadata(): JsonObject {
        val vpFormats = buildJsonObject {
            putJsonObject("mso_mdoc") {
                putJsonArray("alg") {
                    // TODO: should we list other algorithms here?
                    add(Algorithm.ESP256.joseAlgorithmIdentifier)
                }
            }
            putJsonObject("dc+sd-jwt") {
                putJsonArray("sd-jwt_alg_values") {
                    add(Algorithm.ESP256.joseAlgorithmIdentifier)
                }
                putJsonArray("kb-jwt_alg_values") {
                    add(Algorithm.ESP256.joseAlgorithmIdentifier)
                }
            }
        }
        return buildJsonObject {
            putJsonArray("encrypted_response_enc_values_supported") {
                add(Algorithm.A128GCM.joseAlgorithmIdentifier)
            }
            put("vp_formats_supported", vpFormats)
            put("jwks", buildJsonObject {
                put("keys", buildJsonArray {
                    add(JsonWebKey(ephemeralPrivateKey.publicKey).toRawJwk {
                        put("use", "enc")
                        put("kid", "default")
                        put("alg", "ECDH-ES")
                    })
                })
            })
        }
    }

    // defined in ISO 18013-7 Annex B
    private fun createSessionTranscript(
        clientId: String,
        responseUri: String,
        authorizationRequestNonce: String,
        mdocGeneratedNonce: String
    ): ByteArray {
        val clientIdToHash = Cbor.encode(
            CborArray.builder()
                .add(clientId)
                .add(mdocGeneratedNonce)
                .end()
                .build())
        val clientIdHash = Crypto.digest(Algorithm.SHA256, clientIdToHash)

        val responseUriToHash = Cbor.encode(
            buildCborArray {
                add(responseUri)
                add(mdocGeneratedNonce)
            }
        )
        val responseUriHash = Crypto.digest(Algorithm.SHA256, responseUriToHash)

        val oid4vpHandover = buildCborArray {
            add(clientIdHash)
            add(responseUriHash)
            add(authorizationRequestNonce)
        }

        return Cbor.encode(
            buildCborArray {
                add(Simple.NULL)
                add(Simple.NULL)
                add(oid4vpHandover)
            }
        )
    }

    sealed class Presentation

    class MdocPresentation(val deviceResponse: DeviceResponseParser.DeviceResponse): Presentation()

    class SdJwtPresentation(val presentation: SdJwtVerifiablePresentation): Presentation()

    companion object {
        // TODO: for now, instead of using the per-site Reader Root generated at first run, use the
        //  well-known OWF IC Reader root checked into Git.
        val owfIcReaderCert = X509Cert.fromPem("""
            -----BEGIN CERTIFICATE-----
            MIICCTCCAY+gAwIBAgIQZc/0rhdjZ9n3XoZYzpt2GjAKBggqhkjOPQQDAzA+MS8wLQYDVQQDDCZP
            V0YgSWRlbnRpdHkgQ3JlZGVudGlhbCBURVNUIFJlYWRlciBDQTELMAkGA1UEBhMCWlowHhcNMjQw
            OTE3MTY1NjA5WhcNMjkwOTE3MTY1NjA5WjA+MS8wLQYDVQQDDCZPV0YgSWRlbnRpdHkgQ3JlZGVu
            dGlhbCBURVNUIFJlYWRlciBDQTELMAkGA1UEBhMCWlowdjAQBgcqhkjOPQIBBgUrgQQAIgNiAATM
            1ZVDQ7E4A+ujJl0J7Op8qvy/BSgg/UCTw+WrwYI32/jV9pk8Qu5BSTbUDZE2PQheqy4s3j8y1gMu
            +Q5pemhYn/c4OMYXZY8uD+t4Wo9UFoSDkFbvlumZ/cuO5TTAI76jUjBQMB0GA1UdDgQWBBTgtILK
            HJ50qO/Nc33zshz2aX4+4TAfBgNVHSMEGDAWgBTgtILKHJ50qO/Nc33zshz2aX4+4TAOBgNVHQ8B
            Af8EBAMCAQYwCgYIKoZIzj0EAwMDaAAwZQIxALmOcU+Ggax3wHbD8tcd8umuDxzimf9PSICjvlh5
            kwR0/1SZZF7bqMAOQXsrwNYFLgIwLVirmU4WvRlUktR2Ty5kxgDG0iy+g00ur9JXCF+wAUQjKHbg
            VvIQ6NRr06GwpPJR
            -----END CERTIFICATE-----
        """.trimIndent())

        val owfIcReaderRoot = EcPrivateKey.fromPem("""
            -----BEGIN PRIVATE KEY-----
            MFcCAQAwEAYHKoZIzj0CAQYFK4EEACIEQDA+AgEBBDDxgrZBXnoO54/hZM2DAGrByoWRatjH9hGs
            lrW+vvdmRHBgS+ss56uWyYor6W7ah9ygBwYFK4EEACI=
            -----END PRIVATE KEY-----
        """.trimIndent(),
            owfIcReaderCert.ecPublicKey)
    }
}