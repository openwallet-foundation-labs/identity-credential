package com.android.identity_credential.mrtd

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.identity.jpeg2k.Jpeg2kConverter
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
class MrtdNfcDataDecoder(private val mTmpFolder: File) {
    /**
     * Validates and decodes [MrtdNfcData].
     *
     * This should generally be called on a background thread.
     *
     * TODO: validate certificate used for data signing
     */
    fun decode(data: MrtdNfcData): MrtdDecodedData {
        mrtdLogI(TAG, "Decoding SOD")
        val sod = SODFile(ByteArrayInputStream(data.sod))
        mrtdLogI(TAG, "SOD decoded, digest algorithm: ${sod.digestAlgorithm}")
        val bytes1 = data.dataGroups[1]!!
        val digest1Bytes = MessageDigest.getInstance(sod.digestAlgorithm).digest(bytes1)
        if (!Arrays.equals(digest1Bytes, sod.dataGroupHashes[1])) {
            mrtdLogE(TAG, "DG1 digest mismatch")
            throw Exception("DG1 stream did not pass validation")
        }
        mrtdLogI(TAG, "Decoding DG1")
        val dg1 = DG1File(ByteArrayInputStream(bytes1))
        mrtdLogI(TAG, "Done with DG1")

        var photo: Bitmap? = null
        if (data.dataGroups.containsKey(2)) {
            val bytes = data.dataGroups[2]!!
            // NB: don't try to calculate digest wrapping ByteArrayInputStream with
            // DigestInputStream: DG2File constructor is not guaranteed to read it to the
            // end of the stream! (Saw such case in real life with a UK passport).
            val digestBytes = MessageDigest.getInstance(sod.digestAlgorithm).digest(bytes)
            if (!Arrays.equals(digestBytes, sod.dataGroupHashes[2])) {
                mrtdLogE(TAG, "DG2 digest mismatch")
                throw Exception("DG2 stream did not pass validation")
            }

            mrtdLogI(TAG, "Decoding DG2")
            val dg2 = DG2File(ByteArrayInputStream(bytes))
            for (faceInfo in dg2.faceInfos) {
                for (faceImageInfo in faceInfo.faceImageInfos) {
                    mrtdLogI(TAG, "reading face image from DG2")
                    photo = readImage(faceImageInfo)
                    if (photo != null) {
                        break
                    }
                }
            }
            mrtdLogI(TAG, "Done with DG2")
        }

        var signature: Bitmap? = null
        if (data.dataGroups.containsKey(7)) {
            val bytes = data.dataGroups[7]!!
            val digestBytes = MessageDigest.getInstance(sod.digestAlgorithm).digest(bytes)
            if (!Arrays.equals(digestBytes, sod.dataGroupHashes[7])) {
                mrtdLogE(TAG, "DG7 digest mismatch")
                throw Exception("DG7 stream did not pass validation")
            }
            mrtdLogI(TAG, "Decoding DG7")
            val dg7 = DG7File(ByteArrayInputStream(bytes))

            for (image in dg7.images) {
                mrtdLogI(TAG, "reading signature from DG7")
                signature = readImage(image)
                if (signature != null) {
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
            info.secondaryIdentifier,
            info.primaryIdentifier,
            info.issuingState,
            info.nationality,
            info.gender.toString(),
            photo,
            signature
        )
    }

    private fun readImage(imageInfo: AbstractImageInfo): Bitmap? {
        val arr = ByteArray(imageInfo.imageLength)
        val stream = imageInfo.imageInputStream
        val len = stream.read(arr)
        if (len != imageInfo.imageLength) {
            // Failed to read somehow
            return null
        }
        mrtdLogI(TAG, "Image type: ${imageInfo.mimeType}")
        return if ("image/jp2" == imageInfo.mimeType) {
            Jpeg2kConverter(mTmpFolder).convertToBitmap(arr)
        } else {
            BitmapFactory.decodeByteArray(arr, 0, arr.size)
        }
    }
}
