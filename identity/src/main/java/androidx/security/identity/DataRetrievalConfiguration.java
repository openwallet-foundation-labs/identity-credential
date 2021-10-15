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

import androidx.annotation.NonNull;
import androidx.security.identity.Constants.BleServiceMode;

/**
 * An object to hold configuration about which data retrieval methods to listen on.
 *
 * <p>This is used by the application to specify which data retrieval methods are offered to the
 * initiator of a transaction. This is used by {@link PresentationHelper}.
 *
 * <p>By default all data retrieval methods are disabled.
 */
public class DataRetrievalConfiguration {

    // Note: one reason for this class is that we expect this to be used in VerificationHelper
    //       class for QR code reverse engagement (will be part of of 23220-4 and 18013-7)
    //

    boolean mNfcEnabled;
    boolean mWifiAwareEnabled;
    boolean mBleEnabled;
    @BleServiceMode
    int mBleServiceMode;

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
     * BLE should operate as central client, peripheral server or both
     *
     * @return {@link BleServiceMode} 0 - only central client mode,
     * 1 - only peripheral server mode or 2 - central client and peripheral server mode
     */
    @BleServiceMode
    public int getBleServiceMode() {
        return mBleServiceMode;
    }

    /**
     * A builder for {@link DataRetrievalConfiguration}.
     */
    public static final class Builder {
        private final DataRetrievalConfiguration mConfiguration;

        /**
         * Creates a new builder for a {@link DataRetrievalConfiguration}.
         *
         */
        public Builder() {
            mConfiguration = new DataRetrievalConfiguration();
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
         * @param enabled        {@code true} to enable, {@code false} to disable.
         * @param bleServiceMode indicates if ble should operate as central client,
         *                       peripheral server or both
         * @return the builder.
         */
        public @NonNull
        Builder setBleEnabled(boolean enabled, @BleServiceMode int bleServiceMode) {
            mConfiguration.mBleEnabled = enabled;
            mConfiguration.mBleServiceMode = bleServiceMode;
            return this;
        }

        public @NonNull DataRetrievalConfiguration build() {
            return mConfiguration;
        }
    }
}
