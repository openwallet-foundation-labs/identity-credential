package com.android.identity.testapp.provisioning.model

import kotlinx.io.bytestring.ByteString
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.SecureEnclaveCreateKeySettings
import org.multipaz.securearea.cloud.CloudCreateKeySettings
import org.multipaz.securearea.config.SecureAreaConfiguration
import org.multipaz.securearea.config.SecureAreaConfigurationAndroidKeystore
import org.multipaz.securearea.config.SecureAreaConfigurationCloud
import org.multipaz.securearea.config.SecureAreaConfigurationSoftware
import org.multipaz.securearea.software.SoftwareCreateKeySettings
import org.multipaz.testapp.platformSecureAreaProvider

actual suspend fun SecureAreaRepository.byConfiguration(
    secureAreaConfiguration: SecureAreaConfiguration,
    challenge: ByteString
): Pair<SecureArea, CreateKeySettings> {
    return when(secureAreaConfiguration) {
        is SecureAreaConfigurationSoftware -> Pair(
            getImplementation("SoftwareSecureArea")!!,
            SoftwareCreateKeySettings.Builder()
                .applyConfiguration(secureAreaConfiguration)
                .build()
        )

        is SecureAreaConfigurationAndroidKeystore -> Pair(
            platformSecureAreaProvider().get(),
            SecureEnclaveCreateKeySettings.Builder()
                .build()
        )

        is SecureAreaConfigurationCloud -> Pair(
            getImplementation(secureAreaConfiguration.cloudSecureAreaId)!!,
            CloudCreateKeySettings.Builder(challenge)
                .applyConfiguration(secureAreaConfiguration)
                .build()
        )
    }
}