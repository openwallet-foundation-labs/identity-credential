package com.android.identity.testapp

import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaProvider
import com.android.identity.storage.Storage
import kotlinx.io.bytestring.ByteString

enum class Platform(val displayName: String) {
    ANDROID("Android"),
    IOS("iOS")
}

expect val platform: Platform

expect suspend fun platformInit()

expect fun getLocalIpAddress(): String

expect val platformIsEmulator: Boolean

expect fun platformStorage(): Storage

/**
 * Gets a provider for the preferred [SecureArea] implementation for the platform.
 */
expect fun platformSecureAreaProvider(): SecureAreaProvider<SecureArea>

/**
 * Gets a [CreateKeySettings] object for creating auth-bound keys that works with the [SecureArea] returned
 * returned by [platformSecureAreaProvider].
 *
 * @param challenge the challenge to use in the generated attestation, if the [SecureArea] supports that.
 * @param userAuthenticationRequired set to `true` to require user authentication, `false` otherwise.
 */
expect fun platformCreateKeySettings(
    challenge: ByteString,
    keyPurposes: Set<KeyPurpose>,
    userAuthenticationRequired: Boolean
): CreateKeySettings
