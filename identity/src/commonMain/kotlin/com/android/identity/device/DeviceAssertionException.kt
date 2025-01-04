package com.android.identity.device

/**
 * Exception thrown when [DeviceAssertion] validation fails.
 */
class DeviceAssertionException(
    message: String,
    cause: Throwable? = null
): Exception(message, cause)