package com.android.identity.server.openid4vci

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.X509CertificateCreateOption
import com.android.identity.crypto.X509CertificateExtension
import com.android.identity.crypto.create
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.documenttype.DocumentWellKnownRequest
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.sdjwt.util.JsonWebKey
import com.android.identity.util.toBase64Url
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import kotlin.random.Random

data class Openid4VpSession(
    val jwt: String,
    val privateKey: EcPrivateKey,
    val nonce: String
)

fun initiateOpenid4Vp(
    clientId: String,
    responseUri: String,
    state: String
): Openid4VpSession {
    val (singleUseReaderKeyPriv, singleUseReaderKeyCertChain) = createSingleUseReaderKey()

    val request = EUPersonalID.getDocumentType().sampleRequests.first { it.id == "full" }
    val nonce = Random.nextBytes(15).toBase64Url()
    val publicKey = singleUseReaderKeyPriv.publicKey

    val header = buildJsonObject {
        put("typ", JsonPrimitive("oauth-authz-req+jwt"))
        put("alg", JsonPrimitive(publicKey.curve.defaultSigningAlgorithm.jwseAlgorithmIdentifier))
        put("jwk", publicKey.toJson(null))
        put("x5c", buildJsonArray {
            for (cert in singleUseReaderKeyCertChain.certificates) {
                add(cert.encodedCertificate.toBase64Url())
            }
        })
    }.toString().toByteArray().toBase64Url()

    val body = buildJsonObject {
        put("client_id", clientId)
        put("response_uri", responseUri)
        put("response_type", "vp_token")
        put("response_mode", "direct_post.jwt")
        put("nonce", nonce)
        put("state", state)
        put("presentation_definition", mdocCalcPresentationDefinition(request))
        put("client_metadata", calcClientMetadata(singleUseReaderKeyPriv.publicKey))
    }.toString().toByteArray().toBase64Url()

    val message = "$header.$body"
    val signature = Crypto.sign(singleUseReaderKeyPriv, Algorithm.ES256, message.toByteArray())
        .toCoseEncoded().toBase64Url()

    return Openid4VpSession("$message.$signature", singleUseReaderKeyPriv, nonce)
}

private fun EcPublicKey.toJson(keyId: String?): JsonObject {
    return JsonWebKey(this).toRawJwk {
        if (keyId != null) {
            put("kid", JsonPrimitive(keyId))
        }
        put("alg", JsonPrimitive(curve.defaultSigningAlgorithm.jwseAlgorithmIdentifier))
        put("use", JsonPrimitive("sig"))
    }
}

private fun mdocCalcPresentationDefinition(
    request: DocumentWellKnownRequest
): JsonObject {
    return buildJsonObject {
        // Fill in a unique ID.
        put("id", Random.Default.nextBytes(15).toBase64Url())
        put("input_descriptors", buildJsonArray {
            add(buildJsonObject {
                put("id", request.mdocRequest!!.docType)
                put("format", buildJsonObject {
                    put("mso_mdoc", buildJsonObject {
                        put("alg", buildJsonArray {
                            add("ES256")
                        })
                    })
                })
                put("constraints", buildJsonObject {
                    put("limit_disclosure", "required")
                    put("fields", buildJsonArray {
                        for (ns in request.mdocRequest!!.namespacesToRequest) {
                            for ((de, intentToRetain) in ns.dataElementsToRequest) {
                                add(buildJsonObject {
                                    put("path", buildJsonArray {
                                        add("\$['${ns.namespace}']['${de.attribute.identifier}']")
                                    })
                                    put("intent_to_retain", intentToRetain)
                                })
                            }
                        }
                    })
                })
            })
        })
    }
}

private fun createSingleUseReaderKey(): Pair<EcPrivateKey, X509CertChain> {
    val now = Clock.System.now()
    val validFrom = now.plus(DateTimePeriod(minutes = -10), TimeZone.currentSystemDefault())
    val validUntil = now.plus(DateTimePeriod(minutes = 10), TimeZone.currentSystemDefault())
    val readerKey = Crypto.createEcPrivateKey(EcCurve.P256)

    val extensions = mutableListOf<X509CertificateExtension>()
    extensions.add(
        X509CertificateExtension(
            Extension.keyUsage.toString(),
            true,
            KeyUsage(KeyUsage.digitalSignature).encoded
        )
    )
    extensions.add(
        X509CertificateExtension(
            Extension.extendedKeyUsage.toString(),
            true,
            ExtendedKeyUsage(
                KeyPurposeId.getInstance(ASN1ObjectIdentifier("1.0.18013.5.1.2"))
            ).encoded
        )
    )
    val readerKeySubject = "CN=OWF IC Online Verifier Single-Use Reader Key"

    // TODO: for now, instead of using the per-site Reader Root generated at first run, use the
    //  well-know OWF IC Reader root checked into Git.
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
    val owfIcReaderRootSignatureAlgorithm = Algorithm.ES384
    val owfIcReaderRootIssuer = owfIcReaderCert.javaX509Certificate.issuerX500Principal.name

    val readerKeyCertificate = X509Cert.create(
        readerKey.publicKey,
        owfIcReaderRoot,
        owfIcReaderCert,
        owfIcReaderRootSignatureAlgorithm,
        "1",
        readerKeySubject,
        owfIcReaderRootIssuer,
        validFrom,
        validUntil,
        setOf(
            X509CertificateCreateOption.INCLUDE_SUBJECT_KEY_IDENTIFIER,
            X509CertificateCreateOption.INCLUDE_AUTHORITY_KEY_IDENTIFIER_FROM_SIGNING_KEY_CERTIFICATE
        ),
        extensions
    )
    return Pair(
        readerKey,
        X509CertChain(listOf(readerKeyCertificate) + owfIcReaderCert)
    )
}

private fun calcClientMetadata(publicKey: EcPublicKey): JsonObject {
    val encPub = publicKey as EcPublicKeyDoubleCoordinate
    val formats = buildJsonObject {
        put("mso_mdoc", buildJsonObject {
            put("alg", buildJsonArray {
                add("ES256")
            })
        })
    }
    return buildJsonObject {
        put("authorization_encrypted_response_alg", "ECDH-ES")
        put("authorization_encrypted_response_enc", "A128CBC-HS256")
        put("response_mode", "direct_post.jwt")
        put("vp_formats", formats)
        put("vp_formats_supported", formats)
        put("jwks", buildJsonObject {
            put("keys", buildJsonArray {
                add(buildJsonObject {
                    put("kty", "EC")
                    put("use", "enc")
                    put("crv", "P-256")
                    put("alg", "ECDH-ES")
                    put("x", encPub.x.toBase64Url())
                    put("y", encPub.y.toBase64Url())
                })
            })
        })
    }
}

