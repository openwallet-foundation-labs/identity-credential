package org.multipaz.mrtd

import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import net.sf.scuba.smartcards.ISO7816
import org.jmrtd.AccessKeySpec
import org.jmrtd.BACKey
import org.jmrtd.PACEKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo

private const val TAG = "MrtdNfcChipAccess"

/**
 * Implements Chip Access Procedure (see ICAO 9303 part 11, Section 4.2), establishing encrypted
 * connection to the chip. Emits [MrtdNfc.Status] as the reading progresses.
 */
class MrtdNfcChipAccess(private val shouldCheckMac: Boolean) {
    internal var service: PassportService? = null

    /**
     * Opens NFC stream for reading, executing some kind of access control protocol.
     *
     * This must be called on a background thread.
     */
    fun open(cardService: CardService, accessData: MrtdAccessData,
             onStatus: (MrtdNfc.Status) -> Unit): PassportService {
        mrtdLogI(TAG, "Opening NFC connection")
        cardService.open()
        val service = PassportService(
            cardService,
            PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
            PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
            false,
            shouldCheckMac
        )
        this.service = service  // for testing
        service.open()
        mrtdLogI(TAG, "NFC connection opened")
        onStatus(MrtdNfc.Connected)


        val accessKeySpec: AccessKeySpec = when (accessData) {
            is MrtdAccessDataMrz -> BACKey(accessData.documentNumber, accessData.dateOfBirth, accessData.dateOfExpiration)
            is MrtdAccessDataCan -> PACEKeySpec(accessData.canCode, PassportService.CAN_PACE_KEY_REFERENCE)
            is MrtdAccessDataPin -> PACEKeySpec(accessData.pinCode, PassportService.PIN_PACE_KEY_REFERENCE)
        }

        var hasPaceSucceeded = false
        try {
            mrtdLogI(TAG, "reading EF_CARD_ACCESS")
            val cardAccessFile = CardAccessFile(
                service.getInputStream(
                    PassportService.EF_CARD_ACCESS, PassportService.DEFAULT_MAX_BLOCKSIZE
                )
            )
            for (securityInfo in cardAccessFile.securityInfos) {
                if (securityInfo is PACEInfo) {
                    mrtdLogI(TAG, "attempting PACE")
                    onStatus(MrtdNfc.AttemptingPACE)
                    service.doPACE(
                        accessKeySpec,
                        securityInfo.objectIdentifier,
                        PACEInfo.toParameterSpec(securityInfo.parameterId),
                        securityInfo.parameterId
                    )
                    mrtdLogI(TAG, "PACE succeeded")
                    onStatus(MrtdNfc.PACESucceeded)
                    hasPaceSucceeded = true
                    break
                }
            }
        } catch (err: CardServiceException) {
            if (err.sw == ISO7816.SW_FILE_NOT_FOUND.toInt()) {
                mrtdLogI(TAG, "PACE not supported")
                onStatus(MrtdNfc.PACENotSupported)  // Certainly acceptable
            } else {
                mrtdLogI(TAG, "PACE failed")
                onStatus(MrtdNfc.PACEFailed)  // Questionable, but happens in reality
            }
        }

        service.sendSelectApplet(hasPaceSucceeded)

        if (!hasPaceSucceeded && accessKeySpec is BACKey) {
            mrtdLogI(TAG, "attempting BAC")
            onStatus(MrtdNfc.AttemptingBAC)
            service.doBAC(accessKeySpec)
            mrtdLogI(TAG, "BAC succeeded")
            onStatus(MrtdNfc.BACSucceeded)
        }

        return service
    }
}