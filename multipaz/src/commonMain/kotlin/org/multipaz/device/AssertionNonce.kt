package org.multipaz.device

import kotlinx.io.bytestring.ByteString

/**
 * Asserts that the device has access to the opaque private key in [DeviceAttestation] by signing
 * server-supplied [nonce].
 */
class AssertionNonce (
    val nonce: ByteString,
): Assertion()