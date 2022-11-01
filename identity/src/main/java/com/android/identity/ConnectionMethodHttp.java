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
    private final String mUri;

    static final int METHOD_TYPE = 4;
    static final int METHOD_MAX_VERSION = 1;
    private static final int OPTION_KEY_URI = 0;

    /**
     * Creates a new connection method for HTTP.
     *
     * @param uri the URI.
     */
    public ConnectionMethodHttp(@NonNull String uri) {
        mUri = uri;
    }

    /**
     * Gets the URI.
     *
     * @return the URI.
     */
    public @NonNull
    String getUri() {
        return mUri;
    }

    public @Override
    @NonNull
    DataTransport createDataTransport(@NonNull Context context,
                                      @DataTransport.Role int role,
                                      @NonNull DataTransportOptions options) {
        // For the mdoc reader role, this should be empty since DataTransportHttp will return
        // an ConnectionMethodHttp object containing the local IP address and the TCP port that
        // was assigned.
        if (role == DataTransport.ROLE_MDOC_READER) {
            if (!mUri.equals("")) {
                throw new IllegalArgumentException("URI must be empty for mdoc reader role");
            }
            DataTransportHttp transport = new DataTransportHttp(context, role, this, options);
            return transport;
        }

        // For the mdoc role, this should be an URI pointing to a server on the Internet.
        URI uri = null;
        try {
            uri = new URI(mUri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        DataTransportHttp transport = new DataTransportHttp(context, role, this, options);
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
        return "http:uri=" + mUri;
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
                Util.cborMapExtractString(options, OPTION_KEY_URI));
    }

    @NonNull
    @Override
    DataItem toDeviceEngagement() {
        MapBuilder<CborBuilder> builder = new CborBuilder().addMap();
        builder.put(OPTION_KEY_URI, mUri);
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