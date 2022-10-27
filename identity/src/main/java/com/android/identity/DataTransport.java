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
import android.nfc.NdefRecord;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Number;

/**
 * Abstraction for data transfer between prover and verifier devices.
 *
 * <p>The data transfer is packetized, that is, data is delivered at the same
 * granularity as it is sent. For example, if {@link #sendMessage(byte[])} is used to send
 * <code>N</code> bytes then this blob is what the remote peer will receive in the
 * {@link Listener#onMessageReceived()} callback.
 *
 * <p>Instances constructed from subclasses deriving from this class must be inert when
 * constructed, that is, they must not do anything. This constraint exists to easily facilitate
 * factory-patterns.
 *
 * <p>If an unrecoverable error is detected, this is conveyed using the
 * {@link Listener#onError(Throwable)} callback.
 *
 * <p>This class can be used to implement both provers and verifiers.
 */
public abstract class DataTransport {
    private static final String TAG = "DataTransport";

    protected final Context mContext;
    protected final DataTransportOptions mOptions;
    boolean mInhibitCallbacks;
    private @Nullable
    Listener mListener;
    private @Nullable
    Executor mListenerExecutor;
    private @Nullable
    TransmissionProgressListener mProgressListener;
    private @Nullable
    Executor mProgressListenerExecutor;
    private Queue<byte[]> mMessageReceivedQueue = new ArrayDeque<>();

    DataTransport(@NonNull Context context,
                  @NonNull DataTransportOptions options) {
        mContext = context;
        mOptions = options;
    }

