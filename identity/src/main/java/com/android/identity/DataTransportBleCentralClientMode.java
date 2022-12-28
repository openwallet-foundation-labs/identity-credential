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
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BLE data transport implementation conforming to ISO 18013-5 mdoc
 * central client mode.
 */
@SuppressWarnings("MissingPermission")
class DataTransportBleCentralClientMode extends DataTransportBle {
    private static final String TAG = "DataTransportBleCCM"; // limit to <= 23 chars

    UUID mCharacteristicStateUuid = UUID.fromString("00000005-a123-48ce-896b-4c76973373e6");
    UUID mCharacteristicClient2ServerUuid = UUID.fromString("00000006-a123-48ce-896b-4c76973373e6");
    UUID mCharacteristicServer2ClientUuid = UUID.fromString("00000007-a123-48ce-896b-4c76973373e6");
    UUID mCharacteristicIdentUuid = UUID.fromString("00000008-a123-48ce-896b-4c76973373e6");
    /**
     * In _mdoc central client mode_ the _mdoc_ acts as the GATT client and the _mdoc reader_ acts as the
     * GATT server. According to ISO 18013-5 Table A.1 this means that in _mdoc central client mode_
     * the GATT server (for the _mdoc reader_) should advertise
     * UUID 0000000B-A123-48CE896B-4C76973373E6 and the GATT client (for the _mdoc_) should
     * connect to that UUID.
     */
    UUID mCharacteristicL2CAPUuidMdocReader = UUID.fromString("0000000b-a123-48ce-896b-4c76973373e6");

    BluetoothManager mBluetoothManager;
    BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    GattClient mGattClient;
    BluetoothLeScanner mScanner;
    byte[] mEncodedEDeviceKeyBytes;
    long mTimeScanningStartedMillis;

    // a flag to prevent multiple GattClient connects which cause to multiple
    // new GattClient instances and to crashes
    // https://stackoverflow.com/a/38276808/4940838
    boolean mIsConnecting = false;

    ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            // if we already scanned and connect to device we don't want to
            // reconnect to another GattClient instance.
            if (mIsConnecting) {
                return;
            }
            mIsConnecting = true;
            UUID characteristicL2CAPUuid = null;
            if (mOptions.getBleUseL2CAP()) {
                characteristicL2CAPUuid = mCharacteristicL2CAPUuidMdocReader;
            }
            mGattClient = new GattClient(mContext,
                    mServiceUuid, mEncodedEDeviceKeyBytes,
                    mCharacteristicStateUuid, mCharacteristicClient2ServerUuid,
                    mCharacteristicServer2ClientUuid, mCharacteristicIdentUuid,
                    characteristicL2CAPUuid);
            mGattClient.setListener(new GattClient.Listener() {
                @Override
                public void onPeerConnected() {
                    reportConnected();
                }

                @Override
                public void onPeerDisconnected() {
                    if (mGattClient != null) {
                        mGattClient.setListener(null);
                        mGattClient.disconnect();
                        mGattClient = null;
                    }
                    reportDisconnected();
                }

                @Override
                public void onMessageReceived(@NonNull byte[] data) {
                    reportMessageReceived(data);
                }

                @Override
                public void onMessageSendProgress(final long progress, final long max) {
                    reportMessageProgress(progress, max);
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

            reportConnecting();
            long scanTimeMillis = System.currentTimeMillis() - mTimeScanningStartedMillis;
            Logger.d(TAG, "Scanned for " + scanTimeMillis + " milliseconds. "
                    + "Connecting to device with address " + result.getDevice().getAddress());
            mGattClient.setClearCache(mOptions.getBleClearCache());
            mGattClient.connect(result.getDevice());
            if (mScanner != null) {
                Logger.d(TAG, "Stopped scanning for UUID " + mServiceUuid);
                try {
                    mScanner.stopScan(mScanCallback);

                } catch (SecurityException e) {
                    reportError(e);
                    return;
                }
                mScanner = null;
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Logger.w(TAG, "Ignoring unexpected onBatchScanResults");
        }

        @Override
        public void onScanFailed(int errorCode) {
            reportError(new Error("BLE scan failed with error code " + errorCode));
        }
    };
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
    private GattServer mGattServer;

    public DataTransportBleCentralClientMode(@NonNull Context context,
                                             @Role int role,
                                             @NonNull ConnectionMethodBle connectionMethod,
                                             @NonNull DataTransportOptions options) {
        super(context, role, connectionMethod, options);
    }

    @Override
    public void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes) {
        mEncodedEDeviceKeyBytes = encodedEDeviceKeyBytes;
    }

    private void connectAsMdoc() {
        // TODO: Check if BLE is enabled and error out if not so...

        // Start scanning...
        mBluetoothManager = mContext.getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(mServiceUuid))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        Logger.d(TAG, "Started scanning for UUID " + mServiceUuid);
        mTimeScanningStartedMillis = System.currentTimeMillis();
        mScanner = bluetoothAdapter.getBluetoothLeScanner();
        List<ScanFilter> filterList = new ArrayList<>();
        filterList.add(filter);
        try {
            mScanner.startScan(filterList, settings, mScanCallback);
        } catch (SecurityException e) {
            reportError(e);
        }
    }

