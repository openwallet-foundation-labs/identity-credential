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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.security.identity.Constants.LoggingFlag;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.UUID;

class GattClient extends BluetoothGattCallback {
    private static final String TAG = "GattClient";
    
    private final Context mContext;
    private final UUID mServiceUuid;
    private final byte[] mEncodedEDeviceKeyBytes;
    Listener mListener;
    private boolean mInhibitCallbacks = false;

    BluetoothGatt mGatt;

    BluetoothGattCharacteristic mCharacteristicState;
    BluetoothGattCharacteristic mCharacteristicClient2Server;
    BluetoothGattCharacteristic mCharacteristicServer2Client;
    BluetoothGattCharacteristic mCharacteristicIdent;

    UUID mCharacteristicStateUuid;
    UUID mCharacteristicClient2ServerUuid;
    UUID mCharacteristicServer2ClientUuid;
    UUID mCharacteristicIdentUuid;

    // This is what the 16-bit UUID 0x29 0x02 is encoded like.
    UUID mClientCharacteristicConfigUuid =  UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private int mNegotiatedMtu;
    private byte[] mIdentValue;

    private final @LoggingFlag int mLoggingFlags;


    GattClient(@NonNull Context context, @LoggingFlag int loggingFlags, @NonNull UUID serviceUuid,
        @NonNull byte[] encodedEDeviceKeyBytes,
        @NonNull UUID characteristicStateUuid,
        @NonNull UUID characteristicClient2ServerUuid,
        @NonNull UUID characteristicServer2ClientUuid,
        @Nullable UUID characteristicIdentUuid) {
        mContext = context;
        mLoggingFlags = loggingFlags;
        mServiceUuid = serviceUuid;
        mEncodedEDeviceKeyBytes = encodedEDeviceKeyBytes;
        mCharacteristicStateUuid = characteristicStateUuid;
        mCharacteristicClient2ServerUuid = characteristicClient2ServerUuid;
        mCharacteristicServer2ClientUuid = characteristicServer2ClientUuid;
        mCharacteristicIdentUuid = characteristicIdentUuid;
    }

    void setListener(@Nullable Listener listener) {
        mListener = listener;
    }
    
    void connect(BluetoothDevice device) {
        byte[] ikm = mEncodedEDeviceKeyBytes;
        byte[] info = new byte[] {'B', 'L', 'E', 'I', 'd', 'e', 'n', 't'};
        byte[] salt = new byte[] {};
        mIdentValue = Util.computeHkdf("HmacSha256", ikm, salt, info, 16);
        mGatt = device.connectGatt(mContext, false, this);
    }

