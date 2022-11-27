package com.android.mdl.app.provisioning;

import static com.android.mdl.app.util.LogginExtensionsKt.log;
import static com.android.mdl.app.util.LogginExtensionsKt.logError;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;

class CborHelper {
    private static final String TAG = "CborHelper";

    public static @NonNull
    byte[] encode(@NonNull DataItem dataitem) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            new CborEncoder(outputStream).encode(dataitem);
        } catch (CborException e) {
            String message = "CborEncode Exception: " + e.getMessage();
            log(TAG, message, e.fillInStackTrace());
            throw new IllegalArgumentException(message, e);
        }
        return outputStream.toByteArray();
    }

    public static @NonNull
    DataItem decode(@NonNull byte[] encodedBytes) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(encodedBytes);
        List<DataItem> dataItems;
        try {
            dataItems = new CborDecoder(inputStream).decode();
        } catch (CborException e) {
            String message = "CborDecode Exception: " + e.getMessage();
            log(TAG, message, e.fillInStackTrace());
            throw new IllegalArgumentException(message, e);
        }
        if (dataItems == null) {
            String message = "Error decoding " + encodeToString(encodedBytes) + " result"
                    + " in a null list";
            logError(TAG, message);
            throw new IllegalArgumentException(message);
        }
        if (dataItems.size() != 1) {
            String message = "Unexpected number of items, expected 1 got "
                    + dataItems.size();
            logError(TAG, message);
            throw new IllegalArgumentException(message);
        }
        return dataItems.get(0);
    }

    public static @NonNull
    String encodeToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        if (bytes == null) return sb.toString();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
