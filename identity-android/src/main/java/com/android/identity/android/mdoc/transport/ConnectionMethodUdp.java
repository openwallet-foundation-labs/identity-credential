/*
 * Copyright (C) 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity.android.mdoc.transport;

import androidx.annotation.NonNull;

import com.android.identity.internal.Util;
import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.util.Logger;

import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Number;

public class ConnectionMethodUdp extends ConnectionMethod {
    private static final String TAG = "ConnectionMethodUdp";

    // NOTE: 18013-5 only allows positive integers, but our codebase also supports negative
    // ones and this way we won't clash with types defined in the standard.
    public static final int METHOD_TYPE = -11;
    static final int METHOD_MAX_VERSION = 1;
    private static final int OPTION_KEY_HOST = 0;
    private static final int OPTION_KEY_PORT = 1;
    private final String mHost;
    private final int mPort;

    public ConnectionMethodUdp(@NonNull String host, int port) {
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

    public static ConnectionMethod fromDeviceEngagementUdp(byte[] encodedDeviceRetrievalMethod) {
        DataItem cmDataItem = Util.cborDecode(encodedDeviceRetrievalMethod);
        if (!(cmDataItem instanceof Array)) {
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
        return new ConnectionMethodUdp(host, (int) port);
    }

    @Override
    public @NonNull
    String toString() {
        return "udp:host=" + mHost + ":port=" + mPort;
    }

}
