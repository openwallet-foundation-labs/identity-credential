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
        val sod = SODFile(ByteArrayInputStream(data.sod))
        val bytes1 = data.dataGroups[1]!!
        val digest1Bytes = MessageDigest.getInstance(sod.digestAlgorithm).digest(bytes1)
        val dg1 = DG1File(ByteArrayInputStream(bytes1))
        if (!Arrays.equals(digest1Bytes, sod.dataGroupHashes[1])) {
            throw Exception("DG1 stream did not pass validation")
        }

        var photo: Bitmap? = null
        if (data.dataGroups.containsKey(2)) {
            val bytes = data.dataGroups[2]!!
            // NB: don't try to calculate digest wrapping ByteArrayInputStream with
            // DigestInputStream: DG2File constructor is not guaranteed to read it to the
            // end of the stream! (Saw such case in real life with a UK passport).
            val dg2 = DG2File(ByteArrayInputStream(bytes))
            val digestBytes = MessageDigest.getInstance(sod.digestAlgorithm).digest(bytes)
            if (!Arrays.equals(digestBytes, sod.dataGroupHashes[2])) {
                throw Exception("DG2 stream did not pass validation")
            }

            for (faceInfo in dg2.faceInfos) {
                for (faceImageInfo in faceInfo.faceImageInfos) {
                    photo = readImage(faceImageInfo)
                    if (photo != null) {
                        break
                    }
                }
            }
        }

        var signature: Bitmap? = null
        if (data.dataGroups.containsKey(7)) {
            val bytes = data.dataGroups[7]!!
            val digestBytes = MessageDigest.getInstance(sod.digestAlgorithm).digest(bytes)
            val dg7 = DG7File(ByteArrayInputStream(bytes))
            if (!Arrays.equals(digestBytes, sod.dataGroupHashes[7])) {
                throw Exception("DG7 stream did not pass validation")
            }

            for (image in dg7.images) {
                signature = readImage(image)
                if (signature != null) {
                    break
                }
            }
        }

        val sig = Signature.getInstance(
            sod.digestEncryptionAlgorithm, BouncyCastleProvider.PROVIDER_NAME)
        sig.initVerify(sod.docSigningCertificate)
        sig.update(sod.eContent)
        try {
            sig.verify(sod.encryptedDigest)
        } catch (err: Exception) {
            // TODO: specialized exception
            throw Exception("Passport data cannot be validated")
        }

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
        return if ("image/jp2" == imageInfo.mimeType) {
            Jpeg2kConverter(mTmpFolder).convertToBitmap(arr)
        } else {
            BitmapFactory.decodeByteArray(arr, 0, arr.size)
        }
    }
}
