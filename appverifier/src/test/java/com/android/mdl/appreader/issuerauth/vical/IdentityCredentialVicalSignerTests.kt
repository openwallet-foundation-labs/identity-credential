package com.android.mdl.appreader.issuerauth.vical

import androidx.compose.ui.input.key.Key
import org.bouncycastle.util.encoders.Hex
import org.bouncycastle.util.io.pem.PemReader
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.LinkedList
import java.util.Optional
import java.util.Set

class IdentityCredentialVicalSignerTests {

//    @Before
//    @Throws(Exception::class)
//    fun loadSigningKey() {
//        try {
//            PemReader(
//                InputStreamReader(
//                    this.javaClass.classLoader.getResourceAsStream("com/ul/vical/test/UL_VICAL_Sign_privkey.pem")
//                )
//            ).use { pemReader ->
//                val pemObject = pemReader.readPemObject()
//                val ecdsa = KeyFactory.getInstance("EC")
//                signingKey = ecdsa.generatePrivate(PKCS8EncodedKeySpec(pemObject.content))
//            }
//        } catch (e: IOException) {
//            throw RuntimeException(e)
//        } catch (e: NoSuchAlgorithmException) {
//            throw RuntimeException(e)
//        } catch (e: InvalidKeySpecException) {
//            throw RuntimeException(e)
//        }
//    }

    @org.junit.jupiter.api.Test
    @Throws(Exception::class)
    fun signVerifyShortByteArray() {
        val signer: VicalSigner = IdentityCredentialVicalSigner(
            TestCertificates.signingCert!!,
            TestCertificates.signingKey!!,
            "SHA256WithECDSA"
        )
        val oneZeroVical = byteArrayOf(1)
        val signedZeroVical = signer.createCose1Signature(oneZeroVical)
        val verifier: VicalVerifier = IdentityCredentialVicalVerifier()
        val result = verifier.verifyCose1Signature(signedZeroVical)
        // println(result)

        assert(result.code() == VicalVerificationResult.Code.VERIFICATION_SUCCEEDED)
        // following was to verify result using UL verifier implementation

//        val ulVerifier = IdentityCredentialVicalVerifier()
//        val ulResult: VicalVerificationResult = ulVerifier.verifyCose1Signature(signedZeroVical)
//        println(ulResult)
    }

    @org.junit.jupiter.api.Test
    @Throws(Exception::class)
    fun signVerifyMinimal() {

        // === build a VICAL structure (objects in memory) ===

        // --- create a VICAL builder ---
        // this constructor is used to set the basic fields
        // - vicalProvider
        // - date
        // - vicalIssueID
        val vicalBuilder: Vical.Builder = Vical.Builder("Test", Instant.now(), 1)

        // --- build a CertificateInfo for each trusted (root) certificate, and add it ---

        // this constructor is taking the certificate and a set of docTypes
        // note that there is an extended constructor to copy optional fields from the certificate
        val certificateInfoBuilder = CertificateInfo.Builder(
            TestCertificates.iacaCerts[0],
            setOf(KnownDocType.MDL.docType),
            setOf()
        )
        vicalBuilder.addCertificateInfo(certificateInfoBuilder.build())

        // --- build the VICAL structure ---
        val vical = vicalBuilder.build()

        // === sign and encode the VICAL ===

        // --- create a signer ---
        val signer: VicalSigner =
            IdentityCredentialVicalSigner(
                TestCertificates.signingCert!!,
                TestCertificates.signingKey!!,
                "SHA256WithECDSA"
            )

        // --- create an encoder for the VICAL structure ---
        val encoder = Vical.Encoder()

        // --- call the convenience method that performs both the encoding and the signing ---
        val encodedAndSignedBytes: ByteArray = encoder.encodeToSignedBytes(vical, signer)
        println(Hex.toHexString(encodedAndSignedBytes))

        // === verify and decode the VICAL ===

        // --- create a verifier (for the signature only) ---
        val verifier: VicalVerifier = IdentityCredentialVicalVerifier()

        // --- create decoder ---
        val decoder = Vical.Decoder()

        // --- call the convenience method that performs both the verification and the decoding ---
        val decodedVical: Vical =
            decoder.verifyAndDecodeSignedBytes(encodedAndSignedBytes, verifier)
        println(decodedVical)

        // following was to verify result using UL verifier implementation
//        val ulVerifier: VicalVerifier = ULVicalVerifier()
//        val ulDecodedVical: Vical =
//            decoder.verifyAndDecodeSignedBytes(encodedAndSignedBytes, ulVerifier)
//        println(decodedVical)
//
//        // TODO equals is asking a bit much maybe
//        if (decodedVical != ulDecodedVical || decodedVical != vical) {
//            throw RuntimeException()
//        }

//        Vical.Decoder decoder = new Vical.Decoder();
//        Vical decodedVical = decoder.decode(encodedVical);
//        System.out.println(decodedVical);
    }

    @org.junit.jupiter.api.Test
    @Throws(Exception::class)
    fun signVerifySigningCertAndKey() {
        val s = Signature.getInstance("ECDSA");
        s.initSign(TestCertificates.signingKey)
        s.update("Hello World".toByteArray(StandardCharsets.US_ASCII))
        val sig = s.sign()

        s.initVerify(TestCertificates.signingCert)
        s.update("Hello World".toByteArray(StandardCharsets.US_ASCII))
        assert(s.verify(sig))
    }
}