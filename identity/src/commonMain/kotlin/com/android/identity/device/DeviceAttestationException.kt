package com.android.identity.device

/**
 * Exception thrown when [DeviceAttestation] validation fails.
 */
class DeviceAttestationException(
    message: String,
    cause: Throwable? = null
): Exception(message, cause)