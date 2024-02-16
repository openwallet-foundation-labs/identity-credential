package com.android.identity_credential.mrtd

import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import org.jmrtd.PassportService
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.IllegalArgumentException

class MrtdNfcDataReader(private val dataGroups: List<Int>) : MrtdNfcReader<MrtdNfcData> {
    override fun read(rawConnection: CardService, connection: PassportService,
                      onStatus: (MrtdNfc.Status) -> Unit): MrtdNfcData {
        var totalLength = 0
        var groupsRead = 0
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
                totalLength += stream.length
                groupsRead++
                onStatus(MrtdNfc.ReadingData(groupsRead))
                stream
            } catch (err: CardServiceException) {
                null
            }
        }.filterValues { it != null }
        val sodStream =
            connection.getInputStream(PassportService.EF_SOD, PassportService.DEFAULT_MAX_BLOCKSIZE)
        val sodLength = sodStream.length
        totalLength += sodLength
        val onProgress = { progress: Int ->
            val adjustedProgress = groupsRead + progress * (100 - groupsRead) / 100
            onStatus(MrtdNfc.ReadingData(adjustedProgress))
        }
        val sod = readStream(0, totalLength, sodStream, onProgress)
        var bytesRead = sodLength
        val dataGroupMap = streams.mapValues { entry ->
            val bytes = readStream(bytesRead, totalLength, entry.value!!, onProgress)
            bytesRead += entry.value!!.length
            bytes
        }
        return MrtdNfcData(dataGroupMap, sod)
    }

    companion object {
        fun readStream(
            bytesReadInitial: Int,
            bytesTotal: Int,
            inputStream: InputStream,
            onProgress: (Int) -> Unit
        ): ByteArray {
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var bytesRead = bytesReadInitial
            while (true) {
                val len = inputStream.read(buffer)
                if (len <= 0) {
                    break
                }
                bytesRead += len
                onProgress(bytesRead * 100 / bytesTotal)
                bytes.write(buffer, 0, len)
            }
            return bytes.toByteArray()
        }
    }
}