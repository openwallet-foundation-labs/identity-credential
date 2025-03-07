package com.android.identity.android.mdoc.transport

/**
 * A set of options used when creating a [DataTransport] derived instance.
 */
class DataTransportOptions internal constructor(
    /**
     * The preference for using BLE L2CAP transmission profile.
     *
     * If true, L2CAP will be used if supported by the OS and remote mdoc.
     */
    val bleUseL2CAP: Boolean,

    /**
     * The preference to clear the BLE Service Cache before service discovery when
     * acting as a GATT Client.
     */
    val bleClearCache: Boolean,

    /**
     * Whether BLE L2CAP PSM is conveyed in engagement.
     *
     * See [Builder.setExperimentalBleL2CAPPsmInEngagement] for details on this option.
     */
    val experimentalBleL2CAPPsmInEngagement: Boolean,
) {
    /**
     * A builder for [DataTransportOptions].
     */
    class Builder {
        private var bleUseL2CAP = false
        private var bleClearCache = false
        private var experimentalBleL2CAPPsmInEngagement = false

        /**
         * Sets the preference for use BLE L2CAP transmission profile.
         *
         * Use L2CAP if supported by the OS and remote mdoc.
         *
         * The default value for this is `false`.
         *
         * @param value indicates if it should use L2CAP socket if available.
         * @return the builder.
         */
        fun setBleUseL2CAP(value: Boolean) = apply {
            bleUseL2CAP = value
        }

        /**
         * Sets whether to clear the BLE Service Cache before service discovery when acting as
         * a GATT Client.
         *
         * The default value for this is `false`.
         *
         * @param value indicates if the BLE Service Cache should be cleared.
         * @return the builder.
         */
        fun setBleClearCache(value: Boolean) = apply {
            bleClearCache = value
        }

        /**
         * Sets whether the BLE L2CAP PSM is conveyed in the engagement.
         *
         * This uses a non-standardized mechanisms for conveying the BLE L2CAP PSM
         * in NFC and QR engagement.
         *
         * The default value for this is *false*.
         *
         * @param value
         * @return the builder.
         */
        fun setExperimentalBleL2CAPPsmInEngagement(value: Boolean) = apply {
            experimentalBleL2CAPPsmInEngagement = value
        }

        /**
         * Builds the [DataTransportOptions].
         *
         * @return the built [DataTransportOptions] instance.
         */
        fun build(): DataTransportOptions {
            return DataTransportOptions(
                bleUseL2CAP,
                bleClearCache,
                experimentalBleL2CAPPsmInEngagement
            )
        }
    }
}
