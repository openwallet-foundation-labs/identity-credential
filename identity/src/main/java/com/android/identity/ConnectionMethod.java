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

    abstract @NonNull
    Pair<NdefRecord, byte[]> toNdefRecord();

    static @Nullable
    ConnectionMethod fromNdefRecord(@NonNull NdefRecord record) {
        // BLE Carrier Configuration record
        //
        if (record.getTnf() == 0x02
                && Arrays.equals(record.getType(),
                "application/vnd.bluetooth.le.oob".getBytes(UTF_8))
                && Arrays.equals(record.getId(), "0".getBytes(UTF_8))) {
            return ConnectionMethodBle.fromNdefRecord(record);
        }

        // Wifi Aware Carrier Configuration record
        //
        if (record.getTnf() == 0x02
                && Arrays.equals(record.getType(),
                "application/vnd.wfa.nan".getBytes(UTF_8))
                && Arrays.equals(record.getId(), "W".getBytes(UTF_8))) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return ConnectionMethodWifiAware.fromNdefRecord(record);
            } else {
                Log.i(TAG, "Ignoring Wifi Aware Carrier Configuration since Wifi Aware "
                        + "is not available on this API level");
                return null;
            }
        }

        // NFC Carrier Configuration record
        //
        if (record.getTnf() == 0x02
                && Arrays.equals(record.getType(),
                "iso.org:18013:nfc".getBytes(UTF_8))
                && Arrays.equals(record.getId(), "nfc".getBytes(UTF_8))) {
            return ConnectionMethodNfc.fromNdefRecord(record);
        }

        Logger.d(TAG, "Unknown NDEF record " + record);
        return null;
    }


    /**
     * Disambiguates a list of connection methods.
     *
     * Given a list of connection methods, produce a list where each method represents exactly
     * one connectable endpoint. For example, for BLE if both central client mode and peripheral
     * server mode is set, replaces this with two connection methods so it's clear which one is
     * which.
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
