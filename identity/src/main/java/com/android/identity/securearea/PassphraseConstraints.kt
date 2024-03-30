package com.android.identity.securearea

import com.android.identity.cbor.annotation.CborSerializable

/**
 * Enumeration used to convey constraints on passphrases and PINs.
 *
 * This information is helpful for user interfaces when creating and validating passphrases.
 *
 * @param minLength the minimum allowed length of the passphrase.
 * @param maxLength the maximum allowed length of the passphrase.
 * @param requireNumerical if `true`, each character in the passphrase must be decimal digits (0-9).
 */
@CborSerializable
data class PassphraseConstraints(
    val minLength: Int,
    val maxLength: Int,
    val requireNumerical: Boolean
) {
    companion object {
        val NONE = PassphraseConstraints(0, Int.MAX_VALUE, false)

        val PIN_FOUR_DIGITS = PassphraseConstraints(4, 4, true)
        val PIN_FOUR_DIGITS_OR_LONGER = PassphraseConstraints(4, Int.MAX_VALUE, true)
        val PASSPHRASE_FOUR_CHARS = PassphraseConstraints(4, 4, false)
        val PASSPHRASE_FOUR_CHARS_OR_LONGER = PassphraseConstraints(4, Int.MAX_VALUE, true)

        val PIN_SIX_DIGITS = PassphraseConstraints(6, 6, true)
        val PIN_SIX_DIGITS_OR_LONGER = PassphraseConstraints(6, Int.MAX_VALUE, true)
        val PASSPHRASE_SIX_CHARS = PassphraseConstraints(6, 6, false)
        val PASSPHRASE_SIX_CHARS_OR_LONGER = PassphraseConstraints(6, Int.MAX_VALUE, true)
    }
}
