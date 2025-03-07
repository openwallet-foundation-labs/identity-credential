package org.multipaz_credential.wallet

import org.multipaz.issuance.evidence.EvidenceRequestIcaoNfcTunnelType
import org.multipaz.issuance.evidence.EvidenceResponseIcaoNfcTunnel
import org.multipaz.mrtd.MrtdNfc
import org.multipaz.mrtd.MrtdNfcReader
import kotlinx.io.bytestring.ByteString
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CommandAPDU
import org.jmrtd.PassportService

/**
 * Reads from NFC-card serving commands that come from NFC tunnel.
 */
class NfcTunnelScanner(private val provisioningViewModel: ProvisioningViewModel) :
    MrtdNfcReader<Unit> {
    override fun read(
        rawConnection: CardService,
        connection: PassportService?,
        onStatus: (MrtdNfc.Status) -> Unit
    ) {
        provisioningViewModel.runIcaoNfcTunnel { request ->
            val command = CommandAPDU(request.message.toByteArray())

            val rawCommand = if (request.passThrough) {
                command // pass as is to the chip
            } else {
                val wrapper = connection?.wrapper
                if (wrapper != null) {
                    wrapper.wrap(command)
                } else {
                    command
                }
            }
            onStatus(
                if (request.requestType == EvidenceRequestIcaoNfcTunnelType.AUTHENTICATING) {
                    MrtdNfc.TunnelAuthenticating(request.progressPercent)
                } else {
                    MrtdNfc.TunnelReading(request.progressPercent)
                }
            )
            val rawResponse = rawConnection.transmit(rawCommand)
            val response = if (request.passThrough) {
                rawResponse // transmit as is into the tunnel
            } else {
                val wrapper = connection?.wrapper
                if (wrapper != null) {
                    wrapper.unwrap(rawResponse)
                } else {
                    rawResponse
                }
            }
            EvidenceResponseIcaoNfcTunnel(ByteString(response.bytes))
        }
    }
}