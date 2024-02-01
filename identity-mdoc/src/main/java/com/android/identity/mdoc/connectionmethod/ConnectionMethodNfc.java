package com.android.identity.mdoc.connectionmethod;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.internal.Util;
import com.android.identity.util.Logger;

import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Number;

/**
 * Connection method for NFC.
 */
public class ConnectionMethodNfc extends ConnectionMethod {
    private static final String TAG = "ConnectionMethodNfc";
    private final long mCommandDataFieldMaxLength;
    private final long mResponseDataFieldMaxLength;

    static final int METHOD_TYPE = 1;
    static final int METHOD_MAX_VERSION = 1;
    private static final int OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH = 0;
    private static final int OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH = 1;

    /**
     * Creates a new connection method for NFC.
     *
     * @param commandDataFieldMaxLength  the maximum length for the command data field.
     * @param responseDataFieldMaxLength the maximum length of the response data field.
     */
    public ConnectionMethodNfc(long commandDataFieldMaxLength,
                               long responseDataFieldMaxLength) {
        mCommandDataFieldMaxLength = commandDataFieldMaxLength;
        mResponseDataFieldMaxLength = responseDataFieldMaxLength;
    }

    /**
     * Gets the maximum length for the command data field.
     *
     * @return the value.
     */
    public long getCommandDataFieldMaxLength() {
        return mCommandDataFieldMaxLength;
    }

    /**
     * Gets the maximum length for the response data field.
     *
     * @return the value.
     */
    public long getResponseDataFieldMaxLength() {
        return mResponseDataFieldMaxLength;
    }

    @Override
    public @NonNull
    String toString() {
        return "nfc:cmd_max_length=" + mCommandDataFieldMaxLength
                + ":resp_max_length=" + mResponseDataFieldMaxLength;
    }

    @Nullable
    static ConnectionMethodNfc fromDeviceEngagementNfc(@NonNull byte[] encodedDeviceRetrievalMethod) {
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
        return new ConnectionMethodNfc(
                Util.cborMapExtractNumber(options, OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH),
                Util.cborMapExtractNumber(options, OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH));
    }

    @Override
    public @NonNull
    byte[] toDeviceEngagement() {
        MapBuilder<CborBuilder> builder = new CborBuilder().addMap();
        builder.put(OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH, mCommandDataFieldMaxLength);
        builder.put(OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH, mResponseDataFieldMaxLength);
        return Util.cborEncode(new CborBuilder()
                .addArray()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(builder.end().build().get(0))
                .end()
                .build().get(0));
    }
}