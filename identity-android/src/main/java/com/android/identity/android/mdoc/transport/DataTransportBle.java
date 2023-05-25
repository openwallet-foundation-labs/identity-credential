/*
 * Copyright 2022 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.nfc.NdefRecord;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle;
import com.android.identity.util.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;

/**
 * BLE data transport
 */
public abstract class DataTransportBle extends DataTransport {
    private static final String TAG = "DataTransportBle";

    // The size of buffer for read() calls when using L2CAP sockets.
    static final int L2CAP_BUF_SIZE = 4096;
    protected UUID mServiceUuid;
    protected ConnectionMethodBle mConnectionMethod;

    public DataTransportBle(@NonNull Context context,
                            @Role int role,
                            @NonNull ConnectionMethodBle connectionMethod,
                            @NonNull DataTransportOptions options) {
        super(context, role, options);
        mConnectionMethod = connectionMethod;
    }

    public static @Nullable
    ConnectionMethodBle fromNdefRecord(@NonNull NdefRecord record,
                                       boolean isForHandoverSelect) {
        boolean centralClient = false;
        boolean peripheral = false;
        UUID uuid = null;
        boolean gotLeRole = false;
        boolean gotUuid = false;

        // See createNdefRecords() method for how this data is encoded.
        //
        ByteBuffer payload = ByteBuffer.wrap(record.getPayload()).order(ByteOrder.LITTLE_ENDIAN);
        while (payload.remaining() > 0) {
            int len = payload.get();
            int type = payload.get();
            if (type == 0x1c && len == 2) {
                gotLeRole = true;
                int value = payload.get();
                if (value == 0x00) {
                    if (isForHandoverSelect) {
                        peripheral = true;
                    } else {
                        centralClient = true;
                    }
                } else if (value == 0x01) {
                    if (isForHandoverSelect) {
                        centralClient = true;
                    } else {
                        peripheral = true;
                    }
                } else if (value == 0x02 || value == 0x03) {
                    centralClient = true;
                    peripheral = true;
                } else {
                    Logger.w(TAG, String.format("Invalid value %d for LE role", value));
                    return null;
                }
            } else if (type == 0x07) {
                int uuidLen = len - 1;
                if (uuidLen % 16 != 0) {
                    Logger.w(TAG, String.format("UUID len %d is not divisible by 16", uuidLen));
                    return null;
                }
                // We only use the last UUID...
                for (int n = 0; n < uuidLen; n += 16) {
                    long lsb = payload.getLong();
                    long msb = payload.getLong();
                    uuid = new UUID(msb, lsb);
                    gotUuid = true;
                }
            } else {
                Logger.d(TAG, String.format("Skipping unknown type %d of length %d", type, len));
                payload.position(payload.position() + len - 1);
            }
        }

        if (!gotLeRole) {
            Logger.w(TAG, "Did not find LE role");
            return null;
        }

        // Note that UUID may _not_ be set.

        // Note that the UUID for both modes is the same if both peripheral and
        // central client mode is used!
        return new ConnectionMethodBle(peripheral,
                centralClient,
                peripheral ? uuid : null,
                centralClient ? uuid : null);
    }

    void setServiceUuid(@NonNull UUID serviceUuid) {
        mServiceUuid = serviceUuid;
    }

    @Override
    public @NonNull ConnectionMethod getConnectionMethod() {
        return mConnectionMethod;
    }

    static @NonNull
    DataTransport fromConnectionMethod(@NonNull Context context,
                                       @NonNull ConnectionMethodBle cm,
                                       @DataTransport.Role int role,
                                       @NonNull DataTransportOptions options) {
        if (cm.getSupportsCentralClientMode() && cm.getSupportsPeripheralServerMode()) {
            throw new IllegalArgumentException(
                    "BLE connection method is ambiguous. Use ConnectionMethod.disambiguate() "
                            + "before picking one.");
        }
        if (cm.getSupportsCentralClientMode()) {
            DataTransportBle t = new DataTransportBleCentralClientMode(context, role, cm, options);
            t.setServiceUuid(cm.getCentralClientModeUuid());
            return t;
        }
        if (cm.getSupportsPeripheralServerMode()) {
            DataTransportBle t = new DataTransportBlePeripheralServerMode(context, role, cm, options);
            t.setServiceUuid(cm.getPeripheralServerModeUuid());
            return t;
        }
        throw new IllegalArgumentException("BLE connection method supports neither modes");
    }

