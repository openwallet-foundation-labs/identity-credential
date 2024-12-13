package com.android.identity.device

import kotlinx.io.bytestring.ByteString

/** On iOS device attestation is the result of Apple's DeviceCheck API. */
data class DeviceAttestationIos(
    val blob: ByteString
): DeviceAttestation()