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

package com.google.sps.servlets;

import static java.nio.charset.StandardCharsets.UTF_8;

//import android.content.Context;
//import android.net.wifi.WifiManager;
//import android.text.format.Formatter;

//import androidx.annotation.NonNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

/**
 * HTTP data transport.
 *
 * TODO: port connection-part (HTTP Client) to use volley or okhttp
 * TODO: mark listening-part (HTTP Server) as dangerous to use
 * TODO: support HTTPS
 */
public class DataTransportHttp extends DataTransport {
    private static final String TAG = "DataTransportHttp";
    // The maximum message size we support.
    private static final int MAX_MESSAGE_SIZE = 16 * 1024 * 1024;
    Socket mSocket;
    BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    ServerSocket mServerSocket = null;
    Thread mSocketWriterThread;
    private String mHost;
    private int mPort;
    private String mPath;
    private boolean mUseTls;

    public DataTransportHttp(DataTransportOptions options) {
        super(options);
    }

    /*
    @SuppressWarnings("deprecation")
    static private String getWifiIpAddress(Context context) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        return Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
    }
    */

    @Override
    void setEDeviceKeyBytes(byte[] encodedEDeviceKeyBytes) {
        // Not used.
    }

    // Returns the SessionEstablishment/SessionData CBOR received
    //
    // On end-of-stream returns the empty array
    //
    // Returns null on error.
    byte[] readMessageFromSocket(InputStream inputStream) {
        //Logger.d(TAG, "Reading HTTP message...");
        DataInputStream dis = new DataInputStream(inputStream);
        int contentLength = -1;
        try {
            // Loop to read headers.
            while (true) {
                //Logger.d(TAG, "Calling readLine()...");
                String line = dis.readLine();
                if (line == null) {
                    // End of stream...
                    return new byte[0];
                }
                //Logger.d(TAG, "read line '" + line + "'");
                if (line.toLowerCase(Locale.US).startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (NumberFormatException e) {
                        //Logger.w(TAG, "Error parsing Content-Length line '" + line + "'");
                        return null;
                    }
                }
                if (line.length() == 0) {
                    // End of headers...
                    if (contentLength == -1) {
                        //Logger.w(TAG, "No Content-Length header");
                        return null;
                    }
                    if (contentLength > MAX_MESSAGE_SIZE) {
                        //Logger.w(TAG, "Content-Length " + contentLength + " rejected "
                        //        + "exceeds max size of " + MAX_MESSAGE_SIZE);
                        return null;
                    }
                    //Logger.d(TAG, "Going to read " + contentLength + " bytes");
                    byte[] data = new byte[contentLength];
                    dis.readFully(data);
                    return data;
                }
            }
        } catch (IOException e) {
            //Logger.w(TAG, "Caught exception while reading", e);
            return null;
        }
    }


    @Override
    void listen() {
        //String address = getWifiIpAddress(mContext);
        String address = "";
        try {
            /*
            SSLContext context = SSLContext.getDefault();
            SSLServerSocketFactory factory = context.getServerSocketFactory();
            mServerSocket = factory.createServerSocket(0);
             */
            mServerSocket = new ServerSocket(0);
        } catch (IOException e) {
            reportError(e);
            return;
        }
        int port = mServerSocket.getLocalPort();
        Thread socketServerThread = new Thread() {
            @Override
            public void run() {
                try {
                    // We only accept a single client with this server socket...
                    //
                    mSocket = mServerSocket.accept();
                    mServerSocket = null;

                    setupWritingThread(true);

                    reportListeningPeerConnecting();
                    reportListeningPeerConnected();

                    InputStream inputStream = mSocket.getInputStream();
                    while (!mSocket.isClosed()) {
                        byte[] data = readMessageFromSocket(inputStream);
                        if (data == null) {
                            reportError(new Error("Error reading message from socket"));
                            break;
                        } else if (data.length == 0) {
                            // End Of Stream
                            reportListeningPeerDisconnected();
                            break;
                        }
                        reportMessageReceived(data);
                    }

                } catch (Exception e) {
                    reportError(new Error("Error reading from socket", e));
                }
            }
        };
        socketServerThread.start();
        mHost = address;
        mPort = port;
        reportListeningSetupCompleted();
    }