    void disconnect() {
        mInhibitCallbacks = true;
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt = null;
        }
    }

    @Override
    public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
        if ((mLoggingFlags & Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC) != 0) {
            Log.i(TAG, "onConnectionStateChange: status=" + status + " newState=" + newState);
        }
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            //Log.d(TAG, "Connected");
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
            gatt.discoverServices();
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            //Log.d(TAG, "Disconnected");
            reportPeerDisconnected();
        }
    }

    @Override
    public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
        if ((mLoggingFlags & Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC) != 0) {
            Log.i(TAG, "onServicesDiscovered: status=" + status);
        }
        if (status == BluetoothGatt.GATT_SUCCESS) {
            BluetoothGattService s = gatt.getService(mServiceUuid);
            if (s != null) {
                mCharacteristicState = s.getCharacteristic(mCharacteristicStateUuid);
                if (mCharacteristicState == null) {
                    reportError(new Error("State characteristic not found"));
                    return;
                }

                mCharacteristicClient2Server = s.getCharacteristic(mCharacteristicClient2ServerUuid);
                if (mCharacteristicClient2Server == null) {
                    reportError(new Error("Client2Server characteristic not found"));
                    return;
                }

                mCharacteristicServer2Client = s.getCharacteristic(mCharacteristicServer2ClientUuid);
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
            if (!gatt.requestMtu(517)) {
                reportError(new Error("Error requesting MTU"));
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

        if ((mLoggingFlags & Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC) != 0) {
            Log.i(TAG, "Negotiated MTU " + mtu);
        }

        if (mCharacteristicIdent != null) {
            // Read ident characteristics...
            //
            // TODO: maybe skip this, it's optional after all...
            //
            if (!gatt.readCharacteristic(mCharacteristicIdent)) {
                reportError(new Error("Error reading from ident characteristic"));
            }
            // Callback happens in onDescriptorRead() right below...
            //
        } else {
            afterIdentObtained(gatt);
        }
    }

    @Override
    public void onCharacteristicRead(@NonNull BluetoothGatt gatt,
            @NonNull BluetoothGattCharacteristic characteristic,
            int status) {
        if (!characteristic.getUuid().equals(mCharacteristicIdentUuid)) {
            reportError(new Error("Unexpected onCharacteristicRead for characteristic "
                + characteristic.getUuid() + ", expected " + mCharacteristicIdentUuid));
            return;
        }
        byte[] identValue = characteristic.getValue();
        if ((mLoggingFlags & Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC) != 0) {
            Log.d(TAG, "Received identValue: " + Util.toHex(identValue));
        }
        // TODO: maybe comment out or change to warning since it's optional... several readers
        //  send the wrong value (others send the right one though)
        if (!Arrays.equals(identValue, mIdentValue)) {
            reportError(new Error("Received ident does not match expected ident"));
            return;
        }

        afterIdentObtained(gatt);
    }

    private void afterIdentObtained(@NonNull BluetoothGatt gatt) {
        if (!gatt.setCharacteristicNotification(mCharacteristicServer2Client, true)) {
            reportError(new Error("Error setting notification on Server2Client"));
            return;
        }

        BluetoothGattDescriptor d =
                mCharacteristicServer2Client.getDescriptor(mClientCharacteristicConfigUuid);
        if (d == null) {
            reportError(new Error("Error getting Server2Client clientCharacteristicConfig desc"));
            return;
        }

        d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!gatt.writeDescriptor(d)) {
            reportError(new Error("Error writing to Server2Client clientCharacteristicConfig "
                    + "desc"));
            return;
        }

        // Callback happens in onDescriptorWrite() right below...
        //
    }

    @Override
    public void onDescriptorWrite(@NonNull BluetoothGatt gatt,
            @NonNull BluetoothGattDescriptor descriptor,
            int status) {
        if ((mLoggingFlags & Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC) != 0) {
            Log.d(TAG, "onDescriptorWrite: " + descriptor.getUuid() + " char="
                + descriptor.getCharacteristic().getUuid() + " status="
                + status);
        }

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
                reportError(new Error("Error writing to State clientCharacteristicConfig desc"));
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
            reportError(new Error("Unexpected onDescriptorWrite for characteristic UUID " + charUuid
                    + " and descriptor UUID " + descriptor.getUuid()));
        }
    }

    @Override
    public void onCharacteristicWrite(@NonNull BluetoothGatt gatt,
            @NonNull BluetoothGattCharacteristic characteristic,
            int status) {
        UUID charUuid = characteristic.getUuid();

        if ((mLoggingFlags & Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC) != 0) {
            Log.i(TAG, "onCharacteristicWrite " + status + " " + charUuid);
        }

        if (charUuid.equals(mCharacteristicStateUuid)) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                reportError(new Error("Unexpected status for writing to State, status=" + status));
                return;
            }

            reportPeerConnected();

        } else if (charUuid.equals(mCharacteristicClient2ServerUuid)) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                reportError(new Error("Unexpected status for writing to Client2Server, status=" + status));
                return;
            }
            writeIsOutstanding = false;
            drainWritingQueue();
        }
    }

    ByteArrayOutputStream mIncomingMessage = new ByteArrayOutputStream();

    @Override
    public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
            @NonNull BluetoothGattCharacteristic characteristic) {
        if ((mLoggingFlags & Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC) != 0) {
            Log.i(TAG, "in onCharacteristicChanged, uuid=" + characteristic.getUuid());
        }
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

    Queue<byte[]> mWritingQueue = new ArrayDeque<>();
    boolean writeIsOutstanding = false;

    void drainWritingQueue() {
        if ((mLoggingFlags & Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC) != 0) {
            Log.i(TAG, "drainWritingQueue " + writeIsOutstanding);
        }
        if (writeIsOutstanding) {
            return;
        }
        byte[] chunk = mWritingQueue.poll();
        if (chunk == null) {
            return;
        }

        if ((mLoggingFlags & Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE) != 0) {
            Log.i(TAG, "writing chunk " + mCharacteristicClient2Server.getUuid() + " " + Util.toHex(chunk));
        }

        mCharacteristicClient2Server.setValue(chunk);
        if (!mGatt.writeCharacteristic(mCharacteristicClient2Server)) {
            reportError(new Error("Error writing to Client2Server characteristic"));
            return;
        }
        writeIsOutstanding = true;
    }


    void sendMessage(@NonNull byte[] data) {
        int mtu = mNegotiatedMtu;
        if (mNegotiatedMtu == 0) {
            Log.w(TAG, "MTU not negotiated, defaulting to 23. Performance will suffer.");
            mNegotiatedMtu = 23;
        }

        if ((mLoggingFlags & Constants.LOGGING_FLAG_TRANSPORT_SPECIFIC_VERBOSE) != 0) {
            Log.i(TAG, "sendMessage " + Util.toHex(data));
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
