package com.android.identity.issuance

import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.crypto.javaX509Certificates
import com.android.identity.util.AndroidAttestationExtensionParser
import com.android.identity.util.Logger
import com.android.identity.util.fromHex
import com.android.identity.util.toHex
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence

private val salt = byteArrayOf((0xe7).toByte(), 0x7c, (0xf8).toByte(), (0xec).toByte())

private const val KEY_DESCRIPTION_OID: String = "1.3.6.1.4.1.11129.2.1.17"

private const val TAG = "authenticationUtilities"

fun authenticationMessage(clientId: String, nonce: ByteString): ByteString {
    val buffer = ByteStringBuilder()
    buffer.append(salt)
    buffer.append(clientId.toByteArray())
    buffer.append(nonce)
    return buffer.toByteString()
}

fun extractAttestationSequence(chain: X509CertChain): ASN1Sequence {
    val extension = chain.certificates[0].javaX509Certificate.getExtensionValue(KEY_DESCRIPTION_OID)
    val asn1InputStream = ASN1InputStream(extension)
    val derSequenceBytes = (asn1InputStream.readObject() as ASN1OctetString).octets
    val seqInputStream = ASN1InputStream(derSequenceBytes)
    return seqInputStream.readObject() as ASN1Sequence
}

fun validateKeyAttestation(
    chain: X509CertChain,
    clientId: String?,
    requireGmsAttestation: Boolean,
    requireVerifiedBootGreen: Boolean,
    requireAppSignatureCertificateDigests: List<String>,
) {
    val x509certs = chain.javaX509Certificates

    // First check that all the certificates sign each other...
    for (n in 0 until x509certs.size - 1) {
        val cert = x509certs[n]
        val nextCert = x509certs[n + 1]
        try {
            cert.verify(nextCert.publicKey)
        } catch (e: Throwable) {
            throw IllegalArgumentException("Key attestation error: error validating chain", e)
        }
    }
    val rootCertificatePublicKey = x509certs[x509certs.size - 1].publicKey

    if (requireGmsAttestation) {
        // Must match the well-known Google root
        check(
            GOOGLE_ROOT_ATTESTATION_KEY contentEquals rootCertificatePublicKey.encoded
        ) { "Unexpected attestation root" }
    }

    // Finally, check the Attestation Extension...
    try {
        val parser = AndroidAttestationExtensionParser(x509certs[0])

        // Challenge must match...
        check(clientId == null || clientId.toByteArray() contentEquals parser.attestationChallenge)
        { "Challenge didn't match what was expected" }

        if (requireVerifiedBootGreen) {
            // Verified Boot state must VERIFIED
            check(
                parser.verifiedBootState ==
                        AndroidAttestationExtensionParser.VerifiedBootState.GREEN
            ) { "Verified boot state is not GREEN" }
        }

        if (requireAppSignatureCertificateDigests.size > 0) {
            check (parser.applicationSignatureDigests.size == requireAppSignatureCertificateDigests.size)
            { "Number Signing certificates mismatch" }
            for (n in 0..<parser.applicationSignatureDigests.size) {
                check (parser.applicationSignatureDigests[n] contentEquals requireAppSignatureCertificateDigests[n].fromHex())
                { "Signing certificate $n mismatch" }
            }
        }

        // Log the digests for easy copy-pasting into config file.
        Logger.d(TAG, "Accepting Android client with ${parser.applicationSignatureDigests.size} " +
                "signing certificates digests")
        for (n in 0..<parser.applicationSignatureDigests.size) {
            Logger.d(TAG, "Digest $n: ${parser.applicationSignatureDigests[n].toHex()}")
        }

    } catch (e: Exception) {
        throw IllegalStateException("Error parsing Android Attestation Extension", e)
    }
}


// This public key is from https://developer.android.com/training/articles/security-key-attestation
private val GOOGLE_ROOT_ATTESTATION_KEY =
    "30820222300d06092a864886f70d01010105000382020f003082020a0282020100afb6c7822bb1a701ec2bb42e8bcc541663abef982f32c77f7531030c97524b1b5fe809fbc72aa9451f743cbd9a6f1335744aa55e77f6b6ac3535ee17c25e639517dd9c92e6374a53cbfe258f8ffbb6fd129378a22a4ca99c452d47a59f3201f44197ca1ccd7e762fb2f53151b6feb2fffd2b6fe4fe5bc6bd9ec34bfe08239daafceb8eb5a8ed2b3acd9c5e3a7790e1b51442793159859811ad9eb2a96bbdd7a57c93a91c41fccd27d67fd6f671aa0b815261ad384fa37944864604ddb3d8c4f920a19b1656c2f14ad6d03c56ec060899041c1ed1a5fe6d3440b556bad1d0a152589c53e55d370762f0122eef91861b1b0e6c4c80927499c0e9bec0b83e3bc1f93c72c049604bbd2f1345e62c3f8e26dbec06c94766f3c128239d4f4312fad8123887e06becf567583bf8355a81feeabaf99a83c8df3e2a322afc672bf120b135158b6821ceaf309b6eee77f98833b018daa10e451f06a374d50781f359082966bb778b9308942698e74e0bcd24628a01c2cc03e51f0b3e5b4ac1e4df9eaf9ff6a492a77c1483882885015b422ce67b80b88c9b48e13b607ab545c723ff8c44f8f2d368b9f6520d31145ebf9e862ad71df6a3bfd2450959d653740d97a12f368b13ef66d5d0a54a6e2f5d9a6fef446832bc67844725861f093dd0e6f3405da89643ef0f4d69b6420051fdb93049673e36950580d3cdf4fbd08bc58483952600630203010001"
        .fromHex()
