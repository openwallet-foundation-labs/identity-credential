package org.multipaz.testapp.provisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.device.DeviceAssertion
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.provisioning.CredentialConfiguration
import org.multipaz.provisioning.CredentialFormat
import org.multipaz.provisioning.CredentialRequest
import org.multipaz.provisioning.KeyPossessionChallenge
import org.multipaz.provisioning.KeyPossessionProof
import org.multipaz.provisioning.RequestCredentials
import org.multipaz.util.toBase64Url
import kotlin.time.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthInspector
import org.multipaz.rpc.handler.RpcAuthInspectorAssertion

@RpcState(endpoint = "openid4vci.cred.pofp")
@CborSerializable
class RequestCredentialsUsingProofOfPossession(
    val clientId: String,
    val issuanceClientId: String,
    override val documentId: String,
    override val credentialConfiguration: CredentialConfiguration,
    val credentialIssuerId: String,
    override var format: CredentialFormat? = null,
    var credentialRequests: List<ProofOfPossessionCredentialRequest>? = null,
) : AbstractRequestCredentials, RequestCredentials, RpcAuthInspector by RpcAuthBackendDelegate {
    override suspend fun getCredentialConfiguration(
        format: CredentialFormat
    ): CredentialConfiguration {
        checkClientId()
        this.format = format
        return credentialConfiguration
    }

    override suspend fun sendCredentials(
        credentialRequests: List<CredentialRequest>,
        keysAssertion: DeviceAssertion?
    ): List<KeyPossessionChallenge> {
        checkClientId()
        if (this.credentialRequests != null) {
            throw IllegalStateException("Credentials were already sent")
        }
        val deviceAttestation = RpcAuthInspectorAssertion.getClientDeviceAttestation(clientId)!!
        validateDeviceAssertionBindingKeys(
            deviceAttestation = deviceAttestation,
            keyAttestations = credentialRequests.map { it.secureAreaBoundKeyAttestation },
            deviceAssertion = keysAssertion!!,
            nonce = credentialConfiguration.challenge
        )
        val nonce = JsonPrimitive(credentialConfiguration.challenge.decodeToString())
        val requests = credentialRequests.map { request ->
            val header = buildJsonObject {
                put("typ", "openid4vci-proof+jwt")
                put("alg", "ES256")
                put("jwk", request.secureAreaBoundKeyAttestation.publicKey.toJwk())
            }.toString().encodeToByteArray().toBase64Url()
            val body = buildJsonObject {
                put("iss", clientId)
                put("aud", credentialIssuerId)
                put("iat", Clock.System.now().epochSeconds)
                put("nonce", nonce)
            }.toString().encodeToByteArray().toBase64Url()
            ProofOfPossessionCredentialRequest(request, format!!, "$header.$body")
        }
        this.credentialRequests = requests
        return requests.map {
            KeyPossessionChallenge(ByteString(it.proofOfPossessionJwtHeaderAndBody.encodeToByteArray()))
        }
    }

    override suspend fun sendPossessionProofs(keyPossessionProofs: List<KeyPossessionProof>) {
        checkClientId()
        if (keyPossessionProofs.size != credentialRequests?.size) {
            throw IllegalStateException("wrong number of key possession proofs: ${keyPossessionProofs.size}")
        }
        credentialRequests!!.zip(keyPossessionProofs).map {
            it.first.proofOfPossessionJwtSignature = it.second.signature.toByteArray().toBase64Url()
        }
    }

    private suspend fun checkClientId() {
        check(clientId == RpcAuthContext.getClientId())
    }

    companion object
}