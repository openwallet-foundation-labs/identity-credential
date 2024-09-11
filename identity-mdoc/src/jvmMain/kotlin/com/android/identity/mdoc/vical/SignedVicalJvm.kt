package com.android.identity.mdoc.vical

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.toDataItem
import com.android.identity.cbor.toDataItemDateTimeString
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseNumberLabel
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.javaX509Certificate
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import java.security.cert.X509Certificate

// This is currently Java-only because we need Java-only functionality in X509Certificate

/**
 * Generates a VICAL
 *
 * @param signingKey the key used to sign the VICAL. This must match the public key in the leaf
 * certificate in `vicalProviderCertificateChain`.
 * @param signingAlgorithm the algorithm used to make the signature
 * @return the bytes of the CBOR encoded COSE_Sign1 with the VICAL.
 */
fun SignedVical.generate(
    signingKey: EcPrivateKey,
    signingAlgorithm: Algorithm
): ByteArray {
    val certInfosBuilder = CborArray.builder()
    for (certInfo in vical.certificateInfos) {
        val javaCert = X509Cert(certInfo.certificate).javaX509Certificate

        val docTypesBuilder = CborArray.builder()
        certInfo.docType.forEach { docTypesBuilder.add(it) }

        certInfosBuilder.addMap()
            .put("certificate", certInfo.certificate)
            .put("serialNumber", Tagged(2, Bstr(javaCert.serialNumber.toByteArray())))
            .put("ski", javaCert.subjectKeyIdentifier)
            .put("docType", docTypesBuilder.end().build())
            .end()
    }

    val vicalBuilder = CborMap.builder()
        .put("version", vical.version)
        .put("vicalProvider", vical.vicalProvider)
        .put("date", vical.date.toDataItemDateTimeString())
    vical.nextUpdate?.let { vicalBuilder.put("nextUpdate", it.toDataItemDateTimeString())}
    vical.vicalIssueID?.let { vicalBuilder.put("vicalIssueID", it.toDataItem()) }
    vicalBuilder.put("certificateInfos", certInfosBuilder.end().build())

    val encodedVical = Cbor.encode(vicalBuilder.end().build())

    val signature = Cose.coseSign1Sign(
        key = signingKey,
        dataToSign = encodedVical,
        includeDataInPayload = true,
        signatureAlgorithm = signingAlgorithm,
        protectedHeaders = mapOf(
            Pair(CoseNumberLabel(Cose.COSE_LABEL_ALG), signingAlgorithm.coseAlgorithmIdentifier.toDataItem())
        ),
        unprotectedHeaders = mapOf(
            Pair(CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN), vicalProviderCertificateChain.toDataItem())
        )
    )

    return Cbor.encode(signature.toDataItem())
}

/**
 * Get the Subject Key Identifier Extension from the X509 certificate.
 */
private val X509Certificate.subjectKeyIdentifier: ByteArray
    get() {
        val extensionValue = this.getExtensionValue(Extension.subjectKeyIdentifier.id)
            ?: throw IllegalArgumentException("No SubjectKeyIdentifier extension")
        val octets = DEROctetString.getInstance(extensionValue).octets
        val subjectKeyIdentifier = SubjectKeyIdentifier.getInstance(octets)
        return subjectKeyIdentifier.keyIdentifier
    }
