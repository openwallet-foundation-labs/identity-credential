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
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.Constants.LoggingFlag;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.util.OptionalLong;
import java.util.concurrent.Executor;

/**
 * Helper used for establishing engagement with, interacting with, and presenting credentials to a
 * remote <em>mdoc verifier</em> device.
 *
 * <p>This class implements the interface between an <em>mdoc</em> and <em>mdoc verifier</em> using
 * the connection setup and device retrieval interfaces defined in
 * <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5</a>.
 *
 * <p>Supported device engagement methods include QR code and NFC static handover. Support for
 * NFC negotiated handover may be added in a future release.
 *
 * <p>Supported device retrieval transfer methods include BLE (both <em>mdoc central client
 * mode</em> and <em>mdoc peripheral server</em>), Wifi Aware, and NFC.
 *
 * <p>Additional device engagement and device retrieval methods may be added in the future.
 *
 * <p>As with {@link PresentationSession}, instances of this class are only good for a single
 * session with a remote verifier. Once a session ends (indicated by e.g.
 * {@link Listener#onDeviceDisconnected(boolean)} or {@link Listener#onError(Throwable)} the
 * object should no longer be used.
 *
 * <p>For NFC device engagement the application should use a {@link HostApduService}
 * registered for AID <code>D2 76 00 00 85 01 01</code> (NFC Type 4 Tag) and for NFC data
 * transfer it should also be registered for AID <code>A0 00 00 02 48 04 00</code> (as per
 * ISO/IEC 18013-5 section 8.3.3.1.2). In both cases
 * {@link HostApduService#processCommandApdu(byte[], Bundle)} calls should be forwarded to
 * {@link #nfcProcessCommandApdu(byte[])} and
 * {@link HostApduService#onDeactivated(int)} calls should be forwarded to
 * {@link #nfcOnDeactivated(HostApduService, int)}. The application should also use
 * {@link #setNfcResponder(NfcApduRouter)} to set up a {@link NfcApduRouter} to send
 * response APDUs back to the reader.
 *
 * <p>Unlike {@link IdentityCredentialStore}, {@link IdentityCredential},
 * {@link WritableIdentityCredential}, and {@link PresentationSession} this class is never backed
 * by secure hardware and is entirely implemented in the Jetpack. The class does however depend
 * on data returned by
 * {@link PresentationSession#getCredentialData(String, CredentialDataRequest)} which may be
 * backed by secure hardware.
 */
// Suppress with NotCloseable since we actually don't hold any resources needing to be
// cleaned up at object finalization time.
@SuppressWarnings("NotCloseable")
public class PresentationHelper {
    private static final String TAG = "PresentationHelper";

    private final KeyPair mEphemeralKeyPair;
    private final Context mContext;
    private final byte[] mDeviceEngagement;
    private final byte[] mHandover;
    SessionEncryptionDevice mSessionEncryption;
    private final byte[] mAlternateDeviceEngagement;
    private final byte[] mAlternateHandover;
    SessionEncryptionDevice mAlternateSessionEncryption;
    PresentationSession mPresentationSession;
    Listener mListener;
    Executor mDeviceRequestListenerExecutor;
    DataTransport mTransport;

    boolean mReceivedSessionTerminated;
    @LoggingFlag
    int mLoggingFlags = 0;

    private boolean mInhibitCallbacks;
    private boolean mUseTransportSpecificSessionTermination;
    private boolean mSendSessionTerminationMessage;
    Util.Logger mLog;
    private NfcApduRouter mNfcRouter;

    /**
     * Creates a new {@link PresentationHelper}.
     *
     * @param context             the application context.
     * @param presentationSession a {@link PresentationSession} instance.
     */
    public PresentationHelper(@NonNull Context context,
                              @NonNull PresentationSession presentationSession,
                              @Nullable DataTransport transport, //TODO: make it NonNull
                              @NonNull byte[] deviceEngagement,
                              @NonNull byte[] handover,
                              @Nullable byte[] alternateDeviceEngagement,
                              @Nullable byte[] alternateHandover) {
        mPresentationSession = presentationSession;
        mContext = context;
        mEphemeralKeyPair = mPresentationSession.getEphemeralKeyPair();
        mTransport = transport;
        mDeviceEngagement = deviceEngagement;
        mHandover = handover;
        mAlternateDeviceEngagement = alternateDeviceEngagement;
        mAlternateHandover = alternateHandover;
        mLog = new Util.Logger(TAG, 0);

        mSessionEncryption = new SessionEncryptionDevice(
                mEphemeralKeyPair.getPrivate(),
                mDeviceEngagement,
                mHandover);

        if (mAlternateDeviceEngagement != null && mAlternateHandover != null) {
            mAlternateSessionEncryption = new SessionEncryptionDevice(
                    mEphemeralKeyPair.getPrivate(),
                    mAlternateDeviceEngagement,
                    mAlternateHandover);
        }
    }

