package org.multipaz.issuance.funke

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.device.DeviceAssertion
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.annotation.FlowState
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.flow.server.getTable
import org.multipaz.issuance.CredentialConfiguration
import org.multipaz.issuance.CredentialFormat
import org.multipaz.issuance.CredentialRequest
import org.multipaz.issuance.KeyPossessionChallenge
import org.multipaz.issuance.KeyPossessionProof
import org.multipaz.issuance.RequestCredentialsFlow
import org.multipaz.issuance.validateDeviceAssertionBindingKeys
import org.multipaz.issuance.wallet.AuthenticationState
import org.multipaz.issuance.wallet.ClientRecord
import org.multipaz.issuance.wallet.fromCbor
import org.multipaz.util.toBase64Url
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@FlowState(
    flowInterface = RequestCredentialsFlow::class
)
@CborSerializable
class RequestCredentialsUsingProofOfPossession(
    val clientId: String,
    val issuanceClientId: String,
    documentId: String,
    credentialConfiguration: CredentialConfiguration,
    val credentialIssuerUri: String,
    format: CredentialFormat? = null,
    var credentialRequests: List<ProofOfPossessionCredentialRequest>? = null,
) : AbstractRequestCredentials(documentId, credentialConfiguration, format) {
    companion object

    @FlowMethod
    fun getCredentialConfiguration(
        env: FlowEnvironment,
        format: CredentialFormat
    ): CredentialConfiguration {
        this.format = format
        return credentialConfiguration
    }

    @FlowMethod
    suspend fun sendCredentials(
        env: FlowEnvironment,
        newCredentialRequests: List<CredentialRequest>,
        keysAssertion: DeviceAssertion?
    ): List<KeyPossessionChallenge> {
        if (credentialRequests != null) {
            throw IllegalStateException("Credentials were already sent")
        }
        val storage = env.getTable(AuthenticationState.clientTableSpec)
        val clientRecord = ClientRecord.fromCbor(storage.get(clientId)!!.toByteArray())
        validateDeviceAssertionBindingKeys(
            env = env,
            deviceAttestation = clientRecord.deviceAttestation,
            keyAttestations = newCredentialRequests.map { it.secureAreaBoundKeyAttestation },
            deviceAssertion = keysAssertion!!,
            nonce = credentialConfiguration.challenge
        )
        val nonce = JsonPrimitive(String(credentialConfiguration.challenge.toByteArray()))
        val requests = newCredentialRequests.map { request ->
            val header = JsonObject(mapOf(
                "typ" to JsonPrimitive("openid4vci-proof+jwt"),
                "alg" to JsonPrimitive("ES256"),
                "jwk" to request.secureAreaBoundKeyAttestation.publicKey.toJson(null)
            )).toString().toByteArray().toBase64Url()
            val body = JsonObject(mapOf(
                "iss" to JsonPrimitive(issuanceClientId),
                "aud" to JsonPrimitive(credentialIssuerUri),
                "iat" to JsonPrimitive(Clock.System.now().epochSeconds),
                "nonce" to nonce
            )).toString().toByteArray().toBase64Url()
            ProofOfPossessionCredentialRequest(request, format!!, "$header.$body")
        }
        credentialRequests = requests
        return requests.map {
            KeyPossessionChallenge(ByteString(it.proofOfPossessionJwtHeaderAndBody.toByteArray()))
        }
    }

    @FlowMethod
    fun sendPossessionProofs(env: FlowEnvironment, keyPossessionProofs: List<KeyPossessionProof>) {
        if (keyPossessionProofs.size != credentialRequests?.size) {
            throw IllegalStateException("wrong number of key possession proofs: ${keyPossessionProofs.size}")
        }
        credentialRequests!!.zip(keyPossessionProofs).map {
            it.first.proofOfPossessionJwtSignature = it.second.signature.toByteArray().toBase64Url()
        }
    }
}