package org.multipaz.rpc.handler

import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.buildCborMap
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.device.AssertionRpcAuth
import org.multipaz.device.DeviceCheck
import org.multipaz.device.DeviceAssertion
import org.multipaz.device.toDataItem
import org.multipaz.securearea.SecureArea
import org.multipaz.util.toBase64Url
import kotlin.random.Random

/**
 * [RpcAuthIssuer] implementation that authorizes each call with using [AssertionRpcAuth] object
 * signed by a secure device key. In addition to `payload` field in in authorization Cbor map it
 * adds an `assertion` field that holds [DeviceAssertion].
 */
class RpcAuthIssuerAssertion(
    private val clientId: String,
    private val secureArea: SecureArea,
    private val deviceAttestationId: String
): RpcAuthIssuer {
    override suspend fun auth(target: String, method: String, payload: Bstr): DataItem {
        val assertion = AssertionRpcAuth(
            target = target,
            method = method,
            clientId = clientId,
            nonce = Random.Default.nextBytes(12).toBase64Url(),
            timestamp = Clock.System.now(),
            payloadHash = ByteString(Crypto.digest(Algorithm.SHA256, payload.value))
        )
        val deviceAssertion = DeviceCheck.generateAssertion(
            secureArea = secureArea,
            deviceAttestationId = deviceAttestationId,
            assertion = assertion
        )
        return buildCborMap {
            put("payload", payload)
            put("assertion", deviceAssertion.toDataItem())
        }
    }
}