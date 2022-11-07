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
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

class L2CAPServer {
    private static final String TAG = "L2CAPServer";

    Listener mListener;
    boolean mInhibitCallbacks = false;
    private BluetoothServerSocket mServerSocket;
    private BluetoothSocket mSocket;
    private BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    Thread mWritingThread;

    L2CAPServer(@Nullable L2CAPServer.Listener listener) {
        mListener = listener;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    byte[] start(@NonNull BluetoothAdapter bluetoothAdapter) {
        try {
            // Using insecure L2CAP allows the app to use L2CAP frictionless, otherwise
            // Android will require bluetooth pairing with other device showing the pairing code
            mServerSocket = bluetoothAdapter.listenUsingInsecureL2capChannel();

            // TODO: it's not clear this is the right way to encode the PSM and 18013-5 doesn't
            //   seem to give enough guidance on it.
            int psm = mServerSocket.getPsm();
            byte[] psmValue = ByteBuffer.allocate(4).putInt(psm).array();
            Logger.d(TAG, "PSM Value generated: " + psm + " byte: " + Util.toHex(psmValue));

            Thread waitingForConnectionThread = new Thread(this::waitForConnectionThread);
            waitingForConnectionThread.start();

            return psmValue;
        } catch (IOException e) {
            // It is unable to listen L2CAP channel
            Log.w(TAG, "Error creating L2CAP channel " + e.getMessage());
            mServerSocket = null;
            return null;
        }
    }

    void stop() {
        mInhibitCallbacks = true;
        if (mWritingThread != null) {
            mWriterQueue.add(new byte[0]);
            try {
                mWritingThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Caught exception while joining writing thread: " + e);
            }
        }
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
            if (mSocket != null) {
                mSocket.close();
                mSocket = null;
            }
        } catch (IOException e) {
            // Ignoring this error
            Log.e(TAG, " Error closing socket connection " + e.getMessage(), e);
        }
    }

    private void waitForConnectionThread() {
        try {
            Logger.d(TAG, "Calling accept() to wait for connection");
            mSocket = mServerSocket.accept();
            Logger.d(TAG, "Accepted a connection");

            // Stop accepting new connections
            mServerSocket.close();
            mServerSocket = null;
        } catch (IOException e) {
            Log.e(TAG, "Error getting connection from socket server for L2CAP " + e.getMessage(), e);
            reportError(new Error("Error getting connection from socket server for L2CAP", e));
        }

        // Let the app know we have a connection
        reportPeerConnected();

        // Start writing thread
        mWritingThread = new Thread(this::writeToSocket);
        mWritingThread.start();

        // Reuse this thread for reading
        readFromSocket();
    }

    private void writeToSocket() {
        while (true) {
            byte[] messageToSend;
            try {
                messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                if (messageToSend == null) {
                    continue;
                }
                if (messageToSend.length == 0) {
                    Logger.d(TAG, "Empty message, exiting writer thread");
                    return;
                }
            } catch (InterruptedException e) {
                continue;
            }

            try {
                OutputStream os = mSocket.getOutputStream();
                os.write(messageToSend);
                os.flush();
                reportMessageSendProgress(messageToSend.length, messageToSend.length);
                Logger.d(TAG, String.format(Locale.US, "Wrote CBOR data item of size %d bytes",
                        messageToSend.length));
            } catch (IOException e) {
                Log.e(TAG, "Error writing message on L2CAP socket", e);
                reportError(new Error("Error writing message on L2CAP socket", e));
                return;
            }
            // TODO: If we don't do this we run into problems when sending a session termination
            //   message and then immediately closing the connection, as is usual.
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error sleeping after writing", e);
            }
        }
    }

    private void readFromSocket() {
        Logger.d(TAG, "Start reading socket input");
        ByteArrayOutputStream pendingDataBaos = new ByteArrayOutputStream();

        // Keep listening to the InputStream until an exception occurs.
        InputStream inputStream = null;
        try {
            inputStream = mSocket.getInputStream();
        } catch (IOException e) {
            reportError(new Error("Error on listening input stream from socket L2CAP", e));
            return;
        }
        while (true) {
            byte[] buf = new byte[DataTransportBle.L2CAP_BUF_SIZE];
            try {
                int numBytesRead = inputStream.read(buf);
                if (numBytesRead == -1) {
                    Logger.d(TAG, "End of stream reading from socket");
                    reportPeerDisconnected();
                    break;
                }
                pendingDataBaos.write(buf, 0, numBytesRead);

                byte[] dataItemBytes = Util.cborExtractFirstDataItem(pendingDataBaos);
                if (dataItemBytes == null) {
                    continue;
                }
                Logger.d(TAG, String.format(Locale.US, "Received CBOR data item of size %d bytes",
                        dataItemBytes.length));
                reportMessageReceived(dataItemBytes);
            } catch (IOException e) {
                reportError(new Error("Error on listening input stream from socket L2CAP", e));
                break;
            }
        }
    }

    void sendMessage(@NonNull byte[] data) {
        reportMessageSendProgress(0, data.length);
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

    void reportMessageSendProgress(long progress, long max) {
        if (mListener != null && !mInhibitCallbacks) {
            mListener.onMessageSendProgress(progress, max);
        }
    }

    interface Listener {
        void onPeerConnected();

        void onPeerDisconnected();

        void onMessageReceived(@NonNull byte[] data);

        void onError(@NonNull Throwable error);

        void onMessageSendProgress(long progress, long max);
    }
}