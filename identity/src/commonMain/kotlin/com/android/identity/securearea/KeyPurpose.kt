package com.android.identity.securearea

/**
 * Key purposes.
 */
enum class KeyPurpose(val flagValue: Long) {
    /**
     * Purpose of key: signing.
     */
    SIGN(1 shl 0),

    /**
     * Purpose of key: key agreement.
     */
    AGREE_KEY(1 shl 1);

    companion object {
        /**
         * Helper to encode a set of [KeyPurpose] as an integer.
         */
        fun encodeSet(purposes: Set<KeyPurpose>): Long {
            var value = 0L
            for (purpose in purposes) {
                value = value or purpose.flagValue
            }
            return value
        }

        /**
         * Helper to decode an integer into a set of [KeyPurpose].
         */
        fun decodeSet(purposes: Long): Set<KeyPurpose> {
            val result = mutableSetOf<KeyPurpose>()
            for (purpose in values()) {
                if ((purposes and purpose.flagValue) != 0L) {
                    result.add(purpose)
                }
            }
            return result
        }
    }
}

/** Decodes the number into a set of [KeyPurpose] */
val Long.keyPurposeSet: Set<KeyPurpose>
    get() = KeyPurpose.decodeSet(this)
