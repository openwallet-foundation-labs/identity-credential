package com.android.identity.issuance.wallet

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcPublicKey
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.Storage
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.AuthenticationFlow
import com.android.identity.issuance.ClientAuthentication
import com.android.identity.issuance.ClientChallenge
import com.android.identity.issuance.WalletServerCapabilities
import com.android.identity.issuance.WalletServerSettings
import com.android.identity.issuance.authenticationMessage
import com.android.identity.issuance.toDataItem
import com.android.identity.issuance.validateKeyAttestation
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

@FlowState(flowInterface = AuthenticationFlow::class)
@CborSerializable
class AuthenticationState(
    var nonce: ByteString? = null,
    var clientId: String = "",
    var publicKey: EcPublicKey? = null,
    var authenticated: Boolean = false
) {
    companion object

    @OptIn(ExperimentalEncodingApi::class)
    @FlowMethod
    suspend fun requestChallenge(env: FlowEnvironment, clientId: String): ClientChallenge {
        check(this.clientId.isEmpty())
        check(nonce != null)
        val storage = env.getInterface(Storage::class)!!
        val keyData = storage.get("ClientKeys", "", clientId)
        if (keyData != null) {
            this.publicKey = EcPublicKey.fromDataItem(Cbor.decode(keyData.toByteArray()))
            this.clientId = clientId
            println("Existing client id: ${this.clientId}")
        }

        if (this.clientId.isEmpty()) {
            this.clientId = Base64.encode(Random.Default.nextBytes(18))
            println("New client id: ${this.clientId}")
        }

        return ClientChallenge(nonce!!, this.clientId)
    }

    @FlowMethod
    suspend fun authenticate(env: FlowEnvironment, auth: ClientAuthentication): WalletServerCapabilities {
        val chain = auth.attestation

        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val storage = env.getInterface(Storage::class)!!

        if (chain != null) {
            if (this.publicKey != null) {
                throw IllegalStateException("Client already registered")
            }
            validateKeyAttestation(
                chain,
                this.clientId,
                settings.androidRequireGmsAttestation,
                settings.androidRequireVerifiedBootGreen,
                settings.androidRequireAppSignatureCertificateDigests
            )
            this.publicKey = chain.certificates[0].ecPublicKey
            val keyData = ByteString(Cbor.encode(this.publicKey!!.toDataItem()))
            storage.insert("ClientKeys", "", keyData, key = clientId)
        }
        if (!Crypto.checkSignature(
                this.publicKey!!,
                authenticationMessage(this.clientId, this.nonce!!).toByteArray(),
                Algorithm.ES256,
                auth.signature)) {
            throw IllegalArgumentException("Authentication failed")
        }
        authenticated = true
        if (storage.get(
            "WalletApplicationCapabilities",
            "",
            clientId,
        ) == null) {
            storage.insert(
                "WalletApplicationCapabilities",
                "",
                ByteString(Cbor.encode(auth.walletApplicationCapabilities.toDataItem())),
                clientId
            )
        } else {
            storage.update(
                "WalletApplicationCapabilities",
                "",
                clientId,
                ByteString(Cbor.encode(auth.walletApplicationCapabilities.toDataItem()))
            )
        }
        return WalletServerCapabilities(
            Clock.System.now(),
            settings.getBool("waitForNotificationSupported", false)
        )
    }
}