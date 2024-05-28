package com.android.identity.issuance.remote

import android.content.Context
import com.android.identity.crypto.Algorithm
import com.android.identity.flow.handler.FlowHandler
import com.android.identity.flow.handler.FlowHandlerLocal
import com.android.identity.flow.handler.FlowHandlerRemote
import com.android.identity.issuance.ClientAuthentication
import com.android.identity.issuance.ClientChallenge
import com.android.identity.issuance.WalletServer
import com.android.identity.issuance.WalletServerImpl
import com.android.identity.issuance.authenticationMessage
import com.android.identity.issuance.extractAttestationSequence
import com.android.identity.issuance.hardcoded.WalletServerState
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.SecureArea
import com.android.identity.util.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1OctetString


/**
 * An object used to connecting to a remote wallet server.
 */
class WalletServerProvider(
    private val context: Context,
    private val secureArea: SecureArea,
    private val baseURL: String
) {
    private var instance: WalletServer? = null
    private val instanceLock = Mutex()

    companion object {
        private const val TAG = "WalletServerProvider"

        private val noopCipher = object : FlowHandlerLocal.SimpleCipher {
            override fun encrypt(plaintext: ByteArray): ByteArray {
                return plaintext
            }

            override fun decrypt(ciphertext: ByteArray): ByteArray {
                return ciphertext
            }
        }
    }

    private fun createFlowHandler(): FlowHandler {
        return if (baseURL == "dev:") {
            val flowHandlerLocal = FlowHandlerLocal.Builder()
            WalletServerState.registerAll(flowHandlerLocal)
            flowHandlerLocal.build(noopCipher, LocalDevelopmentEnvironment(context, this))
        } else {
            val httpClient = WalletHttpClient(baseURL)
            FlowHandlerRemote(httpClient)
        }
    }

    /**
     * Connects to the remote wallet server.
     *
     * This process includes running through the authentication flow to prove to the remote wallet
     * server that the client is in good standing, e.g. that Verified Boot is GREEN, Android patch
     * level is sufficiently fresh, the app signature keys are as expected, and so on.
     *
     * This is usually called at application startup-time. When the call succeeds, the resulting
     * instance is cached and returned immediately in future calls.
     *
     * @return A [WalletServer] which can be used to interact with the remote wallet server.
     * @throws FlowHandlerRemote.ConnectionException if unable to connect.
     */
    suspend fun getWalletServer(): WalletServer {
        instanceLock.withLock {
            if (instance == null) {
                instance = getWalletServerUnlocked()
                Logger.i(TAG, "Created new WalletServer instance (URL $baseURL)")
            } else {
                Logger.i(TAG, "Reusing existing WalletServer instance (URL $baseURL)")
            }
            return instance!!
        }
    }

    private suspend fun getWalletServerUnlocked(): WalletServer {
        // "root" is the entry point for the server, see FlowState annotation on
        // com.android.identity.issuance.hardcoded.WalletServerState
        val walletServer = WalletServerImpl(createFlowHandler(), "root")
        val alias = "ClientKey:$baseURL"
        var keyInfo: KeyInfo? = null
        var challenge: ClientChallenge? = null
        val authentication = walletServer.authenticate();
        try {
            keyInfo = secureArea.getKeyInfo(alias)
        } catch (ex: Exception) {
            challenge = authentication.requestChallenge("")
        }
        if (keyInfo != null) {
            val seq = extractAttestationSequence(keyInfo.attestation)
            val clientId = String(ASN1OctetString.getInstance(seq.getObjectAt(4)).octets)
            challenge = authentication.requestChallenge(clientId)
            if (clientId != challenge.clientId) {
                secureArea.deleteKey(alias)
                keyInfo = null
            }
        }
        val newClient = keyInfo == null
        if (newClient) {
            secureArea.createKey(alias, CreateKeySettings(challenge!!.clientId.toByteArray()))
            keyInfo = secureArea.getKeyInfo(alias)
        }
        val message = authenticationMessage(challenge!!.clientId, challenge.nonce)
        authentication.authenticate(ClientAuthentication(
            ByteString(secureArea.sign(alias, Algorithm.ES256, message.toByteArray(), null)),
            if (newClient) keyInfo!!.attestation else null
        ))
        authentication.complete()
        return walletServer
    }

    /**
     * A [SharedFlow] which can be used to listen for when a document has changed state
     * on the issuer side.
     *
     * The first parameter in the pair is `issuingAuthorityIdentifier`, the second parameter
     * is `documentIdentifier`.
     */
    val eventFlow : SharedFlow<Pair<String, String>>
        get() = _eventFlow.asSharedFlow()

    internal val _eventFlow = MutableSharedFlow<Pair<String, String>>()
}