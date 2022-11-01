package com.android.identity;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.nfc.NdefRecord;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.Number;

/**
 * Connection method for BLE.
 */
public class ConnectionMethodBle extends ConnectionMethod {
    private static final String TAG = "ConnectionOptionsBle";
    private boolean mSupportsPeripheralServerMode;
    private boolean mSupportsCentralClientMode;
    private UUID mPeripheralServerModeUuid;
    private UUID mCentralClientModeUuid;

    static final int METHOD_TYPE = 2;
    static final int METHOD_MAX_VERSION = 1;
    private static final int OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE = 0;
    private static final int OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE = 1;
    private static final int OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID = 10;
    private static final int OPTION_KEY_CENTRAL_CLIENT_MODE_UUID = 11;
    private static final int OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS = 20;

    /**
     * Creates a new connection method for BLE.
     *
     * @param supportsPeripheralServerMode whether mdoc peripheral mode is supported.
     * @param supportsCentralClientMode    whether mdoc central client mode is supported.
     * @param peripheralServerModeUuid     the UUID to use for mdoc peripheral server mode.
     * @param centralClientModeUuid        the UUID to use for mdoc central client mode.
     */
    public ConnectionMethodBle(boolean supportsPeripheralServerMode,
                               boolean supportsCentralClientMode,
                               @Nullable UUID peripheralServerModeUuid,
                               @Nullable UUID centralClientModeUuid) {
        mSupportsPeripheralServerMode = supportsPeripheralServerMode;
        mSupportsCentralClientMode = supportsCentralClientMode;
        mPeripheralServerModeUuid = peripheralServerModeUuid;
        mCentralClientModeUuid = centralClientModeUuid;
    }

    /**
     * Gets whether the connection method indicates that mdoc peripheral server mode is enabled.
     *
     * @return the value.
     */
    public boolean getSupportsPeripheralServerMode() {
        return mSupportsPeripheralServerMode;
    }

    /**
     * Gets whether the connection method indicates that mdoc central client mode is enabled.
     *
     * @return the value.
     */
    public boolean getSupportsCentralClientMode() {
        return mSupportsCentralClientMode;
    }

    /**
     * Gets the UUID used for mdoc peripheral server mode, if any.
     *
     * @return the value or {@code null}.
     */
    public @Nullable
    UUID getPeripheralServerModeUuid() {
        return mPeripheralServerModeUuid;
    }

    /**
     * Gets the UUID used for mdoc central client mode, if any.
     *
     * @return the value or {@code null}.
     */
    public @Nullable
    UUID getCentralClientModeUuid() {
        return mCentralClientModeUuid;
    }

    public @Override
    @NonNull
    DataTransport createDataTransport(@NonNull Context context,
                                      @DataTransport.Role int role,
                                      @NonNull DataTransportOptions options) {
        if (mSupportsCentralClientMode && mSupportsPeripheralServerMode) {
            throw new IllegalArgumentException(
                    "BLE connection method is ambiguous. Use ConnectionMethod.disambiguate() "
                            + "before picking one.");
        }
        if (mSupportsCentralClientMode) {
            DataTransportBle t = new DataTransportBleCentralClientMode(context, role, this, options);
            t.setServiceUuid(mCentralClientModeUuid);
            return t;
        }
        if (mSupportsPeripheralServerMode) {
            DataTransportBle t = new DataTransportBlePeripheralServerMode(context, role, this, options);
            t.setServiceUuid(mPeripheralServerModeUuid);
            return t;
        }

        throw new IllegalArgumentException("BLE connection method supports neither modes");
    }

    @Override
    public @NonNull
    String toString() {
        StringBuilder sb = new StringBuilder("ble");
        if (mSupportsPeripheralServerMode) {
            sb.append(":peripheral_server_mode:uuid=" + mPeripheralServerModeUuid);
        }
        if (mSupportsCentralClientMode) {
            sb.append(":central_client_mode:uuid=" + mCentralClientModeUuid);
        }
        return sb.toString();
    }

    @Nullable
    static ConnectionMethodBle fromDeviceEngagement(@NonNull DataItem cmDataItem) {
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
        boolean supportsPeripheralServerMode =
                Util.cborMapExtractBoolean(options, OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE);
        boolean supportsCentralClientMode =
                Util.cborMapExtractBoolean(options, OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE);
        UUID peripheralServerModeUuid = null;
        if (Util.cborMapHasKey(options, OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID)) {
            peripheralServerModeUuid =
                    Util.uuidFromBytes(Util.cborMapExtractByteString(options,
                            OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID));
        }
        UUID centralClientModeUuid = null;
        if (Util.cborMapHasKey(options, OPTION_KEY_CENTRAL_CLIENT_MODE_UUID)) {
            centralClientModeUuid =
                    Util.uuidFromBytes(Util.cborMapExtractByteString(options,
                            OPTION_KEY_CENTRAL_CLIENT_MODE_UUID));
        }

        return new ConnectionMethodBle(
                supportsPeripheralServerMode,
                supportsCentralClientMode,
                peripheralServerModeUuid,
                centralClientModeUuid);
    }

