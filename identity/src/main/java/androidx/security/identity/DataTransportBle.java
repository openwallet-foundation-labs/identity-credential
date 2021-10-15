/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.security.identity;

import static android.content.Context.BLUETOOTH_SERVICE;
import static androidx.security.identity.Constants.BLE_CENTRAL_CLIENT_AND_PERIPHERAL_SERVER_MODE;
import static androidx.security.identity.Constants.BLE_CENTRAL_CLIENT_ONLY_MODE;
import static androidx.security.identity.Constants.BLE_PERIPHERAL_SERVER_MODE;
import static androidx.security.identity.Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.nfc.NdefRecord;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.security.identity.Constants.BleServiceMode;
import androidx.security.identity.Constants.LoggingFlag;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.Number;

/**
 * @hide
 *
 * BLE data transport implementation conforming to ISO 18013-5.
 *
 */
public class DataTransportBle extends DataTransport {
    private static final String TAG = "DataTransportBle";

    public static final int DEVICE_RETRIEVAL_METHOD_TYPE = 2;
    public static final int DEVICE_RETRIEVAL_METHOD_VERSION = 1;
    public static final int RETRIEVAL_OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE = 0;
    public static final int RETRIEVAL_OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE = 1;
    public static final int RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID = 10;
    public static final int RETRIEVAL_OPTION_KEY_CENTRAL_CLIENT_MODE_UUID = 11;
    public static final int RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS = 20;

    byte[] mEncodedDeviceRetrievalMethod;
    BluetoothManager mBluetoothManager;
    BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private @LoggingFlag
    final int mLoggingFlags;
    UUID mServiceClientUuid;
    UUID mServiceServerUuid;
    private GattServer mGattServer;
    GattClient mGattClient;
    BluetoothLeScanner mScanner;
    byte[] mEncodedEDeviceKeyBytes;