    public static @Nullable
    Pair<NdefRecord, byte[]> toNdefRecord(@NonNull ConnectionMethodBle cm,
                                          @NonNull List<String> auxiliaryReferences,
                                          boolean isForHandoverSelect) {
        // The OOB data is defined in "Supplement to the Bluetooth Core Specification".
        //
        // See section 1.17.2 for values
        //
        UUID uuid;
        int leRole;
        if (cm.getSupportsCentralClientMode() && cm.getSupportsPeripheralServerMode()) {
            // Peripheral and Central Role supported,
            // Central Role preferred for connection
            // establishment
            leRole = 0x03;
            if (!cm.getCentralClientModeUuid().equals(cm.getPeripheralServerModeUuid())) {
                throw new IllegalStateException("UUIDs for both BLE modes must be the same");
            }
            uuid = cm.getCentralClientModeUuid();
        } else if (cm.getSupportsCentralClientMode()) {
            if (isForHandoverSelect) {
                // Only Central Role supported
                leRole = 0x01;
            } else {
                // Only Peripheral Role supported
                leRole = 0x00;
            }
            uuid = cm.getCentralClientModeUuid();
        } else if (cm.getSupportsPeripheralServerMode()) {
            if (isForHandoverSelect) {
                // Only Peripheral Role supported
                leRole = 0x00;
            } else {
                // Only Central Role supported
                leRole = 0x01;
            }
            uuid = cm.getPeripheralServerModeUuid();
        } else {
            throw new IllegalStateException("At least one of the BLE modes must be set");
        }

        // See "3 Handover to a Bluetooth Carrier" of "Bluetooth® Secure Simple Pairing Using
        // NFC Application Document" Version 1.2. This says:
        //
        //   For Bluetooth LE OOB the name “application/vnd.bluetooth.le.oob” is used as the
        //   [NDEF] record type name. The payload of this type of record is then defined by the
        //   Advertising and Scan Response Data (AD) format that is specified in the Bluetooth Core
        //   Specification ([BLUETOOTH_CORE], Volume 3, Part C, Section 11).
        //
        // Looking that up it says it's just a sequence of {length, AD type, AD data} where each
        // AD is defined in the "Bluetooth Supplement to the Core Specification" document.
        //
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x02);  // LE Role
        baos.write(0x1c);
        baos.write(leRole);
        if (uuid != null) {
            baos.write(0x11);  // Complete List of 128-bit Service UUID’s (0x07)
            baos.write(0x07);
            ByteBuffer uuidBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            uuidBuf.putLong(0, uuid.getLeastSignificantBits());
            uuidBuf.putLong(8, uuid.getMostSignificantBits());
            try {
                baos.write(uuidBuf.array());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        byte[] oobData = baos.toByteArray();
        NdefRecord record = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
                "application/vnd.bluetooth.le.oob".getBytes(UTF_8),
                "0".getBytes(UTF_8),
                oobData);

        // From 7.1 Alternative Carrier Record
        //
        baos = new ByteArrayOutputStream();
        baos.write(0x01); // CPS: active
        baos.write(0x01); // Length of carrier data reference ("0")
        baos.write('0');  // Carrier data reference
        baos.write(auxiliaryReferences.size()); // Number of auxiliary references
        for (String auxRef : auxiliaryReferences) {
            // Each auxiliary reference consists of a single byte for the length and then as
            // many bytes for the reference itself.
            byte[] auxRefUtf8 = auxRef.getBytes(UTF_8);
            baos.write(auxRefUtf8.length);
            baos.write(auxRefUtf8, 0, auxRefUtf8.length);
        }
        byte[] acRecordPayload = baos.toByteArray();

        return new Pair<>(record, acRecordPayload);
    }
}