package com.android.mdl.appreader.issuerauth.vical

import org.bouncycastle.util.encoders.Hex
import java.time.Instant

class IdentityCredentialVicalSignerTests {
    @org.junit.jupiter.api.Test
    @Throws(Exception::class)
    fun signVerifyShortByteArray() {
        val signer: VicalSigner = IdentityCredentialVicalSigner(
            TestCertificates.signingCert!!,
            TestCertificates.signingKey!!,
            "SHA256withECDSA"
        )
        val oneZeroVical = byteArrayOf(1)
        val signedZeroVical = signer.createCose1Signature(oneZeroVical)
        val verifier: VicalVerifier = IdentityCredentialVicalVerifier()
        val result = verifier.verifyCose1Signature(signedZeroVical)

        assert(result.code == VicalVerificationResult.Code.VERIFICATION_SUCCEEDED)
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
                "SHA256withECDSA"
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

        // NOTE considered ok if verification and decoding succeeds without exception
        decoder.verifyAndDecodeSignedBytes(encodedAndSignedBytes, verifier)
    }
}