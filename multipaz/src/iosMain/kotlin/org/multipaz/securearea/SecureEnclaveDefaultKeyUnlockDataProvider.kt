package org.multipaz.securearea

import org.multipaz.crypto.Algorithm
import org.multipaz.prompt.IosPromptModel
import org.multipaz.prompt.PromptModel
import org.multipaz.prompt.PromptModelNotAvailableException
import org.multipaz.prompt.Reason
import platform.LocalAuthentication.LAContext

object SecureEnclaveDefaultKeyUnlockDataProvider: KeyUnlockDataProvider {
    override suspend fun getKeyUnlockData(
        secureArea: SecureArea,
        alias: String,
        algorithm: Algorithm,
        unlockReason: Reason
    ): KeyUnlockData {
        check(secureArea is SecureEnclaveSecureArea)
        val humanReadable = try {
            PromptModel.get().toHumanReadable(unlockReason, null)
        } catch (_: PromptModelNotAvailableException) {
            IosPromptModel.defaultConvertToHumanReadable(unlockReason, null)
        }
        val authenticationContext = LAContext()
        authenticationContext.localizedReason = if (humanReadable.subtitle.isNotBlank()) {
            "${humanReadable.title} - ${humanReadable.subtitle}"
        } else {
            humanReadable.title
        }
        return SecureEnclaveKeyUnlockData(
            secureArea = secureArea,
            alias = alias,
            authenticationContext = authenticationContext
        )
    }
}