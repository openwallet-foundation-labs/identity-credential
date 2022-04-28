/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity;

import androidx.annotation.NonNull;
import com.android.identity.Constants.BleDataRetrievalOption;

/**
 * An object to hold configuration about which data retrieval methods to listen on.
 *
 * <p>In ISO/IEC 18013-5 this is used by the mdoc to specify which data retrieval methods
 * are offered to mdoc reader.
 *
 * <p>Applications are expected to use a {@link Builder} to build an instance of this class
 * and use it with {@link PresentationHelper#startListening(DataRetrievalListenerConfiguration)}.
 */
public final class DataRetrievalListenerConfiguration {

    // Note: one reason for this class is that we expect this to be used in VerificationHelper
    //       class for QR code reverse engagement (will be part of 23220-4 and 18013-7)
    //

    private final boolean mNfcEnabled;
    private final boolean mWifiAwareEnabled;
    private final boolean mBleEnabled;
    @BleDataRetrievalOption
    private final int mBleDataRetrievalOptions;

    DataRetrievalListenerConfiguration(boolean nfcEnabled,
            boolean wifiAwareEnabled,
            boolean bleEnabled,
            @BleDataRetrievalOption int bleDataRetrievalOptions) {
        mNfcEnabled = nfcEnabled;
        mWifiAwareEnabled = wifiAwareEnabled;
        mBleEnabled = bleEnabled;
        mBleDataRetrievalOptions = bleDataRetrievalOptions;
    }

    /**
     * Whether NFC data retrieval is enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    public boolean isNfcEnabled() {
        return mNfcEnabled;
    }

    /**
     * Whether Wifi Aware data retrieval is enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    public boolean isWifiAwareEnabled() {
        return mWifiAwareEnabled;
    }

    /**
     * Whether BLE data retrieval is enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    public boolean isBleEnabled() {
        return mBleEnabled;
    }

    /**
     * Gets the BLE data retrieval options.
     *
     * @return One or more data retrieval options, for example
     * {@link Constants#BLE_DATA_RETRIEVAL_OPTION_MDOC_CENTRAL_CLIENT_MODE} and
     * {@link Constants#BLE_DATA_RETRIEVAL_OPTION_MDOC_PERIPHERAL_SERVER_MODE}.
     */
    @BleDataRetrievalOption
    public int getBleDataRetrievalOptions() {
        return mBleDataRetrievalOptions;
    }

    /**
     * A builder for {@link DataRetrievalListenerConfiguration}.
     *
     * <p>By default all data retrieval methods are disabled, the application is expected
     * to manually configure which transports it wants to listen on.
     */
    public static final class Builder {
        private boolean mNfcEnabled;
        private boolean mWifiAwareEnabled;
        private boolean mBleEnabled;
        @BleDataRetrievalOption
        private int mBleDataRetrievalOptions =
                Constants.BLE_DATA_RETRIEVAL_OPTION_MDOC_CENTRAL_CLIENT_MODE;

        /**
         * Creates a new builder for a {@link DataRetrievalListenerConfiguration}.
         */
        public Builder() {}

        /**
         * Sets whether to offer NFC data retrieval to remote initiator.
         *
         * @param enabled {@code true} to enable, {@code false} to disable.
         * @return the builder.
         */
        @NonNull
        public Builder setNfcEnabled(boolean enabled) {
            mNfcEnabled = enabled;
            return this;
        }

        /**
         * Sets whether to offer Wifi Aware retrieval to remote initiator.
         *
         * @param enabled {@code true} to enable, {@code false} to disable.
         * @return the builder.
         */
        @NonNull
        public Builder setWifiAwareEnabled(boolean enabled) {
            mWifiAwareEnabled = enabled;
            return this;
        }

        /**
         * Sets whether to offer BLE data retrieval to remote initiator.
         *
         * @param enabled {@code true} to enable, {@code false} to disable.
         * @return the builder.
         */
        @NonNull
        public Builder setBleEnabled(boolean enabled) {
            mBleEnabled = enabled;
            return this;
        }

        /**
         * Sets which data retrieval options to use when offering BLE data retrieval to the
         * remote initiator.
         *
         * <p>By default this is set to
         * {@link Constants#BLE_DATA_RETRIEVAL_OPTION_MDOC_CENTRAL_CLIENT_MODE}.
         *
         * @param bleDataRetrievalOptions one or more BLE data retrieval options.
         */
        @NonNull
        public Builder setBleDataRetrievalOptions(
                @BleDataRetrievalOption int bleDataRetrievalOptions) {
            mBleDataRetrievalOptions = bleDataRetrievalOptions;
            return this;
        }

        @NonNull
        public DataRetrievalListenerConfiguration build() {
            DataRetrievalListenerConfiguration configuration =
                    new DataRetrievalListenerConfiguration(mNfcEnabled, mWifiAwareEnabled,
                            mBleEnabled, mBleDataRetrievalOptions);
            return configuration;
        }
    }
}
