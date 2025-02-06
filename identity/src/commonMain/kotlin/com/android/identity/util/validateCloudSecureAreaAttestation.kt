package com.android.identity.util

import com.android.identity.asn1.OID
import com.android.identity.cbor.Cbor
import com.android.identity.crypto.X509CertChain
import com.android.identity.securearea.cloud.CloudAttestationExtension
import kotlinx.io.bytestring.ByteString

private const val TAG = "validateCloudSecureAreaAttestation"

fun isCloudKeyAttestation(chain: X509CertChain): Boolean {
    return chain.certificates.first()
        .getExtensionValue(OID.X509_EXTENSION_MULTIPAZ_KEY_ATTESTATION.oid) != null
}

fun validateCloudKeyAttestation(
    chain: X509CertChain,
    nonce: ByteString,
    trustedRootKeys: Set<ByteString>
) {
    check(chain.validate()) {
        "Certificate chain did not validate"
    }
    val certificates = chain.certificates
    val leafX509Cert = certificates.first()
    val extensionDerEncodedString = leafX509Cert.getExtensionValue(OID.X509_EXTENSION_MULTIPAZ_KEY_ATTESTATION.oid)
        ?: throw IllegalStateException(
            "No attestation extension at OID ${OID.X509_EXTENSION_MULTIPAZ_KEY_ATTESTATION.oid}")

    val attestation = CloudAttestationExtension.decode(ByteString(extensionDerEncodedString))
    if (attestation.challenge != nonce) {
        throw IllegalStateException("Challenge in attestation does match expected nonce")
    }

    val rootPublicKey = certificates.last().ecPublicKey.toDataItem()
    val trusted = trustedRootKeys.firstOrNull { trustedKey ->
        Cbor.decode(trustedKey.toByteArray()) == rootPublicKey
    }

    if (trusted == null) {
        throw IllegalArgumentException("Unexpected cloud attestation root")
    }
}