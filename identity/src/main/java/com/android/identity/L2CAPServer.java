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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.identity.Constants.LoggingFlag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

class L2CAPServer {
    private static final String TAG = "L2CAPServer";
    final Util.Logger mLog;

    Listener mListener;
    boolean mInhibitCallbacks = false;
    private BluetoothServerSocket mServerSocket;
    ByteArrayOutputStream mIncomingMessage = new ByteArrayOutputStream();
    private BluetoothSocket mSocket;

    L2CAPServer(@Nullable L2CAPServer.Listener listener, @LoggingFlag int loggingFlags) {
        mListener = listener;
        mLog = new Util.Logger(TAG, loggingFlags);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    boolean start(@NonNull BluetoothAdapter bluetoothAdapter) {
        try {
            mServerSocket = bluetoothAdapter.listenUsingInsecureL2capChannel();
        } catch (IOException e) {
            // It is unable to listen L2CAP channel
            Log.w(TAG, "Unable to listen L2CAP channel " + e.getMessage());
            mServerSocket = null;
            return false;
        }
        return true;
    }

    void stop() {
        mInhibitCallbacks = true;
        closeServerSocket();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public byte[] getPsmValue() {
        if (mServerSocket == null) {
            reportError(new Error("Socket for L2CAP not available"));
            return null;
        }
        int psm = mServerSocket.getPsm();
        byte[] psmValue = ByteBuffer.allocate(4).putInt(psm).array();
        if (mLog.isTransportEnabled()) {
            mLog.transport("PSM Value generated: " + psm + " byte: " + Util.toHex(psmValue));
        }
        return psmValue;
    }

    public void acceptConnection() {
        Thread socketServerThread = new Thread(() -> {
            try {
                mSocket = mServerSocket.accept();
                if (mSocket.isConnected()) {
                    // Stop accepting new connections
                    mServerSocket.close();
                    mServerSocket = null;
                    reportPeerConnected();
                    listenSocket();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error getting connection from socket server for L2CAP " + e.getMessage(), e);
                reportError(new Error("Error getting connection from socket server for L2CAP", e));
            }
        });
        socketServerThread.start();
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
                numBytes = mSocket.getInputStream().read(mmBuffer, 0, mmBuffer.length);
                // Returns -1 if there is no more data because the end of the stream has been reached.
                if (numBytes == -1) {
                    if (mLog.isTransportEnabled()) {
                        mLog.transport("End of stream reading from socket");
                    }
                    reportPeerDisconnected();
                    break;
                }
                if (mLog.isTransportEnabled()) {
                    mLog.transport("Chunk received by socket: (" + numBytes + ") " + Util.toHex(mmBuffer));
                }
                // Report message received.
                mIncomingMessage.write(mmBuffer, 0, numBytes);
                byte[] entireMessage = mIncomingMessage.toByteArray();
                long size = Util.getMessageSize(entireMessage);
                // Check last chunk received
                // - if bytes received is less than buffer
                // - or message length is equal as expected size of the message
                if (numBytes < mmBuffer.length || (size != -1 && entireMessage.length == size)) {
                    if (mLog.isTransportEnabled()) {
                        mLog.transport("Data size from message: (" + size + ") message size: (" + entireMessage.length + ")");
                        mLog.transport("Message: " + Util.toHex(entireMessage));
                    }
                    mIncomingMessage.reset();
                    reportMessageReceived(entireMessage);
                }
            } catch (IOException e) {
                reportError(new Error("Error on listening input stream from socket L2CAP"));
                break;
            }
        }
    }

    private void closeServerSocket() {
        try {
            if (mServerSocket != null) {
                mServerSocket.close();
                mServerSocket = null;
            }
        } catch (IOException e) {
            // Ignoring this error
            Log.e(TAG, " Error closing server socket connection " + e.getMessage(), e);
        }
        try {
            if (mSocket != null && mSocket.isConnected()) {
                // Stop accepting new connections
                mSocket.close();
                mSocket = null;
            }
        } catch (IOException e) {
            // Ignoring this error
            Log.e(TAG, " Error closing server socket connection " + e.getMessage(), e);
        }
    }


    void sendMessage(@NonNull byte[] data) {
        if (isConnected()) {
            if (mLog.isTransportVerboseEnabled()) {
                mLog.transportVerbose("sendMessage using L2CAP socket");
            }
            try {
                final OutputStream os = mSocket.getOutputStream();
                os.write(data);
                os.flush();
            } catch (IOException e) {
                Log.e(TAG, "Error sending message by L2CAP socket " + e.getMessage(), e);
                reportError(new Error("Error sending message by L2CAP socket"));
            }
            return;
        } else {
            Log.e(TAG, "No socket connection while trying to send a message by L2CAP");
            reportError(new Error("No socket connection while trying to send a message by L2CAP"));
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

    void reportError(@NonNull Throwable error) {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onError(error);
        }
    }

    public boolean isConnected() {
        return mSocket != null && mSocket.isConnected();
    }

    interface Listener {
        void onPeerConnected();

        void onPeerDisconnected();

        void onMessageReceived(@NonNull byte[] data);

        void onError(@NonNull Throwable error);
    }
}