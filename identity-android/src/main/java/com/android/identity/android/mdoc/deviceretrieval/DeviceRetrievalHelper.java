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

package com.android.identity.android.mdoc.deviceretrieval;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.android.mdoc.transport.DataTransportBle;
import com.android.identity.cbor.Bstr;
import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.Tagged;
import com.android.identity.crypto.EcPrivateKey;
import com.android.identity.crypto.EcPublicKey;
import com.android.identity.mdoc.sessionencryption.SessionEncryption;
import com.android.identity.android.mdoc.transport.TransmissionProgressListener;
import com.android.identity.android.mdoc.engagement.NfcEngagementHelper;
import com.android.identity.android.mdoc.engagement.QrEngagementHelper;
import com.android.identity.android.mdoc.transport.DataTransport;
import com.android.identity.mdoc.engagement.EngagementGenerator;
import com.android.identity.mdoc.engagement.EngagementParser;
import com.android.identity.mdoc.origininfo.OriginInfo;
import com.android.identity.mdoc.request.DeviceRequestParser;
import com.android.identity.mdoc.response.DeviceResponseGenerator;
import com.android.identity.util.Constants;
import com.android.identity.util.Logger;
import com.android.identity.internal.Util;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.Executor;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * Helper used for establishing engagement with, interacting with, and presenting credentials to a
 * remote <em>mdoc reader</em> device.
 *
 * <p>This class implements the interface between an <em>mdoc</em> and <em>mdoc reader</em> using
 * the connection setup and device retrieval interfaces defined in
 * <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5:2021</a>.
 *
 * <p>Reverse engagement as per drafts of 18013-7 and 23220-4 is supported. These protocols
 * are not finalized so should only be used for testing.
 */
// Suppress with NotCloseable since we actually don't hold any resources needing to be
// cleaned up at object finalization time.
@SuppressWarnings("NotCloseable")
public class DeviceRetrievalHelper {
    private static final String TAG = "DeviceRetrievalHelper";

    private EcPrivateKey mEDeviceKey;
    private Context mContext;
    private EcPublicKey mEReaderKey;

    private byte[] mDeviceEngagement;
    private byte[] mHandover;
    SessionEncryption mSessionEncryption;
    private byte[] mEncodedSessionTranscript;

    private byte[] mAlternateDeviceEngagement;
    private byte[] mAlternateHandover;
    SessionEncryption mAlternateSessionEncryption;
    private byte[] mEncodedAlternateSessionTranscript;

    Listener mListener;
    Executor mListenerExecutor;
    DataTransport mTransport;

    boolean mReceivedSessionTerminated;

    private boolean mInhibitCallbacks;
    private byte[] mReverseEngagementReaderEngagement;
    private List<OriginInfo> mReverseEngagementOriginInfos;
    private byte[] mReverseEngagementEncodedEReaderKey;

    DeviceRetrievalHelper() {}

    // Note: The report*() methods are safe to call from any thread.

