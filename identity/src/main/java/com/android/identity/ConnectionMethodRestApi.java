package com.android.identity;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.Number;

/**
 * Connection method for REST API.
 */
public class ConnectionMethodRestApi extends ConnectionMethod {
    private static final String TAG = "ConnectionOptionsRestApi";
    private String mUriWebsite;

    static final int METHOD_TYPE = 4;
    static final int METHOD_MAX_VERSION = 1;
    private static final int OPTION_KEY_URI_WEBSITE = 0;

    /**
     * Creates a new connection method for REST API.
     *
     * @param uriWebsite the URL for the website.
     */
    public ConnectionMethodRestApi(@NonNull String uriWebsite) {
        mUriWebsite = uriWebsite;
    }

    /**
     * Gets the URL for the website.
     *
     * @return the website URL.
     */
    public @NonNull String getUriWebsite() {
        return mUriWebsite;
    }

    @NonNull
    @Override
    DataItem encode() {
        MapBuilder<CborBuilder> builder = new CborBuilder().addMap();
        builder.put(OPTION_KEY_URI_WEBSITE, mUriWebsite);
        return new CborBuilder()
                .addArray()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(builder.end().build().get(0))
                .end()
                .build().get(0);
    }

    @Nullable
    static ConnectionMethodRestApi decode(@NonNull DataItem cmDataItem) {
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
        return new ConnectionMethodRestApi(
                Util.cborMapExtractString(options, OPTION_KEY_URI_WEBSITE));
    }
}
