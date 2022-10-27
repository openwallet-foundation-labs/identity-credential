package com.android.identity;

import android.content.Context;
import android.nfc.NdefRecord;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.Number;

/**
 * Connection method for HTTP connections.
 */
public class ConnectionMethodHttp extends ConnectionMethod {
    private static final String TAG = "ConnectionOptionsRestApi";
    private String mUriWebsite;

    static final int METHOD_TYPE = 4;
    static final int METHOD_MAX_VERSION = 1;
    private static final int OPTION_KEY_URI_WEBSITE = 0;

    /**
     * Creates a new connection method for HTTP.
     *
     * @param uriWebsite the URL for the website.
     */
    public ConnectionMethodHttp(@NonNull String uriWebsite) {
        mUriWebsite = uriWebsite;
    }

    /**
     * Gets the URL for the website.
     *
     * @return the website URL.
     */
    public @NonNull
    String getUriWebsite() {
        return mUriWebsite;
    }

    public @Override
    @NonNull
    DataTransport createDataTransport(@NonNull Context context,
                                      @NonNull DataTransportOptions options) {
        URI uri = null;
        try {
            uri = new URI(mUriWebsite);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        DataTransportHttp transport = new DataTransportHttp(context, options);
        if (uri.getScheme().equals("http")) {
            transport.setHost(uri.getHost());
            int port = uri.getPort();
            if (port == -1) {
                port = 80;
            }
            transport.setPort(port);
            transport.setPath(uri.getPath());
            return transport;
        } else if (uri.getScheme().equals("https")) {
            transport.setHost(uri.getHost());
            int port = uri.getPort();
            if (port == -1) {
                port = 443;
            }
            transport.setPort(port);
            transport.setPath(uri.getPath());
            transport.setUseTls(true);
            return transport;
        }
        throw new IllegalArgumentException("Unsupported scheme " + uri.getScheme());
    }

    @Override
    public @NonNull
    String toString() {
        return "http:uri=" + mUriWebsite;
    }

    @Nullable
    static ConnectionMethodHttp fromDeviceEngagement(@NonNull DataItem cmDataItem) {
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
        return new ConnectionMethodHttp(
                Util.cborMapExtractString(options, OPTION_KEY_URI_WEBSITE));
    }

    @NonNull
    @Override
    DataItem toDeviceEngagement() {
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

    @Override @NonNull
    Pair<NdefRecord, byte[]> toNdefRecord() {
        throw new IllegalStateException("NDEF records for this connection method is not defined");
    }
}