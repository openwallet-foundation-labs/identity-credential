package com.android.identity;

import androidx.annotation.NonNull;

/**
 * A set of options used when creating a {@link DataTransport} derived instance.
 */
public class DataTransportOptions {

    private boolean mBleUseL2CAP;
    private boolean mBleClearCache;

    DataTransportOptions() {}

    /**
     * Returns the preference for use BLE L2CAP transmission profile.
     *
     * <p>If true, L2CAP will be used if supported by the OS and remote mdoc.
     *
     * @return indicates if L2CAP should be used, if available.
     */
    public boolean getBleUseL2CAP() {
        return mBleUseL2CAP;
    }

    /**
     * Returns the preference to clear the BLE Service Cache before service discovery when acting as
     * a GATT Client.
     *
     * @return indicates if the BLE Service Cache should be cleared.
     */
    public boolean getBleClearCache() {
        return mBleClearCache;
    }

    /**
     * A builder for {@link DataTransportOptions}.
     */
    public static class Builder {
        DataTransportOptions mOptions;

        /**
         * Creates a new builder.
         */
        public Builder() {
            mOptions = new DataTransportOptions();
        }

        /**
         * Sets the preference for use BLE L2CAP transmission profile.
         *
         * <p>Use L2CAP if supported by the OS and remote mdoc.
         *
         * <p>The default value for this is <em>false</em>.
         *
         * @param useL2CAP indicates if it should use L2CAP socket if available.
         * @return the builder.
         */
        public Builder setBleUseL2CAP(boolean useL2CAP) {
            mOptions.mBleUseL2CAP = useL2CAP;
            return this;
        }

        /**
         * Sets whether to clear the BLE Service Cache before service discovery when acting as
         * a GATT Client.
         *
         * <p>The default value for this is <em>false</em>.
         *
         * @param bleClearCache indicates if the BLE Service Cache should be cleared.
         * @return the builder.
         */
        public Builder setBleClearCache(boolean bleClearCache) {
            mOptions.mBleClearCache = bleClearCache;
            return this;
        }

        /**
         * Builds the {@link DataTransportOptions}.
         *
         * @return the built {@link DataTransportOptions} instance.
         */
        public @NonNull DataTransportOptions build() {
            return mOptions;
        }
    }
}
