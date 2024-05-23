package com.android.identity.issuance.remote

import android.content.Context
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.javaX509Certificate
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
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import java.io.IOException


// Run the server locally on your dev computer and tunnel it to your phone
// using this command:
//
// adb reverse tcp:8080 tcp:8080
class WalletServerProvider(
    private val context: Context,
    private val secureArea: SecureArea,
    private val baseURL: String
) {
    companion object {
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
            flowHandlerLocal.build(noopCipher, LocalDevelopmentEnvironment(context))
        } else {
            val httpClient = WalletHttpClient(baseURL)
            FlowHandlerRemote(httpClient)
        }
    }

    suspend fun getWalletServer(): WalletServer {
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
}