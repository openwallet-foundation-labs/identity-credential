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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareNetworkInfo;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.DoNotInline;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.DataInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

/**
 * Wifi Aware data transport.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DataTransportWifiAware extends DataTransport {
    private static final String TAG = "DataTransportWifiAware";
    private final ConnectionMethodWifiAware mConnectionMethod;

    BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    String mServiceName;
    WifiAwareSession mSession;
    ServerSocket mListenerServerSocket;
    Socket mInitiatorSocket;
    Socket mListenerSocket;
    private byte[] mEncodedEDeviceKeyBytes;
    private WifiAwareManager mWifiAwareManager;
    private String mInitiatorIPv6HostString;
    private String mPassphrase;
    @SuppressWarnings("unused")
    private int mCipherSuites;

    public DataTransportWifiAware(@NonNull Context context,
                                  @Role int role,
                                  @NonNull ConnectionMethodWifiAware connectionMethod,
                                  @NonNull DataTransportOptions options) {
        super(context, role, options);
        mConnectionMethod = connectionMethod;
    }

    @Override
    public void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes) {
        mEncodedEDeviceKeyBytes = encodedEDeviceKeyBytes;

        // Service name is always derived from EReaderKey as per 18013-5.
        byte[] ikm = mEncodedEDeviceKeyBytes;
        byte[] info = "NANService".getBytes(UTF_8);
        byte[] salt = new byte[]{};
        mServiceName = Util.base16(Util.computeHkdf("HmacSha256", ikm, salt, info, 16));
        Logger.d(TAG, String.format("Using calculated service name '%s'", mServiceName));

        // If the passphrase isn't given, derive as per 18013-5.
        if (mPassphrase == null) {
            ikm = mEncodedEDeviceKeyBytes;
            info = "NANPassphrase".getBytes(UTF_8);
            salt = new byte[]{};
            mPassphrase = Base64.encodeToString(
                    Util.computeHkdf("HmacSha256", ikm, salt, info, 32),
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            Logger.d(TAG, String.format("Using calculated passphrase '%s'", mPassphrase));
        } else {
            Logger.d(TAG, String.format("Using provided passphrase '%s'", mPassphrase));
        }
    }

    private void connectAsMdoc() {
        mWifiAwareManager = mContext.getSystemService(WifiAwareManager.class);

        mWifiAwareManager.attach(
                new AttachCallback() {
                    @Override
                    public void onAttachFailed() {
                        Log.d(TAG, "onAttachFailed");
                        reportError(new Error("Wifi-Aware attach failed"));
                    }

                    @SuppressLint("ClassVerificationFailure")
                    @Override
                    public void onAttached(@NonNull WifiAwareSession session) {
                        Log.d(TAG, "onAttached: " + session);
                        mSession = session;

                        PublishConfig config = new PublishConfig.Builder()
                                .setServiceName(mServiceName)
                                .build();

                        mSession.publish(config, new DiscoverySessionCallback() {
                            private PublishDiscoverySession mPublishDiscoverySession;

                            @Override
                            public void onPublishStarted(PublishDiscoverySession session) {
                                Log.d(TAG, "onPublishStarted");
                                mPublishDiscoverySession = session;
                            }

                            @Override
                            public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
                                Log.d(TAG, "onMessageReceived: peer: " + peerHandle
                                        + " message: " + Util.toHex(message));

                                listenerOnMessageReceived(mPublishDiscoverySession, peerHandle);
                            }
                        }, null);

                    }
                },
                null);

        // Passphrase is mandatory for NFC so we always set it...
        //
        byte[] passphraseBytes = new byte[16];
        Random r = new SecureRandom();
        r.nextBytes(passphraseBytes);
        String passphraseInfoPassphrase = Util.base16(passphraseBytes);
        WifiManager wm = mContext.getSystemService(WifiManager.class);
        int supportedBandsBitmap = 0x04;   // Bit 2: 2.4 GHz
        if (wm.is5GHzBandSupported()) {
            supportedBandsBitmap |= 0x10;  // Bit 4: 4.9 and 5 GHz
        }
        byte[] bandInfoSupportedBands = new byte[]{(byte) (supportedBandsBitmap & 0xff)};

        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (characteristics != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mCipherSuites = Api30Impl.getSupportedCipherSuites(characteristics);
            } else {
                // Pre-R, just assume that only NCS-SK-128 works.
                mCipherSuites = Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;
            }
        }
    }

    void listenerOnMessageReceived(PublishDiscoverySession session, PeerHandle peerHandle) {
        reportConnecting();

        try {
            mListenerServerSocket = new ServerSocket(0);
        } catch (IOException e) {
            reportError(e);
            return;
        }
        int port = mListenerServerSocket.getLocalPort();
        Log.d(TAG, "Listener on port " + port);

        listenOnServerSocket();

        NetworkSpecifier networkSpecifier = new WifiAwareNetworkSpecifier.Builder(session,
                peerHandle)
                .setPskPassphrase(mPassphrase)
                .setPort(port)
                .build();
        NetworkRequest myNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                .setNetworkSpecifier(networkSpecifier)
                .build();
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "onAvailable");
            }

            @Override
            public void onCapabilitiesChanged(Network network,
                    NetworkCapabilities networkCapabilities) {
                Log.d(TAG, "onCapabilitiesChanged " + networkCapabilities);
            }

            @Override
            public void onLost(Network network) {
                Log.d(TAG, "onLost");
            }
        };

        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);

        cm.requestNetwork(myNetworkRequest, callback);

        session.sendMessage(peerHandle,
                0,
                "helloSub".getBytes(UTF_8));

    }

    private void listenOnServerSocket() {
        Thread socketServerThread = new Thread() {
            @Override
            public void run() {
                try {
                    // We only accept a single client with this server socket...
                    //
                    mListenerSocket = mListenerServerSocket.accept();

                    Thread writingThread = new Thread() {
                        @Override
                        public void run() {
                            writeToSocket(true, mListenerSocket);
                        }
                    };
                    writingThread.start();

                    reportConnected();

                    readFromSocket(true, mListenerSocket);

                } catch (IOException e) {
                    reportError(e);
                }
            }
        };
        socketServerThread.start();
    }

    public void setPassphrase(@NonNull String passphrase) {
        mPassphrase = passphrase;
    }

    private void connectAsMdocReader() {

        mWifiAwareManager = mContext.getSystemService(WifiAwareManager.class);

        mWifiAwareManager.attach(
                new AttachCallback() {
                    @Override
                    public void onAttachFailed() {
                        Log.d(TAG, "onAttachFailed");
                        reportError(new Error("Wifi-Aware attach failed"));
                    }

                    @SuppressLint("ClassVerificationFailure")
                    @Override
                    public void onAttached(@NonNull WifiAwareSession session) {
                        Log.d(TAG, "onAttached: " + session);
                        mSession = session;

                        SubscribeConfig config = new SubscribeConfig.Builder()
                                .setServiceName(mServiceName)
                                .build();

                        mSession.subscribe(config, new DiscoverySessionCallback() {
                            private SubscribeDiscoverySession mSubscribeDiscoverySession;

                            @Override
                            public void onMessageSendFailed(int messageId) {
                                Log.d(TAG, "onMessageSendFailed");
                            }

                            @Override
                            public void onMessageSendSucceeded(int messageId) {
                                Log.d(TAG, "onMessageSendSucceeded");
                            }

                            @Override
                            public void onSubscribeStarted(SubscribeDiscoverySession session) {
                                mSubscribeDiscoverySession = session;
                                Log.d(TAG, "onSubscribeStarted");
                            }

                            @Override
                            public void onServiceDiscovered(PeerHandle peerHandle,
                                    byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
                                Log.d(TAG, "onServiceDiscovered: peer: " + peerHandle
                                        + " serviceSpecificInfo: " + Util.toHex(
                                        serviceSpecificInfo));

                                mSubscribeDiscoverySession.sendMessage(peerHandle,
                                        0,
                                        "helloPub".getBytes(UTF_8));
                            }

                            @Override
                            public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
                                Log.d(TAG, "onMessageReceived: peer: " + peerHandle
                                        + " message: " + Util.toHex(message));

                                initiatorOnMessageReceived(mSubscribeDiscoverySession, peerHandle);
                            }


                        }, null);

                    }
                },
                null);
    }

    @Override
    public void connect() {
        if (mRole == ROLE_MDOC) {
            connectAsMdoc();
        } else {
            connectAsMdocReader();
        }
        reportConnectionMethodReady();
    }

    void initiatorOnMessageReceived(SubscribeDiscoverySession session, PeerHandle peerHandle) {
        NetworkSpecifier networkSpecifier = new WifiAwareNetworkSpecifier.Builder(session,
                peerHandle)
                .setPskPassphrase(mPassphrase)
                .build();
        NetworkRequest myNetworkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                .setNetworkSpecifier(networkSpecifier)
                .build();
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            private NetworkCapabilities mNetworkCapabilities = null;
            private boolean mIsAvailable = false;
            private boolean mInitiatedConnection = false;

            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "onAvailable sub");
                mIsAvailable = true;
                if (mInitiatedConnection) {
                    return;
                }
                if (mIsAvailable && mNetworkCapabilities != null) {
                    initiatorConnect(network, mNetworkCapabilities);
                    mInitiatedConnection = true;
                }
            }

            @Override
            public void onCapabilitiesChanged(Network network,
                    NetworkCapabilities networkCapabilities) {
                Log.d(TAG, "onCapabilitiesChanged sub " + networkCapabilities);
                mNetworkCapabilities = networkCapabilities;
                if (mInitiatedConnection) {
                    return;
                }
                if (mIsAvailable && mNetworkCapabilities != null) {
                    initiatorConnect(network, mNetworkCapabilities);
                    mInitiatedConnection = true;
                }
            }

            @Override
            public void onLost(Network network) {
                Log.d(TAG, "onLost sub");
            }
        };

        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);

        cm.requestNetwork(myNetworkRequest, callback);

        //session.sendMessage(peerHandle,
        //        0,
        //        "helloSub".getBytes(UTF_8));

    }

    void initiatorConnect(Network network, NetworkCapabilities networkCapabilities) {
        WifiAwareNetworkInfo peerAwareInfo =
                (WifiAwareNetworkInfo) networkCapabilities.getTransportInfo();
        Inet6Address peerIpv6 = peerAwareInfo.getPeerIpv6Addr();
        int peerPort = peerAwareInfo.getPort();

        // peerIpv6.getHostAddress() returns something like "fe80::75:baff:fedd:ce16%aware_data0",
        // this is how we get rid of it...
        InetAddress strippedAddress;
        try {
            strippedAddress = InetAddress.getByAddress(peerIpv6.getAddress());
        } catch (UnknownHostException e) {
            reportError(e);
            return;
        }

        // TODO: it's not clear whether port should be included here, we include it for now...
        //
        mInitiatorIPv6HostString = String.format(Locale.US,
                "[%s]:%d", strippedAddress.getHostAddress(), peerPort);
        Log.d(TAG, "Connecting to " + mInitiatorIPv6HostString);

        try {
            mInitiatorSocket = network.getSocketFactory().createSocket(peerIpv6, peerPort);

            Thread writingThread = new Thread() {
                @Override
                public void run() {
                    writeToSocket(false, mInitiatorSocket);
                }
            };
            writingThread.start();

            Thread listenerThread = new Thread() {
                @Override
                public void run() {
                    readFromSocket(false, mInitiatorSocket);
                }
            };
            listenerThread.start();
            reportConnected();
        } catch (IOException e) {
            reportError(e);
        }
    }

    @Override
    public void close() {
        Log.d(TAG, "close() called");
        inhibitCallbacks();

        if (mWifiAwareManager != null) {
            // TODO: any way to detach?
            mWifiAwareManager = null;
        }
        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
        if (mListenerServerSocket != null) {
            try {
                mListenerServerSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing listener's server socket");
                e.printStackTrace();
            }
            mListenerServerSocket = null;
        }
        if (mInitiatorSocket != null) {
            try {
                mInitiatorSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing initiator's socket");
                e.printStackTrace();
            }
            mInitiatorSocket = null;
        }
        if (mListenerSocket != null) {
            try {
                mListenerSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing listener's socket");
                e.printStackTrace();
            }
            mListenerSocket = null;
        }
    }

    @Override
    public void sendMessage(@NonNull byte[] data) {
        mWriterQueue.add(data);
    }

    void writeToSocket(boolean isListener, Socket socket) {
        OutputStream os;

        try {
            os = socket.getOutputStream();
        } catch (IOException e) {
            reportError(e);
            return;
        }

        Log.d(TAG, "Writing socket isListener=" + isListener);
        while (socket.isConnected()) {
            byte[] messageToSend = null;
            try {
                messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                if (messageToSend == null) {
                    continue;
                }
            } catch (InterruptedException e) {
                continue;
            }

            Log.d(TAG, "Sending " + messageToSend.length + " bytes");

            reportMessageProgress(0, messageToSend.length);
            OutputStream pros = new ProgressReportingOutputStream(os, messageToSend.length, this);
            try {
                if (isListener) {
                    pros.write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Length: " + messageToSend.length + "\r\n"
                            + "Content-Type: application/CBOR\r\n"
                            + "\r\n").getBytes(UTF_8));
                } else {
                    pros.write(("POST /mdoc HTTP/1.1\r\n"
                            + "Host: " + mInitiatorIPv6HostString + "\r\n"
                            + "Content-Length: " + messageToSend.length + "\r\n"
                            + "Content-Type: application/CBOR\r\n"
                            + "\r\n").getBytes(UTF_8));
                }
                pros.write(messageToSend);
                pros.flush();

            } catch (IOException e) {
                Log.d(TAG, "Caught exception while writing isListener=" + isListener);
                reportError(e);
                return;
            }
        }
    }

    @SuppressWarnings("deprecation")
    void readFromSocket(boolean isListener, Socket socket) {
        InputStream inputStream = null;
        try {
            inputStream = socket.getInputStream();
        } catch (IOException e) {
            Log.d(TAG, "Caught exception while getting inputstream isListener=" + isListener);
            e.printStackTrace();
            reportError(e);
            return;
        }

        Log.d(TAG, "Reading from socket isListener=" + isListener);
        DataInputStream dis = new DataInputStream(inputStream);
        boolean keepGoing = true;
        int contentLength = -1;
        try {
            while (keepGoing) {
                Log.d(TAG, "Calling readLine()...");
                String line = dis.readLine();
                if (line == null) {
                    // End of stream...
                    if (isListener) {
                        reportDisconnected();
                    } else {
                        reportDisconnected();
                    }
                    return;
                }
                Log.d(TAG, "read line '" + line + "'");
                if (line.toLowerCase(Locale.US).startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Error parsing Content-Length line '" + line + "'");
                        reportError(e);
                        return;
                    }
                }
                if (line.length() == 0) {
                    // End of headers...
                    if (contentLength == -1) {
                        reportError(new Error("No Content-Length header"));
                        return;
                    }
                    if (contentLength > 1024 * 1024) {
                        reportError(new Error("Content-Length " + contentLength + " rejected"));
                        return;
                    }
                    Log.d(TAG,
                            "Going to read " + contentLength + " bytes, isListener=" + isListener);
                    byte[] data = new byte[contentLength];
                    dis.readFully(data);
                    reportMessageReceived(data);
                    contentLength = -1;
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "Caught exception while reading isListener=" + isListener);
            e.printStackTrace();
            reportError(e);
        }
    }

    @Override
    public void sendTransportSpecificTerminationMessage() {
        reportError(new Error("Transport-specific termination message not supported"));
    }

    @Override
    public boolean supportsTransportSpecificTerminationMessage() {
        return false;
    }

    @TargetApi(30)
    @RequiresApi(30)
    static class Api30Impl {
        private Api30Impl() {
            // This class is not instantiable.
        }

        @DoNotInline
        static int getSupportedCipherSuites(Characteristics characteristics) {
            return characteristics.getSupportedCipherSuites();
        }

    }

    public static class ProgressReportingOutputStream extends FilterOutputStream {

        private final long totalBytes;
        private long bytesSent;
        private final DataTransport transport;

        public ProgressReportingOutputStream(
            final OutputStream out,
            final long totalBytes,
            final DataTransport transport) {
            super(out);
            this.totalBytes = totalBytes;
            this.transport = transport;
            this.bytesSent = 0;
        }

        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            this.bytesSent += len;
            reportProgress();
        }

        public void write(int b) throws IOException {
            out.write(b);
            this.bytesSent++;
            reportProgress();
        }

        private void reportProgress() {
            this.transport.reportMessageProgress(bytesSent, totalBytes);
        }

    }

    @Override
    public @NonNull ConnectionMethod getConnectionMethod() {
        return mConnectionMethod;
    }
}
