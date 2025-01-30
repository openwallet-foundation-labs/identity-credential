package com.android.identity.testapp

import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaProvider
import com.android.identity.storage.Storage

enum class Platform(val displayName: String) {
    ANDROID("Android"),
    IOS("iOS")
}

expect val platform: Platform

expect fun getLocalIpAddress(): String

expect val platformIsEmulator: Boolean

expect fun platformStorage(): Storage

expect fun platformSecureAreaProvider(): SecureAreaProvider<SecureArea>

expect fun platformKeySetting(clientId: String): CreateKeySettings
