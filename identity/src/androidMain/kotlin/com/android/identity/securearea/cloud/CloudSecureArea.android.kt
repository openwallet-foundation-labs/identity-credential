package com.android.identity.securearea.cloud

import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaProvider
import com.android.identity.storage.Storage
import com.android.identity.storage.android.AndroidStorage
import com.android.identity.util.AndroidContexts
import kotlinx.io.bytestring.ByteString
import java.io.File


private val androidStorage: AndroidStorage by lazy {
    AndroidStorage(
        File(AndroidContexts.applicationContext.dataDir.path, "storage.db").absolutePath
    )
}

private val androidKeystoreSecureAreaProvider = SecureAreaProvider {
    AndroidKeystoreSecureArea.create(AndroidContexts.applicationContext, androidStorage)
}

internal actual suspend fun cloudSecureAreaGetPlatformSecureArea(
    storage: Storage,
    partitionId: String,
): SecureArea {
    return AndroidKeystoreSecureArea.create(AndroidContexts.applicationContext, androidStorage)
}

internal actual fun cloudSecureAreaGetPlatformSecureAreaCreateKeySettings(
    challenge: ByteString,
    keyPurposes: Set<KeyPurpose>,
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

    return AndroidKeystoreCreateKeySettings.Builder(challenge.toByteArray())
        .setKeyPurposes(keyPurposes)
        .setUserAuthenticationRequired(
            required = userAuthenticationRequired,
            timeoutMillis = 0,
            userAuthenticationTypes = androidUserAuthTypes
        )
        .build()
}
