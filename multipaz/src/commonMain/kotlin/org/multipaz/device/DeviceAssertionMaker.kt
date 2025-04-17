package org.multipaz.device

fun interface DeviceAssertionMaker {
    suspend fun makeDeviceAssertion(
        assertionFactory: (attestationChallenge: String) -> Assertion
    ): DeviceAssertion
}