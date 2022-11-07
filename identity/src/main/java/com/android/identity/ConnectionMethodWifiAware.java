package com.android.identity;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.net.wifi.aware.Characteristics;
import android.nfc.NdefRecord;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.Number;

/**
 * Connection method for Wifi Aware.
 */
public class ConnectionMethodWifiAware extends ConnectionMethod {
    private static final String TAG = "ConnectionMethodWifiAware";
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

    public @Override
    @NonNull
    DataTransport createDataTransport(@NonNull Context context,
                                      @DataTransport.Role int role,
                                      @NonNull DataTransportOptions options) {
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
            throw new IllegalStateException("Wifi Aware is not supported");
        }
        DataTransportWifiAware t = new DataTransportWifiAware(context, role, this, options);
        if (mPassphraseInfoPassphrase != null) {
            t.setPassphrase(mPassphraseInfoPassphrase);
        }
        // TODO: set mBandInfoSupportedBands, mChannelInfoChannelNumber, mChannelInfoOperatingClass
        return t;
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
    static ConnectionMethodWifiAware fromDeviceEngagement(@NonNull DataItem cmDataItem) {
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
        DataItem options = (Map) items.get(2);
        if (type != METHOD_TYPE) {
            Log.w(TAG, "Unexpected method type " + type);
            return null;
        }
        if (version > METHOD_MAX_VERSION) {
            Log.w(TAG, "Unsupported options version " + version);
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

    static @Nullable
    ConnectionMethod fromNdefRecord(@NonNull NdefRecord record) {
        String passphraseInfoPassphrase = null;
        byte[] bandInfoSupportedBands = null;
        OptionalLong channelInfoChannelNumber = OptionalLong.empty();
        OptionalLong channelInfoOperatingClass = OptionalLong.empty();

        ByteBuffer payload = ByteBuffer.wrap(record.getPayload()).order(ByteOrder.LITTLE_ENDIAN);
        while (payload.remaining() > 0) {
            int len = payload.get();
            int type = payload.get();
            int offset = payload.position();
            if (type == 0x03 && len > 1) {
                // passphrase
                byte[] encodedPassphrase = new byte[len - 1];
                payload.get(encodedPassphrase, 0, len - 1);
                passphraseInfoPassphrase = new String(encodedPassphrase, UTF_8);
            } else if (type == 0x04 && len > 1) {
                bandInfoSupportedBands = new byte[len - 1];
                payload.get(bandInfoSupportedBands, 0, len - 1);
            } else {
                // TODO: add support for other options...
                Logger.d(TAG, String.format(Locale.US,
                        "Skipping unknown type %d of length %d", type, len));
            }
            payload.position(offset + len - 1);
        }

        return new ConnectionMethodWifiAware(passphraseInfoPassphrase,
                channelInfoChannelNumber,
                channelInfoOperatingClass,
                bandInfoSupportedBands);
    }

    @NonNull
    @Override
    DataItem toDeviceEngagement() {
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
        return new CborBuilder()
                .addArray()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(builder.end().build().get(0))
                .end()
                .build().get(0);
    }

    @Override
    @NonNull
    Pair<NdefRecord, byte[]> toNdefRecord() {
        // The NdefRecord and its OOB data is defined in "Wi-Fi Aware Specification", table 142.
        //
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // TODO: use mCipherSuites
            int cipherSuites = Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;

            // Spec says: The NFC Handover Selector shall include the Cipher Suite Info field
            // with one or multiple NAN Cipher Suite IDs in the WiFi Aware Carrier Configuration
            // Record to indicate the supported NAN cipher suite(s)."
            //
            int numCipherSuitesSupported = 0;
            if ((cipherSuites & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128) != 0) {
                numCipherSuitesSupported++;
            }
            if ((cipherSuites & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256) != 0) {
                numCipherSuitesSupported++;
            }
            baos.write(1 + numCipherSuitesSupported);
            baos.write(0x01); // Data Type 0x01 - Cipher Suite Info
            if ((cipherSuites & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128) != 0) {
                baos.write(0x01); // NCS-SK-128
            }
            if ((cipherSuites & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256) != 0) {
                baos.write(0x02); // NCS-SK-256
            }

            // Spec says: "If the NFC Handover Selector indicates an NCS-SK cipher suite, it
            // shall include a Pass-phrase Info field in the Wi-Fi Aware Carrier Configuration Record
            // to specify the selected pass-phrase for the supported cipher suite."
            //
            // Additionally, 18013-5 says: "If NFC is used for device engagement, either the
            // Pass-phrase Info or the DH Info shall be explicitly transferred from the mdoc to
            // the mdoc reader during device engagement according to the Wi-Fi Alliance
            // Neighbor Awareness Networking Specification section 12."
            //
            if (mPassphraseInfoPassphrase != null) {
                byte[] encodedPassphrase = mPassphraseInfoPassphrase.getBytes(UTF_8);
                baos.write(1 + encodedPassphrase.length);
                baos.write(0x03); // Data Type 0x03 - Pass-phrase Info
                baos.write(encodedPassphrase);
            }

            // Spec says: "The NFC Handover Selector shall also include a Band Info field in the
            // Wi-Fi Aware Configuration Record to indicate the supported NAN operating band
            // (s)."
            //
            if (mBandInfoSupportedBands != null) {
                baos.write(1 + mBandInfoSupportedBands.length);
                baos.write(0x04); // Data Type 0x04 - Band Info
                baos.write(mBandInfoSupportedBands);
            }

            // Spec says: "The Channel Info field serves as a placeholder for future
            // extension, and
            // may optionally be included in the Wi-Fi Aware Carrier Configuration Record in the
            // NFC Handover Select message."
            //
            // We don't include this for now.
            //
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        byte[] oobData = baos.toByteArray();

        NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                "application/vnd.wfa.nan".getBytes(UTF_8),
                "W".getBytes(UTF_8),
                oobData);

        // From 7.1 Alternative Carrier Record
        //
        baos = new ByteArrayOutputStream();
        baos.write(0x01); // CPS: active
        baos.write(0x01); // Length of carrier data reference ("0")
        baos.write('W');  // Carrier data reference
        baos.write(0x01); // Number of auxiliary references
        byte[] auxReference = "mdoc".getBytes(UTF_8);
        baos.write(auxReference.length);
        baos.write(auxReference, 0, auxReference.length);
        byte[] acRecordPayload = baos.toByteArray();

        return new Pair<>(record, acRecordPayload);
    }
}
