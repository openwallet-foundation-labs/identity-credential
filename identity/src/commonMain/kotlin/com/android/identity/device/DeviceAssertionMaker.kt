package com.android.identity.device

fun interface DeviceAssertionMaker {
    suspend fun makeDeviceAssertion(assertion: Assertion): DeviceAssertion
}