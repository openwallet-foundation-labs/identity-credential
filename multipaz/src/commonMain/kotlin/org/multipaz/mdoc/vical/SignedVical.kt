package org.multipaz.mdoc.vical

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.toDataItem
import org.multipaz.cbor.toDataItemDateTimeString
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.cose.CoseSign1
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain

/**
 * A signed VICAL according to ISO/IEC 18013-5:2021.
 *
 * @property vical the VICAL that is signed.
 * @property vicalProviderCertificateChain the X.509 certificate chain.
 */
data class SignedVical(
    val vical: Vical,
    val vicalProviderCertificateChain: X509CertChain,
) {
    /**
     * Generates a VICAL
     *
     * @param signingKey the key used to sign the VICAL. This must match the public key in the leaf
     * certificate in `vicalProviderCertificateChain`.
     * @param signingAlgorithm the algorithm used to make the signature
     * @return the bytes of the CBOR encoded COSE_Sign1 with the VICAL.
     */
    fun generate(
        signingKey: EcPrivateKey,
        signingAlgorithm: Algorithm
    ): ByteArray {
        val certInfosBuilder = CborArray.builder()
        for (certInfo in vical.certificateInfos) {
            val docTypesBuilder = CborArray.builder()
            certInfo.docType.forEach { docTypesBuilder.add(it) }

            certInfosBuilder.addMap()
                .put("certificate", certInfo.certificate.encodedCertificate)
                .put("serialNumber", Tagged(Tagged.UNSIGNED_BIGNUM, Bstr(certInfo.certificate.serialNumber.value)))
                .put("ski", certInfo.certificate.subjectKeyIdentifier!!)
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

    companion object {
        private const val TAG = "SignedVical"

        /**
         * Parses a signed VAL.
         *
         * This takes a `COSE_Sign1` according to ISO/IEC 18013-5:2021 section
         * C.1.7.1 VICAL CDDL profile.
         *
         * This includes checking that the VICAL is signed by the key in the leaf certificate
         * of the X.509 certificate chain. It is not checked that the certificate chain is
         * well-formed.
         *
         * @param encodedSignedVical the encoded CBOR with the COSE_Sign1 described above.
         * @return a `SignedVical` instance.
         * @throws IllegalArgumentException if the passed in signed VICAL is malformed or signature
         * verification failed.
         */
        fun parse(encodedSignedVical: ByteArray): SignedVical {
            val signature = CoseSign1.fromDataItem(Cbor.decode(encodedSignedVical))

            val vicalPayload = signature?.payload
                ?: throw IllegalArgumentException("Unexpected null payload for signed VICAL")

            val certChain = signature.unprotectedHeaders[CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN)]?.asX509CertChain
                ?: throw IllegalArgumentException("x5chain not set")

            val signatureAlgorithm = signature.protectedHeaders[CoseNumberLabel(Cose.COSE_LABEL_ALG)]?.asNumber?.toInt()
                ?.let { Algorithm.fromInt(it) }
                ?: throw IllegalArgumentException("Signature Algorithm not set")

            if (!Cose.coseSign1Check(
                certChain.certificates.first().ecPublicKey,
                null,
                signature,
                signatureAlgorithm
            )) {
                throw IllegalArgumentException("Signature check failed")
            }

            val vicalMap = Cbor.decode(vicalPayload)
            val version = vicalMap["version"].asTstr
            val vicalProvider = vicalMap["vicalProvider"].asTstr
            val date = vicalMap["date"].asDateTimeString
            val nextUpdate = vicalMap.getOrNull("nextUpdate")?.asDateTimeString
            val vicalIssueID = vicalMap.getOrNull("vicalIssueID")?.asNumber

            val certificateInfos = mutableListOf<VicalCertificateInfo>()

            for (certInfo in (vicalMap["certificateInfos"] as CborArray).items) {
                val certBytes = certInfo["certificate"].asBstr
                val docType = (certInfo["docType"] as CborArray).items.map { it.asTstr }
                val certProfiles = certInfo.getOrNull("certificateProfile")?.let {
                    (it as CborArray).items.map { it.asTstr }
                }
                certificateInfos.add(VicalCertificateInfo(
                    X509Cert(certBytes),
                    docType,
                    certProfiles
                ))
            }

            val vical = Vical(
                version,
                vicalProvider,
                date,
                nextUpdate,
                vicalIssueID,
                certificateInfos
            )

            return SignedVical(vical, certChain)
        }
    }
}