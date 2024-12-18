package com.android.identity.device

import com.android.identity.SwiftBridge
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.securearea.SecureArea
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.io.bytestring.ByteString
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalForeignApi::class)
actual object DeviceCheck {
    actual suspend fun generateAttestation(
        secureArea: SecureArea,
        clientId: String
    ): DeviceAttestationResult {
        val nonce = Crypto.digest(Algorithm.SHA256, clientId.encodeToByteArray())
        return suspendCoroutine { continuation ->
            SwiftBridge.generateDeviceAttestation(nonce.toNSData()) { keyId, blob, err ->
                if (err != null) {
                    continuation.resumeWithException(Exception())
                } else {
                    continuation.resume(DeviceAttestationResult(
                        keyId!!,
                        DeviceAttestationIos(blob!!.toByteString())
                    ))
                }
            }
        }
    }

    actual suspend fun generateAssertion(
        secureArea: SecureArea,
        deviceAttestationId: String,
        assertion: Assertion
    ): DeviceAssertion {
        return suspendCoroutine { continuation ->
            val assertionData = assertion.toCbor()
            val digest = Crypto.digest(Algorithm.SHA256, assertionData)
            SwiftBridge.generateDeviceAssertion(
                deviceAttestationId,
                digest.toNSData()
            ) { blob, err ->
                if (err != null) {
                    continuation.resumeWithException(Exception())
                } else {
                    continuation.resume(
                        DeviceAssertion(
                            platformAssertion = blob!!.toByteString(),
                            assertionData = ByteString(assertionData)
                        )
                    )
                }
            }
        }
    }
}

private fun ByteString.toNSData(): NSData = toByteArray().toNSData()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteString(): ByteString {
    return ByteString(ByteArray(length.toInt()).apply {
        usePinned {
            memcpy(it.addressOf(0), bytes, length)
        }
    })
}
