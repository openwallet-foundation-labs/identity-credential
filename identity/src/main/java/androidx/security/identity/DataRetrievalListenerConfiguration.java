/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.security.identity;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.security.identity.Constants.BleDataRetrievalOption;

/**
 * An object to hold configuration about which data retrieval methods to listen on.
 *
 * <p>In ISO/IEC 18013-5 this is used by the mdoc to specify which data retrieval methods
 * are offered to mdoc reader.
 *
 * <p>Applications are expected to use a {@link Builder} to build an instance of this class
 * and use it with {@link PresentationHelper#startListening(DataRetrievalListenerConfiguration)}.
 */
public class DataRetrievalListenerConfiguration {

    // Note: one reason for this class is that we expect this to be used in VerificationHelper
    //       class for QR code reverse engagement (will be part of of 23220-4 and 18013-7)
    //

    boolean mNfcEnabled;
    boolean mWifiAwareEnabled;
    boolean mBleEnabled;
    @BleDataRetrievalOption int mBleDataRetrievalOptions =
        Constants.BLE_DATA_RETRIEVAL_OPTION_MDOC_CENTRAL_CLIENT_MODE;

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
    public @BleDataRetrievalOption int getBleDataRetrievalOptions() {
        return mBleDataRetrievalOptions;
    }

    /**
     * A builder for {@link DataRetrievalListenerConfiguration}.
     *
     * <p>By default all data retrieval methods are disabled, the application is expected
     * to manually configure which transports it wants to listen on.
     */
    public static final class Builder {
        private final DataRetrievalListenerConfiguration mConfiguration;

        /**
         * Creates a new builder for a {@link DataRetrievalListenerConfiguration}.
         *
         */
        public Builder() {
            mConfiguration = new DataRetrievalListenerConfiguration();
        }

        /**
         * Set whether to offer NFC data retrieval to remote initiator.
         *
         * @param enabled {@code true} to enable, {@code false} to disable.
         * @return the builder.
         */
        public @NonNull
        Builder setNfcEnabled(boolean enabled) {
            mConfiguration.mNfcEnabled = enabled;
            return this;
        }

        /**
         * Set whether to offer Wifi Aware retrieval to remote initiator.
         *
         * @param enabled {@code true} to enable, {@code false} to disable.
         * @return the builder.
         */
        public @NonNull
        Builder setWifiAwareEnabled(boolean enabled) {
            mConfiguration.mWifiAwareEnabled = enabled;
            return this;
        }

        /**
         * Set whether to offer BLE data retrieval to remote initiator.
         *
         * @param enabled {@code true} to enable, {@code false} to disable.
         * @return the builder.
         */
        public @NonNull
        Builder setBleEnabled(boolean enabled) {
            mConfiguration.mBleEnabled = enabled;
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
        public @NonNull Builder setBleDataRetrievalOptions(
            @BleDataRetrievalOption int bleDataRetrievalOptions) {
            mConfiguration.mBleDataRetrievalOptions = bleDataRetrievalOptions;
            return this;
        }

        public @NonNull
        DataRetrievalListenerConfiguration build() {
            return mConfiguration;
        }
    }
}
