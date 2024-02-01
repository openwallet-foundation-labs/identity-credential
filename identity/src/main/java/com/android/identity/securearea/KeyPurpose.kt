package com.android.identity.securearea

/**
 * Key purposes.
 */
enum class KeyPurpose(val flagValue: Int) {
    /**
     * Purpose of key: signing.
     */
    SIGN(1 shl 0),

    /**
     * Purpose of key: key agreement.
     */
    AGREE_KEY(1 shl 1);

    companion object {
        fun encodeSet(purposes: Set<KeyPurpose>): Int {
            var value = 0
            for (purpose in purposes) {
                value = value or purpose.flagValue
            }
            return value
        }

        fun decodeSet(purposes: Int): Set<KeyPurpose> {
            val result = mutableSetOf<KeyPurpose>()
            for (purpose in values()) {
                if ((purposes and purpose.flagValue) != 0) {
                    result.add(purpose)
                }
            }
            return result
        }
    }
}