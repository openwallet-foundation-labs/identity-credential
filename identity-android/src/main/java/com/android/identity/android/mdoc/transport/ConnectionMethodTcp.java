package com.android.identity.android.mdoc.transport;

import androidx.annotation.NonNull;

import com.android.identity.internal.Util;
import com.android.identity.mdoc.connectionmethod.ConnectionMethod;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;

public class ConnectionMethodTcp extends ConnectionMethod {
    // NOTE: 18013-5 only allows positive integers, but our codebase also supports negative
    // ones and this way we won't clash with types defined in the standard.
    static final int METHOD_TYPE = -10;
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
}
