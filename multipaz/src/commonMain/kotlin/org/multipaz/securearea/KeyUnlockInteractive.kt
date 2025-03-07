package org.multipaz.securearea

/**
 * A [KeyUnlockData] which will automatically show dialogs to interact with the user.
 *
 * This can be passed to [SecureArea.sign] and [SecureArea.keyAgreement] to automatically
 * prompt the user for authentication. Depending on the Secure Area implementation this uses
 * either platform native dialogs (for biometric unlock) or the [PassphrasePromptModel]
 * mechanism.
 *
 * @property title the title to show in the authentication prompt or `null` to use default.
 * @property subtitle the subtitle to show in the authentication prompt or `null` to use default.
 * @property requireConfirmation if `true`, require active user confirmation if used for passive biometrics.
 */
class KeyUnlockInteractive(
    val title: String? = null,
    val subtitle: String? = null,
    val requireConfirmation: Boolean = false,
): KeyUnlockData
