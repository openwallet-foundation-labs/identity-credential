package com.android.identity_credential.wallet

import com.android.identity.issuance.evidence.EvidenceRequestIcaoNfcTunnelType
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnel
import com.android.identity_credential.mrtd.MrtdNfc
import com.android.identity_credential.mrtd.MrtdNfcReader
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CommandAPDU
import org.jmrtd.PassportService

/**
 * Reads from NFC-card serving commands that come from NFC tunnel.
 */
class NfcTunnelScanner(private val provisioningViewModel: ProvisioningViewModel) : MrtdNfcReader<Unit> {
    override fun read(
        rawConnection: CardService,
        connection: PassportService,
        onStatus: (MrtdNfc.Status) -> Unit
    ) {
        provisioningViewModel.runIcaoNfcTunnel { request ->
            val command = CommandAPDU(request.message)

            val rawCommand = if (request.requestType == EvidenceRequestIcaoNfcTunnelType.READING_ENCRYPTED) {
                command // already encrypted from tunnel
            } else {
                val wrapper = connection.wrapper
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
            val response = if (request.requestType == EvidenceRequestIcaoNfcTunnelType.READING_ENCRYPTED) {
                rawResponse // transmit encrypted into the tunnel
            } else {
                val wrapper = connection.wrapper
                if (wrapper != null) {
                    wrapper.unwrap(rawResponse)
                } else {
                    rawResponse
                }
            }
            EvidenceResponseIcaoNfcTunnel(response.bytes)
        }
    }
}