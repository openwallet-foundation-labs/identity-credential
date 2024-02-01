package com.android.identity.mdoc.connectionmethod;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.internal.Util;
import com.android.identity.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    /**
     * Generates {@code DeviceRetrievalMethod} CBOR for the given {@link ConnectionMethod}.
     *
     * <p>See ISO/IEC 18013-5:2021 section 8.2.1.1 Device engagement structure for where
     * {@code DeviceRetrievalMethod} CBOR is defined.
     *
     * <p>This is the reverse operation of {@link #toDeviceEngagement()}.
     *
     * @return
     */
    public abstract @NonNull
    byte[] toDeviceEngagement();

    /**
     * Constructs a new {@link ConnectionMethod} from {@code DeviceRetrievalMethod} CBOR.
     *
     * <p>See ISO/IEC 18013-5:2021 section 8.2.1.1 Device engagement structure for where
     * {@code DeviceRetrievalMethod} CBOR is defined.
     *
     * <p>This is the reverse operation of {@link #toDeviceEngagement()}.
     *
     * @param encodedDeviceRetrievalMethod the bytes of {@code DeviceRetrievalMethod} CBOR.
     * @return A {@link ConnectionMethod}-derived instance or {@code null} if the method
     *         isn't supported.
     * @throws IllegalArgumentException if the given CBOR is malformed.
     */
    public static @Nullable
    ConnectionMethod fromDeviceEngagement(@NonNull byte[] encodedDeviceRetrievalMethod) {
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
        if (!(items.get(2) instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalArgumentException("Third item is not a map");
        }
        switch ((int) type) {
            case ConnectionMethodNfc.METHOD_TYPE:
                return ConnectionMethodNfc.fromDeviceEngagementNfc(encodedDeviceRetrievalMethod);
            case ConnectionMethodBle.METHOD_TYPE:
                return ConnectionMethodBle.fromDeviceEngagementBle(encodedDeviceRetrievalMethod);
            case ConnectionMethodWifiAware.METHOD_TYPE:
                return ConnectionMethodWifiAware.fromDeviceEngagementWifiAware(encodedDeviceRetrievalMethod);
            case ConnectionMethodHttp.METHOD_TYPE:
                return ConnectionMethodHttp.fromDeviceEngagementHttp(encodedDeviceRetrievalMethod);
        }
        Logger.w(TAG, String.format(Locale.US,
                "Unsupported ConnectionMethod type %d in DeviceEngagement", type));
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
}
