package org.multipaz.securearea.cloud

import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.KeyPurpose
import org.multipaz.securearea.SecureArea
import org.multipaz.storage.Storage
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
