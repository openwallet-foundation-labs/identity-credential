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

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

@SuppressWarnings("deprecation")
@SuppressLint("MissingPermission")
class GattClient extends BluetoothGattCallback {
    private static final String TAG = "GattClient";

    private final Context mContext;
    private final UUID mServiceUuid;
    private final byte[] mEncodedEDeviceKeyBytes;
    Listener mListener;
    BluetoothGatt mGatt;

    BluetoothGattCharacteristic mCharacteristicState;
    BluetoothGattCharacteristic mCharacteristicClient2Server;
    BluetoothGattCharacteristic mCharacteristicServer2Client;
    BluetoothGattCharacteristic mCharacteristicIdent;
    BluetoothGattCharacteristic mCharacteristicL2CAP;

    UUID mCharacteristicStateUuid;
    UUID mCharacteristicClient2ServerUuid;
    UUID mCharacteristicServer2ClientUuid;
    UUID mCharacteristicIdentUuid;
    UUID mCharacteristicL2CAPUuid;
    private L2CAPClient mL2CAPClient;

    // This is what the 16-bit UUID 0x29 0x02 is encoded like.
    UUID mClientCharacteristicConfigUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    ByteArrayOutputStream mIncomingMessage = new ByteArrayOutputStream();
    Queue<byte[]> mWritingQueue = new ArrayDeque<>();
    int writingQueueTotalChunks;
    boolean writeIsOutstanding = false;
    private boolean mInhibitCallbacks = false;
    private int mNegotiatedMtu;
    private byte[] mIdentValue;
    private boolean mUsingL2CAP = false;
    private boolean mClearCache;

    GattClient(@NonNull Context context,
               @NonNull UUID serviceUuid,
               @Nullable byte[] encodedEDeviceKeyBytes,
               @NonNull UUID characteristicStateUuid,
               @NonNull UUID characteristicClient2ServerUuid,
               @NonNull UUID characteristicServer2ClientUuid,
               @Nullable UUID characteristicIdentUuid,
               @Nullable UUID characteristicL2CAPUuid) {
        mContext = context;
        mServiceUuid = serviceUuid;
        mEncodedEDeviceKeyBytes = encodedEDeviceKeyBytes;
        mCharacteristicStateUuid = characteristicStateUuid;
        mCharacteristicClient2ServerUuid = characteristicClient2ServerUuid;
        mCharacteristicServer2ClientUuid = characteristicServer2ClientUuid;
        mCharacteristicIdentUuid = characteristicIdentUuid;
        mCharacteristicL2CAPUuid = characteristicL2CAPUuid;
    }

    void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    void setClearCache(boolean clearCache) {
        mClearCache = clearCache;
    }

    void connect(BluetoothDevice device) {
        if (mEncodedEDeviceKeyBytes != null) {
            byte[] ikm = mEncodedEDeviceKeyBytes;
            byte[] info = new byte[]{'B', 'L', 'E', 'I', 'd', 'e', 'n', 't'};
            byte[] salt = new byte[]{};
            mIdentValue = Util.computeHkdf("HmacSha256", ikm, salt, info, 16);
        }
        try {
            mGatt = device.connectGatt(mContext, false, this, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) {
            reportError(e);
        }
    }

    void disconnect() {
        mInhibitCallbacks = true;
        if (mL2CAPClient != null) {
            mL2CAPClient.disconnect();
            mL2CAPClient = null;
        }
        if (mGatt != null) {
            try {
                mGatt.disconnect();
            } catch (SecurityException e) {
                Logger.e(TAG, "Caught SecurityException while shutting down: " + e);
            }
            mGatt = null;
        }
    }

    private void clearCache(BluetoothGatt gatt) {
        Logger.d(TAG, "Application requested clearing BLE Service Cache");
        // BluetoothGatt.refresh() is not public API but can be accessed via introspection...
        try {
            Method refreshMethod = gatt.getClass().getMethod("refresh");
            Boolean result = false;
            if (refreshMethod != null) {
                result = (Boolean) refreshMethod.invoke(gatt);
            }
            if (result) {
                Logger.d(TAG, "BluetoothGatt.refresh() invoked successfully");
            } else {
                Logger.e(TAG, "BluetoothGatt.refresh() invoked but returned false");
            }
        } catch (NoSuchMethodException e) {
            Logger.e(TAG, "Getting BluetoothGatt.refresh() failed with NoSuchMethodException", e);
        } catch (IllegalAccessException e) {
            Logger.e(TAG, "Getting BluetoothGatt.refresh() failed with IllegalAccessException", e);
        } catch (InvocationTargetException e) {
            Logger.e(TAG, "Getting BluetoothGatt.refresh() failed with InvocationTargetException", e);
        }
    }

    @Override
    public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
        Logger.d(TAG, "onConnectionStateChange: status=" + status + " newState=" + newState);
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            //Logger.d(TAG, "Connected");
            try {
                if (mClearCache) {
                    clearCache(gatt);
                }
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                gatt.discoverServices();
            } catch (SecurityException e) {
                reportError(e);
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            //Logger.d(TAG, "Disconnected");
            reportPeerDisconnected();
        }
    }

