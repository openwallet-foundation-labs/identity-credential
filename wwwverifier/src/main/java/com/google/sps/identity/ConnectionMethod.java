package com.android.identity.wwwreader;

import static java.nio.charset.StandardCharsets.UTF_8;

//import android.content.Context;

//import android.util.Log;

//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;

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

    abstract
    DataItem toDeviceEngagement();

    static
    ConnectionMethod fromDeviceEngagement(DataItem cmDataItem) {
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
            /*
            case ConnectionMethodNfc.METHOD_TYPE:
                return ConnectionMethodNfc.decode(cmDataItem);
            case ConnectionMethodBle.METHOD_TYPE:
                return ConnectionMethodBle.decode(cmDataItem);
            case ConnectionMethodWifiAware.METHOD_TYPE:
                return ConnectionMethodWifiAware.decode(cmDataItem);
            */
            case ConnectionMethodHttp.METHOD_TYPE:
                return ConnectionMethodHttp.fromDeviceEngagement(cmDataItem);
        }
        //Log.w(TAG, "Unsupported type " + type);
        return null;
    }
}