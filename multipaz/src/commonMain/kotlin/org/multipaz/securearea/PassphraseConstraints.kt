package org.multipaz.securearea

import org.multipaz.cbor.annotation.CborSerializable

/**
 * Enumeration used to convey constraints on passphrases and PINs.
 *
 * This information is helpful for user interfaces when creating and validating passphrases.
 *
 * @param minLength the minimum allowed length of the passphrase.
 * @param maxLength the maximum allowed length of the passphrase.
 * @param requireNumerical if `true`, each character in the passphrase must be decimal digits (0-9).
 */
@CborSerializable(schemaHash = "uvM3E3bDjLlMMDv9rQeipnZxlfnqpkbh401zi6v-nAk")
data class PassphraseConstraints(
    val minLength: Int,
    val maxLength: Int,
    val requireNumerical: Boolean,
) {
    companion object {
        val NONE = PassphraseConstraints(0, Int.MAX_VALUE, false)

        val PIN_FOUR_DIGITS = PassphraseConstraints(4, 4, true)
        val PIN_FOUR_DIGITS_OR_LONGER = PassphraseConstraints(4, Int.MAX_VALUE, true)
        val PASSPHRASE_FOUR_CHARS = PassphraseConstraints(4, 4, false)
        val PASSPHRASE_FOUR_CHARS_OR_LONGER = PassphraseConstraints(4, Int.MAX_VALUE, false)

        val PIN_SIX_DIGITS = PassphraseConstraints(6, 6, true)
        val PIN_SIX_DIGITS_OR_LONGER = PassphraseConstraints(6, Int.MAX_VALUE, true)
        val PASSPHRASE_SIX_CHARS = PassphraseConstraints(6, 6, false)
        val PASSPHRASE_SIX_CHARS_OR_LONGER = PassphraseConstraints(6, Int.MAX_VALUE, false)
    }

    /**
     * Get whether the constraints define a fixed length entry, when [minLength] == [maxLength].
     * // TODO : fix CBOR processor so this function can be refactored into a public property:
     *          val isFixedLength: Boolean
     *              get() = minLength == maxLength
     *      currently, generates PassphraseConstraints_Cbor.kt that instantiates PassphraseConstraints
     *      with an invalid constructor signature, such as,
     *          "return PassphraseConstraints(isFixedLength, minLength, maxLength, requireNumerical)"
     *
     * @return [true] if this object's properties [minLength] and [maxLength] are the same, else [false]
     */
    fun isFixedLength() = minLength == maxLength
}
