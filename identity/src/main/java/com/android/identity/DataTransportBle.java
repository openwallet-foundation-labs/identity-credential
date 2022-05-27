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

package com.android.identity;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.nfc.NdefRecord;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.identity.Constants.LoggingFlag;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;

/**
 * BLE data transport
 */
abstract class DataTransportBle extends DataTransport {
    public static final int DEVICE_RETRIEVAL_METHOD_TYPE = 2;
    public static final int DEVICE_RETRIEVAL_METHOD_VERSION = 1;
    public static final int RETRIEVAL_OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE = 0;
    public static final int RETRIEVAL_OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE = 1;
    public static final int RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID = 10;
    public static final int RETRIEVAL_OPTION_KEY_CENTRAL_CLIENT_MODE_UUID = 11;
    public static final int RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS = 20;
    private static final String TAG = "DataTransportBle";
    @LoggingFlag
    protected int mLoggingFlags;

    public DataTransportBle(
            @NonNull Context context, @LoggingFlag int loggingFlags) {
        super(context);
        mLoggingFlags = loggingFlags;
    }

    protected static @NonNull
    byte[] uuidToBytes(@NonNull UUID uuid) {
        ByteBuffer data = ByteBuffer.allocate(16);
        data.order(ByteOrder.BIG_ENDIAN);
        data.putLong(uuid.getMostSignificantBits());
        data.putLong(uuid.getLeastSignificantBits());
        return data.array();
    }

