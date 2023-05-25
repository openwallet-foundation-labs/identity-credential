package com.android.identity.mdoc.connectionmethod;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.internal.Util;
import com.android.identity.util.Logger;

import java.util.List;
import java.util.UUID;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Number;

/**
 * Connection method for BLE.
 */
public class ConnectionMethodBle extends ConnectionMethod {
    private static final String TAG = "ConnectionOptionsBle";
    private final boolean mSupportsPeripheralServerMode;
    private final boolean mSupportsCentralClientMode;
    private final UUID mPeripheralServerModeUuid;
    private final UUID mCentralClientModeUuid;

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
    static ConnectionMethodBle fromDeviceEngagementBle(@NonNull byte[] encodedDeviceRetrievalMethod) {
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

    @Override
    public @NonNull
    byte[] toDeviceEngagement() {
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
        return Util.cborEncode(new CborBuilder()
                .addArray()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(builder.end().build().get(0))
                .end()
                .build().get(0));
    }

}