    @Override
    public void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes) {
        mEncodedEDeviceKeyBytes = encodedEDeviceKeyBytes;
    }

    static class BleOptions {
        boolean supportsPeripheralServerMode;
        boolean supportsCentralClientMode;
        byte[] peripheralServerModeUuid;
        byte[] centralClientModeUuid;
        byte[] peripheralServerModeBleDeviceAddress;
    }

    ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            mGattClient = new GattClient(mContext, mLoggingFlags, mServiceClientUuid, mEncodedEDeviceKeyBytes);
            mGattClient.setListener(new GattClient.Listener() {
                @Override
                public void onPeerConnected() {
                    // Connect to another device with GattClient stop GattServer if started
                    if (mGattServer != null) {
                        mGattServer.setListener(null);
                        mGattServer.stop();
                        mGattServer = null;
                    }
                    reportListeningPeerConnected();
                }

                @Override
                public void onPeerDisconnected() {
                    if (mGattClient != null) {
                        mGattClient.setListener(null);
                        mGattClient.disconnect();
                        mGattClient = null;
                    }
                    reportListeningPeerDisconnected();
                }

                @Override
                public void onMessageReceived(@NonNull byte[] data) {
                    reportMessageReceived(data);
                }

                @Override
                public void onTransportSpecificSessionTermination() {
                    reportTransportSpecificSessionTermination();
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    reportError(error);
                }
            });

            reportListeningPeerConnecting();
            mGattClient.connect(result.getDevice());
            mScanner.stopScan(mScanCallback); //
            Log.d(TAG, "stopScan");
            //mScanner = null;
            // TODO: Investigate. When testing with Reader C (which is on iOS) we get two callbacks
            //  and thus a NullPointerException when calling stopScan().
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.w(TAG, "Ignoring unexpected onBatchScanResults");
        }

        @Override
        public void onScanFailed(int errorCode) {
            reportError(new Error("BLE scan failed with error code " + errorCode));
        }
    };

    // Returns DeviceRetrievalMethod CBOR.
    //
    public static @Nullable byte[] parseNdefRecord(@NonNull NdefRecord record) {
        boolean centralClient = false;
        boolean peripheral = false;
        UUID uuid = null;
        boolean gotLeRole = false;
        boolean gotUuid = false;

        // The OOB data is defined in "Supplement to the Bluetooth Core Specification".
        //
        ByteBuffer payload = ByteBuffer.wrap(record.getPayload()).order(ByteOrder.LITTLE_ENDIAN);
        // We ignore length and just chew through all data...
        //
        payload.position(2);
        while (payload.remaining() > 0) {
            Log.d(TAG, "hasR: " + payload.hasRemaining() + " rem: " + payload.remaining());
            int len = payload.get();
            int type = payload.get();
            Log.d(TAG, String.format("type %d len %d", type, len));
            if (type == 0x1c && len == 2) {
                gotLeRole = true;
                int value = payload.get();
                if (value == 0x00) {
                    peripheral = true;
                } else if (value == 0x01) {
                    centralClient = true;
                } else if (value == 0x02) {
                    centralClient = true;
                    peripheral = true;
                } else {
                    Log.d(TAG, String.format("Invalid value %d for LE role", value));
                    return null;
                }
            } else if (type == 0x07) {
                int uuidLen = len - 1;
                if (uuidLen % 16 != 0) {
                    Log.d(TAG, String.format("UUID len %d is not divisible by 16", uuidLen));
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

        if (gotLeRole && gotUuid) {
            BleOptions options = new BleOptions();
            // If the the mdoc says it can do both central and peripheral, prefer central client.
            if (centralClient) {
                options.supportsCentralClientMode = true;
                options.centralClientModeUuid = uuidToBytes(uuid);
            } else {
                options.supportsPeripheralServerMode = true;
                options.peripheralServerModeUuid = uuidToBytes(uuid);
            }
            return buildDeviceRetrievalMethod(options);
        }

        return null;
    }

    private int mBleServiceMode;

    // Constructor used by the device who offers the device engagement
    public DataTransportBle(@NonNull Context context,
                            @BleServiceMode int bleServiceMode,
                            @LoggingFlag int loggingFlags) {
        super(context);
        mLoggingFlags = loggingFlags;
        mBleServiceMode = bleServiceMode;
    }

    private BleOptions parseDeviceRetrievalMethod(byte[] encodedDeviceRetrievalMethod) {
        DataItem d = Util.cborDecode(encodedDeviceRetrievalMethod);
        if (!(d instanceof Array)) {
            throw new IllegalArgumentException("Given CBOR is not an array");
        }
        DataItem[] items = ((Array) d).getDataItems().toArray(new DataItem[0]);
        if (items.length != 3) {
            throw new IllegalArgumentException("Expected three elements, found " + items.length);
        }
        if (!(items[0] instanceof Number)
                || !(items[1] instanceof Number)
                || !(items[2] instanceof Map)) {
            throw new IllegalArgumentException("Items not of required type");
        }
        int type = ((Number) items[0]).getValue().intValue();
        int version = ((Number) items[1]).getValue().intValue();
        if (type != DEVICE_RETRIEVAL_METHOD_TYPE
                || version > DEVICE_RETRIEVAL_METHOD_VERSION) {
            throw new IllegalArgumentException("Unexpected type or version");
        }
        Map options = ((Map) items[2]);
        BleOptions result = new BleOptions();
        if (Util.cborMapHasKey(options, RETRIEVAL_OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE)) {
            result.supportsPeripheralServerMode = Util.cborMapExtractBoolean(options,
                    RETRIEVAL_OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE);
        }
        if (Util.cborMapHasKey(options, RETRIEVAL_OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE)) {
            result.supportsCentralClientMode = Util.cborMapExtractBoolean(options,
                    RETRIEVAL_OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE);
        }
        if (Util.cborMapHasKey(options, RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID)) {
            result.peripheralServerModeUuid = Util.cborMapExtractByteString(options,
                    RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID);
        }
        if (Util.cborMapHasKey(options, RETRIEVAL_OPTION_KEY_CENTRAL_CLIENT_MODE_UUID)) {
            result.centralClientModeUuid = Util.cborMapExtractByteString(options,
                    RETRIEVAL_OPTION_KEY_CENTRAL_CLIENT_MODE_UUID);
        }
        if (Util.cborMapHasKey(options, RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS)) {
            result.peripheralServerModeBleDeviceAddress = Util.cborMapExtractByteString(options,
                    RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS);
        }
        return result;
    }


    // Constructor used by the device who reads the device engagement
    public DataTransportBle(@NonNull Context context, @LoggingFlag int loggingFlags) {
        super(context);
        mLoggingFlags = loggingFlags;
    }

    static @NonNull
    byte[] buildDeviceRetrievalMethod(@NonNull BleOptions options) {
        CborBuilder ob = new CborBuilder();
        MapBuilder<CborBuilder> omb = ob.addMap();
        omb.put(RETRIEVAL_OPTION_KEY_SUPPORTS_PERIPHERAL_SERVER_MODE,
                options.supportsPeripheralServerMode);
        omb.put(RETRIEVAL_OPTION_KEY_SUPPORTS_CENTRAL_CLIENT_MODE,
                options.supportsCentralClientMode);
        if (options.peripheralServerModeUuid != null) {
            omb.put(RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_UUID, options.peripheralServerModeUuid);
        }
        if (options.centralClientModeUuid != null) {
            omb.put(RETRIEVAL_OPTION_KEY_CENTRAL_CLIENT_MODE_UUID, options.centralClientModeUuid);
        }
        if (options.peripheralServerModeBleDeviceAddress != null) {
            omb.put(RETRIEVAL_OPTION_KEY_PERIPHERAL_SERVER_MODE_BLE_DEVICE_ADDRESS,
                    options.peripheralServerModeBleDeviceAddress);
        }

        byte[] ret = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(DEVICE_RETRIEVAL_METHOD_TYPE)
                .add(DEVICE_RETRIEVAL_METHOD_VERSION)
                .add(ob.build().get(0))
                .end()
                .build().get(0));
        return ret;
    }

    @Override
    public @Nullable
    Pair<NdefRecord, byte[]> getNdefRecords() {
        byte[] oobData;

        // The OOB data is defined in "Supplement to the Bluetooth Core Specification".
        //
        oobData = new byte[]{
                0, 0,
                // LE Role: Central Only (0x1c)
                (byte) 0x02, (byte) 0x1c, (byte) 0x01,
                // Complete List of 128-bit Service UUID’s (0x07)
                (byte) 0x11, (byte) 0x07,
                // UUID will be copied here..
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        };
        ByteBuffer uuidBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        // TODO: Fix NFC engagement
//        uuidBuf.putLong(0, mServiceUuid.getLeastSignificantBits());
//        uuidBuf.putLong(8, mServiceUuid.getMostSignificantBits());
        System.arraycopy(uuidBuf.array(), 0, oobData, 7, 16);
        // Length is stored in LE...
        oobData[0] = (byte) (oobData.length & 0xff);
        oobData[1] = (byte) (oobData.length / 256);
//        Log.d(TAG, "Encoding UUID " + mServiceUuid + " in NDEF");

        NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                "application/vnd.bluetooth.le.oob".getBytes(StandardCharsets.UTF_8),
                "0".getBytes(StandardCharsets.UTF_8),
                oobData);

        // From 7.1 Alternative Carrier Record
        //
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x01); // CPS: active
        baos.write(0x01); // Length of carrier data reference ("0")
        baos.write('0');  // Carrier data reference
        baos.write(0x01); // Number of auxiliary references
        // Each auxiliary reference consists of a single byte for the lenght and then as
        // many bytes for the reference itself.
        byte[] auxReference = "mdoc".getBytes(StandardCharsets.UTF_8);
        baos.write(auxReference.length);
        baos.write(auxReference, 0, auxReference.length);
        byte[] acRecordPayload = baos.toByteArray();

        return new Pair<>(record, acRecordPayload);
    }

    @Override
    public void listen() {
        if (mEncodedEDeviceKeyBytes == null) {
            reportError(new Error("EDeviceKeyBytes not set"));
            return;
        }

        BleOptions options = new BleOptions();


        if (mBleServiceMode == BLE_CENTRAL_CLIENT_ONLY_MODE ||
                mBleServiceMode == BLE_CENTRAL_CLIENT_AND_PERIPHERAL_SERVER_MODE) {
            // Add support to central client mode
            options.supportsCentralClientMode = true;
            mServiceClientUuid = UUID.randomUUID();
            options.centralClientModeUuid = uuidToBytes(mServiceClientUuid);
            // Central Client Mode
            startScan();
        }
        if (mBleServiceMode == BLE_PERIPHERAL_SERVER_MODE ||
                mBleServiceMode == BLE_CENTRAL_CLIENT_AND_PERIPHERAL_SERVER_MODE) {
            // Add support to peripheral server mode
            options.supportsPeripheralServerMode = true;
            mServiceServerUuid = UUID.randomUUID();
            options.peripheralServerModeUuid = uuidToBytes(mServiceServerUuid);
            // Peripheral Mode
            startAdvertising();
        }

        mEncodedDeviceRetrievalMethod = buildDeviceRetrievalMethod(options);
        reportListeningSetupCompleted(mEncodedDeviceRetrievalMethod);

    }

    private void startAdvertising() {
        if (mServiceServerUuid == null) {
            if ((mLoggingFlags & LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE) != 0) {
                Log.d(TAG, "Advertising not started, no UUID provided.");
            }
            return;
        }
        if ((mLoggingFlags & LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE) != 0) {
            Log.d(TAG, "Starting advertising on " + mServiceServerUuid);
        }
        BluetoothManager bluetoothManager =
                (BluetoothManager) mContext.getSystemService(BLUETOOTH_SERVICE);
        mGattServer = new GattServer(mContext, mLoggingFlags, bluetoothManager, mServiceServerUuid,
                mEncodedEDeviceKeyBytes);
        mGattServer.setListener(new GattServer.Listener() {
            @Override
            public void onPeerConnected() {
                // Connect to another device with GattServer stop GattClient if started
                if (mGattClient != null) {
                    mGattClient.setListener(null);
                    mGattClient.disconnect();
                    mGattClient = null;
                }
                reportConnectionResult(null);
                // No need to advertise anymore since we now have a client...
                if (mBluetoothLeAdvertiser != null) {
                    mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
                    mBluetoothLeAdvertiser = null;
                }
            }

            @Override
            public void onPeerDisconnected() {
                reportConnectionDisconnected();
            }

            @Override
            public void onMessageReceived(@NonNull byte[] data) {
                reportMessageReceived(data);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                reportError(error);
            }
        });
        if (!mGattServer.start()) {
            reportError(new Error("Error starting Gatt Server"));
        }

        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            reportError(new Error("Failed to create BLE advertiser"));
            mGattServer.stop();
            mGattServer = null;
        } else {
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setConnectable(true)
                    .setTimeout(0)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .build();
            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(new ParcelUuid(mServiceServerUuid))
                    .build();
            mBluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
            if ((mLoggingFlags & LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE) != 0) {
                Log.d(TAG, "Advertising started on " + mServiceServerUuid);
            }
        }
    }

    private void startScan() {
        if (mServiceClientUuid == null) {
            if ((mLoggingFlags & LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE) != 0) {
                Log.d(TAG, "Scan not started, no UUID provided.");
            }
            return;
        }
        if ((mLoggingFlags & LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE) != 0) {
            Log.d(TAG, "Start scanning on " + mServiceClientUuid);
        }

        // TODO: Check if BLE is enabled and error out if not so...

        // Start scanning...
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(mServiceClientUuid))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        mScanner = bluetoothAdapter.getBluetoothLeScanner();
        mScanner.startScan(List.of(filter), settings, mScanCallback);
        if ((mLoggingFlags & LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE) != 0) {
            Log.d(TAG, "Scanning started on " + mServiceClientUuid);
        }

    }

    static private byte[] uuidToBytes(UUID uuid) {
        ByteBuffer data = ByteBuffer.allocate(16);
        data.order(ByteOrder.BIG_ENDIAN);
        data.putLong(uuid.getMostSignificantBits());
        data.putLong(uuid.getLeastSignificantBits());
        return data.array();
    }

    static private UUID uuidFromBytes(byte[] bytes) {
        if (bytes.length != 16) {
            throw new IllegalStateException("Expected 16 bytes, found " + bytes.length);
        }
        ByteBuffer data = ByteBuffer.wrap(bytes, 0, 16);
        data.order(ByteOrder.BIG_ENDIAN);
        return new UUID(data.getLong(0), data.getLong(8));
    }

    @Override
    public void connect(@NonNull byte[] encodedDeviceRetrievalMethod) {
        if (mEncodedEDeviceKeyBytes == null) {
            reportError(new Error("EDeviceKeyBytes not set"));
            return;
        }

        mEncodedDeviceRetrievalMethod = encodedDeviceRetrievalMethod;

        // TODO: Check if BLE is enabled and error out if not so...

        BleOptions options = parseDeviceRetrievalMethod(encodedDeviceRetrievalMethod);

        // if device retrieval supports client central uses peripheral to advertise
        if (options.supportsCentralClientMode) {
            mServiceServerUuid = uuidFromBytes(options.centralClientModeUuid);
            // Peripheral Mode
            startAdvertising();
        } else if (options.supportsPeripheralServerMode) {
            mServiceClientUuid = uuidFromBytes(options.peripheralServerModeUuid);
            // Central Client Mode
            startScan();
        } else {
            reportError(new Error("Needs to support central client or peripheral server mode."));
        }
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
        }

        @Override
        public void onStartFailure(int errorCode) {
            reportError(new Error("BLE advertise failed with error code " + errorCode));
        }
    };

    @Override
    public @NonNull
    byte[] getEncodedDeviceRetrievalMethod() {
        return mEncodedDeviceRetrievalMethod;
    }

    @Override
    public void close() {
        inhibitCallbacks();
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            mBluetoothLeAdvertiser = null;
        }
        if (mGattServer != null) {
            mGattServer.setListener(null);
            mGattServer.stop();
        }
        if (mScanner != null) {
            mScanner.stopScan(mScanCallback);
            mScanner = null;
        }
        if (mGattClient != null) {
            mGattClient.setListener(null);
            mGattClient.disconnect();
            mGattClient = null;
        }
    }

    @Override
    public void sendMessage(@NonNull byte[] data) {
        if (mGattServer != null) {
            mGattServer.sendMessage(data);
        } else if (mGattClient != null) {
            mGattClient.sendMessage(data);
        }
    }


    @Override
    public void sendTransportSpecificTerminationMessage() {
        if (mGattServer == null) {
            // TODO: this is wrong.. actually implement this for holder too.
            reportError(new Error("Transport-specific termination only available for reader"));
            return;
        }
        mGattServer.sendTransportSpecificTermination();
    }

    @Override
    public boolean supportsTransportSpecificTerminationMessage() {
        return true;
    }
}
