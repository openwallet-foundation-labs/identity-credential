package org.multipaz.mrtd

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import org.jmrtd.PassportService
import java.io.InputStream
import kotlin.IllegalArgumentException

private const val TAG = "MrtdNfcDataReader"

class MrtdNfcDataReader(private val dataGroups: List<Int>) : MrtdNfcReader<MrtdNfcData> {
    override fun read(rawConnection: CardService, connection: PassportService?,
                      onStatus: (MrtdNfc.Status) -> Unit): MrtdNfcData {
        if (connection == null) {
            throw IllegalArgumentException("PassportService is null, card access was not performed")
        }
        var totalLength = 0
        var groupsRead = 0
        mrtdLogI(TAG, "Examining DGs")
        // report 1% progress per group info read (as it does not come free)
        val streams = dataGroups.associateBy({ dgIndex -> dgIndex }) {dgIndex ->
            if (dgIndex < 1 || dgIndex > 16) {
                throw IllegalArgumentException("Illegal data group: $dgIndex")
            }
            try {
                val stream = connection.getInputStream(
                    (PassportService.EF_DG1 + dgIndex - 1).toShort(),
                    PassportService.DEFAULT_MAX_BLOCKSIZE
                )
                val length = stream.length
                mrtdLogI(TAG, "DG$dgIndex length = $length")
                totalLength += length
                groupsRead++
                mrtdLogI(TAG, "Progress: $groupsRead%")
                onStatus(MrtdNfc.ReadingData(groupsRead))
                stream
            } catch (err: CardServiceException) {
                null
            }
        }.filterValues { it != null }
        mrtdLogI(TAG, "Examining SOD")
        val sodStream =
            connection.getInputStream(PassportService.EF_SOD, PassportService.DEFAULT_MAX_BLOCKSIZE)
        val sodLength = sodStream.length
        mrtdLogI(TAG, "SOD length = $sodLength")
        totalLength += sodLength
        val onProgress = { progress: Int ->
            val adjustedProgress = groupsRead + progress * (100 - groupsRead) / 100
            mrtdLogI(TAG, "Progress: $adjustedProgress%")
            onStatus(MrtdNfc.ReadingData(adjustedProgress))
        }
        mrtdLogI(TAG, "Reading SOD")
        val sod = readStream(0, totalLength, sodStream, onProgress)
        var bytesRead = sodLength
        val dataGroupMap = streams.mapValues { entry ->
            mrtdLogI(TAG, "Reading DG${entry.key}")
            val bytes = readStream(bytesRead, totalLength, entry.value!!, onProgress)
            bytesRead += entry.value!!.length
            bytes
        }
        mrtdLogI(TAG, "Finished reading")
        return MrtdNfcData(dataGroupMap, sod)
    }

    companion object {
        fun readStream(
            bytesReadInitial: Int,
            bytesTotal: Int,
            inputStream: InputStream,
            onProgress: (Int) -> Unit
        ): ByteString {
            val bytes = ByteStringBuilder()
            val buffer = ByteArray(1024)
            var bytesRead = bytesReadInitial
            while (true) {
                val len = inputStream.read(buffer)
                if (len <= 0) {
                    break
                }
                bytesRead += len
                onProgress(bytesRead * 100 / bytesTotal)
                bytes.append(buffer, 0, len)
            }
            return bytes.toByteString()
        }
    }
}