    private void connectAsMdocReader() {
        BluetoothManager bluetoothManager = mContext.getSystemService(BluetoothManager.class);
        UUID characteristicL2CAPUuid = null;
        if (mOptions.getBleUseL2CAP()) {
            characteristicL2CAPUuid = mCharacteristicL2CAPUuidMdocReader;
        }
        mGattServer = new GattServer(mContext, bluetoothManager, mServiceUuid,
                mEncodedEDeviceKeyBytes,
                mCharacteristicStateUuid, mCharacteristicClient2ServerUuid,
                mCharacteristicServer2ClientUuid, mCharacteristicIdentUuid,
                characteristicL2CAPUuid);
        mGattServer.setListener(new GattServer.Listener() {
            @Override
            public void onPeerConnected() {
                reportConnected();
                // No need to advertise anymore since we now have a client...
                if (mBluetoothLeAdvertiser != null) {
                    Logger.d(TAG, "Stopping advertising UUID " + mServiceUuid);
                    try {
                        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
                    } catch (SecurityException e) {
                        reportError(e);
                        return;
                    }
                    mBluetoothLeAdvertiser = null;
                }
            }

            @Override
            public void onPeerDisconnected() {
                reportDisconnected();
            }

            @Override
            public void onTransportSpecificSessionTermination() {
                Logger.d(TAG, "onTransportSpecificSessionTermination");
                reportTransportSpecificSessionTermination();
            }

            @Override
            public void onMessageReceived(@NonNull byte[] data) {
                reportMessageReceived(data);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                reportError(error);
            }

            @Override
            public void onMessageSendProgress(final long progress, final long max) {
                reportMessageProgress(progress, max);
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
                    .addServiceUuid(new ParcelUuid(mServiceUuid))
                    .build();
            Logger.d(TAG, "Started advertising UUID " + mServiceUuid);
            try {
                mBluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
            } catch (SecurityException e) {
                reportError(e);
            }
        }
    }

    @Override
    public void connect() {
        // TODO: Check if BLE is enabled and error out if not so...

        if (mRole == ROLE_MDOC) {
            connectAsMdoc();
        } else {
            connectAsMdocReader();
        }
        reportConnectionMethodReady();
    }

    @Override
    public void close() {
        mIsConnecting = false;
        inhibitCallbacks();
        if (mBluetoothLeAdvertiser != null) {
            Logger.d(TAG, "Stopping advertising UUID " + mServiceUuid);
            try {
                mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            } catch (SecurityException e) {
                Logger.e(TAG, "Caught SecurityException while shutting down: " + e);
            }
            mBluetoothLeAdvertiser = null;
        }
        if (mGattServer != null) {
            mGattServer.setListener(null);
            mGattServer.stop();
            mGattServer = null;
        }
        if (mScanner != null) {
            Logger.d(TAG, "Stopped scanning for UUID " + mServiceUuid);
            try {
                mScanner.stopScan(mScanCallback);
            } catch (SecurityException e) {
                Logger.e(TAG, "Caught SecurityException while shutting down: " + e);
            }
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
            if (mGattClient == null) {
                reportError(new Error("Transport-specific termination not available"));
                return;
            }
            mGattClient.sendTransportSpecificTermination();
            return;
        }
        mGattServer.sendTransportSpecificTermination();
    }

    @Override
    public boolean supportsTransportSpecificTerminationMessage() {
        if (mGattServer != null) {
            return mGattServer.supportsTransportSpecificTerminationMessage();
        } else if (mGattClient != null) {
            return mGattClient.supportsTransportSpecificTerminationMessage();
        }
        return false;
    }
}
