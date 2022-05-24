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
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.Constants.LoggingFlag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.UUID;

@SuppressWarnings("deprecation")
class GattClient extends BluetoothGattCallback {
    private static final String TAG = "GattClient";

    private final Context mContext;
    private final UUID mServiceUuid;
    private final byte[] mEncodedEDeviceKeyBytes;
    final Util.Logger mLog;
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
    private BluetoothSocket mSocket;

    // This is what the 16-bit UUID 0x29 0x02 is encoded like.
    UUID mClientCharacteristicConfigUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    ByteArrayOutputStream mIncomingMessage = new ByteArrayOutputStream();
    Queue<byte[]> mWritingQueue = new ArrayDeque<>();
    boolean writeIsOutstanding = false;
    private boolean mInhibitCallbacks = false;
    private int mNegotiatedMtu;
    private byte[] mIdentValue;
    private boolean mUsingL2CAP = false;

    GattClient(@NonNull Context context, @LoggingFlag int loggingFlags, @NonNull UUID serviceUuid,
            @NonNull byte[] encodedEDeviceKeyBytes,
            @NonNull UUID characteristicStateUuid,
            @NonNull UUID characteristicClient2ServerUuid,
            @NonNull UUID characteristicServer2ClientUuid,
            @Nullable UUID characteristicIdentUuid,
            @Nullable UUID characteristicL2CAPUuid) {
        mContext = context;
        mLog = new Util.Logger(TAG, loggingFlags);
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

    void connect(BluetoothDevice device) {
        byte[] ikm = mEncodedEDeviceKeyBytes;
        byte[] info = new byte[]{'B', 'L', 'E', 'I', 'd', 'e', 'n', 't'};
        byte[] salt = new byte[]{};
        mIdentValue = Util.computeHkdf("HmacSha256", ikm, salt, info, 16);
        try {
            mGatt = device.connectGatt(mContext, false, this);
        } catch (SecurityException e) {
            reportError(e);
        }
    }

    void disconnect() {
        mInhibitCallbacks = true;
        try {
            if (mSocket != null) {
                mSocket.close();
                mSocket = null;
            }
        } catch (IOException e) {
            // Ignoring this error
            Log.e(TAG, " Error closing socket connection " + e.getMessage(), e);
        }
        if (mGatt != null) {
            try {
                mGatt.disconnect();
            } catch (SecurityException e) {
                Log.e(TAG, "Caught SecurityException while shutting down: " + e);
            }
            mGatt = null;
        }
    }

    @Override
    public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
        mLog.transport("onConnectionStateChange: status=" + status + " newState=" + newState);
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            //Log.d(TAG, "Connected");
            try {
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                gatt.discoverServices();
            } catch (SecurityException e) {
                reportError(e);
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            //Log.d(TAG, "Disconnected");
            reportPeerDisconnected();
        }
    }