    @Override
    public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
        Logger.d(TAG, "onServicesDiscovered: status=" + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            BluetoothGattService s = gatt.getService(mServiceUuid);
            if (s != null) {
                if (mCharacteristicL2CAPUuid != null) {
                    mCharacteristicL2CAP = s.getCharacteristic(mCharacteristicL2CAPUuid);
                    if (mCharacteristicL2CAP != null) {
                        Logger.d(TAG, "L2CAP characteristic found " + mCharacteristicL2CAPUuid);
                    }
                }
                mCharacteristicState = s.getCharacteristic(mCharacteristicStateUuid);
                if (mCharacteristicState == null) {
                    reportError(new Error("State characteristic not found"));
                    return;
                }

                mCharacteristicClient2Server = s.getCharacteristic(
                        mCharacteristicClient2ServerUuid);
                if (mCharacteristicClient2Server == null) {
                    reportError(new Error("Client2Server characteristic not found"));
                    return;
                }

                mCharacteristicServer2Client = s.getCharacteristic(
                        mCharacteristicServer2ClientUuid);
                if (mCharacteristicServer2Client == null) {
                    reportError(new Error("Server2Client characteristic not found"));
                    return;
                }

                if (mCharacteristicIdentUuid != null) {
                    mCharacteristicIdent = s.getCharacteristic(mCharacteristicIdentUuid);
                    if (mCharacteristicIdent == null) {
                        reportError(new Error("Ident characteristic not found"));
                        return;
                    }
                }
            }

            // Start by bumping MTU, callback in onMtuChanged()...
            //
            // Which MTU should we choose? On Android the maximum MTU size is said to be 517.
            //
            // Also 18013-5 section 8.3.3.1.1.6 Data retrieval says to write attributes to
            // Client2Server and Server2Client characteristics of a size which 3 less the
            // MTU size. If we chose an MTU of 517 then the attribute we'd write would be
            // 514 bytes long.
            //
            // Also note that Bluetooth Core specification Part F section 3.2.9 Long attribute
            // values says "The maximum length of an attribute value shall be 512 octets." ... so
            // with an MTU of 517 we'd blow through that limit. An MTU limited to 515 bytes
            // will work though.
            //
            // ... so we request 515 bytes for the MTU. We might not get such a big MTU, the way
            // it works is that the requestMtu() call will trigger a negotiation between the client (us)
            // and the server (the remote device).
            //
            // We'll get notified in BluetoothGattCallback.onMtuChanged() below.
            //
            // The server will also be notified about the new MTU - if it's running Android
            // it'll be via BluetoothGattServerCallback.onMtuChanged(), see GattServer.java
            // for that in our implementation.
            try {
                if (!gatt.requestMtu(515)) {
                    reportError(new Error("Error requesting MTU"));
                    return;
                }
            } catch (SecurityException e) {
                reportError(e);
                return;
            }

            mGatt = gatt;
        }
    }

    @Override
    public void onMtuChanged(@NonNull BluetoothGatt gatt, int mtu, int status) {
        mNegotiatedMtu = mtu;

        if (status != BluetoothGatt.GATT_SUCCESS) {
            reportError(new Error("Error changing MTU, status: " + status));
            return;
        }

        Logger.d(TAG, "Negotiated MTU " + mtu);

        if (mCharacteristicIdent != null && mIdentValue != null) {
            // Read ident characteristics...
            //
            // TODO: maybe skip this, it's optional after all...
            //
            try {
                if (!gatt.readCharacteristic(mCharacteristicIdent)) {
                    reportError(new Error("Error reading from ident characteristic"));
                }
            } catch (SecurityException e) {
                reportError(e);
            }
            // Callback happens in onDescriptorRead() right below...
            //
        } else {
            afterIdentObtained(gatt);
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void onCharacteristicRead(@NonNull BluetoothGatt gatt,
            @NonNull BluetoothGattCharacteristic characteristic,
            int status) {
        if (characteristic.getUuid().equals(mCharacteristicIdentUuid)) {
            byte[] identValue = characteristic.getValue();
            if (Logger.isDebugEnabled()) {
                Logger.d(TAG, "Received identValue: " + Util.toHex(identValue));
            }
            // TODO: Don't even request IDENT since it cannot work w/ reverse engagement (there's
            //   no way the mdoc reader knows EDeviceKeyBytes at this point) and it's also optional.
            if (!Arrays.equals(identValue, mIdentValue)) {
                Logger.w(TAG, "Received ident '" + Util.toHex(identValue)
                        + "' does not match expected ident '" + Util.toHex(mIdentValue) + "'");
            }

            afterIdentObtained(gatt);
        } else if (characteristic.getUuid().equals(mCharacteristicL2CAPUuid)) {
            if (!mUsingL2CAP) {
                reportError(new Error("Unexpected read for L2CAP characteristic "
                        + characteristic.getUuid() + ", L2CAP not supported"));
                return;
            }
            mL2CAPClient = new L2CAPClient(mContext, new L2CAPClient.Listener() {
                @Override
                public void onPeerConnected() {
                    reportPeerConnected();
                }

                @Override
                public void onPeerDisconnected() {
                    reportPeerDisconnected();
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
                public void onMessageSendProgress(long progress, long max) {
                    reportMessageSendProgress(progress, max);
                }
            });

            mL2CAPClient.connect(mGatt.getDevice(), characteristic.getValue());
        } else {
            reportError(new Error("Unexpected onCharacteristicRead for characteristic "
                    + characteristic.getUuid() + ", expected " + mCharacteristicIdentUuid));
        }
    }

    private void afterIdentObtained(@NonNull BluetoothGatt gatt) {
        try {
            // Use L2CAP if supported by GattServer and by this OS version
            mUsingL2CAP = mCharacteristicL2CAP != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;             
            Logger.d(TAG, "Using L2CAP: " + mUsingL2CAP);
            if (mUsingL2CAP) {
                // value is returned async above in onCharacteristicRead()
                if (!gatt.readCharacteristic(mCharacteristicL2CAP)) {
                    reportError(new Error("Error reading L2CAP characteristic"));
                }
                return;
            }

            if (!gatt.setCharacteristicNotification(mCharacteristicServer2Client, true)) {
                reportError(new Error("Error setting notification on Server2Client"));
                return;
            }

            BluetoothGattDescriptor d =
                    mCharacteristicServer2Client.getDescriptor(mClientCharacteristicConfigUuid);
            if (d == null) {
                reportError(
                        new Error("Error getting Server2Client clientCharacteristicConfig desc"));
                return;
            }

            d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!gatt.writeDescriptor(d)) {
                reportError(new Error("Error writing to Server2Client clientCharacteristicConfig "
                        + "desc"));
                return;
            }
        } catch (SecurityException e) {
            reportError(e);
        }
        // Callback happens in onDescriptorWrite() right below...
        //
    }

    @Override
    public void onDescriptorWrite(@NonNull BluetoothGatt gatt,
            @NonNull BluetoothGattDescriptor descriptor,
            int status) {
        Logger.d(TAG, "onDescriptorWrite: " + descriptor.getUuid() + " char="
                    + descriptor.getCharacteristic().getUuid() + " status="
                    + status);
        try {
            UUID charUuid = descriptor.getCharacteristic().getUuid();
            if (charUuid.equals(mCharacteristicServer2ClientUuid)
                    && descriptor.getUuid().equals(mClientCharacteristicConfigUuid)) {

                if (!gatt.setCharacteristicNotification(mCharacteristicState, true)) {
                    reportError(new Error("Error setting notification on State"));
                    return;
                }
                BluetoothGattDescriptor d =
                        mCharacteristicState.getDescriptor(mClientCharacteristicConfigUuid);
                if (d == null) {
                    reportError(new Error("Error getting State clientCharacteristicConfig desc"));
                    return;
                }
                d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (!gatt.writeDescriptor(d)) {
                    reportError(
                            new Error("Error writing to State clientCharacteristicConfig desc"));
                }

                // Continued in this callback right below...
                //

            } else if (charUuid.equals(mCharacteristicStateUuid)
                    && descriptor.getUuid().equals(mClientCharacteristicConfigUuid)) {

                // Finally we've set everything up, we can write 0x01 to state to signal
                // to the other end (mDL reader) that it can start sending data to us..

                mCharacteristicState.setValue(new byte[]{(byte) 0x01});
                if (!mGatt.writeCharacteristic(mCharacteristicState)) {
                    reportError(new Error("Error writing to state characteristic"));
                }

            } else {
                reportError(
                        new Error("Unexpected onDescriptorWrite for characteristic UUID " + charUuid
                                + " and descriptor UUID " + descriptor.getUuid()));
            }
        } catch (SecurityException e) {
            reportError(e);
        }
    }

    @Override
    public void onCharacteristicWrite(@NonNull BluetoothGatt gatt,
            @NonNull BluetoothGattCharacteristic characteristic,
            int status) {
        UUID charUuid = characteristic.getUuid();

        Logger.d(TAG, "onCharacteristicWrite " + status + " " + charUuid);

        if (charUuid.equals(mCharacteristicStateUuid)) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                reportError(new Error("Unexpected status for writing to State, status=" + status));
                return;
            }

            reportPeerConnected();

        } else if (charUuid.equals(mCharacteristicClient2ServerUuid)) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                reportError(new Error(
                        "Unexpected status for writing to Client2Server, status=" + status));
                return;
            }

            if (writingQueueTotalChunks > 0) {
                if (mWritingQueue.size() == 0) {
                    reportMessageSendProgress(writingQueueTotalChunks, writingQueueTotalChunks);
                    writingQueueTotalChunks = 0;
                } else {
                    reportMessageSendProgress(writingQueueTotalChunks-mWritingQueue.size(), writingQueueTotalChunks);
                }
            }


            writeIsOutstanding = false;
            drainWritingQueue();
        }
    }

    @Override
    public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
            @NonNull BluetoothGattCharacteristic characteristic) {
        Logger.d(TAG, "in onCharacteristicChanged, uuid=" + characteristic.getUuid());
        if (characteristic.getUuid().equals(mCharacteristicServer2ClientUuid)) {
            byte[] data = characteristic.getValue();

            if (data.length < 1) {
                reportError(new Error("Invalid data length " + data.length + " for Server2Client "
                        + "characteristic"));
                return;
            }
            mIncomingMessage.write(data, 1, data.length - 1);
            Logger.d(TAG, String.format(Locale.US,
                    "Received chunk with %d bytes (last=%s), incomingMessage.length=%d",
                    data.length, data[0] == 0x00, mIncomingMessage.toByteArray().length));
            if (data[0] == 0x00) {
                // Last message.
                byte[] entireMessage = mIncomingMessage.toByteArray();
                mIncomingMessage.reset();
                reportMessageReceived(entireMessage);
            } else if (data[0] == 0x01) {
                if (data.length != mNegotiatedMtu - 3) {
                    reportError(new Error(String.format(Locale.US,
                            "Invalid size %d of data written Server2Client characteristic, "
                                    + "expected size %d",
                            data.length, mNegotiatedMtu - 3)));
                    return;
                }
            } else {
                reportError(new Error(String.format(Locale.US,
                        "Invalid first byte %d in Server2Client data chunk, expected 0 or 1",
                        data[0])));
                return;
            }
        } else if (characteristic.getUuid().equals(mCharacteristicStateUuid)) {
            byte[] data = characteristic.getValue();

            if (data.length != 1) {
                reportError(new Error("Invalid data length " + data.length + " for state "
                        + "characteristic"));
                return;
            }
            if (data[0] == 0x02) {
                reportTransportSpecificSessionTermination();
            } else {
                reportError(new Error("Invalid byte " + data[0] + " for state "
                        + "characteristic"));
            }

        }
    }

    void drainWritingQueue() {
        Logger.d(TAG, "drainWritingQueue " + writeIsOutstanding);
        if (writeIsOutstanding) {
            return;
        }
        byte[] chunk = mWritingQueue.poll();
        if (chunk == null) {
            return;
        }

        Logger.d(TAG, String.format(Locale.US,
                "Sending chunk with %d bytes (last=%s)",
                chunk.length, chunk[0] == 0x00));

        mCharacteristicClient2Server.setValue(chunk);
        try {
            if (!mGatt.writeCharacteristic(mCharacteristicClient2Server)) {
                reportError(new Error("Error writing to Client2Server characteristic"));
                return;
            }
        } catch (SecurityException e) {
            reportError(e);
            return;
        }
        writeIsOutstanding = true;
    }


    void sendMessage(@NonNull byte[] data) {
        if (Logger.isDebugEnabled()) {
            Util.dumpHex(TAG, "sendMessage", data);
        }

        // Use socket for L2CAP if applicable
        if (mL2CAPClient != null) {
            mL2CAPClient.sendMessage(data);
            return;
        }

        if (mNegotiatedMtu == 0) {
            Logger.w(TAG, "MTU not negotiated, defaulting to 23. Performance will suffer.");
            mNegotiatedMtu = 23;
        }

        // Three less the MTU but we also need room for the leading 0x00 or 0x01.
        //
        int maxChunkSize = mNegotiatedMtu - 4;

        int offset = 0;
        do {
            boolean moreChunksComing = (offset + maxChunkSize < data.length);
            int size = data.length - offset;
            if (size > maxChunkSize) {
                size = maxChunkSize;
            }

            byte[] chunk = new byte[size + 1];
            chunk[0] = moreChunksComing ? (byte) 0x01 : (byte) 0x00;
            System.arraycopy(data, offset, chunk, 1, size);

            mWritingQueue.add(chunk);

            offset += size;
        } while (offset < data.length);

        writingQueueTotalChunks = mWritingQueue.size();

        drainWritingQueue();
    }

    // When using L2CAP it doesn't support characteristics notification
    public boolean supportsTransportSpecificTerminationMessage() {
        return !mUsingL2CAP;
    }

    public void sendTransportSpecificTermination() {
        byte[] terminationCode = new byte[]{(byte) 0x02};
        mCharacteristicState.setValue(terminationCode);
        try {
            if (!mGatt.writeCharacteristic(mCharacteristicState)) {
                reportError(new Error("Error writing to state characteristic"));
            }
        } catch (SecurityException e) {
            reportError(e);
        }
    }

    void reportPeerConnected() {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onPeerConnected();
        }
    }

    void reportPeerDisconnected() {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onPeerDisconnected();
        }
    }

    void reportMessageReceived(@NonNull byte[] data) {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onMessageReceived(data);
        }
    }

    void reportMessageSendProgress(long progress, long max) {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onMessageSendProgress(progress, max);
        }
    }

    void reportTransportSpecificSessionTermination() {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onTransportSpecificSessionTermination();
        }
    }

    void reportError(@NonNull Throwable error) {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onError(error);
        }
    }

    interface Listener {
        void onPeerConnected();

        void onPeerDisconnected();

        void onMessageReceived(@NonNull byte[] data);

        void onTransportSpecificSessionTermination();

        void onError(@NonNull Throwable error);

        void onMessageSendProgress(long progress, long max);
    }
}
