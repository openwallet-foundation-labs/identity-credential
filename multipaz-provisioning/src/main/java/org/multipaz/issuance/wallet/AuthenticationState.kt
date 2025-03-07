package org.multipaz.issuance.wallet

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.device.AssertionNonce
import org.multipaz.device.DeviceAttestation
import org.multipaz.device.DeviceAttestationValidationData
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.annotation.FlowState
import org.multipaz.flow.server.Configuration
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.flow.server.getTable
import org.multipaz.issuance.AuthenticationFlow
import org.multipaz.issuance.ClientAuthentication
import org.multipaz.issuance.ClientChallenge
import org.multipaz.issuance.WalletServerCapabilities
import org.multipaz.issuance.WalletServerSettings
import org.multipaz.issuance.toCbor
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.toBase64Url
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlin.random.Random

@FlowState(flowInterface = AuthenticationFlow::class)
@CborSerializable
class AuthenticationState(
    var nonce: ByteString? = null,
    var clientId: String = "",
    var deviceAttestation: DeviceAttestation? = null,
    var authenticated: Boolean = false
) {
    companion object {
        val clientTableSpec = StorageTableSpec(
            name = "Clients",
            supportPartitions = false,
            supportExpiration = false
        )

        val walletAppCapabilitiesTableSpec = StorageTableSpec(
            name = "WalletAppCapabilities",
            supportPartitions = false,
            supportExpiration = false
        )
    }

    @FlowMethod
    suspend fun requestChallenge(env: FlowEnvironment, clientId: String): ClientChallenge {
        check(this.clientId.isEmpty())
        check(nonce != null)
        val clientTable = env.getTable(clientTableSpec)
        val clientData = clientTable.get(clientId)
        if (clientData != null) {
            this.deviceAttestation =
                ClientRecord.fromCbor(clientData.toByteArray()).deviceAttestation
            this.clientId = clientId
            println("Existing client id: ${this.clientId}")
        }

        if (this.clientId.isEmpty()) {
            this.clientId = Random.Default.nextBytes(18).toBase64Url()
            println("New client id: ${this.clientId}")
        }

        return ClientChallenge(nonce!!, this.clientId)
    }

    @FlowMethod
    suspend fun authenticate(env: FlowEnvironment, auth: ClientAuthentication): WalletServerCapabilities {
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val clientTable = env.getTable(clientTableSpec)

        val attestation = auth.attestation
        if (attestation != null) {
            if (this.deviceAttestation != null) {
                throw IllegalStateException("Client already registered")
            }
            attestation.validate(DeviceAttestationValidationData(
                attestationChallenge = clientId.encodeToByteString(),
                iosReleaseBuild = settings.iosReleaseBuild,
                iosAppIdentifier = settings.iosAppIdentifier,
                androidGmsAttestation = settings.androidRequireGmsAttestation,
                androidVerifiedBootGreen = settings.androidRequireVerifiedBootGreen,
                androidAppSignatureCertificateDigests = listOf()
            ))
            val clientData = ByteString(ClientRecord(attestation).toCbor())
            this.deviceAttestation = attestation
            clientTable.insert(data = clientData, key = clientId)
        }

        this.deviceAttestation!!.validateAssertion(auth.assertion)

        if ((auth.assertion.assertion as AssertionNonce).nonce != this.nonce) {
            throw IllegalArgumentException("nonce mismatch")
        }
        authenticated = true
        val walletAppCapabilitiesTable = env.getTable(walletAppCapabilitiesTableSpec)
        if (walletAppCapabilitiesTable.get(clientId) == null) {
            walletAppCapabilitiesTable.insert(
                key = clientId,
                data = ByteString(auth.walletApplicationCapabilities.toCbor()),
            )
        } else {
            walletAppCapabilitiesTable.update(
                key = clientId,
                data = ByteString(auth.walletApplicationCapabilities.toCbor())
            )
        }
        return WalletServerCapabilities(
            Clock.System.now()
        )
    }
}