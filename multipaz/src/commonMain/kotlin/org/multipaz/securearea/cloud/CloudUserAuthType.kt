package org.multipaz.securearea.cloud

/**
 * An enumeration for different user authentication types when using [CloudSecureArea].
 */
enum class CloudUserAuthType(val flagValue: Long) {
    /**
     * Flag indicating that authentication is needed using the user's knowledge
     * factor for Device Lock, e.g. passcode on iOS or LSKF on Android.
     */
    PASSCODE(1 shl 0),

    /**
     * Flag indicating that authentication is needed using the user's biometric.
     */
    BIOMETRIC(1 shl 1);

    companion object {
        /**
         * Helper to encode a set of [CloudUserAuthType] as an integer.
         */
        fun encodeSet(types: Set<CloudUserAuthType>): Long {
            var value = 0L
            for (type in types) {
                value = value or type.flagValue
            }
            return value
        }

        /**
         * Helper to decode an integer into a set of [CloudUserAuthType].
         *
         * Bits not corresponding to an authentication type are ignored.
         */
        fun decodeSet(types: Long): Set<CloudUserAuthType> {
            val result = mutableSetOf<CloudUserAuthType>()
            for (type in CloudUserAuthType.values()) {
                if ((types and type.flagValue) != 0L) {
                    result.add(type)
                }
            }
            return result
        }
    }
}
