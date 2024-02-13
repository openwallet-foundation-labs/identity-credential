package com.android.identity.mdoc.connectionmethod;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.internal.Util;
import com.android.identity.util.Logger;

import java.util.List;
import java.util.OptionalInt;
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
    private OptionalInt mPeripheralServerModePsm = OptionalInt.empty();
    private byte[] mPeripheralServerModeMacAddress = null;

    static final int METHOD_TYPE = 2;
    static final int METHOD_MAX_VERSION = 1;
    private static final int OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE = 0;
    private static final int OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE = 1;
    private static final int OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID = 10;
    private static final int OPTION_KEY_CENTRAL_CLIENT_MODE_UUID = 11;
    private static final int OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS = 20;
    private static final int OPTION_KEY_PERIPHERAL_SERVER_MODE_PSM = 2023; // NOTE: not yet standardized

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

    /**
     * Gets the L2CAP PSM, if set.
     *
     * <p>This is currently not standardized so use at your own risk.
     *
     * @return the L2CAP PSM, if set.
     */
    public
    OptionalInt getPeripheralServerModePsm() {
        return mPeripheralServerModePsm;
    }

    /**
     * Sets the L2CAP PSM or unsets it.
     *
     * <p>This is currently not standardized so use at your own risk.
     *
     * @param psm the value.
     */
    public
    void setPeripheralServerModePsm(@NonNull OptionalInt psm) {
        mPeripheralServerModePsm = psm;
    }

    /**
     * Gets the MAC address, if set.
     *
     * @return the MAC address or {@code null} if not set.
     */
    public @Nullable
    byte[] getPeripheralServerModeMacAddress() {
        return mPeripheralServerModeMacAddress;
    }

    /**
     * Sets or unsets the MAC address.
     *
     * @param macAddress the MAC address or {@code null} to unset it.
     */
    public
    void setPeripheralServerModeMacAddress(@Nullable byte[] macAddress) {
        if (macAddress != null) {
            if (macAddress.length != 6) {
                throw new IllegalArgumentException("MAC address should be 6 bytes, got " + macAddress.length);
            }
        }
        mPeripheralServerModeMacAddress = macAddress;
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
        if (mPeripheralServerModePsm.isPresent()) {
            sb.append(":psm=" + mPeripheralServerModePsm.getAsInt());
        }
        if (mPeripheralServerModeMacAddress != null) {
            sb.append(":mac=");
            for (int n = 0; n < 6; n++) {
                if (n > 0) {
                    sb.append("-");
                }
                sb.append(String.format("%02x", mPeripheralServerModeMacAddress[n]));
            }
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

        ConnectionMethodBle cm = new ConnectionMethodBle(
                supportsPeripheralServerMode,
                supportsCentralClientMode,
                peripheralServerModeUuid,
                centralClientModeUuid);
        if (Util.cborMapHasKey(options, OPTION_KEY_PERIPHERAL_SERVER_MODE_PSM)) {
            cm.setPeripheralServerModePsm(OptionalInt.of(
                    (int) Util.cborMapExtractNumber(options, OPTION_KEY_PERIPHERAL_SERVER_MODE_PSM)));
        }
        if (Util.cborMapHasKey(options, OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS)) {
            cm.setPeripheralServerModeMacAddress(
                    Util.cborMapExtractByteString(options, OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS));
        }
        return cm;
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
        if (mPeripheralServerModePsm.isPresent()) {
            builder.put(OPTION_KEY_PERIPHERAL_SERVER_MODE_PSM, mPeripheralServerModePsm.getAsInt());
        }
        if (mPeripheralServerModeMacAddress != null) {
            builder.put(OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS, mPeripheralServerModeMacAddress);
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