    void reportEReaderKeyReceived(@NonNull EcPublicKey eReaderKey) {
        Logger.d(TAG, "reportEReaderKeyReceived: " + eReaderKey);
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onEReaderKeyReceived(eReaderKey);
                }
            });
        }
    }

    void reportDeviceRequest(@NonNull byte[] deviceRequestBytes) {
        Logger.d(TAG, "reportDeviceRequest: deviceRequestBytes: " + deviceRequestBytes.length + " bytes");
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onDeviceRequest(deviceRequestBytes);
                }
            });
        }
    }

    void reportDeviceDisconnected(boolean transportSpecificTermination) {
        Logger.d(TAG, "reportDeviceDisconnected: transportSpecificTermination: "
                + transportSpecificTermination);
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onDeviceDisconnected(transportSpecificTermination);
                }
            });
        }
    }

    void reportError(@NonNull Throwable error) {
        Logger.d(TAG, "reportError: error: ", error);
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onError(error);
                }
            });
        }
    }

    void start() {
        mTransport.setListener(new DataTransport.Listener() {
            @Override
            public void onConnecting() {
                Logger.d(TAG, "onConnecting");
            }

            @Override
            public void onDisconnected() {
                Logger.d(TAG, "onDisconnected");
                if (mTransport != null) {
                    mTransport.close();
                }
                if (!mReceivedSessionTerminated) {
                    reportError(new Error("Peer disconnected without proper session termination"));
                } else {
                    reportDeviceDisconnected(false);
                }
            }

            @Override
            public void onConnected() {
                Logger.d(TAG, "onConnected");
                if (mReverseEngagementReaderEngagement != null) {
                    Logger.d(TAG, "onConnected for reverse engagement");

                    EngagementGenerator generator = new EngagementGenerator(
                            mEDeviceKey.getPublicKey(),
                            EngagementGenerator.ENGAGEMENT_VERSION_1_1);
                    generator.addOriginInfos(mReverseEngagementOriginInfos);
                    mDeviceEngagement = generator.generate();

                    // 18013-7 says to use ReaderEngagementBytes for Handover when ReaderEngagement
                    // is available and neither QR or NFC is used.
                    mHandover = Util.cborEncode(Util.cborBuildTaggedByteString(mReverseEngagementReaderEngagement));

                    // 18013-7 says to transmit DeviceEngagementBytes in MessageData
                    CborBuilder builder = new CborBuilder();
                    MapBuilder<CborBuilder> mapBuilder = builder.addMap();
                    mapBuilder.put(new UnicodeString("deviceEngagementBytes"),
                            Util.cborBuildTaggedByteString(mDeviceEngagement));
                    mapBuilder.end();
                    byte[] messageData = Util.cborEncode(builder.build().get(0));

                    Logger.dCbor(TAG, "MessageData for reverse engagement to send", messageData);
                    mTransport.sendMessage(messageData);
                } else {
                    throw new IllegalStateException("Unexpected onConnected callback");
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                if (mTransport != null) {
                    mTransport.close();
                }
                reportError(error);
            }

            @Override
            public void onMessageReceived() {
                byte[] data = mTransport.getMessage();
                if (data == null) {
                    reportError(new Error("onMessageReceived but no message"));
                    return;
                }
                processMessageReceived(data);
            }

            @Override
            public void onTransportSpecificSessionTermination() {
                Logger.d(TAG, "Received transport-specific session termination");
                mReceivedSessionTerminated = true;
                if (mTransport != null) {
                    mTransport.close();
                }
                reportDeviceDisconnected(true);
            }

        }, mListenerExecutor);

        byte[] data = mTransport.getMessage();
        if (data != null) {
            processMessageReceived(data);
        }

        if (mReverseEngagementReaderEngagement != null) {
            // Get EReaderKey
            EngagementParser parser = new EngagementParser(mReverseEngagementReaderEngagement);
            EngagementParser.Engagement readerEngagement = parser.parse();
            mReverseEngagementEncodedEReaderKey = Util.cborExtractTaggedCbor(readerEngagement.getESenderKeyBytes());

            // This is reverse engagement, we actually haven't connected yet...
            byte[] encodedEDeviceKeyBytes =
                    Cbor.encode(new Tagged(24, new Bstr(
                            Cbor.encode(mEDeviceKey.toCoseKey(Map.of()).getDataItem()))));
            mTransport.setEDeviceKeyBytes(encodedEDeviceKeyBytes);
            mTransport.connect();
        }

    }

    // Returns nothing if everything parses correctly or session encryption has already been set
    // up, otherwise the status code (10, 11, 20 as per 18013-5 table 20) to include in the
    // SessionData response.
    private OptionalLong ensureSessionEncryption(@NonNull byte[] data) {
        if (mSessionEncryption != null) {
            return OptionalLong.empty();
        }

        // For reverse engagement, we get EReaderKeyBytes via Reverse Engagement...
        byte[] encodedEReaderKey = null;
        if (mReverseEngagementEncodedEReaderKey != null) {
            encodedEReaderKey = mReverseEngagementEncodedEReaderKey;
            // This is unnecessary but a nice warning regardless...
            DataItem decodedData = Util.cborDecode(data);
            if (Util.cborMapHasKey(decodedData, "eReaderKey")) {
                Logger.w(TAG, "Ignoring eReaderKey in SessionEstablishment since we "
                        + "already got this get in ReaderEngagement");
            }
        } else {
            // This is the first message. Extract eReaderKey to set up session encryption...
            DataItem decodedData = Util.cborDecode(data);
            try {
                encodedEReaderKey = Util.cborMapExtractByteString(decodedData, "eReaderKey");
            } catch (IllegalArgumentException e) {
                Logger.w(TAG, "Error extracting eReaderKey", e);
                return OptionalLong.of(Constants.SESSION_DATA_STATUS_ERROR_CBOR_DECODING);
            }
        }
        mEReaderKey = Cbor.decode(encodedEReaderKey).getAsCoseKey().getEcPublicKey();

        mEncodedSessionTranscript = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(Util.cborBuildTaggedByteString(mDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(encodedEReaderKey))
                .add(Util.cborDecode(mHandover))
                .end()
                .build().get(0));
        mSessionEncryption = new SessionEncryption(SessionEncryption.ROLE_MDOC,
                mEDeviceKey,
                mEReaderKey,
                mEncodedSessionTranscript);

        reportEReaderKeyReceived(mEReaderKey);

        if (mAlternateDeviceEngagement != null && mAlternateHandover != null) {
            mEncodedAlternateSessionTranscript = Util.cborEncode(new CborBuilder()
                    .addArray()
                    .add(Util.cborBuildTaggedByteString(mAlternateDeviceEngagement))
                    .add(Util.cborBuildTaggedByteString(encodedEReaderKey))
                    .add(Util.cborDecode(mAlternateHandover))
                    .end()
                    .build().get(0));
            mAlternateSessionEncryption = new SessionEncryption(SessionEncryption.ROLE_MDOC,
                    mEDeviceKey,
                    mEReaderKey,
                    mEncodedAlternateSessionTranscript);
        }
        return OptionalLong.empty();
    }

    private void processMessageReceived(@NonNull byte[] data) {
        Logger.dCbor(TAG, "SessionData received", data);
        OptionalLong status = ensureSessionEncryption(data);
        if (status.isPresent()) {
            mTransport.sendMessage(SessionEncryption.encodeStatus(status.getAsLong()));
            mTransport.close();
            reportError(new Error(String.format(Locale.US,
                    "Error decoding EReaderKey in SessionEstablishment, returning status %d",
                    status.getAsLong())));
            return;
        }

        SessionEncryption.DecryptedMessage decryptedMessage = null;
        try {
            decryptedMessage = mSessionEncryption.decryptMessage(data);
        } catch (RuntimeException e) {
            mTransport.sendMessage(mSessionEncryption.encryptMessage(
                    null, OptionalLong.of(Constants.SESSION_DATA_STATUS_ERROR_SESSION_ENCRYPTION)));
            mTransport.close();
            reportError(new Error("Error decrypting message from reader", e));
            return;
        }
        if (decryptedMessage == null && mAlternateSessionEncryption != null) {
            Logger.d(TAG, "Decryption failed, trying alternate");
            mSessionEncryption = mAlternateSessionEncryption;
            mEncodedSessionTranscript = mEncodedAlternateSessionTranscript;
            try {
                decryptedMessage = mSessionEncryption.decryptMessage(data);
            } catch (RuntimeException e) {
                mTransport.sendMessage(mSessionEncryption.encryptMessage(
                        null, OptionalLong.of(Constants.SESSION_DATA_STATUS_ERROR_SESSION_ENCRYPTION)));
                mTransport.close();
                reportError(new Error("Error decrypting message from reader", e));
                return;
            }
        }
        if (decryptedMessage == null) {
            Logger.d(TAG, "Decryption failed!");
            mTransport.sendMessage(mSessionEncryption.encryptMessage(
                    null, OptionalLong.of(Constants.SESSION_DATA_STATUS_ERROR_SESSION_ENCRYPTION)));
            mTransport.close();
            reportError(new Error("Error decrypting message from reader"));
            return;
        }

        // If there's data in the message, assume it's DeviceRequest (ISO 18013-5
        // currently does not define other kinds of messages).
        //
        if (decryptedMessage.getData() != null) {
            Logger.dCbor(TAG, "DeviceRequest received", decryptedMessage.getData());

            reportDeviceRequest(decryptedMessage.getData());
        } else {
            // No data, so status must be set.
            if (!decryptedMessage.getStatus().isPresent()) {
                mTransport.close();
                reportError(new Error("No data and no status in SessionData"));
            } else {
                long statusCode = decryptedMessage.getStatus().getAsLong();

                Logger.d(TAG, "Message received from reader with status: " + statusCode);

                if (statusCode == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION) {
                    mReceivedSessionTerminated = true;
                    mTransport.close();
                    reportDeviceDisconnected(false);
                } else {
                    mTransport.close();
                    reportError(new Error("Expected status code 20, got " + statusCode + " instead"));
                }
            }
        }
    }

    /**
     * Gets the session transcript.
     *
     * <p>This must not be called until {@link Listener#onEReaderKeyReceived(EcPublicKey)}
     * has been called.
     *
     * <p>See <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5</a> for the
     * definition of the bytes in the session transcript.
     *
     * @return the session transcript.
     * @throws IllegalStateException if called before a message is received from the reader.
     */
    public @NonNull
    byte[] getSessionTranscript() {
        if (mEncodedSessionTranscript == null) {
            throw new IllegalStateException("No message received from reader");
        }
        return mEncodedSessionTranscript;
    }

    /**
     * Gets the ephemeral reader key.
     *
     * <p>This must not be called until {@link Listener#onEReaderKeyReceived(EcPublicKey)}
     * has been called.
     *
     * @return the ephemeral key received by the reader.
     * @throws IllegalStateException if called before a message is received from the reader.
     */
    public @NonNull
    EcPublicKey getEReaderKey() {
        if (mEReaderKey == null) {
            throw new IllegalStateException("No message received from reader");
        }
        return mEReaderKey;
    }

    public @NonNull
    byte[] getDeviceEngagement() {
        return mDeviceEngagement;
    }

    /**
     * Send a response to the remote mdoc verifier.
     *
     * <p>This is typically called in response to the {@link Listener#onDeviceRequest(byte[])}
     * callback.
     *
     * <p>If set, <code>deviceResponseBytes</code> parameter should contain CBOR conforming to
     * <code>DeviceResponse</code> <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>. This
     * can be generated using {@link DeviceResponseGenerator}.
     *
     * At least one of {@code deviceResponseBytes} and {@code status} must be set.
     *
     * @param deviceResponseBytes the response to send or {@code null}.
     * @param status optional status code to send.
     * @throws IllegalArgumentException if {@code deviceResponseBytes} is {@code null } and {@code status} is empty.
     */
    public void sendDeviceResponse(@Nullable byte[] deviceResponseBytes, @NonNull OptionalLong status) {
        this.sendDeviceResponse(deviceResponseBytes, status, null, null);
    }

    /**
     * Send a response to the remote mdoc verifier.
     *
     * <p>This is typically called in response to the {@link Listener#onDeviceRequest(byte[])}
     * callback.
     *
     * <p>If set, <code>deviceResponseBytes</code> parameter should contain CBOR conforming to
     * <code>DeviceResponse</code> <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>. This
     * can be generated using {@link DeviceResponseGenerator}.
     *
     * At least one of {@code deviceResponseBytes} and {@code status} must be set.
     *
     * @param deviceResponseBytes the response to send or {@code null}.
     * @param status optional status code to send.
     * @param progressListener a progress listener that will subscribe to updates or <code>null</code>
     * @param progressExecutor a {@link Executor} to do the progress listener updates in, or
     *                         <code>null</code> (required if <code>progressListener</code> is
     *                         non-null
     */
    public void sendDeviceResponse(@Nullable byte[] deviceResponseBytes,
                                   @NonNull OptionalLong status,
                                   @Nullable TransmissionProgressListener progressListener,
                                   @Nullable Executor progressExecutor) {
        byte[] sessionDataMessage = null;
        if (deviceResponseBytes == null) {
            if (!status.isPresent()) {
                throw new IllegalArgumentException("deviceResponseBytes and status cannot both be null");
            }
            Logger.d(TAG,
                    String.format(Locale.US, "sendDeviceResponse: status is %d and data is unset",
                            status.getAsLong()));
            sessionDataMessage = SessionEncryption.encodeStatus(status.getAsLong());
        } else {
            if (status.isPresent()) {
                Logger.dCbor(TAG,
                        String.format(Locale.US, "sendDeviceResponse: status is %d and data is",
                                status.getAsLong()), deviceResponseBytes);
            } else {
                Logger.dCbor(TAG, "sendDeviceResponse: status is unset and data is",
                        deviceResponseBytes);
            }
            sessionDataMessage = mSessionEncryption.encryptMessage(deviceResponseBytes, status);
        }
        if (mTransport == null) {
            Logger.d(TAG, "sendDeviceResponse: ignoring because transport is unset");
            return;
        }
        mTransport.sendMessage(sessionDataMessage, progressListener, progressExecutor);
    }

    /**
     * Stops the presentation and shuts down the transport.
     *
     * <p>This does not send a message to terminate the session. Applications should use
     * {@link #sendTransportSpecificTermination()} or
     * {@link #sendDeviceResponse(byte[], OptionalLong)}
     * with status {@link Constants#SESSION_DATA_STATUS_SESSION_TERMINATION} to do that.
     *
     * <p>No callbacks will be done on a listener after calling this.
     *
     * <p>This method is idempotent so it is safe to call multiple times.
     */
    public void disconnect() {
        mInhibitCallbacks = true;
        if (mTransport == null) {
            Logger.d(TAG, "disconnect: ignoring call because transport is unset");
            return;
        }
        Logger.d(TAG, "disconnect: closing transport");
        mTransport.close();
        mTransport = null;
    }

    /**
     * Returns whether transport specific termination is available for the current connection.
     *
     * @return <code>true</code> if transport specific termination is available, <code>false</code>
     *   if not or if not connected.
     */
    public boolean isTransportSpecificTerminationSupported() {
        if (mTransport == null) {
            return false;
        }
        return mTransport.supportsTransportSpecificTerminationMessage();
    }

    /**
     * Sends a transport-specific termination message.
     *
     * <p>Transport-specific session terminated is only supported for certain device-retrieval
     * methods. Use {@link #isTransportSpecificTerminationSupported()} to figure out if it
     * is supported for the current connection.
     *
     * <p>If a session is not established or transport-specific session termination is not
     * supported this is a no-op.
     */
    public void sendTransportSpecificTermination() {
        if (mTransport == null) {
            Logger.w(TAG, "No current transport");
            return;
        }

        if (!mTransport.supportsTransportSpecificTerminationMessage()) {
            Logger.w(TAG, "Current transport does not support transport-specific termination message");
            return;
        }

        Logger.d(TAG, "Sending transport-specific termination message");
        mTransport.sendTransportSpecificTerminationMessage();
    }

    public long getScanningTimeMillis() {
        if (mTransport instanceof DataTransportBle) {
            return ((DataTransportBle) mTransport).getScanningTimeMillis();
        }
        return 0;
    }

    /**
     * Interface for listening to messages from the remote verifier device.
     *
     * <p>The {@link Listener#onError(Throwable)} callback can be called at any time - for
     * example - if the remote verifier disconnects without using session termination or if the
     * underlying transport encounters an unrecoverable error.
     */
    public interface Listener {

        /**
         * Called when the reader ephemeral key has been received.
         *
         * <p>When this is called, it's safe to call {@link #getSessionTranscript()} on
         * the {@link DeviceRetrievalHelper}.
         *
         * @param eReaderKey the ephemeral reader key.
         */
        void onEReaderKeyReceived(@NonNull EcPublicKey eReaderKey);

        /**
         * Called when the remote verifier device sends a request.
         *
         * <p>The <code>deviceRequestBytes</code> parameter contains the bytes of
         * <code>DeviceRequest</code> <a href="http://cbor.io/">CBOR</a>
         * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
         *
         * <p>The application should use {@link DeviceRequestParser} to parse the request and
         * {@link DeviceResponseGenerator} to generate a response to be sent using
         * {@link #sendDeviceResponse(byte[], OptionalLong)}.
         *
         * @param deviceRequestBytes     the device request.
         */
        void onDeviceRequest(@NonNull byte[] deviceRequestBytes);

        /**
         * Called when the remote verifier device disconnects normally, that is
         * using the session termination functionality in the underlying protocols.
         *
         * <p>If this is called the application should call {@link #disconnect()} and the
         * object should no longer be used.
         *
         * @param transportSpecificTermination set to <code>true</code> if the termination
         *                                     mechanism used was transport specific.
         */
        void onDeviceDisconnected(boolean transportSpecificTermination);

        /**
         * Called when an unrecoverable error happens, for example if the remote device
         * disconnects unexpectedly (e.g. without first sending a session termination request).
         *
         * <p>If this is called the application should call {@link #disconnect()} and the
         * object should no longer be used.
         *
         * @param error the error.
         */
        void onError(@NonNull Throwable error);
    }

    /**
     * Builder for {@link DeviceRetrievalHelper}.
     */
    public static class Builder {
        DeviceRetrievalHelper mHelper;

        /**
         * Create a new Builder for {@link DeviceRetrievalHelper}.
         *
         * <p>Use {@link #useForwardEngagement(DataTransport, byte[], byte[])} or
         * {@link #useReverseEngagement(DataTransport, byte[], List)} to specifiy which
         * kind of engagement will be used. At least one of these must be used.
         *
         * @param context the application context.
         * @param listener a listener.
         * @param executor a {@link Executor} to use with the listener.
         * @throws IllegalStateException if {@link Executor} is {@code null} for a non-{@code null}
         * listener.
         */
        public Builder(@NonNull Context context,
                       @Nullable Listener listener,
                       @Nullable Executor executor,
                       @NonNull EcPrivateKey eDeviceKey) {
            mHelper = new DeviceRetrievalHelper();
            mHelper.mContext = context;
            if (listener != null && executor == null) {
                throw new IllegalStateException("Cannot have non-null listener with null executor");
            }
            mHelper.mListener = listener;
            mHelper.mListenerExecutor = executor;
            mHelper.mEDeviceKey = eDeviceKey;
        }

        /**
         * Configures the helper to use normal engagement.
         *
         * <p>Applications can use this together with {@link QrEngagementHelper} and
         * {@link NfcEngagementHelper}.
         *
         * @param transport the transport the mdoc reader used to connect with.
         * @param deviceEngagement the bytes of the <code>DeviceEngagement</code> CBOR.
         * @param handover the bytes of the <code>Handover</code> CBOR.
         * @return the builder.
         */
        public @NonNull Builder useForwardEngagement(@NonNull DataTransport transport,
                                                     @NonNull byte[] deviceEngagement,
                                                     @NonNull byte[] handover) {
            mHelper.mTransport = transport;
            mHelper.mDeviceEngagement = deviceEngagement;
            mHelper.mHandover = handover;
            return this;
        }

        /**
         * Tells the helper about secondary/alternate engagement mechanisms.
         *
         * <p>This is useful if using multiple forward engagement mechanisms at the same time, for
         * example QR and NFC.
         *
         * @param deviceEngagement the bytes of the <code>DeviceEngagement</code> CBOR.
         * @param handover the bytes of the <code>Handover</code> CBOR.
         * @return the builder
         */
        public @NonNull Builder addAlternateForwardEngagement(@Nullable byte[] deviceEngagement,
                                                              @Nullable byte[] handover) {
            if (mHelper.mDeviceEngagement == null) {
                throw new IllegalStateException("Helper isn't configured to use forward engagement");
            }
            // TODO: consider if there's an use-case for calling this multiple times
            if (mHelper.mAlternateDeviceEngagement != null) {
                throw new IllegalStateException("Can only add a single alternate engagement");
            }
            mHelper.mAlternateDeviceEngagement = deviceEngagement;
            mHelper.mAlternateHandover = handover;
            return this;
        }

        /**
         * Configures the helper to use reverse engagement.
         *
         * @param transport the transport to use.
         * @param readerEngagement the bytes of the <code>ReaderEngagement</code> CBOR.
         * @param originInfos a set of origin infos describing how reader engagement was obtained.
         * @return the builder.
         */
        public @NonNull Builder useReverseEngagement(@NonNull DataTransport transport,
                                                     @Nullable byte[] readerEngagement,
                                                     List<OriginInfo> originInfos) {
            mHelper.mTransport = transport;
            mHelper.mReverseEngagementReaderEngagement = readerEngagement;
            mHelper.mReverseEngagementOriginInfos = originInfos;
            return this;
        }

        /**
         * Builds the {@link DeviceRetrievalHelper} and starts presentation.
         *
         * @return the helper, ready to be used.
         * @throws IllegalStateException if engagement direction hasn't been configured.
         */
        public @NonNull
        DeviceRetrievalHelper build() {
            if (mHelper.mTransport == null) {
                throw new IllegalStateException("Neither forward nor reverse engagement configured");
            }
            mHelper.start();
            return mHelper;
        }

    }
}
