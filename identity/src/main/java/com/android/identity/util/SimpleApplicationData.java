/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.identity.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.internal.Util;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * An implementation of {@link ApplicationData} using a LinkedHashMap to back the key-value pairs.
 */
public class SimpleApplicationData implements ApplicationData {
    private Map<String, byte[]> mApplicationData = new LinkedHashMap<>();
    private final Listener mListener;

    /**
     * Creates a new SimpleApplicationData and sets the listener to be used for notification
     * when changes are made to the {@link SimpleApplicationData}.
     *
     * @param listener the listener or <code>null</code> if this functionality is not needed.
     */
    public SimpleApplicationData(@Nullable Listener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public ApplicationData setData(@NonNull String key, @Nullable byte[] value) {
        if (value == null) {
            mApplicationData.remove(key);
        } else {
            mApplicationData.put(key, value);
        }

        if (mListener != null) {
            mListener.onDataSet();
        }
        return this;
    }

    @NonNull
    @Override
    public ApplicationData setString(@NonNull String key, @NonNull String value) {
        return this.setData(key, Util.cborEncodeString(value));
    }

    @NonNull
    @Override
    public ApplicationData setNumber(@NonNull String key, long value) {
        return this.setData(key, Util.cborEncodeNumber(value));
    }

    @NonNull
    @Override
    public ApplicationData setBoolean(@NonNull String key, boolean value) {
        return this.setData(key, Util.cborEncodeBoolean(value));
    }

    @Override
    public boolean keyExists(@NonNull String key) {
        return mApplicationData.get(key) != null;
    }

    @NonNull
    @Override
    public byte[] getData(@NonNull String key) {
        byte[] value = mApplicationData.get(key);
        if (value == null) {
            throw new IllegalArgumentException("This key is not present in the ApplicationData.");
        }
        return value;
    }

    @NonNull
    @Override
    public String getString(@NonNull String key) {
        byte[] value = this.getData(key);
        return Util.cborDecodeString(value);
    }

    @Override
    public long getNumber(@NonNull String key) {
        byte[] value = this.getData(key);
        return Util.cborDecodeLong(value);
    }

    @Override
    public boolean getBoolean(@NonNull String key) {
        byte[] value = this.getData(key);
        return Util.cborDecodeBoolean(value);
    }

    /**
     * Encode the {@link ApplicationData} as a byte[] using <a href="http://cbor.io/">CBOR</a>.
     *
     * @return a byte[] of the encoded app data.
     */
    public @NonNull byte[] encodeAsCbor() {
        CborBuilder appDataBuilder = new CborBuilder();
        MapBuilder<CborBuilder> appDataMapBuilder = appDataBuilder.addMap();
        for (String key : mApplicationData.keySet()) {
            appDataMapBuilder.put(key, mApplicationData.get(key));
        }
        appDataMapBuilder.end();
        return Util.cborEncode(appDataBuilder.build().get(0));
    }

    /**
     * Returns a fully populated SimpleApplicationData from a CBOR-encoded byte[] and sets the
     * listener to be used for notification when changes are made to the {@link SimpleApplicationData}.
     *
     * <p> To encode a SimpleApplicationData, use {@link #encodeAsCbor()}.
     *
     * @param encodedApplicationData The byte array resulting from {@link #encodeAsCbor()}.
     * @param listener the listener or <code>null</code> if this functionality is not needed.
     * @return A SimpleApplicationData with the correct key, value pairs.
     */
    public static @NonNull SimpleApplicationData decodeFromCbor(
            @NonNull byte[] encodedApplicationData, @Nullable Listener listener) {
        ByteArrayInputStream bais = new ByteArrayInputStream(encodedApplicationData);
        List<DataItem> dataItems;
        try {
            dataItems = new CborDecoder(bais).decode();
        } catch (CborException e) {
            throw new IllegalStateException("Error decoded CBOR", e);
        }
        if (dataItems.size() != 1) {
            throw new IllegalStateException("Expected 1 item, found " + dataItems.size());
        }
        if (!(dataItems.get(0) instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalStateException("Item is not a map");
        }
        DataItem applicationDataDataItem = dataItems.get(0);

        SimpleApplicationData appData = new SimpleApplicationData(listener);
        for (DataItem keyItem : ((co.nstant.in.cbor.model.Map) applicationDataDataItem).getKeys()) {
            String key = ((UnicodeString) keyItem).getString();
            byte[] value = Util.cborMapExtractByteString(applicationDataDataItem, key);
            appData.mApplicationData.put(key, value);
        }
        return appData;
    }

    /**
     * Interface for listening for changes to the data in the {@link SimpleApplicationData}.
     *
     * <p>The {@link Listener#onDataSet()}  callback will be called every time the data stored
     * is modified (e.g. adding a key-value pair, changing a value, or removing a key-value pair).
     */
    public interface Listener {

        /**
         * Called when the data inside the {@link SimpleApplicationData} is changed
         */
        void onDataSet();
    }
}