    protected static @NonNull
    UUID uuidFromBytes(@NonNull byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalStateException("Expected 16 bytes, found " + bytes.length);
        }
        ByteBuffer data = ByteBuffer.wrap(bytes, 0, 16);
        data.order(ByteOrder.BIG_ENDIAN);
        return new UUID(data.getLong(0), data.getLong(8));
    }

    public static @Nullable
    List<DataRetrievalAddress> parseNdefRecord(@NonNull NdefRecord record) {
        boolean centralClient = false;
        boolean peripheral = false;
        UUID uuid = null;
        boolean gotLeRole = false;
        boolean gotUuid = false;

        // See createNdefRecords() method for how this data is encoded.
        //
        ByteBuffer payload = ByteBuffer.wrap(record.getPayload()).order(ByteOrder.LITTLE_ENDIAN);
        while (payload.remaining() > 0) {
            Log.d(TAG, "hasR: " + payload.hasRemaining() + " rem: " + payload.remaining());
            int len = payload.get();
            int type = payload.get();
            Log.d(TAG, String.format(Locale.US, "type %d len %d", type, len));
            if (type == 0x1c && len == 2) {
                gotLeRole = true;
                int value = payload.get();
                if (value == 0x00) {
                    peripheral = true;
                } else if (value == 0x01) {
                    centralClient = true;
                } else if (value == 0x02 || value == 0x03) {
                    centralClient = true;
                    peripheral = true;
                } else {
                    Log.w(TAG, String.format("Invalid value %d for LE role", value));
                    return null;
                }
            } else if (type == 0x07) {
                int uuidLen = len - 1;
                if (uuidLen % 16 != 0) {
                    Log.w(TAG, String.format("UUID len %d is not divisible by 16", uuidLen));
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
                Log.d(TAG, String.format("Skipping unknown type %d of length %d", type, len));
                payload.position(payload.position() + len - 1);
            }
        }

        if (!gotLeRole) {
            Log.w(TAG, "Did not find LE role");
            return null;
        }
        if (!gotUuid) {
            Log.w(TAG, "Did not find UUID");
            return null;
        }

        List<DataRetrievalAddress> addresses = new ArrayList<>();
        if (centralClient) {
            addresses.add(new DataRetrievalAddressBleCentralClientMode(uuid));
        }
        if (peripheral) {
            addresses.add(new DataRetrievalAddressBlePeripheralServerMode(uuid));
        }

        if (addresses.size() > 0) {
            return addresses;
        }
        return null;
    }

    static public @Nullable
    List<DataRetrievalAddress> parseDeviceRetrievalMethod(int version, @NonNull DataItem[] items) {
        if (version > DEVICE_RETRIEVAL_METHOD_VERSION) {
            Log.w(TAG, "Unexpected version " + version + " for retrieval method");
            return null;
        }
        if (items.length < 3 || !(items[2] instanceof Map)) {
            Log.w(TAG, "Item 3 in device retrieval array is not a map");
        }
        Map options = ((Map) items[2]);

        List<DataRetrievalAddress> addresses = new ArrayList<>();
        if (Util.cborMapHasKey(options, RETRIEVAL_OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE)
                && Util.cborMapExtractBoolean(options,
                RETRIEVAL_OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE)) {
            if (Util.cborMapHasKey(options, RETRIEVAL_OPTION_KEY_CENTRAL_CLIENT_MODE_UUID)) {
                byte[] uuidBytes = Util.cborMapExtractByteString(options,
                        RETRIEVAL_OPTION_KEY_CENTRAL_CLIENT_MODE_UUID);
                addresses.add(
                        new DataRetrievalAddressBleCentralClientMode(uuidFromBytes(uuidBytes)));
            } else {
                Log.w(TAG, "No UUID field for mdoc central client mode");
            }
        }

        if (Util.cborMapHasKey(options, RETRIEVAL_OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE)
                && Util.cborMapExtractBoolean(options,
                RETRIEVAL_OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE)) {
            if (Util.cborMapHasKey(options, RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID)) {
                byte[] uuidBytes = Util.cborMapExtractByteString(options,
                        RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID);
                addresses.add(
                        new DataRetrievalAddressBlePeripheralServerMode(uuidFromBytes(uuidBytes)));
            } else {
                Log.w(TAG, "No UUID field for mdoc peripheral server mode");
            }
        }

        if (addresses.size() > 0) {
            return addresses;
        }
        return null;
    }

    static protected @Nullable
    Pair<NdefRecord, byte[]> buildNdefRecords(boolean centralClientSupported,
            boolean peripheralServerSupported,
            @NonNull UUID serviceUuid) {
        // The OOB data is defined in "Supplement to the Bluetooth Core Specification".
        //
        // See section 1.17.2 for values
        //
        int leRole = 0;
        if (centralClientSupported && peripheralServerSupported) {
            // Peripheral and Central Role supported,
            // Central Role preferred for connection
            // establishment
            leRole = 0x03;
        } else if (centralClientSupported) {
            // Only Central Role supported
            leRole = 0x01;
        } else if (peripheralServerSupported) {
            // Only Peripheral Role supported
            leRole = 0x00;
        }

        byte[] oobData = new byte[]{
                0, 0,
                // LE Role
                (byte) 0x02, (byte) 0x1c, (byte) leRole,
                // Complete List of 128-bit Service UUID’s (0x07)
                (byte) 0x11, (byte) 0x07,
                // UUID will be copied here..
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        };
        ByteBuffer uuidBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        uuidBuf.putLong(0, serviceUuid.getLeastSignificantBits());
        uuidBuf.putLong(8, serviceUuid.getMostSignificantBits());
        System.arraycopy(uuidBuf.array(), 0, oobData, 7, 16);
        // Length is stored in LE...
        oobData[0] = (byte) (oobData.length & 0xff);
        oobData[1] = (byte) (oobData.length / 256);
        Log.d(TAG, "Encoding UUID " + serviceUuid + " in NDEF");

        NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                "application/vnd.bluetooth.le.oob".getBytes(UTF_8),
                "0".getBytes(UTF_8),
                oobData);

        // From 7.1 Alternative Carrier Record
        //
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x01); // CPS: active
        baos.write(0x01); // Length of carrier data reference ("0")
        baos.write('0');  // Carrier data reference
        baos.write(0x01); // Number of auxiliary references
        // Each auxiliary reference consists of a single byte for the length and then as
        // many bytes for the reference itself.
        byte[] auxReference = "mdoc".getBytes(UTF_8);
        baos.write(auxReference.length);
        baos.write(auxReference, 0, auxReference.length);
        byte[] acRecordPayload = baos.toByteArray();

        return new Pair<>(record, acRecordPayload);
    }

    static abstract class DataRetrievalAddressBle extends DataRetrievalAddress {

        UUID uuid;

        DataRetrievalAddressBle findOtherBleAddress(List<DataRetrievalAddress> listeningAddresses) {
            for (DataRetrievalAddress address : listeningAddresses) {
                if (address != this && (address instanceof DataRetrievalAddressBle)) {
                    return (DataRetrievalAddressBle) address;
                }
            }
            return null;
        }

        boolean isOtherBleAddressFirst(List<DataRetrievalAddress> listeningAddresses,
                DataRetrievalAddress otherAddress) {
            for (DataRetrievalAddress address : listeningAddresses) {
                if (address == this) {
                    return false;
                } else if (address == otherAddress) {
                    return true;
                }
            }
            return false;
        }

        // This is the same for both mdoc central client mode and mdoc peripheral server mode.
        @Override
        Pair<NdefRecord, byte[]> createNdefRecords(List<DataRetrievalAddress> listeningAddresses) {
            // If we have two entries, only generate NDEF records for the first.
            DataRetrievalAddressBle otherAddress = findOtherBleAddress(listeningAddresses);
            if (otherAddress != null) {
                if (isOtherBleAddressFirst(listeningAddresses, otherAddress)) {
                    return null;
                }
            }

            boolean centralClientSupported = false;
            boolean peripheralServerSupported = false;
            if (otherAddress == null) {
                if (this instanceof DataRetrievalAddressBleCentralClientMode) {
                    centralClientSupported = true;
                } else {
                    peripheralServerSupported = true;
                }
            } else {
                centralClientSupported = true;
                peripheralServerSupported = true;
                if (!uuid.equals(otherAddress.uuid)) {
                    throw new IllegalStateException("UUIDs for BLE addresses are not identical");
                }
            }

            // The OOB data is defined in "Supplement to the Bluetooth Core Specification".
            //
            // See section 1.17.2 for values
            //
            int leRole = 0;
            if (centralClientSupported && peripheralServerSupported) {
                // Peripheral and Central Role supported,
                // Central Role preferred for connection
                // establishment
                leRole = 0x03;
            } else if (centralClientSupported) {
                // Only Central Role supported
                leRole = 0x01;
            } else if (peripheralServerSupported) {
                // Only Peripheral Role supported
                leRole = 0x00;
            }

            // See "3 Handover to a Bluetooth Carrier" of "Bluetooth® Secure Simple Pairing Using
            // NFC Application Document" Version 1.2. This says:
            //
            //   For Bluetooth LE OOB the name “application/vnd.bluetooth.le.oob” is used as the
            //   [NDEF]
            //   record type name. The payload of this type of record is then defined by the
            //   Advertising
            //   and Scan Response Data (AD) format that is specified in the Bluetooth Core
            //   Specification
            //   ([BLUETOOTH_CORE], Volume 3, Part C, Section 11).
            //
            // Looking that up it says it's just a sequence of {length, AD type, AD data} where each
            // AD is defined in the "Bluetooth Supplement to the Core Specification" document.
            //
            byte[] oobData = new byte[]{
                    // LE Role
                    (byte) 0x02, (byte) 0x1c, (byte) leRole,
                    // Complete List of 128-bit Service UUID’s (0x07)
                    (byte) 0x11, (byte) 0x07,
                    // UUID will be copied here (offset 5)..
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            };
            ByteBuffer uuidBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            uuidBuf.putLong(0, uuid.getLeastSignificantBits());
            uuidBuf.putLong(8, uuid.getMostSignificantBits());
            System.arraycopy(uuidBuf.array(), 0, oobData, 5, 16);
            Log.d(TAG, "Encoding UUID " + uuid + " in NDEF");

            NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                    "application/vnd.bluetooth.le.oob".getBytes(UTF_8),
                    "0".getBytes(UTF_8),
                    oobData);

            // From 7.1 Alternative Carrier Record
            //
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(0x01); // CPS: active
            baos.write(0x01); // Length of carrier data reference ("0")
            baos.write('0');  // Carrier data reference
            baos.write(0x01); // Number of auxiliary references
            // Each auxiliary reference consists of a single byte for the length and then as
            // many bytes for the reference itself.
            byte[] auxReference = "mdoc".getBytes(UTF_8);
            baos.write(auxReference.length);
            baos.write(auxReference, 0, auxReference.length);
            byte[] acRecordPayload = baos.toByteArray();

            return new Pair<>(record, acRecordPayload);
        }

    }

    static class DataRetrievalAddressBleCentralClientMode extends DataRetrievalAddressBle {
        DataRetrievalAddressBleCentralClientMode(UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        @NonNull
        DataTransport createDataTransport(
                @NonNull Context context, @LoggingFlag int loggingFlags) {
            return new DataTransportBleCentralClientMode(context, loggingFlags);
        }

        @Override
        void addDeviceRetrievalMethodsEntry(ArrayBuilder<CborBuilder> arrayBuilder,
                List<DataRetrievalAddress> listeningAddresses) {

            DataRetrievalAddress otherAddress = findOtherBleAddress(listeningAddresses);
            if (otherAddress != null) {
                if (isOtherBleAddressFirst(listeningAddresses, otherAddress)) {
                    return;
                }
                arrayBuilder.addArray()
                        .add(DEVICE_RETRIEVAL_METHOD_TYPE)
                        .add(DEVICE_RETRIEVAL_METHOD_VERSION)
                        .addMap()
                        .put(RETRIEVAL_OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE, true)
                        .put(RETRIEVAL_OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE, true)
                        .put(RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID,
                                uuidToBytes(((DataRetrievalAddressBlePeripheralServerMode)
                                        otherAddress).uuid))
                        .put(RETRIEVAL_OPTION_KEY_CENTRAL_CLIENT_MODE_UUID, uuidToBytes(uuid))
                        .end()
                        .end();
            } else {
                arrayBuilder.addArray()
                        .add(DEVICE_RETRIEVAL_METHOD_TYPE)
                        .add(DEVICE_RETRIEVAL_METHOD_VERSION)
                        .addMap()
                        .put(RETRIEVAL_OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE, false)
                        .put(RETRIEVAL_OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE, true)
                        .put(RETRIEVAL_OPTION_KEY_CENTRAL_CLIENT_MODE_UUID, uuidToBytes(uuid))
                        .end()
                        .end();
            }
        }

        @Override
        public @NonNull
        String toString() {
            return "ble:mdoc_central_client_mode:uuid=" + uuid;
        }
    }

    static class DataRetrievalAddressBlePeripheralServerMode extends DataRetrievalAddressBle {
        // TODO: support MAC address
        DataRetrievalAddressBlePeripheralServerMode(UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        @NonNull
        DataTransport createDataTransport(
                @NonNull Context context, @LoggingFlag int loggingFlags) {
            return new DataTransportBlePeripheralServerMode(context, loggingFlags);
        }

        @Override
        void addDeviceRetrievalMethodsEntry(ArrayBuilder<CborBuilder> arrayBuilder,
                List<DataRetrievalAddress> listeningAddresses) {

            DataRetrievalAddress otherAddress = findOtherBleAddress(listeningAddresses);
            if (otherAddress != null) {
                if (isOtherBleAddressFirst(listeningAddresses, otherAddress)) {
                    return;
                }
                arrayBuilder.addArray()
                        .add(DEVICE_RETRIEVAL_METHOD_TYPE)
                        .add(DEVICE_RETRIEVAL_METHOD_VERSION)
                        .addMap()
                        .put(RETRIEVAL_OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE, true)
                        .put(RETRIEVAL_OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE, true)
                        .put(RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID, uuidToBytes(uuid))
                        .put(RETRIEVAL_OPTION_KEY_CENTRAL_CLIENT_MODE_UUID,
                                uuidToBytes(((DataRetrievalAddressBleCentralClientMode)
                                        otherAddress).uuid))
                        .end()
                        .end();
            } else {
                arrayBuilder.addArray()
                        .add(DEVICE_RETRIEVAL_METHOD_TYPE)
                        .add(DEVICE_RETRIEVAL_METHOD_VERSION)
                        .addMap()
                        .put(RETRIEVAL_OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE, true)
                        .put(RETRIEVAL_OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE, false)
                        .put(RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID, uuidToBytes(uuid))
                        .end()
                        .end();
            }
        }

        @Override
        public @NonNull
        String toString() {
            return "ble:mdoc_peripheral_server_mode:uuid=" + uuid;
        }
    }
}
