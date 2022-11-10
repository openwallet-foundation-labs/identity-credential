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

import android.content.Context;

import androidx.core.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
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
 * <p>This class implements the interface between an <em>mdoc</em> and <em>mdoc verifier</em> using
 * the connection setup and device retrieval interfaces defined in
 * <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5</a>.
 *
 * <p>Reverse engagement as per drafts of 18013-7 and 23220-4 is supported. These protocols
 * are not finalized so should only be used for testing.
 *
 * <p>As with {@link PresentationSession}, instances of this class are only good for a single
 * session with a remote reader. Once a session ends (indicated by e.g.
 * {@link Listener#onDeviceDisconnected(boolean)} or {@link Listener#onError(Throwable)} the
 * object should no longer be used.
 *
 * <p>Unlike {@link IdentityCredentialStore}, {@link IdentityCredential},
 * {@link WritableIdentityCredential}, and {@link PresentationSession} this class is never backed
 * by secure hardware and is entirely implemented in the library. The class does however depend
 * on data returned by
 * {@link PresentationSession#getCredentialData(String, CredentialDataRequest)} which may be
 * backed by secure hardware.
 */
// Suppress with NotCloseable since we actually don't hold any resources needing to be
// cleaned up at object finalization time.
@SuppressWarnings("NotCloseable")
public class PresentationHelper {
    private static final String TAG = "PresentationHelper";

    private KeyPair mEphemeralKeyPair;
    private Context mContext;
    private PublicKey mEReaderKey;

    private byte[] mDeviceEngagement;
    private byte[] mHandover;
    SessionEncryptionDevice mSessionEncryption;
    private byte[] mEncodedSessionTranscript;

    private byte[] mAlternateDeviceEngagement;
    private byte[] mAlternateHandover;
    SessionEncryptionDevice mAlternateSessionEncryption;
    private byte[] mEncodedAlternateSessionTranscript;

    PresentationSession mPresentationSession;
    Listener mListener;
    Executor mListenerExecutor;
    DataTransport mTransport;

    boolean mReceivedSessionTerminated;

    private boolean mInhibitCallbacks;
    private boolean mUseTransportSpecificSessionTermination;
    private boolean mSendSessionTerminationMessage;
    private byte[] mReverseEngagementReaderEngagement;
    private List<OriginInfo> mReverseEngagementOriginInfos;
    private byte[] mReverseEngagementEncodedEReaderKey;

    PresentationHelper() {}

    // Note: The report*() methods are safe to call from any thread.

    void reportDeviceRequest(@NonNull byte[] deviceRequestBytes) {
        Logger.d(TAG, "reportDeviceRequest: deviceRequestBytes: " + deviceRequestBytes.length + " bytes");
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onDeviceRequest(deviceRequestBytes));
        }
    }

    void reportDeviceDisconnected(boolean transportSpecificTermination) {
        Logger.d(TAG, "reportDeviceDisconnected: transportSpecificTermination: "
                + transportSpecificTermination);
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onDeviceDisconnected(
                    transportSpecificTermination));
        }
    }

    void reportError(@NonNull Throwable error) {
        Logger.d(TAG, "reportError: error: ", error);
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onError(error));
        }
    }

    void start() {
        mTransport.setListener(new DataTransport.Listener() {
            @Override
            public void onConnectionMethodReady() {
                Logger.d(TAG, "onConnectionMethodReady");
            }

            @Override
            public void onConnecting() {
                Logger.d(TAG, "onConnecting");
            }

            @Override
            public void onDisconnected() {
                Logger.d(TAG, "onDisconnected");
                mTransport.close();
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
                            mEphemeralKeyPair.getPublic(),
                            EngagementGenerator.ENGAGEMENT_VERSION_1_1);
                    for (OriginInfo originInfo : mReverseEngagementOriginInfos) {
                        generator.addOriginInfo(originInfo);
                    }
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

                    if (Logger.isDebugEnabled()) {
                        Util.dumpHex(TAG, "DeviceEngagement for reverse engagement", messageData);
                    }
                    mTransport.sendMessage(messageData);
                } else {
                    throw new IllegalStateException("Unexpected onConnected callback");
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                mTransport.close();
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
                mTransport.close();
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
            byte[] encodedEDeviceKeyBytes = Util.cborEncode(Util.cborBuildTaggedByteString(
                    Util.cborEncode(Util.cborBuildCoseKey(mEphemeralKeyPair.getPublic()))));
            mTransport.setEDeviceKeyBytes(encodedEDeviceKeyBytes);
            mTransport.connect();
        }

    }

    private void ensureSessionEncryption(@NonNull byte[] data) {
        if (mSessionEncryption != null) {
            return;
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
            encodedEReaderKey = Util.cborMapExtractByteString(decodedData, "eReaderKey");
        }
        mEReaderKey = Util.coseKeyDecode(Util.cborDecode(encodedEReaderKey));

        mEncodedSessionTranscript = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(Util.cborBuildTaggedByteString(mDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(encodedEReaderKey))
                .add(Util.cborDecode(mHandover))
                .end()
                .build().get(0));
        mSessionEncryption = new SessionEncryptionDevice(mEphemeralKeyPair.getPrivate(),
                mEReaderKey,
                mEncodedSessionTranscript);

        if (mAlternateDeviceEngagement != null && mAlternateHandover != null) {
            mEncodedAlternateSessionTranscript = Util.cborEncode(new CborBuilder()
                    .addArray()
                    .add(Util.cborBuildTaggedByteString(mAlternateDeviceEngagement))
                    .add(Util.cborBuildTaggedByteString(encodedEReaderKey))
                    .add(Util.cborDecode(mAlternateHandover))
                    .end()
                    .build().get(0));
            mAlternateSessionEncryption = new SessionEncryptionDevice(mEphemeralKeyPair.getPrivate(),
                    mEReaderKey,
                    mEncodedAlternateSessionTranscript);
        }
    }

    private void processMessageReceived(@NonNull byte[] data) {
        if (Logger.isDebugEnabled()) {
            Util.dumpHex(TAG, "SessionData", data);
        }
        ensureSessionEncryption(data);
        Pair<byte[], OptionalLong> decryptedMessage = null;
        try {
            decryptedMessage = mSessionEncryption.decryptMessageFromReader(data);
        } catch (RuntimeException e) {
            mTransport.close();
            reportError(new Error("Error decrypting message from reader", e));
            return;
        }
        if (decryptedMessage == null && mAlternateSessionEncryption != null) {
            Logger.d(TAG, "Decryption failed, trying alternate");
            mSessionEncryption = mAlternateSessionEncryption;
            mEncodedSessionTranscript = mEncodedAlternateSessionTranscript;
            try {
                decryptedMessage = mSessionEncryption.decryptMessageFromReader(data);
            } catch (RuntimeException e) {
                mTransport.close();
                reportError(new Error("Error decrypting message from reader", e));
                return;
            }
        }
        if (decryptedMessage == null) {
            Logger.d(TAG, "Decryption failed!");
            mTransport.close();
            reportError(new Error("Error decrypting message from reader"));
            return;
        }

        // If there's data in the message, assume it's DeviceRequest (ISO 18013-5
        // currently does not define other kinds of messages).
        //
        if (decryptedMessage.first != null) {
            // Only initialize the PresentationSession a single time.
            //
            if (mSessionEncryption.getNumMessagesEncrypted() == 0) {
                mPresentationSession.setSessionTranscript(mEncodedSessionTranscript);
                try {
                    mPresentationSession.setReaderEphemeralPublicKey(mEReaderKey);
                } catch (InvalidKeyException e) {
                    mTransport.close();
                    reportError(new Error("Reader ephemeral public key is invalid", e));
                    return;
                }
            }

            if (Logger.isDebugEnabled()) {
                Util.dumpHex(TAG, "Received DeviceRequest", decryptedMessage.first);
            }

            reportDeviceRequest(decryptedMessage.first);
        } else {
            // No data, so status must be set.
            if (!decryptedMessage.second.isPresent()) {
                mTransport.close();
                reportError(new Error("No data and no status in SessionData"));
            } else {
                long statusCode = decryptedMessage.second.getAsLong();

                Logger.d(TAG, "Message received from reader with status: " + statusCode);

                if (statusCode == 20) {
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
     * <p>This must not be called until a message has been received from the mdoc verifier.
     *
     * <p>See <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5</a> for the
     * definition of the bytes in the session transcript.
     *
     * @return the session transcript.
     * @throws IllegalStateException if called before a message is received from the verifier.
     */
    public @NonNull
    byte[] getSessionTranscript() {
        if (mEncodedSessionTranscript == null) {
            throw new IllegalStateException("No message received from verifier");
        }
        return mEncodedSessionTranscript;
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
     * <p>The <code>deviceResponseBytes</code> parameter should contain CBOR conforming to
     * <code>DeviceResponse</code> <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>. This
     * can be generated using {@link DeviceResponseGenerator}.
     *
     * @param deviceResponseBytes the response to send.
     */
    public void sendDeviceResponse(@NonNull byte[] deviceResponseBytes) {
        this.sendDeviceResponse(deviceResponseBytes, null, null);
    }

    /**
     * Send a response to the remote mdoc verifier.
     *
     * <p>This is typically called in response to the {@link Listener#onDeviceRequest(byte[])}
     * callback.
     *
     * <p>The <code>deviceResponseBytes</code> parameter should contain CBOR conforming to
     * <code>DeviceResponse</code> <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>. This
     * can be generated using {@link DeviceResponseGenerator}.
     *
     * @param deviceResponseBytes the response to send.
     * @param progressListener a progress listener that will subscribe to updates or <code>null</code>
     * @param progressExecutor a {@link Executor} to do the progress listener updates in, or
     *                         <code>null</code> (required if <code>progressListener</code> is
     *                         non-null
     */
    public void sendDeviceResponse(@NonNull byte[] deviceResponseBytes,
        @Nullable TransmissionProgressListener progressListener,
        @Nullable Executor progressExecutor) {
        if (Logger.isDebugEnabled()) {
            Util.dumpHex(TAG, "Sending DeviceResponse", deviceResponseBytes);
        }
        byte[] encryptedData =
            mSessionEncryption.encryptMessageToReader(deviceResponseBytes, OptionalLong.empty());
        mTransport.sendMessage(encryptedData, progressListener, progressExecutor);
    }

    /**
     * Stops the presentation and shuts down the transport.
     *
     * <p>If connected to a mdoc verifier also sends a session termination message prior to
     * disconnecting if applicable. See {@link #setSendSessionTerminationMessage(boolean)} and
     * {@link #setUseTransportSpecificSessionTermination(boolean)} for how to configure this.
     *
     * <p>No callbacks will be done on a listener after calling this.
     *
     * <p>This method is idempotent so it is safe to call multiple times.
     */
    public void disconnect() {
        mInhibitCallbacks = true;
        if (mTransport != null) {
            // Only send session termination message if the session was actually established.
            boolean sessionEstablished = (mSessionEncryption != null)
                            && (mSessionEncryption.getNumMessagesDecrypted() > 0);
            if (mSendSessionTerminationMessage && sessionEstablished) {
                if (mUseTransportSpecificSessionTermination &&
                        mTransport.supportsTransportSpecificTerminationMessage()) {
                    Logger.d(TAG, "Sending transport-specific termination message");
                    mTransport.sendTransportSpecificTerminationMessage();
                } else {
                    Logger.d(TAG, "Sending generic session termination message");
                    byte[] sessionTermination = mSessionEncryption.encryptMessageToReader(
                            null, OptionalLong.of(20));
                    mTransport.sendMessage(sessionTermination);
                }
            } else {
                Logger.d(TAG, "Not sending session termination message");
            }
            mTransport.close();
            mTransport = null;
        }
    }

    /**
     * Sets whether to use transport-specific session termination.
     *
     * <p>By default this is set to <code>false</code>.
     *
     * <p>As per <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5</a>
     * transport-specific session-termination is currently only supported for BLE. The
     * {@link #isTransportSpecificTerminationSupported()} method can be used to determine whether
     * it's available for the current transport. If the current transport does not support
     * the feature, then this method is a noop.
     *
     * @param useTransportSpecificSessionTermination Whether to use transport-specific session
     */
    public void setUseTransportSpecificSessionTermination(
            boolean useTransportSpecificSessionTermination) {
        mUseTransportSpecificSessionTermination = useTransportSpecificSessionTermination;
    }

    /**
     * Returns whether transport specific termination is available for the current connection.
     *
     * See {@link #setUseTransportSpecificSessionTermination(boolean)} for more information about
     * what transport specific session termination is.
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
     * Sets whether to send session termination message.
     *
     * <p>This controls whether a session termination message is sent when
     * {@link #disconnect()} is called. Most applications would want to do
     * this as it is required by
     * <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5</a>.
     *
     * <p>By default this is set to <code>true</code>.
     *
     * @param sendSessionTerminationMessage Whether to send session termination message.
     */
    public void setSendSessionTerminationMessage(
            boolean sendSessionTerminationMessage) {
        mSendSessionTerminationMessage = sendSessionTerminationMessage;
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
         * Called when the remote verifier device sends a request.
         *
         * <p>The <code>deviceRequestBytes</code> parameter contains the bytes of
         * <code>DeviceRequest</code> <a href="http://cbor.io/">CBOR</a>
         * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
         *
         * <p>The application should use {@link DeviceRequestParser} to parse the request and
         * {@link DeviceResponseGenerator} to generate a response to be sent using
         * {@link #sendDeviceResponse(byte[])}. Alternatively, the application may also choose to
         * terminate the session using {@link #disconnect()}.
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
     * Builder for {@link PresentationHelper}.
     */
    public static class Builder {
        PresentationHelper mHelper;

        /**
         * Create a new Builder for {@link PresentationHelper}.
         *
         * <p>Use {@link #useForwardEngagement(DataTransport, byte[], byte[])} or
         * {@link #useReverseEngagement(DataTransport, byte[], List)} to specifiy which
         * kind of engagement will be used. At least one of these must be used.
         *
         * @param context
         * @param listener the listener or <code>null</code> to stop listening.
         * @param executor a {@link Executor} to do the call in or <code>null</code> if
         *                 <code>listener</code> is <code>null</code>.
         * @param session a {@link PresentationSession}.
         * @throws IllegalStateException if {@link Executor} is {@code null} for a non-{@code null}
         * listener.
         */
        public Builder(@NonNull Context context,
                       @Nullable Listener listener,
                       @Nullable Executor executor,
                       @NonNull PresentationSession session) {
            mHelper = new PresentationHelper();
            mHelper.mContext = context;
            if (listener != null && executor == null) {
                throw new IllegalStateException("Cannot have non-null listener with null executor");
            }
            mHelper.mListener = listener;
            mHelper.mListenerExecutor = executor;
            mHelper.mPresentationSession = session;
            mHelper.mEphemeralKeyPair = mHelper.mPresentationSession.getEphemeralKeyPair();
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
         * @return
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
         * Builds the {@link PresentationHelper} and starts presentation.
         *
         * @return the helper, ready to be used.
         */
        public @NonNull PresentationHelper build() {
            if (mHelper.mTransport == null) {
                throw new IllegalStateException("Neither forward nor reverse engagement configured");
            }
            mHelper.start();
            return mHelper;
        }

    }
}
