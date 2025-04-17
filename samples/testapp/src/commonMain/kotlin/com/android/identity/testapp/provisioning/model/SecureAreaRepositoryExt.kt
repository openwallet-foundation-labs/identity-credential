package com.android.identity.testapp.provisioning.model

import kotlinx.io.bytestring.ByteString
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.config.SecureAreaConfiguration

expect suspend fun SecureAreaRepository.byConfiguration(
    secureAreaConfiguration: SecureAreaConfiguration,
    challenge: ByteString
): Pair<SecureArea, CreateKeySettings>