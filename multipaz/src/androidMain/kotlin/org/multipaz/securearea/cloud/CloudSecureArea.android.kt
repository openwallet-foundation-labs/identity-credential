package org.multipaz.securearea.cloud

import org.multipaz.securearea.AndroidKeystoreCreateKeySettings
import org.multipaz.securearea.AndroidKeystoreSecureArea
import org.multipaz.securearea.UserAuthenticationType
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.Algorithm
import kotlin.time.Duration.Companion.seconds


internal actual suspend fun cloudSecureAreaGetPlatformSecureArea(
    storage: Storage,
    partitionId: String,
): SecureArea {
    return AndroidKeystoreSecureArea.create(storage, "_csa_$partitionId")
}

internal actual fun cloudSecureAreaGetPlatformSecureAreaCreateKeySettings(
    challenge: ByteString,
    algorithm: Algorithm,
    userAuthenticationRequired: Boolean,
    userAuthenticationTypes: Set<CloudUserAuthType>
): CreateKeySettings {
    val androidUserAuthTypes = when (userAuthenticationTypes) {
        setOf<CloudUserAuthType>() -> setOf<UserAuthenticationType>()

        setOf<CloudUserAuthType>(
            CloudUserAuthType.PASSCODE,
        ) -> setOf<UserAuthenticationType>(UserAuthenticationType.LSKF)

        setOf<CloudUserAuthType>(
            CloudUserAuthType.BIOMETRIC,
        ) -> setOf<UserAuthenticationType>(UserAuthenticationType.BIOMETRIC)

        setOf<CloudUserAuthType>(
            CloudUserAuthType.PASSCODE,
            CloudUserAuthType.BIOMETRIC,
        ) -> setOf<UserAuthenticationType>(UserAuthenticationType.LSKF, UserAuthenticationType.BIOMETRIC)

        else -> throw IllegalStateException("Unexpected userAuthenticationTypes $userAuthenticationTypes")
    }

    return AndroidKeystoreCreateKeySettings.Builder(challenge)
        .setAlgorithm(algorithm)
        .setUserAuthenticationRequired(
            required = userAuthenticationRequired,
            timeout = 0.seconds,
            userAuthenticationTypes = androidUserAuthTypes
        )
        .build()
}
