package com.android.identity.securearea.cloud

import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureArea
import com.android.identity.storage.Storage
import kotlinx.io.bytestring.ByteString

internal actual suspend fun cloudSecureAreaGetPlatformSecureArea(
    storage: Storage,
    partitionId: String,
): SecureArea {
    throw NotImplementedError("CloudSecureArea is not available on JVM")
}

internal actual fun cloudSecureAreaGetPlatformSecureAreaCreateKeySettings(
    challenge: ByteString,
    keyPurposes: Set<KeyPurpose>,
    userAuthenticationRequired: Boolean,
    userAuthenticationTypes: Set<CloudUserAuthType>
): CreateKeySettings {
    throw NotImplementedError("CloudSecureArea is not available on JVM")
}
