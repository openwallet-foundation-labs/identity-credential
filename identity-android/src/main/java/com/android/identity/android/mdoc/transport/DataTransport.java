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

package com.android.identity.android.mdoc.transport;

import android.content.Context;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodHttp;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodNfc;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodWifiAware;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

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

    public static final int ROLE_MDOC = 0;
    public static final int ROLE_MDOC_READER = 1;

    /** @hidden */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {ROLE_MDOC, ROLE_MDOC_READER})
    public @interface Role {
    }

    protected final Context mContext;
    protected final @Role int mRole;
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
    private final Queue<byte[]> mMessageReceivedQueue = new ArrayDeque<>();

    public DataTransport(@NonNull Context context,
                  @Role int role,
                  @NonNull DataTransportOptions options) {
        mContext = context;
        mRole = role;
        mOptions = options;
    }

    /**
     * Sets the bytes of <code>EDeviceKeyBytes</code>.
     *
     * <p>This is required for some transports, for example BLE. Listeners (e.g. mdoc apps) will
     * pass the value they generate and initiators (e.g. mdoc reader apps) will pass the value
     * they receive through device engagement.
     *
     * <p>This should be called before calling {@link #connect()}.
     *
     * @param encodedEDeviceKeyBytes bytes of <code>EDeviceKeyBytes</code> CBOR.
     */
    public abstract void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes);

    /**
     * Starts connecting to the remote mdoc or mdoc reader.
     *
     * <p>This is an asynchronous operation, {@link Listener#onConnected()} will
     * be called on success. On error {@link Listener#onError(Throwable)} will
     * be called.
     *
     * <p>It's safe to call {@link #getConnectionMethod()} once this returns.
     */
    public abstract void connect();

    /**
     * Closes the connection with the remote mdoc or mdoc reader.
     *
     * <p>Messages previously sent with {@link #sendMessage(byte[])} will be sent before the
     * connection is closed.
     *
     * <p>If not connected, this method does nothing.
     *
     * <p>After calling this method, no more callbacks will be delivered.
     */
    public abstract void close();

    /**
     * Sends data to the remote mdoc or mdoc reader.
     *
     * <p>This is an asynchronous operation, data will be sent by another thread. It's safe to
     * call this right after {@link #connect()}, data will be queued up and sent once a connection
     * has been established.
     *
     * @param data the data to send, must be at least one byte.
     */
    public abstract void sendMessage(@NonNull byte[] data);

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
    public void sendMessage(@NonNull byte[] data, @Nullable TransmissionProgressListener progressListener, @Nullable Executor progressListenerExecutor) {
        if (progressListener != null && progressListenerExecutor == null) {
            throw new IllegalStateException("Passing null Executor for non-null Listener");
        }
        this.mProgressListener = progressListener;
        this.mProgressListenerExecutor = progressListenerExecutor;
        sendMessage(data);
    }

    public @Role int getRole() {
        return mRole;
    }

    /**
     * Sends a transport-specific termination message.
     *
     * This may not be supported by the transport, use
     * {@link #supportsTransportSpecificTerminationMessage()} to find out.
     */
    public abstract void sendTransportSpecificTerminationMessage();

    /**
     * Whether the transport supports a transport-specific termination message.
     *
     * Only known transport to support this is BLE.
     *
     * @return {@code true} if supported, {@code false} otherwise.
     */
    public abstract boolean supportsTransportSpecificTerminationMessage();

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
    public void setListener(@Nullable Listener listener, @Nullable Executor executor) {
        if (listener != null && executor == null) {
            throw new IllegalStateException("Passing null Executor for non-null Listener");
        }
        mListener = listener;
        mListenerExecutor = executor;
    }

    /**
     * Returns the next message received, if any.
     *
     * @return the next message or {@code null} if none is available.
     */
    public @Nullable byte[] getMessage() {
        return mMessageReceivedQueue.poll();
    }

    // Should be called by close() in subclasses to signal that no callbacks should be made
    // from here on.
    //
    protected void inhibitCallbacks() {
        mInhibitCallbacks = true;
    }


    private boolean mIsConnected = false;

    public boolean isConnected() {
        return mIsConnected;
    }

    // Note: The report*() methods are safe to call from any thread.
    
    protected void reportConnecting() {
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onConnecting();
                }
            });
        }
    }

    protected void reportConnected() {
        mIsConnected = true;
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onConnected();
                }
            });
        }
    }

    protected void reportDisconnected() {
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onDisconnected();
                }
            });
        }
    }

    protected void reportMessageReceived(@NonNull byte[] data) {
        mMessageReceivedQueue.add(data);
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onMessageReceived();
                }
            });
        }
    }

    protected void reportMessageProgress(long progress, long max) {
        final TransmissionProgressListener listener = mProgressListener;
        final Executor executor = mProgressListenerExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onProgressUpdate(progress, max);
                }
            });
        }
    }

    protected void reportTransportSpecificSessionTermination() {
        final Listener listener = mListener;
        final Executor executor = mListenerExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onTransportSpecificSessionTermination();
                }
            });
        }
    }

    protected void reportError(@NonNull Throwable error) {
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

    /**
     * Returns a {@link ConnectionMethod} instance that can be used to connect to this transport.
     *
     * <p>For most data transports this will return the same {@link ConnectionMethod} instance
     * that was passed at construction time. However for some transports where the address to
     * listen on is not known until the connection have been set up (for example dynamic TCP
     * listening port assignments or when a cloud relay is in use) it will differ.
     *
     * <p>This cannot be called until {@link #connect()} has been called.
     *
     * @return A {@link ConnectionMethod}-derived instance.
     */
    public abstract @NonNull ConnectionMethod getConnectionMethod();

    /**
     * Interface for listener.
     */
    public interface Listener {
        /**
         * May be called when attempting to connect and the first sign of progress is seen.
         *
         * <p>The sole purpose of this is to allow the application to convey progress to the
         * user, for example change from a screen where a QR engagement code is show to
         * showing "Connecting to mDL reader...".
         *
         * <p>Depending on the transport in use it could be several seconds until
         * {@link #onConnected()} is called.
         */
        void onConnecting();

        /**
         * Called when the attempt started with {@link #connect()} succeeds.
         */
        void onConnected();

        /**
         * Called when the connection previously established with {@link #connect()} has
         * been disconnected.
         *
         * <p>If this is called, the transport can no longer be used and the caller
         * should call {@link DataTransport#close()} to release resources.
         */
        void onDisconnected();

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

    /**
     * Creates a new {@link DataTransport}-derived instance for the given type
     * of {@link ConnectionMethod}.
     *
     * @param context application context.
     * @param connectionMethod the {@link ConnectionMethod} to use.
     * @param role whether the transport will be used by the mdoc or mdoc reader.
     * @param options options for configuring the created instance.
     * @return A {@link DataTransport}-derived instance configured with the given options.
     * @throws IllegalArgumentException if the connection-method has invalid options specified.
     */
    public static @NonNull
    DataTransport fromConnectionMethod(@NonNull Context context,
                                       @NonNull ConnectionMethod connectionMethod,
                                       @DataTransport.Role int role,
                                       @NonNull DataTransportOptions options) {
        if (connectionMethod instanceof ConnectionMethodBle) {
            return DataTransportBle.fromConnectionMethod(
                    context,
                    (ConnectionMethodBle) connectionMethod,
                    role, options);
        } else if (connectionMethod instanceof ConnectionMethodNfc) {
            return DataTransportNfc.fromConnectionMethod(
                    context,
                    (ConnectionMethodNfc) connectionMethod,
                    role, options);
        } else if (connectionMethod instanceof ConnectionMethodWifiAware) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return DataTransportWifiAware.fromConnectionMethod(
                        context,
                        (ConnectionMethodWifiAware) connectionMethod,
                        role, options);
            } else {
                throw new IllegalStateException("Wifi Aware is not supported");
            }
        } else if (connectionMethod instanceof ConnectionMethodHttp) {
            return DataTransportHttp.fromConnectionMethod(
                    context,
                    (ConnectionMethodHttp) connectionMethod,
                    role, options);
        } else if (connectionMethod instanceof ConnectionMethodTcp) {
            return DataTransportTcp.fromConnectionMethod(
                    context,
                    (ConnectionMethodTcp) connectionMethod,
                    role, options);
        } else if (connectionMethod instanceof ConnectionMethodUdp) {
            return DataTransportUdp.fromConnectionMethod(
                    context,
                    (ConnectionMethodUdp) connectionMethod,
                    role, options);
        } else {
            throw new IllegalArgumentException("Unknown ConnectionMethod");
        }
    }
}

