package com.android.identity;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.nfc.NdefRecord;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
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

    public @Override
    @NonNull
    DataTransport createDataTransport(@NonNull Context context,
                                      @DataTransport.Role int role,
                                      @NonNull DataTransportOptions options) {
        DataTransportNfc t = new DataTransportNfc(context, role, this, options);
        // TODO: set mCommandDataFieldMaxLength and mResponseDataFieldMaxLength
        return t;
    }

    @Override
    public @NonNull
    String toString() {
        return "nfc:cmd_max_length=" + mCommandDataFieldMaxLength
                + ":resp_max_length=" + mResponseDataFieldMaxLength;
    }

    @Nullable
    static ConnectionMethodNfc fromDeviceEngagement(@NonNull DataItem cmDataItem) {
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
        return new ConnectionMethodNfc(
                Util.cborMapExtractNumber(options, OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH),
                Util.cborMapExtractNumber(options, OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH));
    }

    static @Nullable
    ConnectionMethod fromNdefRecord(@NonNull NdefRecord record) {
        ByteBuffer payload = ByteBuffer.wrap(record.getPayload()).order(ByteOrder.LITTLE_ENDIAN);
        int version = payload.get();
        if (version != 0x01) {
            Logger.w(TAG, "Expected version 0x01, found " + version);
            return null;
        }

        int cmdLen = payload.get() & 0xff;
        int cmdType = payload.get() & 0xff;
        if (cmdType != 0x01) {
            Logger.w(TAG, "expected type 0x01, found " + cmdType);
            return null;
        }
        if (cmdLen < 2 || cmdLen > 3) {
            Logger.w(TAG, "expected cmdLen in range 2-3, got " + cmdLen);
            return null;
        }

        int commandDataFieldMaxLength = 0;
        for (int n = 0; n < cmdLen - 1; n++) {
            commandDataFieldMaxLength *= 256;
            commandDataFieldMaxLength += payload.get() & 0xff;
        }

        int rspLen = payload.get() & 0xff;
        int rspType = payload.get() & 0xff;
        if (rspType != 0x02) {
            Logger.w(TAG, "expected type 0x02, found " + rspType);
            return null;
        }
        if (rspLen < 2 || rspLen > 4) {
            Logger.w(TAG, "expected rspLen in range 2-4, got " + rspLen);
            return null;
        }

        int responseDataFieldMaxLength = 0;
        for (int n = 0; n < rspLen - 1; n++) {
            responseDataFieldMaxLength *= 256;
            responseDataFieldMaxLength += payload.get() & 0xff;
        }

        return new ConnectionMethodNfc(commandDataFieldMaxLength, responseDataFieldMaxLength);
    }

    @NonNull
    @Override
    DataItem toDeviceEngagement() {
        MapBuilder<CborBuilder> builder = new CborBuilder().addMap();
        builder.put(OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH, mCommandDataFieldMaxLength);
        builder.put(OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH, mResponseDataFieldMaxLength);
        return new CborBuilder()
                .addArray()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(builder.end().build().get(0))
                .end()
                .build().get(0);
    }

    private static void encodeInt(int dataType, int value, ByteArrayOutputStream baos) {
        if (value < 0x100) {
            baos.write(0x02); // Length
            baos.write(dataType);
            baos.write(value & 0xff);
        } else if (value < 0x10000) {
            baos.write(0x03); // Length
            baos.write(dataType);
            baos.write(value / 0x100);
            baos.write(value & 0xff);
        } else {
            baos.write(0x04); // Length
            baos.write(dataType);
            baos.write(value / 0x10000);
            baos.write((value / 0x100) & 0xff);
            baos.write(value & 0xff);
        }
    }

    @Override @NonNull
    Pair<NdefRecord, byte[]> toNdefRecord() {
        byte[] carrierDataReference = "nfc".getBytes(UTF_8);

        // This is defined by ISO 18013-5 8.2.2.2 Alternative Carrier Record for device
        // retrieval using NFC.
        //
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x01);  // Version
        encodeInt(0x01, (int) mCommandDataFieldMaxLength, baos);
        encodeInt(0x02, (int) mResponseDataFieldMaxLength, baos);
        byte[] oobData = baos.toByteArray();

        NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                "iso.org:18013:nfc".getBytes(UTF_8),
                carrierDataReference,
                oobData);

        // From 7.1 Alternative Carrier Record
        //
        baos = new ByteArrayOutputStream();
        baos.write(0x01); // CPS: active
        baos.write(carrierDataReference.length); // Length of carrier data reference
        try {
            baos.write(carrierDataReference);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        baos.write(0x01); // Number of auxiliary references
        byte[] auxReference = "mdoc".getBytes(UTF_8);
        baos.write(auxReference.length);  // Length of auxiliary reference 0 data
        baos.write(auxReference, 0, auxReference.length);
        byte[] acRecordPayload = baos.toByteArray();

        return new Pair<>(record, acRecordPayload);
    }
}