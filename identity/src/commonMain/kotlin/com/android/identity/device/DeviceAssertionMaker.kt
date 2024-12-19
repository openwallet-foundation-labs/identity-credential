package com.android.identity.device

fun interface DeviceAssertionMaker {
    suspend fun makeDeviceAssertion(
        assertion: (clientId: String) -> Assertion
    ): DeviceAssertion
}