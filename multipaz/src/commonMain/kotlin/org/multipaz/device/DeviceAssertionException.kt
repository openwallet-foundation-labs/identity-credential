package org.multipaz.device

/**
 * Exception thrown when [DeviceAssertion] validation fails.
 */
class DeviceAssertionException(
    message: String,
    cause: Throwable? = null
): Exception(message, cause)