package com.android.identity.mdoc.connectionmethod;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.internal.Util;
import com.android.identity.util.Logger;

import java.util.List;
import java.util.OptionalLong;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Number;

/**
 * Connection method for Wifi Aware.
 */
public class ConnectionMethodWifiAware extends ConnectionMethod {
    private static final String TAG = "ConnectionMethodWifiNAN";    // limit to <= 23 chars
    private final String mPassphraseInfoPassphrase;
    private final OptionalLong mChannelInfoChannelNumber;
    private final OptionalLong mChannelInfoOperatingClass;
    private final byte[] mBandInfoSupportedBands;

    static final int METHOD_TYPE = 3;
    static final int METHOD_MAX_VERSION = 1;
    private static final int OPTION_KEY_PASSPHRASE_INFO_PASSPHRASE = 0;
    private static final int OPTION_KEY_CHANNEL_INFO_OPERATING_CLASS = 1;
    private static final int OPTION_KEY_CHANNEL_INFO_CHANNEL_NUMBER = 2;
    private static final int OPTION_KEY_BAND_INFO_SUPPORTED_BANDS = 3;

    /**
     * Creates a new connection method for Wifi Aware.
     *
     * @param passphraseInfoPassphrase  the passphrase or {@code null}.
     * @param channelInfoChannelNumber  the channel number or unset.
     * @param channelInfoOperatingClass the operating class or unset.
     * @param bandInfoSupportedBands    the supported bands or {@code null}.
     */
    public ConnectionMethodWifiAware(@Nullable String passphraseInfoPassphrase,
                                     OptionalLong channelInfoChannelNumber,
                                     OptionalLong channelInfoOperatingClass,
                                     @Nullable byte[] bandInfoSupportedBands) {
        mPassphraseInfoPassphrase = passphraseInfoPassphrase;
        mChannelInfoChannelNumber = channelInfoChannelNumber;
        mChannelInfoOperatingClass = channelInfoOperatingClass;
        mBandInfoSupportedBands = bandInfoSupportedBands;
    }

    /**
     * @return the value or {@code null}.
     */
    public @Nullable
    String getPassphraseInfoPassphrase() {
        return mPassphraseInfoPassphrase;
    }

    /**
     * Gets the channel number, if set.
     *
     * @return the value, if any.
     */
    public OptionalLong getChannelInfoChannelNumber() {
        return mChannelInfoChannelNumber;
    }

    /**
     * Gets the operating class, if set.
     *
     * @return the value, if any.
     */
    public OptionalLong getChannelInfoOperatingClass() {
        return mChannelInfoOperatingClass;
    }

    /**
     * @return the value or {@code null}.
     */
    public @Nullable
    byte[] getBandInfoSupportedBands() {
        return mBandInfoSupportedBands;
    }

    @Override
    public @NonNull
    String toString() {
        StringBuilder builder = new StringBuilder("wifi_aware");
        if (mPassphraseInfoPassphrase != null) {
            builder.append(":passphrase=");
            builder.append(mPassphraseInfoPassphrase);
        }
        if (mChannelInfoChannelNumber.isPresent()) {
            builder.append(":channel_info_channel_number=");
            builder.append(mChannelInfoChannelNumber.getAsLong());
        }
        if (mChannelInfoOperatingClass.isPresent()) {
            builder.append(":channel_info_operating_class=");
            builder.append(mChannelInfoOperatingClass.getAsLong());
        }
        if (mBandInfoSupportedBands != null) {
            builder.append(":base_info_supported_bands=");
            builder.append(Util.toHex(mBandInfoSupportedBands));
        }
        return builder.toString();
    }

    @Nullable
    static ConnectionMethodWifiAware fromDeviceEngagementWifiAware(@NonNull byte[] encodedDeviceRetrievalMethod) {
        DataItem cmDataItem = Util.cborDecode(encodedDeviceRetrievalMethod);
        if (!(cmDataItem instanceof co.nstant.in.cbor.model.Array)) {
            throw new IllegalArgumentException("Top-level CBOR is not an array");
        }
        List<DataItem> items = ((Array) cmDataItem).getDataItems();
        if (items.size() != 3) {
            throw new IllegalArgumentException("Expected array with 3 elements, got " + items.size());
        }
        if (!(items.get(0) instanceof Number) || !(items.get(1) instanceof Number)) {
            throw new IllegalArgumentException("First two items are not numbers");
        }
        long type = ((Number) items.get(0)).getValue().longValue();
        long version = ((Number) items.get(1)).getValue().longValue();
        if (!(items.get(2) instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalArgumentException("Third item is not a map");
        }
        DataItem options = items.get(2);
        if (type != METHOD_TYPE) {
            Logger.w(TAG, "Unexpected method type " + type);
            return null;
        }
        if (version > METHOD_MAX_VERSION) {
            Logger.w(TAG, "Unsupported options version " + version);
            return null;
        }
        String passphraseInfoPassphrase = null;
        if (Util.cborMapHasKey(options, OPTION_KEY_PASSPHRASE_INFO_PASSPHRASE)) {
            passphraseInfoPassphrase =
                    Util.cborMapExtractString(options, OPTION_KEY_PASSPHRASE_INFO_PASSPHRASE);
        }
        OptionalLong channelInfoChannelNumber = OptionalLong.empty();
        if (Util.cborMapHasKey(options, OPTION_KEY_CHANNEL_INFO_CHANNEL_NUMBER)) {
            channelInfoChannelNumber = OptionalLong.of(
                    Util.cborMapExtractNumber(options, OPTION_KEY_CHANNEL_INFO_CHANNEL_NUMBER));

        }
        OptionalLong channelInfoOperatingClass = OptionalLong.empty();
        if (Util.cborMapHasKey(options, OPTION_KEY_CHANNEL_INFO_OPERATING_CLASS)) {
            channelInfoOperatingClass = OptionalLong.of(
                    Util.cborMapExtractNumber(options, OPTION_KEY_CHANNEL_INFO_OPERATING_CLASS));
        }
        byte[] bandInfoSupportedBands = null;
        if (Util.cborMapHasKey(options, OPTION_KEY_BAND_INFO_SUPPORTED_BANDS)) {
            bandInfoSupportedBands = Util.cborMapExtractByteString(options,
                    OPTION_KEY_BAND_INFO_SUPPORTED_BANDS);
        }
        return new ConnectionMethodWifiAware(
                passphraseInfoPassphrase,
                channelInfoChannelNumber,
                channelInfoOperatingClass,
                bandInfoSupportedBands);
    }

    @Override
    public @NonNull
    byte[] toDeviceEngagement() {
        MapBuilder<CborBuilder> builder = new CborBuilder().addMap();
        if (mPassphraseInfoPassphrase != null) {
            builder.put(OPTION_KEY_PASSPHRASE_INFO_PASSPHRASE, mPassphraseInfoPassphrase);
        }
        if (mChannelInfoChannelNumber.isPresent()) {
            builder.put(OPTION_KEY_CHANNEL_INFO_CHANNEL_NUMBER,
                    mChannelInfoChannelNumber.getAsLong());
        }
        if (mChannelInfoOperatingClass.isPresent()) {
            builder.put(OPTION_KEY_CHANNEL_INFO_OPERATING_CLASS,
                    mChannelInfoOperatingClass.getAsLong());
        }
        if (mBandInfoSupportedBands != null) {
            builder.put(OPTION_KEY_BAND_INFO_SUPPORTED_BANDS,
                    mBandInfoSupportedBands);
        }
        return Util.cborEncode(new CborBuilder()
                .addArray()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(builder.end().build().get(0))
                .end()
                .build().get(0));
    }
}
