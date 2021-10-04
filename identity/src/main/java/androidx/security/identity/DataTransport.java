/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.security.identity;

import android.content.Context;
import android.nfc.NdefRecord;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

/**
 * @hide
 *
 * Abstraction for data transfer between prover and verifier devices.
 *
 * <p>The data transfer is packetized, that is, data is delivered at the same
 * granularity as it is sent. For example, if {@link #sendMessage(byte[])} is used to send
 * <code>N</code> bytes then this blob is what the remote peer will receive in the
 * {@link Listener#onMessageReceived(byte[])} callback.
 *
 * <p>Instances constructed from subclasses deriving from this class are inert when constructed,
 * that is, they don't actually do anything. This constraint exists to easily facilitate
 * factory-patterns.
 *
 * <p>If an unrecoverable error is detected, this is conveyed using the
 * {@link Listener#onError(Throwable)} callback.
 *
 * <p>This class can be used to implement both provers and verifiers.
 *
 */
public abstract class DataTransport {
    private static final String TAG = "DataTransport";

    protected final Context mContext;

    DataTransport(Context context) {
        mContext = context;
    }

    /**
     * Sets the bytes of <code>EDeviceKeyBytes</code>.
     *
     * <p>This is required for some transports, for example BLE. Listeners (e.g. mdoc apps) will
     * pass the value they generate and initiators (e.g. mdoc reader apps) will pass the value
     * they receive through device engagement.
     *
     * <p>This should be called before calling {@link #listen()} or {@link #connect(byte[])}.
     *
     * @param encodedEDeviceKeyBytes bytes of <code>EDeviceKeyBytes</code> CBOR.
     */
    abstract public void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes);

    /**
     * Connects to the address specified in the <code>DeviceRetrievalMethod</code> CBOR passed in
     * the <code>encodedDeviceRetrievalMethod</code> parameter.
     *
     * <p>This is an asynchronous operation, {@link Listener#onConnectionResult(boolean)}
     * is called with whether the connection attempt worked.
     *
     * @param encodedDeviceRetrievalMethod bytes of <code>DeviceRetrievalMethod</code> CBOR.
     * @throws IllegalArgumentException if the given CBOR doesn't match the expected format.
     */
    abstract public void connect(@NonNull byte[] encodedDeviceRetrievalMethod);

    /**
     * Starts listening on the transport.
     *
     * Parameters that may vary (e.g. port number) are chosen by the implementation or informed
     * by the caller out-of-band using e.g. transport-specific setters. All details are returned
     * as part of the <code>DeviceRetrievalMethod</code> CBOR returned.
     *
     * <p>This is an asynchronous operation. When listening has been set up the
     * {@link Listener#onListeningSetupCompleted(byte[])} method is called with the bytes of the
     * <code>DeviceRetrievalMethod</code> CBOR or <code>null</code> if the operation fails. When a
     * peer connects {@link Listener#onListeningPeerConnected()} is called. Only a single peer
     * will be allowed to connect. When the peer disconnects
     * {@link Listener#onListeningPeerDisconnected()} is called.
     */
    abstract public void listen();

    /**
     * Gets the <code>DeviceRetrievalMethod</code> CBOR.
     *
     * If this is a listening transport, this returns the same bytes as returned by
     * {@link Listener#onListeningSetupCompleted(byte[])} (byte[])}, if it's a
     * connecting transport it returns the same bytes as passed to the {@link #connect(byte[])}
     * method.
     *
     * @return bytes of <code>DeviceRetrievalMethod</code> CBOR.
     */
    abstract public @NonNull byte[] getEncodedDeviceRetrievalMethod();

    /**
     * Gets a {@link NdefRecord}s for a listening transport or <code>null</code> if one isn't
     * defined for this kind of transport.
     *
     * <p>The first record is for the Alternative Carrier Configuration Record, the second one is
     * for payload a Alternative Carrier Record which goes into Handover Select record.
     *
     * @return A pair of a {@link NdefRecord} and byte-array or <code>null</code>.
     */
    abstract public @Nullable
    Pair<NdefRecord, byte[]> getNdefRecords();

    /**
     * If this is a listening transport, stops listening and disconnects any peer already
     * connected. If it's a connecting transport, disconnects the active peer. If no peer is
     * connected, does nothing.
     *
     * <p>Messages previously sent with {@link #sendMessage(byte[])} will be sent before the
     * connection is closed.
     * TODO: actually implement this guarantee for all transports.
     * 
     * <p>After calling this method, no more callbacks will be delivered.
     */
    abstract public void close();

    /**
     * Sends data to the remote peer.
     *
     * <p>This is an asynchronous operation, data will be sent by another thread. It's safe to
     * call this right after {@link #connect(byte[])}, data will be queued up and sent once
     * a connection has been established.
     *
     * @param data the data to send
     */
    abstract public void sendMessage(@NonNull byte[] data);

    /**
     * Sends a transport-specific termination message.
     *
     * This may not be supported by the transport, use
     * {@link #supportsTransportSpecificTerminationMessage()} to find out.
     */
    abstract public void sendTransportSpecificTerminationMessage();

    /**
     * Whether the transport supports a transport-specific termination message.
     *
     * Only known transport to support this is BLE.
     *
     * @return {@code true} if supported, {@code false} otherwise.
     */
    abstract public boolean supportsTransportSpecificTerminationMessage();

    /**
     * Set the listener to be used for notification.
     *
     * <p>This may be called multiple times but only one listener is active at one time.
     *
     * @param listener the listener to notify or <code>null</code> clear previously set listener.
     * @param executor the {@link Executor} to use for notifying <code>listener</code>.
     */
    public void setListener(@Nullable Listener listener, @Nullable Executor executor) {
        if (listener != null && executor == null) {
            throw new IllegalStateException("Passing null Executor for non-null Listener");
        }
        mListener = listener;
        mListenerExecutor = executor;
    }

    private @Nullable Listener mListener;
    private @Nullable Executor mListenerExecutor;
    boolean mInhibitCallbacks;
    
    // Should be called by close() in subclasses to signal that no callbacks should be made
    // from here on.
    //
    protected void inhibitCallbacks() {
        mInhibitCallbacks = true;
    }

    protected void reportListeningSetupCompleted(@NonNull byte[] encodedDeviceRetrievalMethod) {
        if (mListener != null && !mInhibitCallbacks) {
            final Listener listener = mListener;
            mListenerExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!mInhibitCallbacks) {
                                listener.onListeningSetupCompleted(encodedDeviceRetrievalMethod);
                            }
                        }
                    }
            );
        }
    }

    protected void reportListeningPeerConnecting() {
        if (mListener != null && !mInhibitCallbacks) {
            final Listener listener = mListener;
            mListenerExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!mInhibitCallbacks) {
                                listener.onListeningPeerConnecting();
                            }
                        }
                    }
            );
        }
    }

    protected void reportListeningPeerConnected() {
        if (mListener != null && !mInhibitCallbacks) {
            final Listener listener = mListener;
            mListenerExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!mInhibitCallbacks) {
                                listener.onListeningPeerConnected();
                            }
                        }
                    }
            );
        }
    }

    protected void reportListeningPeerDisconnected() {
        if (mListener != null && !mInhibitCallbacks) {
            final Listener listener = mListener;
            mListenerExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!mInhibitCallbacks) {
                                listener.onListeningPeerDisconnected();
                            }
                        }
                    }
            );
        }
    }

    protected void reportConnectionResult(@Nullable Throwable error) {
        if (mListener != null && !mInhibitCallbacks) {
            final Listener listener = mListener;
            mListenerExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!mInhibitCallbacks) {
                                listener.onConnectionResult(error);
                            }
                        }
                    }
            );
        }
    }

    protected void reportConnectionDisconnected() {
        if (mListener != null && !mInhibitCallbacks) {
            final Listener listener = mListener;
            mListenerExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!mInhibitCallbacks) {
                                listener.onConnectionDisconnected();
                            }
                        }
                    }
            );
        }
    }


    protected void reportMessageReceived(@NonNull byte[] data) {
        if (mListener != null && !mInhibitCallbacks) {
            final Listener listener = mListener;
            mListenerExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!mInhibitCallbacks) {
                                listener.onMessageReceived(data);
                            }
                        }
                    }
            );
        }
    }

    protected void reportTransportSpecificSessionTermination() {
        if (mListener != null && !mInhibitCallbacks) {
            final Listener listener = mListener;
            mListenerExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!mInhibitCallbacks) {
                                listener.onTransportSpecificSessionTermination();
                            }
                        }
                    }
            );
        }
    }

    protected void reportError(@NonNull Throwable error) {
        Log.d(TAG, "Emitting onError: " + error);
        Thread.dumpStack();
        if (mListener != null && !mInhibitCallbacks) {
            final Listener listener = mListener;
            mListenerExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (!mInhibitCallbacks) {
                                listener.onError(error);
                            }
                        }
                    }
            );
        }
    }

    /**
     * @hide
     *
     * Interface for listener.
     */
    public interface Listener {

        /**
         * Called on a listening transport when listening setup has completed and
         * the <code>DeviceRetrievalMethod</code> CBOR is available.
         *
         * @return bytes of <code>DeviceRetrievalMethod</code> CBOR or <code>null</code> if an
         *   error occurred setting up the listener.
         */
        void onListeningSetupCompleted(@Nullable byte[] encodedDeviceRetrievalMethod);

        /**
         * Called when a listening transport first sees a new connection.
         *
         * <p>Depending on the transport in use it could be several seconds until
         * {@link #onListeningPeerConnected()} is called.
         */
        void onListeningPeerConnecting();

        /**
         * Called when a listening transport has accepted a new connection.
         */
        void onListeningPeerConnected();

        /**
         * Called when the peer which connected to a listening transport disconnects.
         *
         * <p>If this is called, the transport can no longer be used and the caller
         * should call {@link DataTransport#close()} to release resources.
         */
        void onListeningPeerDisconnected();

        /**
         * Called when the connection started with connect() succeeds.
         *
         * <p>If the connection didn't succeed, the transport can no longer be used and the caller
         * should call {@link DataTransport#close()} to release resources.
         *
         * @param error if the connection succeded, this is <code>null</code>, otherwise
         *              details about what failed
         */
        void onConnectionResult(@Nullable Throwable error);

        /**
         * Called when the connection established with connect() has been disconnected.
         *
         * <p>If this is called, the transport can no longer be used and the caller
         * should call {@link DataTransport#close()} to release resources.
         */
        void onConnectionDisconnected();

        /**
         * Called when receiving data from the peer.
         *
         * @param data the received data.
         */
        void onMessageReceived(@NonNull byte[] data);

        /**
         * Called when receiving a transport-specific session termination request.
         *
         * <p>Only known transport to support this is BLE.
         */
        void onTransportSpecificSessionTermination();

        /**
         * Called if the transports encounters an unrecoverable error.
         *
         * <p>If this is called, the transport can no longer be used and the caller
         * should call {@link DataTransport#close()} to release resources.
         *
         * @param error the error that occurred.
         */
        void onError(@NonNull Throwable error);
    }
}

