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

import android.bluetooth.BluetoothDevice;
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
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

class L2CAPClient {
    private static final String TAG = "L2CAPClient";

    Listener mListener;
    final Util.Logger mLog;

    private BluetoothSocket mSocket;
    private BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    private BlockingQueue<byte[]> mMessageReceivedQueue = new LinkedTransferQueue<>();
    Thread mWritingThread;
    Thread mReportReceivedMessageThread;

    private boolean mInhibitCallbacks = false;

    L2CAPClient(@Nullable Listener listener, @LoggingFlag int loggingFlags) {
        mListener = listener;
        mLog = new Util.Logger(TAG, loggingFlags);
    }

    void disconnect() {
        mInhibitCallbacks = true;
        if (mWritingThread != null) {
            mWriterQueue.add(new byte[0]);
            try {
                mWritingThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Caught exception while joining writing thread: " + e);
            }
        }
        if (mReportReceivedMessageThread != null) {
            mMessageReceivedQueue.add(new byte[0]);
            try {
                mReportReceivedMessageThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Caught exception while joining report message received thread: " + e);
            }
        }
        try {
            if (mSocket != null) {
                mSocket.close();
                mSocket = null;
            }
        } catch (IOException e) {
            // Ignoring this error
            Log.e(TAG, " Error closing socket connection " + e.getMessage(), e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void connect(@NonNull BluetoothDevice bluetoothDevice, byte[] psmValue) {
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
        mLog.transport("Received psmValue: " + Util.toHex(psmValue) + " psm: " + psm);

        try {
            mSocket = bluetoothDevice.createInsecureL2capChannel(psm);
            mSocket.connect();
            if (isConnected()) {
                mLog.transport("Connected using L2CAP on PSM: " + psm);
                mWritingThread = new Thread(this::writeToSocket);
                mWritingThread.start();

                Thread readingThread = new Thread(this::readFromSocket);
                readingThread.start();

                mReportReceivedMessageThread = new Thread(this::reportMessageReceived);

                reportPeerConnected();
            } else {
                reportError(new Error("Unable to connect L2CAP socket"));
            }
        } catch (IOException e) {
            Log.e(TAG, "Error connecting to socket L2CAP " + e.getMessage(), e);
            reportError(new Error("Error connecting to socket L2CAP", e));
        }

    }

    private void writeToSocket() {
        OutputStream os;

        try {
            os = mSocket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error writing message on L2CAP socket", e);
            reportError(new Error("Error writing message on L2CAP socket", e));
            return;
        }

        while (isConnected()) {
            byte[] messageToSend;
            try {
                messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                if (messageToSend == null) {
                    continue;
                }
            } catch (InterruptedException e) {
                continue;
            }

            Log.d(TAG, "Writing (" + messageToSend.length + " bytes) on L2CAP socket");

            try {
                os.write(messageToSend);
                os.flush();

            } catch (IOException e) {
                Log.e(TAG, "Error writing message on L2CAP socket", e);
                reportError(new Error("Error writing message on L2CAP socket", e));
                return;
            }
        }
    }

    private void readFromSocket() {
        mLog.transport("Start reading socket input");
        // Use the max size as buffer for L2CAP socket
        byte[] mmBuffer = new byte[mSocket.getMaxReceivePacketSize()];
        ByteArrayOutputStream incomingMessage = new ByteArrayOutputStream();
        // Keep listening to the InputStream until an exception occurs.
        while (isConnected()) {
            try {
                // Read from the InputStream.
                int numBytes = mSocket.getInputStream().read(mmBuffer);
                // Returns -1 once end of the stream has been reached.
                if (numBytes == -1) {
                    mLog.transport("End of stream reading from socket");
                    reportPeerDisconnected();
                    break;
                }
                if (mLog.isTransportVerboseEnabled()) {
                    Util.dumpHex(TAG, "Chunk received by socket: (" + numBytes + ")", Arrays.copyOf(mmBuffer, numBytes));
                }
                // Report message received.
                incomingMessage.write(mmBuffer, 0, numBytes);
                // Add cbor item to the queue that will be reported as message received
                byte[] data = Util.splitCborItemsIntoQueue(incomingMessage.toByteArray(), mMessageReceivedQueue);
                // keep any remaining data to combine with the next chunk
                incomingMessage.reset();
                incomingMessage.write(data);
            } catch (IOException e) {
                reportError(new Error("Error on listening input stream from socket L2CAP", e));
                break;
            }
        }
    }

    void sendMessage(@NonNull byte[] data) {
        mWriterQueue.add(data);
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

    void reportMessageReceived() {
        while (mListener != null && !mInhibitCallbacks) {
            byte[] messageReceived;
            try {
                messageReceived = mMessageReceivedQueue.poll(1000, TimeUnit.MILLISECONDS);
                if (messageReceived == null) {
                    continue;
                }
                if (messageReceived.length == 0) {
                    mLog.transportVerbose("Empty message, shutting down message received");
                    break;
                }
            } catch (InterruptedException e) {
                continue;
            }
            mLog.transport("Data size from message received: (" + messageReceived.length + " bytes)");
            mListener.onMessageReceived(messageReceived);
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