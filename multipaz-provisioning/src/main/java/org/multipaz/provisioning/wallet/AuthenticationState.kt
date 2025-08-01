package org.multipaz.provisioning.wallet

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.device.AssertionNonce
import org.multipaz.device.DeviceAttestation
import org.multipaz.device.DeviceAttestationValidationData
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.getTable
import org.multipaz.provisioning.Authentication
import org.multipaz.provisioning.ClientAuthentication
import org.multipaz.provisioning.ClientChallenge
import org.multipaz.provisioning.ProvisioningBackendCapabilities
import org.multipaz.provisioning.ProvisioningBackendSettings
import org.multipaz.provisioning.toCbor
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.toBase64Url
import kotlin.time.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.buildByteString
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.device.fromCbor
import org.multipaz.device.toCbor
import org.multipaz.rpc.handler.RpcAuthInspectorAssertion
import kotlin.random.Random

@RpcState(
    endpoint = "auth",
    creatable = true
)
@CborSerializable
class AuthenticationState(
    var nonce: ByteString? = buildByteString { Random.nextBytes(16) },
    var clientId: String = "",
    var deviceAttestation: DeviceAttestation? = null,
    var authenticated: Boolean = false
): Authentication {
    companion object {
        val walletAppCapabilitiesTableSpec = StorageTableSpec(
            name = "WalletAppCapabilities",
            supportPartitions = false,
            supportExpiration = false
        )
    }

    override suspend fun requestChallenge(clientId: String): ClientChallenge {
        check(this.clientId.isEmpty())
        check(nonce != null)
        val clientTable = BackendEnvironment.getTable(RpcAuthInspectorAssertion.rpcClientTableSpec)
        val clientData = clientTable.get(clientId)
        if (clientData != null) {
            this.deviceAttestation = DeviceAttestation.fromCbor(clientData.toByteArray())
            this.clientId = clientId
            println("Existing client id: ${this.clientId}")
        }

        if (this.clientId.isEmpty()) {
            this.clientId = Random.Default.nextBytes(18).toBase64Url()
            println("New client id: ${this.clientId}")
        }

        return ClientChallenge(nonce!!, this.clientId)
    }

    override suspend fun authenticate(auth: ClientAuthentication): ProvisioningBackendCapabilities {
        val settings = ProvisioningBackendSettings(BackendEnvironment.getInterface(Configuration::class)!!)
        val clientTable = BackendEnvironment.getTable(RpcAuthInspectorAssertion.rpcClientTableSpec)

        val attestation = auth.attestation
        if (attestation != null) {
            // This can be used to dump an example of DeviceAttestation
            //println("----- Attestation: $clientId")
            //println(Base64.Mime.encode(attestation.toCbor()))
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
            val clientData = ByteString(attestation.toCbor())
            this.deviceAttestation = attestation
            clientTable.insert(data = clientData, key = clientId)
        }

        this.deviceAttestation!!.validateAssertion(auth.assertion)

        // This can be used to dump an example of DeviceAssertion
        //println("---- Assertion")
        //println(Base64.Mime.encode(auth.assertion.toCbor()))

        if ((auth.assertion.assertion as AssertionNonce).nonce != this.nonce) {
            throw IllegalArgumentException("nonce mismatch")
        }
        authenticated = true
        val walletAppCapabilitiesTable = BackendEnvironment.getTable(walletAppCapabilitiesTableSpec)
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
        return ProvisioningBackendCapabilities(
            Clock.System.now()
        )
    }
}