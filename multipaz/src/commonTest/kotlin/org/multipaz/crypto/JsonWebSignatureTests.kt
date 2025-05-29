package org.multipaz.crypto

import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.multipaz.asn1.ASN1Integer
import kotlin.test.Test
import kotlin.time.Duration.Companion.days

class JsonWebSignatureTests {
    @Test
    fun roundtrip() {
        val signingKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val now = Clock.System.now()
        val signingKeyCert = X509Cert.Builder(
            publicKey = signingKey.publicKey,
            signingKey = signingKey,
            signatureAlgorithm = signingKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test Key"),
            issuer = X500Name.fromName("CN=Test Key"),
            validFrom = now,
            validUntil = now + 1.days
        ).includeSubjectKeyIdentifier()
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()

        val claimsSet = buildJsonObject {
            put("vp_token", buildJsonObject {
                put("credential", buildJsonObject {
                    put("foo", JsonPrimitive("blah"))
                })
            })
        }

        val jws = JsonWebSignature.sign(
            key = signingKey,
            signatureAlgorithm = signingKey.curve.defaultSigningAlgorithmFullySpecified,
            claimsSet = claimsSet,
            type = "oauth-authz-req+jwt",
            x5c = X509CertChain(listOf(signingKeyCert))
        )

        JsonWebSignature.verify(jws, signingKey.publicKey)
    }
}