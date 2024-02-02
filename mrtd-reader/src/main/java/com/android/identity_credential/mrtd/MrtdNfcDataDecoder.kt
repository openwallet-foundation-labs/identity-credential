package com.android.identity_credential.mrtd

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.identity.jpeg2k.Jpeg2kConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import java.io.ByteArrayInputStream
import java.io.File
import java.security.DigestInputStream
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
        val digest1 = MessageDigest.getInstance(sod.digestAlgorithm)
        val dg1 = DG1File(DigestInputStream(ByteArrayInputStream(data.dg1), digest1))
        val digest2 = MessageDigest.getInstance(sod.digestAlgorithm)
        val dg2 = DG2File(DigestInputStream(ByteArrayInputStream(data.dg2), digest2))
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
        if (!Arrays.equals(digest2.digest(), sod.dataGroupHashes[2])) {
            throw Exception("DG2 stream cannot be validated")
        }
        if (!Arrays.equals(digest1.digest(), sod.dataGroupHashes[1])) {
            throw Exception("DG1 stream cannot be validated")
        }

        var photo: Bitmap? = null;
        for (faceInfo in dg2.faceInfos) {
            for (faceImageInfo in faceInfo.faceImageInfos) {
                val arr = ByteArray(faceImageInfo.imageLength)
                val stream = faceImageInfo.imageInputStream
                val len = stream.read(arr)
                if (len != faceImageInfo.imageLength) {
                    // Failed to read somehow
                    break;
                }
                photo = if ("image/jp2" == faceImageInfo.mimeType) {
                    Jpeg2kConverter(mTmpFolder).convertToBitmap(arr)
                } else {
                    BitmapFactory.decodeByteArray(arr, 0, arr.size)
                }
                break
            }
        }

        val info = dg1.mrzInfo
        return MrtdDecodedData(
            info.secondaryIdentifier,
            info.primaryIdentifier,
            info.issuingState,
            info.nationality,
            info.gender.toString(),
            photo
        )
    }
}