    static @Nullable
    ConnectionMethod fromNdefRecord(@NonNull NdefRecord record) {
        boolean centralClient = false;
        boolean peripheral = false;
        UUID uuid = null;
        boolean gotLeRole = false;
        boolean gotUuid = false;

        // See createNdefRecords() method for how this data is encoded.
        //
        ByteBuffer payload = ByteBuffer.wrap(record.getPayload()).order(ByteOrder.LITTLE_ENDIAN);
        while (payload.remaining() > 0) {
            Log.d(TAG, "hasR: " + payload.hasRemaining() + " rem: " + payload.remaining());
            int len = payload.get();
            int type = payload.get();
            Log.d(TAG, String.format(Locale.US, "type %d len %d", type, len));
            if (type == 0x1c && len == 2) {
                gotLeRole = true;
                int value = payload.get();
                if (value == 0x00) {
                    peripheral = true;
                } else if (value == 0x01) {
                    centralClient = true;
                } else if (value == 0x02 || value == 0x03) {
                    centralClient = true;
                    peripheral = true;
                } else {
                    Log.w(TAG, String.format("Invalid value %d for LE role", value));
                    return null;
                }
            } else if (type == 0x07) {
                int uuidLen = len - 1;
                if (uuidLen % 16 != 0) {
                    Log.w(TAG, String.format("UUID len %d is not divisible by 16", uuidLen));
                    return null;
                }
                // We only use the last UUID...
                for (int n = 0; n < uuidLen; n += 16) {
                    long lsb = payload.getLong();
                    long msb = payload.getLong();
                    uuid = new UUID(msb, lsb);
                    gotUuid = true;
                }
            } else {
                Log.d(TAG, String.format("Skipping unknown type %d of length %d", type, len));
                payload.position(payload.position() + len - 1);
            }
        }

        if (!gotLeRole) {
            Log.w(TAG, "Did not find LE role");
            return null;
        }
        if (!gotUuid) {
            Log.w(TAG, "Did not find UUID");
            return null;
        }

        // Note that the UUID for both modes is the same if both peripheral and
        // central client mode is used!
        return new ConnectionMethodBle(peripheral,
                centralClient,
                uuid,
                uuid);
    }

    @NonNull
    @Override
    DataItem toDeviceEngagement() {
        MapBuilder<CborBuilder> builder = new CborBuilder().addMap();
        builder.put(OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE, mSupportsPeripheralServerMode);
        builder.put(OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE, mSupportsCentralClientMode);
        if (mPeripheralServerModeUuid != null) {
            builder.put(OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID, Util.uuidToBytes(mPeripheralServerModeUuid));
        }
        if (mCentralClientModeUuid != null) {
            builder.put(OPTION_KEY_CENTRAL_CLIENT_MODE_UUID, Util.uuidToBytes(mCentralClientModeUuid));
        }
        // TODO: add support for OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS
        return new CborBuilder()
                .addArray()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(builder.end().build().get(0))
                .end()
                .build().get(0);
    }

    @Override @NonNull
    Pair<NdefRecord, byte[]> toNdefRecord() {
        // The OOB data is defined in "Supplement to the Bluetooth Core Specification".
        //
        // See section 1.17.2 for values
        //
        UUID uuid;
        int leRole;
        if (mSupportsCentralClientMode && mSupportsPeripheralServerMode) {
            // Peripheral and Central Role supported,
            // Central Role preferred for connection
            // establishment
            leRole = 0x03;
            if (!mCentralClientModeUuid.equals(mPeripheralServerModeUuid)) {
                throw new IllegalStateException("UUIDs for both BLE modes must be the same");
            }
            uuid = mCentralClientModeUuid;
        } else if (mSupportsCentralClientMode) {
            // Only Central Role supported
            leRole = 0x01;
            uuid = mCentralClientModeUuid;
        } else if (mSupportsPeripheralServerMode) {
            // Only Peripheral Role supported
            leRole = 0x00;
            uuid = mPeripheralServerModeUuid;
        } else {
            throw new IllegalStateException("At least one of the BLE modes must be set");
        }

        // See "3 Handover to a Bluetooth Carrier" of "Bluetooth® Secure Simple Pairing Using
        // NFC Application Document" Version 1.2. This says:
        //
        //   For Bluetooth LE OOB the name “application/vnd.bluetooth.le.oob” is used as the
        //   [NDEF] record type name. The payload of this type of record is then defined by the
        //   Advertising and Scan Response Data (AD) format that is specified in the Bluetooth Core
        //   Specification ([BLUETOOTH_CORE], Volume 3, Part C, Section 11).
        //
        // Looking that up it says it's just a sequence of {length, AD type, AD data} where each
        // AD is defined in the "Bluetooth Supplement to the Core Specification" document.
        //
        byte[] oobData = new byte[]{
                // LE Role
                (byte) 0x02, (byte) 0x1c, (byte) leRole,
                // Complete List of 128-bit Service UUID’s (0x07)
                (byte) 0x11, (byte) 0x07,
                // UUID will be copied here (offset 5)..
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        };
        ByteBuffer uuidBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        uuidBuf.putLong(0, uuid.getLeastSignificantBits());
        uuidBuf.putLong(8, uuid.getMostSignificantBits());
        System.arraycopy(uuidBuf.array(), 0, oobData, 5, 16);

        NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                "application/vnd.bluetooth.le.oob".getBytes(UTF_8),
                "0".getBytes(UTF_8),
                oobData);

        // From 7.1 Alternative Carrier Record
        //
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x01); // CPS: active
        baos.write(0x01); // Length of carrier data reference ("0")
        baos.write('0');  // Carrier data reference
        baos.write(0x01); // Number of auxiliary references
        // Each auxiliary reference consists of a single byte for the length and then as
        // many bytes for the reference itself.
        byte[] auxReference = "mdoc".getBytes(UTF_8);
        baos.write(auxReference.length);
        baos.write(auxReference, 0, auxReference.length);
        byte[] acRecordPayload = baos.toByteArray();

        return new Pair<>(record, acRecordPayload);
    }
}
