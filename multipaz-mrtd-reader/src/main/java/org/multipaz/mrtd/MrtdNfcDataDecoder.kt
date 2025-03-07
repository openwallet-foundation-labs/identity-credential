package org.multipaz.mrtd

import kotlinx.io.bytestring.ByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jmrtd.lds.AbstractImageInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.icao.DG7File
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.Signature
import java.util.Arrays

private const val TAG = "MrtdNfcDataDecoder"

/**
 * Decoder that validates [MrtdNfcData] and converts it to [MrtdDecodedData].
 */
class MrtdNfcDataDecoder() {
    /**
     * Validates and decodes [MrtdNfcData].
     *
     * This should generally be called on a background thread.
     *
     * TODO: validate certificate used for data signing
     */
    fun decode(data: MrtdNfcData): MrtdDecodedData {
        mrtdLogI(TAG, "Decoding SOD")
        val sod = SODFile(ByteArrayInputStream(data.sod.toByteArray()))
        mrtdLogI(TAG, "SOD decoded, digest algorithm: ${sod.digestAlgorithm}")
        val bytes1 = data.dataGroups[1]!!.toByteArray()
        val digest1Bytes = MessageDigest.getInstance(sod.digestAlgorithm).digest(bytes1)
        if (!digest1Bytes.contentEquals(sod.dataGroupHashes[1])) {
            mrtdLogE(TAG, "DG1 digest mismatch")
            throw Exception("DG1 stream did not pass validation")
        }
        mrtdLogI(TAG, "Decoding DG1")
        val dg1 = DG1File(ByteArrayInputStream(bytes1))
        mrtdLogI(TAG, "Done with DG1")

        var photo: ByteString? = null
        var photoMediaType: String? = null
        if (data.dataGroups.containsKey(2)) {
            val bytes = data.dataGroups[2]!!.toByteArray()
            // NB: don't try to calculate digest wrapping ByteArrayInputStream with
            // DigestInputStream: DG2File constructor is not guaranteed to read it to the
            // end of the stream! (Saw such case in real life with a UK passport).
            val digestBytes = MessageDigest.getInstance(sod.digestAlgorithm).digest(bytes)
            if (!digestBytes.contentEquals(sod.dataGroupHashes[2])) {
                mrtdLogE(TAG, "DG2 digest mismatch")
                throw Exception("DG2 stream did not pass validation")
            }

            mrtdLogI(TAG, "Decoding DG2")
            val dg2 = DG2File(ByteArrayInputStream(bytes))
            for (faceInfo in dg2.faceInfos) {
                for (faceImageInfo in faceInfo.faceImageInfos) {
                    mrtdLogI(TAG, "reading face image from DG2")
                    photo = readImageBytes(faceImageInfo)
                    if (photo != null) {
                        photoMediaType = faceImageInfo.mimeType
                        break
                    }
                }
            }
            mrtdLogI(TAG, "Done with DG2")
        }

        var signature: ByteString? = null
        var signatureMediaType: String? = null;
        if (data.dataGroups.containsKey(7)) {
            val bytes = data.dataGroups[7]!!.toByteArray()
            val digestBytes = MessageDigest.getInstance(sod.digestAlgorithm).digest(bytes)
            if (!Arrays.equals(digestBytes, sod.dataGroupHashes[7])) {
                mrtdLogE(TAG, "DG7 digest mismatch")
                throw Exception("DG7 stream did not pass validation")
            }
            mrtdLogI(TAG, "Decoding DG7")
            val dg7 = DG7File(ByteArrayInputStream(bytes))

            for (image in dg7.images) {
                mrtdLogI(TAG, "reading signature from DG7")
                signature = readImageBytes(image)
                if (signature != null) {
                    signatureMediaType = image.mimeType
                    break
                }
            }
            mrtdLogI(TAG, "Done with DG7")
        }

        mrtdLogI(TAG, "SOD signature algorithm: ${sod.digestEncryptionAlgorithm}")

        var digestEncryptionAlgorithm = sod.digestEncryptionAlgorithm
        if (sod.digestEncryptionAlgorithm.equals("SSAwithRSA/PSS")) {
            val digestAlg = sod.signerInfoDigestAlgorithm
            digestEncryptionAlgorithm = digestAlg.replace("-", "") + "withRSA/PSS";
            mrtdLogI(TAG, "Modified SOD signature algorithm: $digestEncryptionAlgorithm")
        }

        val sig = Signature.getInstance(
            digestEncryptionAlgorithm, BouncyCastleProvider.PROVIDER_NAME)
        sig.initVerify(sod.docSigningCertificate)
        sig.update(sod.eContent)
        try {
            sig.verify(sod.encryptedDigest)
        } catch (err: Exception) {
            // TODO: specialized exception
            mrtdLogE(TAG, "SOD signature verification failed", err)
            throw Exception("Passport data cannot be validated")
        }

        mrtdLogI(TAG, "data decoded")

        val info = dg1.mrzInfo
        return MrtdDecodedData(
            firstName = info.secondaryIdentifier,
            lastName = info.primaryIdentifier,
            firstNameComponents = info.secondaryIdentifierComponents.toList(),
            issuingState = info.issuingState,
            nationality = info.nationality,
            gender = info.gender.toString(),
            dateOfBirth = info.dateOfBirth,
            dateOfExpiry = info.dateOfExpiry,
            documentCode = info.documentCode,
            documentNumber = info.documentNumber,
            personalNumber = info.personalNumber,
            optionalData1 = info.optionalData1,
            optionalData2 = info.optionalData2,
            photoMediaType = photoMediaType,
            photo = photo,
            signatureMediaType = signatureMediaType,
            signature = signature
        )
    }

    private fun readImageBytes(imageInfo: AbstractImageInfo): ByteString? {
        val arr = ByteArray(imageInfo.imageLength)
        val stream = imageInfo.imageInputStream
        val len = stream.read(arr)
        if (len != imageInfo.imageLength) {
            // Failed to read somehow
            return null
        }
        return ByteString(arr)
    }
}