    /**
     * Configures the amount of logging messages to emit.
     *
     * <p>By default no logging messages are emitted except for warnings and errors. Applications
     * use this with caution as the emitted log messages may contain PII and secrets.
     *
     * @param loggingFlags One or more logging flags e.g. {@link Constants#LOGGING_FLAG_INFO}.
     */
    public void setLoggingFlags(@LoggingFlag int loggingFlags) {
        mLoggingFlags = loggingFlags;
        mLog.setLoggingFlags(loggingFlags);
    }

    // Note: The report*() methods are safe to call from any thread.

    void reportDeviceRequest(@NonNull byte[] deviceRequestBytes) {
        mLog.info("reportDeviceRequest: deviceRequestBytes: " + deviceRequestBytes.length + " bytes");
        final Listener listener = mListener;
        final Executor executor = mDeviceRequestListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onDeviceRequest(deviceRequestBytes));
        }
    }

    void reportDeviceDisconnected(boolean transportSpecificTermination) {
        mLog.info("reportDeviceDisconnected: transportSpecificTermination: "
                + transportSpecificTermination);
        final Listener listener = mListener;
        final Executor executor = mDeviceRequestListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onDeviceDisconnected(
                    transportSpecificTermination));
        }
    }

    void reportError(@NonNull Throwable error) {
        mLog.info("reportError: error: ", error);
        final Listener listener = mListener;
        final Executor executor = mDeviceRequestListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onError(error));
        }
    }

    public void start() {
        mTransport.setListener(new DataTransport.Listener() {
            @Override
            public void onListeningSetupCompleted(@Nullable DataRetrievalAddress address) {
                mLog.info("onListeningSetupCompleted");
                reportError(new Error("Unexpected onListeningSetupCompleted"));
            }

            @Override
            public void onListeningPeerConnecting() {
                mLog.info("onListeningPeerConnecting");
                reportError(new Error("Unexpected onListeningPeerConnecting"));
            }

            @Override
            public void onListeningPeerConnected() {
                mLog.info("onListeningPeerConnecting");
                reportError(new Error("Unexpected onListeningPeerConnected"));
            }

            @Override
            public void onListeningPeerDisconnected() {
                mLog.info("onListeningPeerDisconnected");
                mTransport.close();
                if (!mReceivedSessionTerminated) {
                    reportError(new Error("Peer disconnected without proper session termination"));
                } else {
                    reportDeviceDisconnected(false);
                }
            }

            @Override
            public void onConnectionResult(@Nullable Throwable error) {
                mLog.info("onConnectionResult");
                if (error != null) {
                    throw new IllegalStateException("Unexpected onConnectionResult callback", error);
                }
                throw new IllegalStateException("Unexpected onConnectionResult callback");
            }

            @Override
            public void onConnectionDisconnected() {
                mLog.info("onConnectionDisconnected");
                throw new IllegalStateException("Unexpected onConnectionDisconnected callback");
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
                mLog.info("Received transport-specific session termination");
                mReceivedSessionTerminated = true;
                mTransport.close();
                reportDeviceDisconnected(true);
            }

        }, mDeviceRequestListenerExecutor);

        byte[] data = mTransport.getMessage();
        if (data != null) {
            processMessageReceived(data);
        }
    }

    private void processMessageReceived(@NonNull byte[] data) {
        if (mLog.isTransportVerboseEnabled()) {
            Util.dumpHex(TAG, "SessionData", data);
        }
        Pair<byte[], OptionalLong> decryptedMessage = null;
        try {
            decryptedMessage = mSessionEncryption.decryptMessageFromReader(data);
        } catch (RuntimeException e) {
            mTransport.close();
            reportError(new Error("Error decrypting message from reader", e));
            return;
        }
        if (decryptedMessage == null && mAlternateSessionEncryption != null) {
            mLog.info("Decryption failed, trying alternate");
            mSessionEncryption = mAlternateSessionEncryption;
            try {
                decryptedMessage = mSessionEncryption.decryptMessageFromReader(data);
            } catch (RuntimeException e) {
                mTransport.close();
                reportError(new Error("Error decrypting message from reader", e));
                return;
            }
        }
        if (decryptedMessage == null) {
            mLog.info("Decryption failed!");
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
                mPresentationSession.setSessionTranscript(
                        mSessionEncryption.getSessionTranscript());
                try {
                    mPresentationSession.setReaderEphemeralPublicKey(
                            mSessionEncryption.getEphemeralReaderPublicKey());
                } catch (InvalidKeyException e) {
                    mTransport.close();
                    reportError(new Error("Reader ephemeral public key is invalid", e));
                    return;
                }
            }

            if (mLog.isSessionEnabled()) {
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

                mLog.session("Message received from reader with status: " + statusCode);

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
        if (mSessionEncryption == null) {
            throw new IllegalStateException("No message received from verifier");
        }
        return mSessionEncryption.getSessionTranscript();
    }

    public @NonNull
    byte[] getDeviceEngagement() {
        return mDeviceEngagement;
    }

    /**
     * Send a response to the remote mdoc verifier.
     *
     * <p>This is typically called in response to the {@link Listener#onDeviceRequest(int, byte[])}
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
     * <p>This is typically called in response to the {@link Listener#onDeviceRequest(int, byte[])}
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
        if (mLog.isSessionEnabled()) {
            Util.dumpHex(TAG, "Sending DeviceResponse", deviceResponseBytes);
        }
        byte[] encryptedData =
            mSessionEncryption.encryptMessageToReader(deviceResponseBytes, OptionalLong.empty());
        mTransport.sendMessage(encryptedData, progressListener, progressExecutor);
    }

    /**
     * Sets the listener for listening to events from the remote mdoc verifier device.
     *
     * <p>This may be called multiple times but only the most recent listener will be used.
     *
     * @param listener the listener or <code>null</code> to stop listening.
     * @param executor a {@link Executor} to do the call in or <code>null</code> if
     *                 <code>listener</code> is <code>null</code>.
     * @throws IllegalStateException if {@link Executor} is {@code null} for a non-{@code null}
     * listener.
     */
    public void setListener(@Nullable Listener listener, @Nullable Executor executor) {
        if (listener != null && executor == null) {
            throw new IllegalStateException("Cannot have non-null listener with null executor");
        }
        mListener = listener;
        mDeviceRequestListenerExecutor = executor;
    }

    /**
     * Stops the presentation, shuts down all transports used, and stops listening on
     * transports previously brought into a listening state using
     * {@link #startListening(DataRetrievalListenerConfiguration)}.
     *
     * <p>If connected to a mdoc verifier also sends a session termination message prior to
     * disconnecting if applicable. See {@link #setSendSessionTerminationMessage(boolean)} and
     * {@link #setUseTransportSpecificSessionTermination(boolean)} for how to configure this.
     *
     * <p>No callbacks will be done on a listener registered with
     * {@link #setListener(Listener, Executor)} after calling this.
     *
     * <p>This method is idempotent so it is safe to call multiple times.
     */
    public void disconnect() {
        mInhibitCallbacks = true;
        if (mTransport != null) {
            // Only send session termination message if the session was actually established.
            boolean sessionEstablished = (mSessionEncryption.getNumMessagesDecrypted() > 0);
            if (mSendSessionTerminationMessage && sessionEstablished) {
                if (mUseTransportSpecificSessionTermination &&
                        mTransport.supportsTransportSpecificTerminationMessage()) {
                    mLog.info("Sending transport-specific termination message");
                    mTransport.sendTransportSpecificTerminationMessage();
                } else {
                    mLog.info("Sending generic session termination message");
                    byte[] sessionTermination = mSessionEncryption.encryptMessageToReader(
                            null, OptionalLong.of(20));
                    mTransport.sendMessage(sessionTermination);
                }
            } else {
                mLog.info("Not sending session termination message");
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
     * <p>The callbacks on this interface are called in the following order:
     * <ul>
     *     <li>{@link Listener#onDeviceEngagementReady()} is called when the transports requested
     *     via {@link #startListening(DataRetrievalListenerConfiguration)} have all been set up.
     *     At this point it's safe for the application to call
     *     {@link #getDeviceEngagementForQrCode()} to get the data to put in a QR code which can
     *     be displayed in the application.
     *     <li>{@link Listener#onEngagementDetected()} is called at the first sign of engagement
     *     with a remote verifier device when NFC engagement is used. It is never called if QR
     *     engagement is used.
     *     <li>{@link Listener#onDeviceConnecting()} is called at the first sign of a remote
     *     verifier device. Depending on the transport in use it could be several seconds until
     *     the next callback is called.
     *     <li>{@link Listener#onDeviceConnected()} is called when a remote verifier device has
     *     connected.
     *     <li>{@link Listener#onDeviceRequest(int, byte[])} is called when a request has been
     *     received from the remote verifier. It may be called multiple times.
     *     For each request the application is expected to process the request and call
     *     {@link #sendDeviceResponse(byte[])} or {@link #disconnect()}.
     *     <li>{@link Listener#onDeviceDisconnected(boolean)} is called when the remote verifier
     *     disconnects properly.
     * </ul>
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
         * @param deviceEngagementMethod the engagement method used by the device
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
}
