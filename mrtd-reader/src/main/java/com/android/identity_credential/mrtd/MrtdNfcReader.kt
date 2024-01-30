package com.android.identity_credential.mrtd

import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import net.sf.scuba.smartcards.ISO7816
import org.jmrtd.BACKey
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads [MrtdMrzData] from an NFC card, emitting [Status] as the reading progresses.
 */
class MrtdNfcReader(private val shouldCheckMac: Boolean) {
    internal var service: PassportService? = null

    sealed class Status
    object Initial : Status()
    object Connected : Status()
    object AttemptingPACE : Status()
    object PACESucceeded : Status()
    object PACENotSupported : Status()
    object PACEFailed : Status()
    object AttemptingBAC : Status()
    object BACSucceeded : Status()
    data class ReadingData(val read: Int, val total: Int) : Status()
    object Finished : Status()

    /**
     * Reads NFC card.
     *
     * This must be called on a background thread.
     */
    fun read(
        cardService: CardService, mrzData: MrtdMrzData, onStatus: (Status) -> Unit
    ): MrtdNfcData {
        cardService.open()
        val service = PassportService(
            cardService,
            PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
            PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
            false,
            shouldCheckMac
        )
        this.service = service  // for testing
        service.wrapper
        service.open()
        onStatus(Connected)

        val bacKeySpec =
            BACKey(mrzData.documentNumber, mrzData.dateOfBirth, mrzData.dateOfExpiration)

        var hasPaceSucceeded = false
        try {
            val cardAccessFile = CardAccessFile(
                service.getInputStream(
                    PassportService.EF_CARD_ACCESS, PassportService.DEFAULT_MAX_BLOCKSIZE
                )
            )
            for (securityInfo in cardAccessFile.securityInfos) {
                if (securityInfo is PACEInfo) {
                    onStatus(AttemptingPACE)
                    service.doPACE(
                        bacKeySpec,
                        securityInfo.objectIdentifier,
                        PACEInfo.toParameterSpec(securityInfo.parameterId),
                        securityInfo.parameterId
                    )
                    onStatus(PACESucceeded)
                    hasPaceSucceeded = true
                    break
                }
            }
        } catch (err: CardServiceException) {
            if (err.sw == ISO7816.SW_FILE_NOT_FOUND.toInt()) {
                onStatus(PACENotSupported)  // Certainly acceptable
            } else {
                onStatus(PACEFailed)  // Questionable, but happens in reality
            }
        }

        service.sendSelectApplet(hasPaceSucceeded)

        if (!hasPaceSucceeded) {
            onStatus(AttemptingBAC)
            service.doBAC(bacKeySpec)
            onStatus(BACSucceeded)
        }

        val dg1Stream =
            service.getInputStream(PassportService.EF_DG1, PassportService.DEFAULT_MAX_BLOCKSIZE)
        val dg2Stream =
            service.getInputStream(PassportService.EF_DG2, PassportService.DEFAULT_MAX_BLOCKSIZE)
        val sodStream =
            service.getInputStream(PassportService.EF_SOD, PassportService.DEFAULT_MAX_BLOCKSIZE)
        val length = dg1Stream.length + dg2Stream.length + sodStream.length
        val dg1 = readStream(0, length, dg1Stream, onStatus)
        val dg2 = readStream(dg1.size, length, dg2Stream, onStatus)
        val sod = readStream(dg1.size + dg2.size, length, sodStream, onStatus)
        onStatus(Finished)
        return MrtdNfcData(dg1, dg2, sod)
    }

    private fun readStream(
        bytesReadInitial: Int, bytesTotal: Int, inputStream: InputStream, onStatus: (Status) -> Unit
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
            onStatus(ReadingData(bytesRead, bytesTotal))
            bytes.write(buffer, 0, len)
        }
        return bytes.toByteArray()
    }
}