    /**
     * Sets the bytes of <code>EDeviceKeyBytes</code>.
     *
     * <p>This is required for some transports, for example BLE. Listeners (e.g. mdoc apps) will
     * pass the value they generate and initiators (e.g. mdoc reader apps) will pass the value
     * they receive through device engagement.
     *
     * <p>This should be called before calling {@link #listen()} or
     * {@link #connect()}.
     *
     * @param encodedEDeviceKeyBytes bytes of <code>EDeviceKeyBytes</code> CBOR.
     */
    abstract void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes);

    /**
     * Connects to the mdoc.
     *
     * <p>This is an asynchronous operation, {@link Listener#onConnectionResult(Throwable)}
     * is called with whether the connection attempt worked.
     */
    abstract void connect();

    /**
     * Starts listening on the transport.
     *
     * Parameters that may vary (e.g. port number) are chosen by the implementation or informed
     * by the caller out-of-band using e.g. transport-specific setters. All details are returned
     * as part of the <code>DeviceRetrievalMethod</code> CBOR returned.
     *
     * <p>This is an asynchronous operation. When listening has been set up the
     * {@link Listener#onListeningSetupCompleted()} method is called with
     * address the listener is listening to or <code>null</code> if the operation fails. When a
     * peer connects {@link Listener#onListeningPeerConnected()} is called. Only a single peer
     * will be allowed to connect. When the peer disconnects
     * {@link Listener#onListeningPeerDisconnected()} is called.
     */
    abstract void listen();

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
    abstract void close();

    /**
     * Sends data to the remote peer.
     *
     * <p>This is an asynchronous operation, data will be sent by another thread. It's safe to
     * call this right after {@link #connect()}, data will be queued up and sent once a connection
     * has been established.
     *
     * @param data the data to send
     */
    abstract void sendMessage(@NonNull byte[] data);

    /**
     * Send data to the remote peer and listen for progress updates.
     *
     * @param data the data to send
     * @param progressListener progress listener that will receive updates as data is sent to the
     *                         remote peer.
     * @param progressListenerExecutor a {@link Executor} to do the progress listener updates in or
     *                                 <code>null</code> if <code>listener</code> is
     *                                 <code>null</code>.
     */
    void sendMessage(@NonNull byte[] data, @Nullable TransmissionProgressListener progressListener, @Nullable Executor progressListenerExecutor) {
        if (progressListener != null && progressListenerExecutor == null) {
            throw new IllegalStateException("Passing null Executor for non-null Listener");
        }
        this.mProgressListener = progressListener;
        this.mProgressListenerExecutor = progressListenerExecutor;
        sendMessage(data);
    }

    /**
     * Sends a transport-specific termination message.
     *
     * This may not be supported by the transport, use
     * {@link #supportsTransportSpecificTerminationMessage()} to find out.
     */
    abstract void sendTransportSpecificTerminationMessage();

    /**
     * Whether the transport supports a transport-specific termination message.
     *
     * Only known transport to support this is BLE.
     *
     * @return {@code true} if supported, {@code false} otherwise.
     */
    abstract boolean supportsTransportSpecificTerminationMessage();

    /**
     * Set the listener to be used for notification.
     *
     * <p>This may be called multiple times but only one listener is active at one time.
     *
     * @param listener the listener or <code>null</code> to stop listening.
     * @param executor a {@link Executor} to do the call in or <code>null</code> if
     *                 <code>listener</code> is <code>null</code>.
     * @throws IllegalStateException if {@link Executor} is {@code null} for a non-{@code null}
     * listener.
     */
    void setListener(@Nullable Listener listener, @Nullable Executor executor) {
        if (listener != null && executor == null) {
            throw new IllegalStateException("Passing null Executor for non-null Listener");
        }
        mListener = listener;
        mListenerExecutor = executor;
    }

    // Should be called by close() in subclasses to signal that no callbacks should be made
    // from here on.
    //
    protected void inhibitCallbacks() {
        mInhibitCallbacks = true;
    }

    // Note: The report*() methods are safe to call from any thread.

    protected void reportListeningSetupCompleted() {
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onListeningSetupCompleted());
        }
    }

    protected void reportListeningPeerConnecting() {
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(listener::onListeningPeerConnecting);
        }
    }

    protected void reportListeningPeerConnected() {
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(listener::onListeningPeerConnected);
        }
    }

    protected void reportListeningPeerDisconnected() {
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(listener::onListeningPeerDisconnected);
        }
    }

    protected void reportConnectionResult(@Nullable Throwable error) {
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onConnectionResult(error));
        }
    }

    protected void reportConnectionDisconnected() {
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(listener::onConnectionDisconnected);
        }
    }

    /**
     * Returns the next message received, if any.
     *
     * @return the next message or {@code null} if none is available.
     */
    public @Nullable byte[] getMessage() {
        return mMessageReceivedQueue.poll();
    }

    protected void reportMessageReceived(@NonNull byte[] data) {
        mMessageReceivedQueue.add(data);
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onMessageReceived());
        }
    }

    protected void reportMessageProgress(long progress, long max) {
        final TransmissionProgressListener listener = mProgressListener;
        final Executor executor = mProgressListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onProgressUpdate(progress, max));
        }
    }

    protected void reportTransportSpecificSessionTermination() {
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(listener::onTransportSpecificSessionTermination);
        }
    }

    protected void reportError(@NonNull Throwable error) {
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onError(error));
        }
    }

    /**
     * Interface for listener.
     */
    interface Listener {

        /**
         * Called on a listening transport when listening setup has completed and
         * an address for how to connect is ready.
         */
        void onListeningSetupCompleted();

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
         * Called when the connection started with {@link #connect()} succeeds.
         *
         * <p>If the connection didn't succeed, the transport can no longer be used and the caller
         * should call {@link DataTransport#close()} to release resources.
         *
         * @param error if the connection succeeded, this is <code>null</code>, otherwise
         *              details about what failed
         */
        void onConnectionResult(@Nullable Throwable error);

        /**
         * Called when the connection established with {@link #connect()} has
         * been disconnected.
         *
         * <p>If this is called, the transport can no longer be used and the caller
         * should call {@link DataTransport#close()} to release resources.
         */
        void onConnectionDisconnected();

        /**
         * Called when receiving data from the peer.
         *
         * <p>The received data can be retrieved using {@link DataTransport#getMessage()}.
         */
        void onMessageReceived();

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