    @Override
    public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
        mLog.transport("onServicesDiscovered: status=" + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            BluetoothGattService s = gatt.getService(mServiceUuid);
            if (s != null) {
                if (mCharacteristicL2CAPUuid != null) {
                    mCharacteristicL2CAP = s.getCharacteristic(mCharacteristicL2CAPUuid);
                    if (mCharacteristicL2CAP != null) {
                        if (mLog.isTransportEnabled()) {
                            mLog.transport("L2CAP characteristic found " + mCharacteristicL2CAPUuid);
                        }
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

            // Use L2CAP if supported by GattServer and by this OS version
            mUsingL2CAP = mCharacteristicL2CAP != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
            if (mLog.isTransportEnabled()) {
                mLog.transport("Using L2CAP: " + mUsingL2CAP);
            }
            if (mUsingL2CAP) {
                if (!gatt.readCharacteristic(mCharacteristicL2CAP)) {
                    // As it is not able to read from L2CAP characteristic set mUsingL2CAP to false
                    // and proceed using characteristics to transfer instead of socket
                    Log.w(TAG, "Not possible to read PSM from L2CAP characteristic " + mCharacteristicL2CAP);
                    mUsingL2CAP = false;
                }
            }

            // Start by bumping MTU, callback in onMtuChanged()...
            //
            // As per BT specs, maximum MTU is 517 so we request that. We might not get
            // such a big MTU, the way it works is that the requestMtu() call will trigger a
            // negotiation between the client (us) and the server (the remote device).
            //
            // We'll get notified in BluetoothGattCallback.onMtuChanged() below.
            //
            // The server will also be notified about the new MTU - if it's running Android
            // it'll be via BluetoothGattServerCallback.onMtuChanged(), see GattServer.java
            // for that in our implementation.
            //
            try {
                if (!gatt.requestMtu(517)) {
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

        mLog.transport("Negotiated MTU " + mtu);

        if (mCharacteristicIdent != null) {
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
            if (mLog.isTransportEnabled()) {
                mLog.transport("Received identValue: " + Util.toHex(identValue));
            }
            // TODO: maybe comment out or change to warning since it's optional... several readers
            //  send the wrong value (others send the right one though)
            if (!Arrays.equals(identValue, mIdentValue)) {
                reportError(new Error("Received ident does not match expected ident"));
                return;
            }

            afterIdentObtained(gatt);
        } else if (characteristic.getUuid().equals(mCharacteristicL2CAPUuid)) {
            if (!mUsingL2CAP) {
                reportError(new Error("Unexpected read for L2CAP characteristic "
                        + characteristic.getUuid() + ", L2CAP not supported"));
                return;
            }
            byte[] psmValue = characteristic.getValue();
            if (psmValue == null || psmValue.length == 0 || psmValue.length > 4) {
                reportError(new Error("Invalid PSM value received on L2CAP characteristic"));
                return;
            }
            byte[] psmSized = new byte[4];
            if (psmValue.length < 4) {
                // Add 00 on left if psm length is lower than 4
                System.arraycopy(psmValue, 0, psmSized, 4 - psmValue.length, psmValue.length);
            } else {
                psmSized = psmValue;
            }
            int psm = ByteBuffer.wrap(psmSized).getInt();
            if (mLog.isTransportEnabled()) {
                mLog.transport("Received psmValue: " + Util.toHex(psmValue) + " psm: " + psm);
            }
            Thread socketClientThread = new Thread(() -> {
                try {
                    mSocket = mGatt.getDevice().createInsecureL2capChannel(psm);
                    mSocket.connect();
                    if (mSocket.isConnected()) {
                        if (mLog.isTransportEnabled()) {
                            mLog.transport("Connected using L2CAP on PSM: " + psm);
                        }
                        reportPeerConnected();
                        listenSocket();
                    } else {
                        reportError(new Error("Unable to connect L2CAP socket"));
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error connecting to socket L2CAP " + e.getMessage(), e);
                    reportError(new Error("Error connecting to socket L2CAP", e));
                }
            });
            socketClientThread.start();
        } else {
            reportError(new Error("Unexpected onCharacteristicRead for characteristic "
                    + characteristic.getUuid() + ", expected " + mCharacteristicIdentUuid));
            return;
        }
    }

    private void listenSocket() {
        if (mLog.isTransportEnabled()) {
            mLog.transport("Start reading socket input");
        }
        // We can check better/right buffer size to use with L2CAP
        byte[] mmBuffer = new byte[1024 * 16];
        int numBytes; // bytes returned from read()
        // Keep listening to the InputStream until an exception occurs.
        while (mSocket.isConnected()) {
            try {
                // Read from the InputStream.
                numBytes = mSocket.getInputStream().read(mmBuffer);
                if (numBytes == -1) {
                    if (mLog.isTransportEnabled()) {
                        mLog.transport("Stop listening from socket");
                    }
                    break;
                }
                if (mLog.isTransportEnabled()) {
                    mLog.transport("Chunk received by socket: (" + numBytes + ") " + Util.toHex(mmBuffer));
                }
                // Report message received.
                mIncomingMessage.write(mmBuffer, 0, numBytes);
                if (numBytes < mmBuffer.length) {
                    // Last chunk received.
                    byte[] entireMessage = mIncomingMessage.toByteArray();
                    if (mLog.isTransportEnabled()) {
                        mLog.transport("Entire message received by socket: (" + entireMessage.length + ") " + Util.toHex(entireMessage));
                    }
                    mIncomingMessage.reset();
                    reportMessageReceived(entireMessage);
                }
            } catch (IOException e) {
                reportError(new Error("Error on listening input stream from socket L2CAP", e));
                break;
            }
        }
    }

    private void afterIdentObtained(@NonNull BluetoothGatt gatt) {
        try {
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
        mLog.transport("onDescriptorWrite: " + descriptor.getUuid() + " char="
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

        mLog.transport("onCharacteristicWrite " + status + " " + charUuid);

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
            writeIsOutstanding = false;
            drainWritingQueue();
        }
    }

    @Override
    public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
            @NonNull BluetoothGattCharacteristic characteristic) {
        mLog.transport("in onCharacteristicChanged, uuid=" + characteristic.getUuid());
        if (characteristic.getUuid().equals(mCharacteristicServer2ClientUuid)) {
            byte[] data = characteristic.getValue();

            if (data.length < 1) {
                reportError(new Error("Invalid data length " + data.length + " for Server2Client "
                        + "characteristic"));
                return;
            }
            mIncomingMessage.write(data, 1, data.length - 1);
            if (data[0] == 0x00) {
                // Last message.
                byte[] entireMessage = mIncomingMessage.toByteArray();
                mIncomingMessage.reset();
                reportMessageReceived(entireMessage);
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
        mLog.transport("drainWritingQueue " + writeIsOutstanding);
        if (writeIsOutstanding) {
            return;
        }
        byte[] chunk = mWritingQueue.poll();
        if (chunk == null) {
            return;
        }

        if (mLog.isTransportVerboseEnabled()) {
            mLog.transportVerbose("writing chunk " + mCharacteristicClient2Server.getUuid()
                    + " " + Util.toHex(chunk));
        }

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
        if (mLog.isTransportVerboseEnabled()) {
            mLog.transportVerbose("sendMessage " + Util.toHex(data));
        }

        // Use socket for L2CAP if it is connected
        if (mSocket != null && mSocket.isConnected()) {
            try {
                final OutputStream os = mSocket.getOutputStream();
                os.write(data);
                os.flush();
            } catch (IOException e) {
                Log.e(TAG, "Error sending message by L2CAP socket " + e.getMessage(), e);
                reportError(new Error("Error sending message by L2CAP socket", e));
            }
            return;
        }

        if (mNegotiatedMtu == 0) {
            Log.w(TAG, "MTU not negotiated, defaulting to 23. Performance will suffer.");
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
    }


}
