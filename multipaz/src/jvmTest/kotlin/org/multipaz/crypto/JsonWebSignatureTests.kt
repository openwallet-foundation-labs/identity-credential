package org.multipaz.crypto

import org.multipaz.asn1.ASN1Integer
import org.multipaz.util.toBase64Url
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.BadJWTException
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier
import junit.framework.TestCase.assertTrue
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.days

class JsonWebSignatureTests {

    // TODO: Check for other curves once JsonWebSignature is implemented in a multiplatform fashion.

    @Test
    fun testSigning() {
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

        val signedJwt = JsonWebSignature.sign(
            key = signingKey,
            signatureAlgorithm = signingKey.curve.defaultSigningAlgorithm,
            claimsSet = claimsSet,
            type = "oauth-authz-req+jwt",
            x5c = X509CertChain(listOf(signingKeyCert))
        )

        val sjwt = SignedJWT.parse(signedJwt.jsonPrimitive.content)
        val jwtProcessor = DefaultJWTProcessor<SecurityContext>()
        val x5c = sjwt.header?.x509CertChain ?: throw IllegalArgumentException("Error retrieving x5c")
        val pubCertChain = x5c.mapNotNull { runCatching { X509Cert(it.decode()) }.getOrNull() }
        assertEquals(1, pubCertChain.size)
        assertEquals(signingKeyCert, pubCertChain[0])

        jwtProcessor.jwsTypeVerifier = DefaultJOSEObjectTypeVerifier(
            JOSEObjectType("oauth-authz-req+jwt"),
            JOSEObjectType.JWT,
            JOSEObjectType(""),
            null,
        )
        jwtProcessor.jwsKeySelector = JWSKeySelector { _, _ ->
            listOf(pubCertChain[0].javaX509Certificate.publicKey)
        }
        val resultingClaimsSet = jwtProcessor.process(sjwt, null)
        val extractedClaimsSet = Json.parseToJsonElement(resultingClaimsSet.toString()).jsonObject

        assertEquals(claimsSet, extractedClaimsSet)
    }

    @Test
    fun testVerification() {
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

        val ecKey = ECKey(
            Curve.P_256,
            signingKey.publicKey.javaPublicKey as ECPublicKey,
            signingKey.javaPrivateKey as ECPrivateKey,
            null, null, null, null, null, null, null, null, null
        )
        val builder = JWSHeader.Builder(JWSAlgorithm.ES256)
        builder.x509CertChain(
            listOf(com.nimbusds.jose.util.Base64.from(signingKeyCert.encodedCertificate.toBase64Url()))
        )
        builder.type(JOSEObjectType("oauth-authz-req+jwt"))
        builder.keyID(ecKey.getKeyID())
        val signedJWT = SignedJWT(
            builder.build(),
            JWTClaimsSet.parse(claimsSet.toString())
        )
        val signer: JWSSigner = ECDSASigner(ecKey)
        signedJWT.sign(signer)
        val signedJwt = Json.parseToJsonElement(signedJWT.serialize())

        // Verify JwsInfo fields
        val info = JsonWebSignature.getInfo(signedJwt)
        assertEquals(claimsSet, info.claimsSet)
        assertEquals("oauth-authz-req+jwt", info.type)
        assertEquals(X509CertChain(listOf(signingKeyCert)), info.x5c)

        // Verify signature checks out
        JsonWebSignature.verify(
            signedJwt,
            info.x5c!!.certificates.first().ecPublicKey
        )

        // Verify signature check with another key fails
        val otherKey = Crypto.createEcPrivateKey(EcCurve.P256)
        assertFails {
            JsonWebSignature.verify(
                signedJwt,
                otherKey.publicKey
            )
        }
    }

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
            signatureAlgorithm = signingKey.curve.defaultSigningAlgorithm,
            claimsSet = claimsSet,
            type = "oauth-authz-req+jwt",
            x5c = X509CertChain(listOf(signingKeyCert))
        )

        JsonWebSignature.verify(jws, signingKey.publicKey)
    }
}
