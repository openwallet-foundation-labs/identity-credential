package org.multipaz.server.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.EcPublicKey
import org.multipaz.document.NameSpacedData
import org.multipaz.storage.StorageTableSpec
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.multipaz.models.verifier.Openid4VpVerifierModel
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.getTable

//------------ JSON-formatted replies from various OpenID4VCI servlets

@Serializable
data class ParResponse(
    @SerialName("request_uri") val requestUri: String,
    @SerialName("expires_in") val expiresIn: Int
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("c_nonce") val cNonce: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("c_nonce_expires_in") val cNonceExpiresIn: Int,
    @SerialName("token_type") val tokenType: String
)

//--------------- authorization session data stored in the database ------------

@CborSerializable
data class IssuanceState(
    val clientId: String,
    val scope: String,
    val dpopKey: EcPublicKey,
    var redirectUri: String?,
    var codeChallenge: ByteString?,
    var dpopNonce: ByteString? = null,
    var cNonce: ByteString? = null,
    var openid4VpVerifierModel: Openid4VpVerifierModel? = null,
    var credentialData: NameSpacedData? = null
) {
    companion object {
        private val tableSpec = StorageTableSpec(
            name = "Openid4VciIssuanceSession",
            supportPartitions = false,
            supportExpiration = false
        )

        suspend fun createIssuanceState(issuanceState: IssuanceState): String {
            return BackendEnvironment.getTable(tableSpec).insert(
                key = null,
                data = ByteString(issuanceState.toCbor())
            )
        }

        suspend fun updateIssuanceState(issuanceStateId: String, issuanceState: IssuanceState) {
            return BackendEnvironment.getTable(tableSpec).update(
                key = issuanceStateId,
                data = ByteString(issuanceState.toCbor())
            )
        }

        suspend fun getIssuanceState(issuanceStateId: String): IssuanceState {
            val data = BackendEnvironment.getTable(tableSpec).get(issuanceStateId)
                ?: throw IllegalStateException("Unknown or stale issuance session")
            return IssuanceState.fromCbor(data.toByteArray())
        }
    }
}

/**
 * Types of opaque session ids for client-server communication.
 */
enum class OpaqueIdType {
    PAR_CODE,
    AUTHORIZATION_STATE,
    ISSUER_STATE,
    REDIRECT,
    ACCESS_TOKEN,
    REFRESH_TOKEN,
    PID_READING,
    AUTH_SESSION,  // for use in /authorize_challenge
    OPENID4VP_CODE,  // send to /authorize when we want openid4vp request
    OPENID4VP_STATE,  // for state field in openid4vp
    OPENID4VP_PRESENTATION  // for use in presentation_during_issuance_session
}
