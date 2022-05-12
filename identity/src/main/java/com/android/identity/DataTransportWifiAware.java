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
import android.nfc.NdefRecord;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.DoNotInline;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.android.identity.Constants.LoggingFlag;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;

/**
 * Wifi Aware data transport.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DataTransportWifiAware extends DataTransport {
    public static final int DEVICE_RETRIEVAL_METHOD_TYPE = 3;
    public static final int DEVICE_RETRIEVAL_METHOD_VERSION = 1;
    public static final int RETRIEVAL_OPTION_KEY_PASSPHRASE_INFO_PASSPHRASE = 0;
    public static final int RETRIEVAL_OPTION_KEY_CHANNEL_INFO_OPERATING_CLASS = 1;
    public static final int RETRIEVAL_OPTION_KEY_CHANNEL_INFO_CHANNEL_NUMBER = 2;
    public static final int RETRIEVAL_OPTION_KEY_BAND_INFO_SUPPORTED_BANDS = 3;

    private static final String TAG = "DataTransportWifiAware";

    BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    String mServiceName;
    WifiAwareSession mSession;
    ServerSocket mListenerServerSocket;
    Socket mInitiatorSocket;
    Socket mListenerSocket;
    DataRetrievalAddressWifiAware mConnectAddress;
    private byte[] mEncodedEDeviceKeyBytes;
    private WifiAwareManager mWifiAwareManager;
    private String mInitiatorIPv6HostString;
    private String mDerivedPassphrase;
    @SuppressWarnings("unused")
    private int mCipherSuites;
    private DataRetrievalAddressWifiAware mListeningAddress;

    public DataTransportWifiAware(@NonNull Context context) {
        super(context);
    }

    public static @Nullable
    List<DataRetrievalAddress> parseNdefRecord(@NonNull NdefRecord record) {
        String passphraseInfoPassphrase = null;
        byte[] bandInfoSupportedBands = null;
        OptionalInt channelInfoChannelNumber = OptionalInt.empty();
        OptionalInt channelInfoOperatingClass = OptionalInt.empty();

        // See above for OOB data and where it's defined.
        //
        ByteBuffer payload = ByteBuffer.wrap(record.getPayload()).order(ByteOrder.LITTLE_ENDIAN);
        Log.d(TAG, "Examining " + payload.remaining() + " bytes: " + Util.toHex(payload.array()));
        while (payload.remaining() > 0) {
            Log.d(TAG, "hasR: " + payload.hasRemaining() + " rem: " + payload.remaining());
            int len = payload.get();
            int type = payload.get();
            int offset = payload.position();
            Log.d(TAG, String.format("type %d len %d", type, len));
            if (type == 0x03 && len > 1) {
                // passphrase
                byte[] encodedPassphrase = new byte[len - 1];
                payload.get(encodedPassphrase, 0, len - 1);
                passphraseInfoPassphrase = new String(encodedPassphrase, StandardCharsets.UTF_8);
            } else if (type == 0x04 && len > 1) {
                bandInfoSupportedBands = new byte[len - 1];
                payload.get(bandInfoSupportedBands, 0, len - 1);
            } else {
                // TODO: add support for other options...
                Log.d(TAG, String.format("Skipping unknown type %d of length %d", type, len));
            }
            payload.position(offset + len - 1);
        }

        List<DataRetrievalAddress> addresses = new ArrayList<>();
        addresses.add(new DataRetrievalAddressWifiAware(passphraseInfoPassphrase,
                channelInfoChannelNumber, channelInfoOperatingClass, bandInfoSupportedBands));
        return addresses;
    }

    static public @Nullable
    List<DataRetrievalAddress> parseDeviceRetrievalMethod(int version, @NonNull DataItem[] items) {
        if (version > DEVICE_RETRIEVAL_METHOD_VERSION) {
            Log.w(TAG, "Unexpected version " + version + " for retrieval method");
            return null;
        }
        if (items.length < 3 || !(items[2] instanceof Map)) {
            Log.w(TAG, "Item 3 in device retrieval array is not a map");
        }
        Map options = ((Map) items[2]);

        String passphraseInfoPassphrase = null;
        if (Util.cborMapHasKey(options, 0)) {
            passphraseInfoPassphrase = Util.cborMapExtractString(options, 0);
        }
        OptionalInt channelInfoChannelNumber = OptionalInt.empty();
        if (Util.cborMapHasKey(options, 1)) {
            channelInfoChannelNumber = OptionalInt.of(Util.cborMapExtractNumber(options, 1));
        }
        OptionalInt channelInfoOperatingClass = OptionalInt.empty();
        if (Util.cborMapHasKey(options, 2)) {
            channelInfoOperatingClass = OptionalInt.of(Util.cborMapExtractNumber(options, 2));
        }
        byte[] bandInfoSupportedBands = null;
        if (Util.cborMapHasKey(options, 3)) {
            bandInfoSupportedBands = Util.cborMapExtractByteString(options, 3);
        }

        List<DataRetrievalAddress> addresses = new ArrayList<>();
        addresses.add(new DataRetrievalAddressWifiAware(passphraseInfoPassphrase,
                channelInfoChannelNumber, channelInfoOperatingClass, bandInfoSupportedBands));
        return addresses;
    }

    @Override
    public void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes) {
        mEncodedEDeviceKeyBytes = encodedEDeviceKeyBytes;

        byte[] ikm = mEncodedEDeviceKeyBytes;
        byte[] info = "NANService".getBytes(StandardCharsets.UTF_8);
        byte[] salt = new byte[]{};
        mServiceName = Util.base16(Util.computeHkdf("HmacSha256", ikm, salt, info, 16));
        Log.d(TAG, String.format("Using service name '%s'", mServiceName));

        ikm = mEncodedEDeviceKeyBytes;
        info = "NANPassphrase".getBytes(StandardCharsets.UTF_8);
        salt = new byte[]{};
        mDerivedPassphrase = Base64.encodeToString(
                Util.computeHkdf("HmacSha256", ikm, salt, info, 32),
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        Log.d(TAG, String.format("Passphrase '%s' foo", mDerivedPassphrase));

    }

    @Override
    public @NonNull
    DataRetrievalAddress getListeningAddress() {
        return mListeningAddress;
    }

    @Override
    public void listen() {
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

        mListeningAddress = new DataRetrievalAddressWifiAware(passphraseInfoPassphrase,
                OptionalInt.empty(), OptionalInt.empty(),
                bandInfoSupportedBands);

        reportListeningSetupCompleted(mListeningAddress);

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
        reportListeningPeerConnecting();

        try {
            mListenerServerSocket = new ServerSocket(0);
        } catch (IOException e) {
            reportError(e);
            return;
        }
        int port = mListenerServerSocket.getLocalPort();
        Log.d(TAG, "Listener on port " + port);

        listenOnServerSocket();

        String passphrase = mListeningAddress.passphraseInfoPassphrase;
        if (passphrase == null) {
            passphrase = mDerivedPassphrase;
        }

        NetworkSpecifier networkSpecifier = new WifiAwareNetworkSpecifier.Builder(session,
                peerHandle)
                .setPskPassphrase(passphrase)
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
                "helloSub".getBytes(StandardCharsets.UTF_8));

    }

    private void listenOnServerSocket() {
        Thread socketServerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // We only accept a single client with this server socket...
                    //
                    mListenerSocket = mListenerServerSocket.accept();

                    Thread writingThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            writeToSocket(true, mListenerSocket);
                        }
                    });
                    writingThread.start();

                    reportListeningPeerConnected();

                    readFromSocket(true, mListenerSocket);

                } catch (IOException e) {
                    reportError(e);
                }
            }
        });
        socketServerThread.start();
    }

    @Override
    public void connect(@NonNull DataRetrievalAddress genericAddress) {
        mConnectAddress = (DataRetrievalAddressWifiAware) genericAddress;

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
                                        "helloPub".getBytes(StandardCharsets.UTF_8));
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

    void initiatorOnMessageReceived(SubscribeDiscoverySession session, PeerHandle peerHandle) {
        String passphrase = mConnectAddress.passphraseInfoPassphrase;
        if (passphrase == null) {
            passphrase = mDerivedPassphrase;
        }

        NetworkSpecifier networkSpecifier = new WifiAwareNetworkSpecifier.Builder(session,
                peerHandle)
                .setPskPassphrase(passphrase)
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
        //        "helloSub".getBytes(StandardCharsets.UTF_8));

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
        mInitiatorIPv6HostString = String.format("[%s]:%d", strippedAddress.getHostAddress(),
                peerPort);
        Log.d(TAG, "Connecting to " + mInitiatorIPv6HostString);

        try {
            mInitiatorSocket = network.getSocketFactory().createSocket(peerIpv6, peerPort);

            Thread writingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    writeToSocket(false, mInitiatorSocket);
                }
            });
            writingThread.start();

            Thread listenerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    readFromSocket(false, mInitiatorSocket);
                }
            });
            listenerThread.start();
            reportConnectionResult(null);
        } catch (IOException e) {
            reportConnectionResult(e);
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

            try {
                if (isListener) {
                    os.write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Length: " + messageToSend.length + "\r\n"
                            + "Content-Type: application/CBOR\r\n"
                            + "\r\n").getBytes(StandardCharsets.UTF_8));
                } else {
                    os.write(("POST /mdoc HTTP/1.1\r\n"
                            + "Host: " + mInitiatorIPv6HostString + "\r\n"
                            + "Content-Length: " + messageToSend.length + "\r\n"
                            + "Content-Type: application/CBOR\r\n"
                            + "\r\n").getBytes(StandardCharsets.UTF_8));
                }
                os.write(messageToSend);
                os.flush();

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
                        reportListeningPeerDisconnected();
                    } else {
                        reportConnectionDisconnected();
                    }
                    return;
                }
                Log.d(TAG, "read line '" + line + "'");
                if (line.toLowerCase().startsWith("content-length:")) {
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

    static class DataRetrievalAddressWifiAware extends DataRetrievalAddress {
        @Nullable
        String passphraseInfoPassphrase;
        OptionalInt channelInfoChannelNumber;
        OptionalInt channelInfOperatingClass;
        @Nullable
        byte[] bandInfoSupportedBands;
        // TODO: support MAC address
        DataRetrievalAddressWifiAware(@Nullable String passphraseInfoPassphrase,
                OptionalInt channelInfoChannelNumber,
                OptionalInt channelInfOperatingClass,
                @Nullable byte[] bandInfoSupportedBands) {
            this.passphraseInfoPassphrase = passphraseInfoPassphrase;
            this.channelInfoChannelNumber = channelInfoChannelNumber;
            this.channelInfOperatingClass = channelInfOperatingClass;
            this.bandInfoSupportedBands = bandInfoSupportedBands;
        }

        @Override
        @NonNull
        DataTransport createDataTransport(
                @NonNull Context context, @LoggingFlag int loggingFlags) {
            return new DataTransportWifiAware(context /*, loggingFlags*/);
        }

        @Override
        Pair<NdefRecord, byte[]> createNdefRecords(List<DataRetrievalAddress> listeningAddresses) {
            // The NdefRecord and its OOB data is defined in "Wi-Fi Aware Specification", table 142.
            //
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                // TODO: use mCipherSuites
                int cipherSuites = Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128;

                // Spec says: The NFC Handover Selector shall include the Cipher Suite Info field
                // with
                // one or multiple NAN Cipher Suite IDs in the WiFi Aware Carrier Configuration
                // Record
                // to indicate the supported NAN cipher suite(s)."
                //
                int numCipherSuitesSupported = 0;
                if ((cipherSuites & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128) != 0) {
                    numCipherSuitesSupported++;
                }
                if ((cipherSuites & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256) != 0) {
                    numCipherSuitesSupported++;
                }
                baos.write(1 + numCipherSuitesSupported);
                baos.write(0x01); // Data Type 0x01 - Cipher Suite Info
                if ((cipherSuites & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128) != 0) {
                    baos.write(0x01); // NCS-SK-128
                }
                if ((cipherSuites & Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_256) != 0) {
                    baos.write(0x02); // NCS-SK-256
                }

                // Spec says: "If the NFC Handover Selector indicates an NCS-SK cipher suite, it
                // shall
                // include a Pass-phrase Info field in the Wi-Fi Aware Carrier Configuration Record
                // to specify the selected pass-phrase for the supported cipher suite."
                //
                // Additionally, 18013-5 says: "If NFC is used for device engagement, either the
                // Pass-phrase Info or the DH Info shall be explicitly transferred from the mdoc to
                // the mdoc reader during device engagement according to the Wi-Fi Alliance
                // Neighbor Awareness Networking Specification section 12."
                //
                // So we have to make up a passphrase.
                //
                byte[] encodedPassphrase = passphraseInfoPassphrase.getBytes(
                        StandardCharsets.UTF_8);
                baos.write(1 + encodedPassphrase.length);
                baos.write(0x03); // Data Type 0x03 - Pass-phrase Info
                baos.write(encodedPassphrase);

                // Spec says: "The NFC Handover Selector shall also include a Band Info field in the
                // Wi-Fi Aware Configuration Record to indicate the supported NAN operating band
                // (s)."
                //
                baos.write(1 + bandInfoSupportedBands.length);
                baos.write(0x04); // Data Type 0x04 - Band Info
                baos.write(bandInfoSupportedBands);

                // Spec says: "The Channel Info field serves as a placeholder for future
                // extension, and
                // may optionally be included in the Wi-Fi Aware Carrier Configuration Record in the
                // NFC Handover Select message."
                //
                // We don't include this for now.
                //
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            byte[] oobData = baos.toByteArray();

            NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                    "application/vnd.wfa.nan".getBytes(StandardCharsets.UTF_8),
                    "W".getBytes(StandardCharsets.UTF_8),
                    oobData);

            // From 7.1 Alternative Carrier Record
            //
            baos = new ByteArrayOutputStream();
            baos.write(0x01); // CPS: active
            baos.write(0x01); // Length of carrier data reference ("0")
            baos.write('W');  // Carrier data reference
            baos.write(0x01); // Number of auxiliary references
            byte[] auxReference = "mdoc".getBytes(StandardCharsets.UTF_8);
            baos.write(auxReference.length);
            baos.write(auxReference, 0, auxReference.length);
            byte[] acRecordPayload = baos.toByteArray();

            return new Pair<>(record, acRecordPayload);
        }

        @Override
        void addDeviceRetrievalMethodsEntry(ArrayBuilder<CborBuilder> arrayBuilder,
                List<DataRetrievalAddress> listeningAddresses) {

            ArrayBuilder<ArrayBuilder<CborBuilder>> innerArrayBuilder = arrayBuilder.addArray();
            innerArrayBuilder
                    .add(DEVICE_RETRIEVAL_METHOD_TYPE)
                    .add(DEVICE_RETRIEVAL_METHOD_VERSION);

            MapBuilder<ArrayBuilder<ArrayBuilder<CborBuilder>>> mapBuilder =
                    innerArrayBuilder.addMap();

            mapBuilder.put(RETRIEVAL_OPTION_KEY_PASSPHRASE_INFO_PASSPHRASE,
                    passphraseInfoPassphrase);
            if (channelInfoChannelNumber.isPresent()) {
                mapBuilder.put(RETRIEVAL_OPTION_KEY_CHANNEL_INFO_CHANNEL_NUMBER,
                        channelInfoChannelNumber.getAsInt());
            }
            if (channelInfOperatingClass.isPresent()) {
                mapBuilder.put(RETRIEVAL_OPTION_KEY_CHANNEL_INFO_OPERATING_CLASS,
                        channelInfOperatingClass.getAsInt());
            }
            mapBuilder.put(RETRIEVAL_OPTION_KEY_BAND_INFO_SUPPORTED_BANDS, bandInfoSupportedBands);
            mapBuilder.end();
            innerArrayBuilder.end();
        }

        @Override
        public @NonNull
        String toString() {
            StringBuilder builder = new StringBuilder("wifi_aware");
            if (passphraseInfoPassphrase != null) {
                builder.append(":passphrase=");
                builder.append(passphraseInfoPassphrase);
            }
            if (channelInfoChannelNumber.isPresent()) {
                builder.append(":channel_info_channel_number=");
                builder.append(channelInfoChannelNumber.getAsInt());
            }
            if (channelInfOperatingClass.isPresent()) {
                builder.append(":channel_info_operating_class=");
                builder.append(channelInfOperatingClass.getAsInt());
            }
            if (bandInfoSupportedBands != null) {
                builder.append(":base_info_supported_bands=");
                builder.append(Util.toHex(bandInfoSupportedBands));
            }
            return builder.toString();
        }
    }

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
}
