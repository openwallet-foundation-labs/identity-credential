package org.multipaz.device

fun interface DeviceAssertionMaker {
    suspend fun makeDeviceAssertion(
        assertion: (attestationChallenge: String) -> Assertion
    ): DeviceAssertion
}