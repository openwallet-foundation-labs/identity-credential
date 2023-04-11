package com.android.identity;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.nfc.NdefRecord;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Number;

/**
 * A class representing the ConnectionMethod structure exchanged between mdoc and mdoc reader.
 *
 * <p>This is an abstract class - applications are expected to interact with concrete
 * implementations, for example {@link ConnectionMethodBle} or {@link ConnectionMethodNfc}.
 */
public abstract class ConnectionMethod {
    private static final String TAG = "ConnectionMethod";

    abstract @NonNull
    DataItem toDeviceEngagement();

    static @Nullable
    ConnectionMethod fromDeviceEngagement(@NonNull DataItem cmDataItem) {
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
        if (!(items.get(2) instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalArgumentException("Third item is not a map");
        }
        switch ((int) type) {
            case ConnectionMethodNfc.METHOD_TYPE:
                return ConnectionMethodNfc.fromDeviceEngagement(cmDataItem);
            case ConnectionMethodBle.METHOD_TYPE:
                return ConnectionMethodBle.fromDeviceEngagement(cmDataItem);
            case ConnectionMethodWifiAware.METHOD_TYPE:
                return ConnectionMethodWifiAware.fromDeviceEngagement(cmDataItem);
            case ConnectionMethodHttp.METHOD_TYPE:
                return ConnectionMethodHttp.fromDeviceEngagement(cmDataItem);
        }
        Log.w(TAG, "Unsupported type " + type);
        return null;
    }

    /**
     * Creates Carrier Reference and Auxiliary Data Reference records.
     *
     * <p>If this is to be included in a Handover Select method, pass <code>{"mdoc"}</code>
     * for <code>auxiliaryReferences</code>.
     *
     * @param auxiliaryReferences A list of references to include in the Alternative Carrier Record
     * @param isForHandoverSelect Set to <code>true</code> if this is for a Handover Select method,
     *                            and <code>false</code> if for Handover Request record.
     * @return <code>null</code> if the connection method doesn't support NFC handover, otherwise
     *         the NDEF record and the Alternative Carrier record.
     */
    abstract @Nullable
    Pair<NdefRecord, byte[]> toNdefRecord(@NonNull List<String> auxiliaryReferences, boolean isForHandoverSelect);

    static @Nullable
    ConnectionMethod fromNdefRecord(@NonNull NdefRecord record, boolean isForHandoverSelect) {
        // BLE Carrier Configuration record
        //
        if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA
                && Arrays.equals(record.getType(),
                "application/vnd.bluetooth.le.oob".getBytes(UTF_8))
                && Arrays.equals(record.getId(), "0".getBytes(UTF_8))) {
            return ConnectionMethodBle.fromNdefRecord(record, isForHandoverSelect);
        }

        // Wifi Aware Carrier Configuration record
        //
        if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA
                && Arrays.equals(record.getType(),
                "application/vnd.wfa.nan".getBytes(UTF_8))
                && Arrays.equals(record.getId(), "W".getBytes(UTF_8))) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return ConnectionMethodWifiAware.fromNdefRecord(record, isForHandoverSelect);
            } else {
                Log.i(TAG, "Ignoring Wifi Aware Carrier Configuration since Wifi Aware "
                        + "is not available on this API level");
                return null;
            }
        }

        // NFC Carrier Configuration record
        //
        if (record.getTnf() == NdefRecord.TNF_EXTERNAL_TYPE
                && Arrays.equals(record.getType(),
                "iso.org:18013:nfc".getBytes(UTF_8))
                && Arrays.equals(record.getId(), "nfc".getBytes(UTF_8))) {
            return ConnectionMethodNfc.fromNdefRecord(record, isForHandoverSelect);
        }

        Logger.d(TAG, "Unknown NDEF record " + record);
        return null;
    }

    /**
     * Combines connection methods.
     *
     * <p>Given a list of connection methods, produce a list where similar methods are combined
     * into a single one. This is currently only applicable for BLE and one requirement is that
     * all method instances share the same UUID. If this is not the case,
     * {@link IllegalArgumentException} is thrown.
     *
     * <p>This is the reverse of {@link #disambiguate(List)}.
     *
     * @param connectionMethods a list of connection methods.
     * @return the given list of connection methods where similar methods are combined into one.
     * @throws IllegalArgumentException if some methods couldn't be combined
     */
    public static @NonNull
    List<ConnectionMethod> combine(@NonNull List<ConnectionMethod> connectionMethods) {
        List<ConnectionMethod> result = new ArrayList<>();

        // Don't change the order if there is nothing to combine.
        int numBleMethods = 0;
        for (ConnectionMethod cm : connectionMethods) {
            if (cm instanceof ConnectionMethodBle) {
                numBleMethods += 1;
            }
        }
        if (numBleMethods <= 1) {
            result.addAll(connectionMethods);
            return result;
        }

        List<ConnectionMethodBle> bleMethods = new ArrayList<>();
        for (ConnectionMethod cm : connectionMethods) {
            if (cm instanceof ConnectionMethodBle) {
                bleMethods.add((ConnectionMethodBle) cm);
            } else {
                result.add(cm);
            }
        }

        if (bleMethods.size() > 0) {
            boolean supportsPeripheralServerMode = false;
            boolean supportsCentralClientMode = false;
            UUID uuid = null;
            for (ConnectionMethodBle ble : bleMethods) {
                if (ble.getSupportsPeripheralServerMode()) {
                    supportsPeripheralServerMode = true;
                }
                if (ble.getSupportsCentralClientMode()) {
                    supportsCentralClientMode = true;
                }
                UUID c = ble.getCentralClientModeUuid();
                UUID p = ble.getPeripheralServerModeUuid();
                if (uuid == null) {
                    if (c != null) {
                        uuid = c;
                    } else if (p != null) {
                        uuid = p;
                    }
                } else {
                    if (c != null && !uuid.equals(c)) {
                        throw new IllegalArgumentException("UUIDs for both BLE modes are not the same");
                    }
                    if (p != null && !uuid.equals(p)) {
                        throw new IllegalArgumentException("UUIDs for both BLE modes are not the same");
                    }
                }
            }
            result.add(new ConnectionMethodBle(
                    supportsPeripheralServerMode,
                    supportsCentralClientMode,
                    supportsPeripheralServerMode ? uuid : null,
                    supportsCentralClientMode ? uuid : null));
        }
        return result;
    }

    /**
     * Disambiguates a list of connection methods.
     *
     * <p>Given a list of connection methods, produce a list where each method represents exactly
     * one connectable endpoint. For example, for BLE if both central client mode and peripheral
     * server mode is set, replaces this with two connection methods so it's clear which one is
     * which.
     *
     * <p>This is the reverse of {@link #combine(List)}.
     *
     * @param connectionMethods a list of connection methods.
     * @return the given list of connection methods where each instance is unambiguously refers
     *   to one and only one connectable endpoint.
     */
    public static @NonNull
    List<ConnectionMethod> disambiguate(@NonNull List<ConnectionMethod> connectionMethods) {
        List<ConnectionMethod> result = new ArrayList<>();
        for (ConnectionMethod cm : connectionMethods) {
            // Only BLE needs disambiguation
            if (cm instanceof ConnectionMethodBle) {
                ConnectionMethodBle cmBle = (ConnectionMethodBle) cm;
                if (cmBle.getSupportsCentralClientMode() && cmBle.getSupportsPeripheralServerMode()) {
                    result.add(new ConnectionMethodBle(
                            false,
                            true,
                            null,
                            cmBle.getCentralClientModeUuid()));
                    result.add(new ConnectionMethodBle(
                            true,
                            false,
                            cmBle.getPeripheralServerModeUuid(),
                            null));
                    continue;
                }
            }
            result.add(cm);
        }
        return result;
    }

    /**
     * Creates a new {@link DataTransport}-derived instance for the given type
     * of {@link ConnectionMethod}.
     *
     * @param context application context.
     * @param role whether the transport will be used by the mdoc or mdoc reader.
     * @param options options for configuring the created instance.
     * @return A {@link DataTransport}-derived instance configured with the given options.
     * @throws IllegalArgumentException if the connection-method has invalid options specified.
     */
    public abstract @NonNull
    DataTransport createDataTransport(@NonNull Context context,
                                      @DataTransport.Role int role,
                                      @NonNull DataTransportOptions options);
}
