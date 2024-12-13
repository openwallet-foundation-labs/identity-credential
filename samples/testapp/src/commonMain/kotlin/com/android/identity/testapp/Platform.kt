package com.android.identity.testapp

import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.SecureArea

enum class Platform {
    ANDROID,
    IOS
}

expect val platform: Platform

expect fun getLocalIpAddress(): String

expect val platformIsEmulator: Boolean

expect fun platformSecureArea(): SecureArea

expect fun platformKeySetting(clientId: String): CreateKeySettings
