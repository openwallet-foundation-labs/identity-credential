package com.android.identity.android.mdoc.transport;

import androidx.annotation.NonNull;

import com.android.identity.internal.Util;
import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle;
import com.android.identity.util.Logger;

import java.util.List;
import java.util.UUID;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Number;

public class ConnectionMethodTcp extends ConnectionMethod {
    private static final String TAG = "ConnectionMethodTcp";

    // NOTE: 18013-5 only allows positive integers, but our codebase also supports negative
    // ones and this way we won't clash with types defined in the standard.
    public static final int METHOD_TYPE = -10;
    static final int METHOD_MAX_VERSION = 1;
    private static final int OPTION_KEY_HOST = 0;
    private static final int OPTION_KEY_PORT = 1;
    private final String mHost;
    private final int mPort;

    public ConnectionMethodTcp(@NonNull String host, int port) {
        mHost = host;
        mPort = port;
    }

    public @NonNull String getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    @NonNull
    @Override
    public byte[] toDeviceEngagement() {
        MapBuilder<CborBuilder> builder = new CborBuilder().addMap();
        builder.put(OPTION_KEY_HOST, mHost);
        builder.put(OPTION_KEY_PORT, mPort);
        return Util.cborEncode(new CborBuilder()
                .addArray()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(builder.end().build().get(0))
                .end()
                .build().get(0));
    }

    public static ConnectionMethod fromDeviceEngagementTcp(byte[] encodedDeviceRetrievalMethod) {
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
        String host = Util.cborMapExtractString(options, OPTION_KEY_HOST);
        long port = Util.cborMapExtractNumber(options, OPTION_KEY_PORT);
        return new ConnectionMethodTcp(host, (int) port);
    }

    @Override
    public @NonNull
    String toString() {
        return "tcp:host=" + mHost + ":port=" + mPort;
    }

}
