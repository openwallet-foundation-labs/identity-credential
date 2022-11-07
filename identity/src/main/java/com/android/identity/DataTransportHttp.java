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
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.Volley;

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
 * TODO: Maybe make it possible for the application to check the TLS root certificate via
 *   DataTransportOptions
 */
public class DataTransportHttp extends DataTransport {
    private static final String TAG = "DataTransportHttp";
    // The maximum message size we support.
    private static final int MAX_MESSAGE_SIZE = 16 * 1024 * 1024;
    private ConnectionMethodHttp mConnectionMethod;
    Socket mSocket;
    BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    ServerSocket mServerSocket = null;
    Thread mSocketWriterThread;
    private String mHost;
    private int mPort;
    private String mPath;
    private boolean mUseTls;

    public DataTransportHttp(@NonNull Context context,
                             @Role int role,
                             @NonNull ConnectionMethodHttp connectionMethod,
                             @NonNull DataTransportOptions options) {
        super(context, role, options);
        mConnectionMethod = connectionMethod;
    }

    @SuppressWarnings("deprecation")
    static private String getWifiIpAddress(Context context) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        return Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
    }

    @Override
    void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes) {
        // Not used.
    }

    // Returns the SessionEstablishment/SessionData CBOR received
    //
    // On end-of-stream returns the empty array
    //
    // Returns null on error.
    byte[] readMessageFromSocket(InputStream inputStream) {
        DataInputStream dis = new DataInputStream(inputStream);
        int contentLength = -1;
        try {
            // Loop to read headers.
            while (true) {
                String line = dis.readLine();
                if (line == null) {
                    // End of stream...
                    return new byte[0];
                }
                if (line.toLowerCase(Locale.US).startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (NumberFormatException e) {
                        Logger.w(TAG, "Error parsing Content-Length line '" + line + "'");
                        return null;
                    }
                }
                if (line.length() == 0) {
                    // End of headers...
                    if (contentLength == -1) {
                        Logger.w(TAG, "No Content-Length header");
                        return null;
                    }
                    if (contentLength > MAX_MESSAGE_SIZE) {
                        Logger.w(TAG, "Content-Length " + contentLength + " rejected "
                                + "exceeds max size of " + MAX_MESSAGE_SIZE);
                        return null;
                    }
                    byte[] data = new byte[contentLength];
                    dis.readFully(data);
                    return data;
                }
            }
        } catch (IOException e) {
            Logger.w(TAG, "Caught exception while reading", e);
            return null;
        }
    }

    private void connectAsMdocReader() {
        try {
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

                    reportConnected();

                    InputStream inputStream = mSocket.getInputStream();
                    while (!mSocket.isClosed()) {
                        byte[] data = readMessageFromSocket(inputStream);
                        if (data == null) {
                            reportError(new Error("Error reading message from socket"));
                            break;
                        } else if (data.length == 0) {
                            // End Of Stream
                            reportDisconnected();
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
        // Use http://<ip>:<port>/mdocreader as the URI
        //
        mHost = getWifiIpAddress(mContext);;
        mPort = port;
        Logger.d(TAG, String.format(Locale.US,
                "Listening with host=%s port=%d useTls=%s", mHost, mPort, mUseTls));
        mConnectionMethod = new ConnectionMethodHttp(
                String.format(Locale.US, "http://%s:%d/mdocreader", mHost, mPort));
        reportConnectionMethodReady();
    }

    public @NonNull String getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    public @NonNull String getPath() {
        return mPath;
    }

    public boolean getUseTls() {
        return mUseTls;
    }

    public void setHost(@NonNull String host) {
        mHost = host;
    }

    public void setPort(int port) {
        mPort = port;
    }

    public void setPath(@NonNull String path) {
        mPath = path;
    }

    public void setUseTls(boolean useTls) {
        mUseTls = useTls;
    }

    private RequestQueue mRequestQueue;

    private void connectAsMdoc() {
        Logger.d(TAG, String.format(Locale.US,
                "Connecting to uri=%s (host=%s port=%d useTls=%s)",
                mConnectionMethod.getUri(),
                mHost, mPort, mUseTls));

        reportConnectionMethodReady();

        mRequestQueue = Volley.newRequestQueue(mContext);

        // We're not really connected yet but if it doesn't work, we'll fail later...
        reportConnected();
    }

    private void sendMessageAsMdoc(@NonNull byte[] data) {
        if (mRequestQueue == null) {
            Logger.w(TAG, "Not sending message since the connection is closed.");
            return;
        }

        if (Logger.isDebugEnabled()) {
            Logger.d(TAG, String.format(Locale.US, "HTTP POST to %s with payload of length %d",
                    mConnectionMethod.getUri(), data.length));
        }
        CborRequest request = new CborRequest(Request.Method.POST,
                mConnectionMethod.getUri(),
                data,
                "application/CBOR",
                new Response.Listener<byte[]>() {
                    @Override
                    public void onResponse(byte[] response) {
                        if (Logger.isDebugEnabled()) {
                            Logger.d(TAG, "Received response to HTTP request payload of length " + response.length);
                        }
                        reportMessageReceived(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Logger.d(TAG, "Received error in response to HTTP request", error);
                        error.printStackTrace();
                        reportError(new Error("Error sending HTTP request", error));
                    }
                }
        );
        // We use long-polling because the duration between delivering a HTTP Request (containing
        // DeviceResponse) and receiving the response (containing DeviceRequest) may be spent
        // by the verifier configuring the mdoc reader which request to make next... set this
        // to two minutes and no retries.
        request.setRetryPolicy(new DefaultRetryPolicy(
                2*60*1000,
                0,
                1.0f));
        mRequestQueue.add(request);
    }

    @Override
    void connect() {
        if (mRole == ROLE_MDOC) {
            connectAsMdoc();
        } else {
            connectAsMdocReader();
        }
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
                            Logger.d(TAG, "Empty message, shutting down writer");
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
                        reportMessageProgress(messageToSend.length, messageToSend.length);

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
                Logger.e(TAG, "Caught exception while joining writing thread: " + e);
            }
        }
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                Logger.e(TAG, "Caught exception while shutting down: " + e);
            }
        }
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                Logger.e(TAG, "Caught exception while shutting down: " + e);
            }
        }
        if (mRequestQueue != null) {
            mRequestQueue.cancelAll(request -> true);
            mRequestQueue = null;
        }
    }

    @Override
    void sendMessage(@NonNull byte[] data) {
        if (mRole == ROLE_MDOC) {
            sendMessageAsMdoc(data);
        } else {
            mWriterQueue.add(data);
        }
    }

    @Override
    void sendTransportSpecificTerminationMessage() {
        reportError(new Error("Transport-specific termination message not supported"));
    }

    @Override
    boolean supportsTransportSpecificTerminationMessage() {
        return false;
    }

    @Override
    public @NonNull ConnectionMethod getConnectionMethod() {
        return mConnectionMethod;
    }

    static class CborRequest extends Request<byte[]> {
        private static final String TAG = "BlobRequest";
        private final Response.Listener<byte[]> mListener;
        private final byte[] mBody;
        private final String mBodyContentType;

        /**
         * Creates a new request with the given method.
         *
         * @param method        the request {@link Method} to use
         * @param url           URL to fetch the string at
         * @param body          the data to POST
         * @param listener      Listener to receive the String response
         * @param errorListener Error mListener, or null to ignore errors
         */
        public CborRequest(
                int method,
                @NonNull String url,
                @Nullable byte[] body,
                @Nullable String bodyContentType,
                @NonNull Response.Listener<byte[]> listener,
                @Nullable Response.ErrorListener errorListener) {
            super(method, url, errorListener);
            mListener = listener;
            mBody = body;
            mBodyContentType = bodyContentType;
        }

        @Override
        protected void deliverResponse(byte[] response) {
            mListener.onResponse(response);
        }

        @Override
        protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
            Log.d(TAG, "response.data");
            return Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response));
        }

        @Override
        public byte[] getBody() {
            return mBody;
        }

        @Override
        public String getBodyContentType() {
            return mBodyContentType;
        }

    }
}
