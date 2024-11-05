package com.android.identity.testapp

enum class Platform {
    ANDROID,
    IOS
}

expect val platform: Platform

expect fun getLocalIpAddress(): String