    public String getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    public String getPath() {
        return mPath;
    }

    public boolean getUseTls() {
        return mUseTls;
    }

    public void setHost(String host) {
        mHost = host;
    }

    public void setPort(int port) {
        mPort = port;
    }

    public void setPath(String path) {
        mPath = path;
    }

    public void setUseTls(boolean useTls) {
        mUseTls = useTls;
    }

    @Override
    void connect() {
        Thread socketReaderThread = new Thread() {
            @Override
            public void run() {
                InputStream inputStream = null;
                try {
                    /*
                    SSLContext context = SSLContext.getDefault();
                    SSLSocketFactory factory = context.getSocketFactory();
                    SSLSocket socket = (SSLSocket) factory.createSocket(mHost, mPort);
                    mSocket = socket;
                    socket.startHandshake();
                     */
                    mSocket = new Socket(mHost, mPort);
                    inputStream = mSocket.getInputStream();
                } catch (IOException e) {
                    reportConnectionResult(e);
                    return;
                }

                reportConnectionResult(null);

                setupWritingThread(false);

                while (!mSocket.isClosed()) {
                    byte[] data = readMessageFromSocket(inputStream);
                    if (data == null) {
                        reportError(new Error("Error reading message from socket"));
                        break;
                    } else if (data.length == 0) {
                        // End Of Stream
                        reportConnectionDisconnected();
                        break;
                    }
                    reportMessageReceived(data);
                }
            }
        };
        socketReaderThread.start();
    }

    void setupWritingThread(boolean isListener) {
        mSocketWriterThread = new Thread() {
            @Override
            public void run() {
                while (mSocket.isConnected()) {
                    byte[] messageToSend = null;
                    try {
                        messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                        if (messageToSend == null) {
                            continue;
                        }
                        // An empty message is used to convey that the writing thread should be
                        // shut down.
                        if (messageToSend.length == 0) {
                            //Logger.d(TAG, "Empty message, shutting down writer");
                            break;
                        }
                    } catch (InterruptedException e) {
                        continue;
                    }

                    try {

                        OutputStream os = mSocket.getOutputStream();
                        if (isListener) {
                            os.write(("HTTP/1.1 200 OK\r\n"
                                    + "Content-Length: " + messageToSend.length + "\r\n"
                                    + "Content-Type: application/CBOR\r\n"
                                    + "\r\n").getBytes(UTF_8));
                        } else {
                            os.write(("POST " + mPath + " HTTP/1.1\r\n"
                                    + "Host: " + mHost + "\r\n"
                                    + "Content-Length: " + messageToSend.length + "\r\n"
                                    + "Content-Type: application/CBOR\r\n"
                                    + "\r\n").getBytes(UTF_8));
                        }
                        os.write(messageToSend);
                        //reportMessageProgress(messageToSend.length, messageToSend.length);

                    } catch (IOException e) {
                        reportError(e);
                        break;
                    }
                }
            }
        };
        mSocketWriterThread.start();
    }

    @Override
    void close() {
        inhibitCallbacks();
        if (mSocketWriterThread != null) {
            mWriterQueue.add(new byte[0]);
            try {
                mSocketWriterThread.join();
            } catch (InterruptedException e) {
                //Logger.e(TAG, "Caught exception while joining writing thread: " + e);
            }
        }
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                //Logger.e(TAG, "Caught exception while shutting down: " + e);
            }
        }
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                //Logger.e(TAG, "Caught exception while shutting down: " + e);
            }
        }
    }

    @Override
    void sendMessage(byte[] data) {
        mWriterQueue.add(data);
    }

    @Override
    void sendTransportSpecificTerminationMessage() {
        reportError(new Error("Transport-specific termination message not supported"));
    }

    @Override
    boolean supportsTransportSpecificTerminationMessage() {
        return false;
    }
}