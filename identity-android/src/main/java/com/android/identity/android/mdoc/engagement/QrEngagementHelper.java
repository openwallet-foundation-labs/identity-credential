package com.android.identity.android.mdoc.engagement;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.android.mdoc.transport.DataTransport;
import com.android.identity.android.mdoc.transport.DataTransportOptions;
import com.android.identity.cbor.Bstr;
import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.Tagged;
import com.android.identity.crypto.EcPublicKey;
import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.mdoc.engagement.EngagementGenerator;
import com.android.identity.util.Logger;
import com.android.identity.internal.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import co.nstant.in.cbor.model.SimpleValue;

/**
 * Helper used for QR engagement.
 *
 * <p>This implements QR engagement as defined in ISO/IEC 18013-5:2021.
 *
 * <p>Applications can instantiate a {@link QrEngagementHelper} using
 * {@link QrEngagementHelper.Builder}, specifying which device retrieval methods
 * to support using {@link QrEngagementHelper.Builder#setConnectionMethods(List)}
 * or {@link QrEngagementHelper.Builder#setTransports(List)}.
 *
 * <p>When {@link Listener#onDeviceEngagementReady()} is called, the application can
 * call {@link #getDeviceEngagement()} and display this as a QR code in the user
 * interface and instruct the user to show this QR code to a remote mdoc reader.
 *
 * <p>When a remote mdoc reader connects to one of the advertised transports, the
 * {@link Listener#onDeviceConnected(DataTransport)} is called and the application
 * can use the passed-in {@link DataTransport} to create a
 * {@link com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper}
 * to start the transaction.
 */
public class QrEngagementHelper {
    private static final String TAG = "QrEngagementHelper";

    private final Context mContext;
    private final EcPublicKey mEDeviceKey;
    private final DataTransportOptions mOptions;
    private final Listener mListener;
    private final Executor mExecutor;
    private boolean mInhibitCallbacks;
    private ArrayList<DataTransport> mTransports = new ArrayList<>();
    private byte[] mEncodedDeviceEngagement;
    private byte[] mEncodedHandover;
    private boolean mReportedDeviceConnecting;

    QrEngagementHelper(@NonNull Context context,
                       @NonNull EcPublicKey eDeviceKey,
                       @Nullable List<ConnectionMethod> connectionMethods,
                       @Nullable List<DataTransport> transports,
                       @NonNull DataTransportOptions options,
                       @NonNull Listener listener,
                       @NonNull Executor executor) {
        mContext = context;
        mEDeviceKey = eDeviceKey;
        mListener = listener;
        mExecutor = executor;
        mOptions = options;

        byte[] encodedEDeviceKeyBytes =
                Cbor.encode(new Tagged(24, new Bstr(
                        Cbor.encode(mEDeviceKey.toCoseKey(Map.of()).getDataItem()))));

        // Set EDeviceKey for transports we were given.
        if (transports != null) {
            for (DataTransport transport : transports) {
                transport.setEDeviceKeyBytes(encodedEDeviceKeyBytes);
                mTransports.add(transport);
            }
        }

        if (connectionMethods != null) {
            // Need to disambiguate the connection methods here to get e.g. two ConnectionMethods
            // if both BLE modes are available at the same time.
            List<ConnectionMethod> disambiguatedMethods = ConnectionMethod.disambiguate(connectionMethods);
            for (ConnectionMethod cm : disambiguatedMethods) {
                DataTransport transport = DataTransport.fromConnectionMethod(
                        mContext, cm, DataTransport.ROLE_MDOC, mOptions);
                transport.setEDeviceKeyBytes(encodedEDeviceKeyBytes);
                mTransports.add(transport);
                Logger.d(TAG, "Added transport for " + cm);
            }
        }

        // Careful, we're using the user-provided Executor below so these callbacks might happen
        // in another thread than we're in right now. For example this happens if using
        // ThreadPoolExecutor.
        //
        final QrEngagementHelper helper = this;

        for (DataTransport transport : mTransports) {
            transport.setListener(new DataTransport.Listener() {

                @Override
                public void onConnecting() {
                    Logger.d(TAG, "onConnecting for " + transport);
                    peerIsConnecting(transport);
                }

                @Override
                public void onConnected() {
                    Logger.d(TAG, "onConnected for " + transport);
                    peerHasConnected(transport);
                }

                @Override
                public void onDisconnected() {
                    Logger.d(TAG, "onDisconnected for " + transport);
                    transport.close();
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    transport.close();
                    reportError(error);
                }

                @Override
                public void onMessageReceived() {
                    Logger.d(TAG, "onMessageReceived for " + transport);
                }

                @Override
                public void onTransportSpecificSessionTermination() {
                    Logger.d(TAG, "Received transport-specific session termination");
                    transport.close();
                }

            }, mExecutor);
            Logger.d(TAG, "Connecting to transport " + transport);
            transport.connect();
        }

        Logger.d(TAG, "All transports are now set up");

        ArrayList<ConnectionMethod> connectionMethodsSetup = new ArrayList<>();
        for (DataTransport transport : mTransports) {
            connectionMethodsSetup.add(transport.getConnectionMethod());
        }

        // Calculate DeviceEngagement and Handover for QR code...
        //
        // TODO: Figure out when we need to use version "1.1".
        //
        EngagementGenerator engagementGenerator =
                new EngagementGenerator(mEDeviceKey, EngagementGenerator.ENGAGEMENT_VERSION_1_0);
        engagementGenerator.addConnectionMethods(connectionMethodsSetup);
        mEncodedDeviceEngagement = engagementGenerator.generate();
        mEncodedHandover = Util.cborEncode(SimpleValue.NULL);
        Logger.dCbor(TAG, "QR DE", mEncodedDeviceEngagement);
        Logger.dCbor(TAG, "QR handover", mEncodedHandover);
        reportDeviceEngagementReady();
    }

    /**
     * Close all transports currently being listened on.
     *
     * <p>No callbacks will be done on a listener after calling this.
     *
     * <p>This method is idempotent so it is safe to call multiple times.
     */
    public void close() {
        mInhibitCallbacks = true;
        if (mTransports != null) {
            for (DataTransport transport : mTransports) {
                transport.close();
            }
            mTransports = null;
        }
    }

    /**
     * Gets {@code DeviceEngagement} as as a URI-encoded string.
     *
     * <p>This is like {@link #getDeviceEngagement()} except that it encodes the
     * {@code DeviceEngagement} according to ISO/IEC 18013-5:2021 section 8.2.2.3,
     * that is with "mdoc:" as scheme and the bytes of the {@code DeviceEngagement}
     * CBOR encoded using base64url-without-padding, according to RFC 4648, as path.
     *
     * <p>This can only be called after {@link Listener#onDeviceEngagementReady()} has been called.
     *
     * @return the {@code DeviceEngagement} CBOR encoded as an URI string.
     */
    public @NonNull
    String getDeviceEngagementUriEncoded() {
        String base64EncodedDeviceEngagement =
                Base64.encodeToString(mEncodedDeviceEngagement,
                        Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        Uri uri = new Uri.Builder()
                .scheme("mdoc")
                .encodedOpaquePart(base64EncodedDeviceEngagement)
                .build();
        String uriString = uri.toString();
        Logger.d(TAG, "qrCode URI: " + uriString);
        return uriString;
    }

    /**
     * Gets the bytes of the {@code DeviceEngagement} CBOR.
     *
     * <p>This returns the bytes of the {@code DeviceEngagement} CBOR according to
     * ISO/IEC 18013-5:2021 section 8.2.1.1 with the device retrieval methods
     * specified using {@link QrEngagementHelper.Builder}.
     *
     * <p>This can only be called after {@link Listener#onDeviceEngagementReady()} has been called.
     *
     * @return the bytes of the {@code DeviceEngagement} CBOR.
     */
    public @NonNull
    byte[] getDeviceEngagement() {
        return mEncodedDeviceEngagement;
    }

    /**
     * Gets the bytes of the {@code Handover} CBOR.
     *
     * <p>This returns the bytes of the {@code Handover} CBOR according to
     * ISO/IEC 18013-5:2021 section 9.1.5.1. For QR Code the Handover is
     * always defined as CBOR with the {@code null} value.
     *
     * @return the Handover used for QR code.
     */
    public @NonNull
    byte[] getHandover() {
        return mEncodedHandover;
    }

    void peerIsConnecting(@NonNull DataTransport transport) {
        if (!mReportedDeviceConnecting) {
            mReportedDeviceConnecting = true;
            reportDeviceConnecting();
        }
    }

    void peerHasConnected(@NonNull DataTransport transport) {
        // stop listening on other transports
        //
        Logger.d(TAG, "Peer has connected on transport " + transport
                + " - shutting down other transports");
        for (DataTransport t : mTransports) {
            if (t != transport) {
                t.setListener(null, null);
                t.close();
            }
        }
        mTransports.clear();

        transport.setListener(null, null);
        reportDeviceConnected(transport);
    }


    // Note: The report*() methods are safe to call from any thread.

    void reportDeviceEngagementReady() {
        Logger.d(TAG, "reportDeviceEngagementReady");
        final Listener listener = mListener;
        final Executor executor = mExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onDeviceEngagementReady();
                }
            });
        }
    }

    void reportDeviceConnecting() {
        Logger.d(TAG, "reportDeviceConnecting");
        final Listener listener = mListener;
        final Executor executor = mExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onDeviceConnecting();
                }
            });
        }
    }

    void reportDeviceConnected(DataTransport transport) {
        Logger.d(TAG, "reportDeviceConnected");
        final Listener listener = mListener;
        final Executor executor = mExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onDeviceConnected(transport);
                }
            });
        }
    }

    void reportError(@NonNull Throwable error) {
        Logger.d(TAG, "reportError: error: ", error);
        final Listener listener = mListener;
        final Executor executor = mExecutor;
        if (listener != null && executor != null) {
            executor.execute(() -> {
                if (!mInhibitCallbacks) {
                    listener.onError(error);
                }
            });
        }
    }

    /**
     * Listener interface for {@link QrEngagementHelper}.
     */
    public interface Listener {
        /**
         * Called when the {@code DeviceEngagement} CBOR is ready.
         *
         * <p>After this method is called it's permissible to call {@link #getDeviceEngagement()}
         * or {@link #getDeviceEngagementUriEncoded()}.
         */
        void onDeviceEngagementReady();

        /**
         * Called when a remote mdoc reader is starting to connect.
         */
        void onDeviceConnecting();

        /**
         * Called when a remote mdoc reader has connected.
         *
         * <p>The application should use the passed-in {@link DataTransport} with
         * {@link com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper}
         * to start the transaction.
         *
         * <p>After this is called, no more callbacks will be done on listener and all other
         * listening transports will be closed. Calling {@link #close()} will not close the
         * passed-in transport.
         *
         * @param transport a {@link DataTransport} for the connection to the remote mdoc reader.
         */
        void onDeviceConnected(@NonNull DataTransport transport);

        /**
         * Called when an irrecoverable error has occurred.
         *
         * @param error details of what error has occurred.
         */
        void onError(@NonNull Throwable error);
    }

    /**
     * A builder for {@link QrEngagementHelper}.
     */
    public static class Builder {

        private final Context mContext;
        private final EcPublicKey mEDeviceKey;
        private final DataTransportOptions mOptions;
        private final Listener mListener;
        private final Executor mExecutor;
        private List<ConnectionMethod> mConnectionMethods;
        private List<DataTransport> mTransports;

        /**
         * Creates a new builder for {@link QrEngagementHelper}.
         *
         * @param context application context.
         * @param eDeviceKey the public part of {@code EDeviceKey} for <em>mdoc session
         *                   encryption</em> according to ISO/IEC 18013-5:2021 section 9.1.1.4.
         * @param options set of options for creating {@link DataTransport} instances.
         * @param listener the listener.
         * @param executor a {@link Executor} to use with the listener.
         */
        public Builder(@NonNull Context context,
                       @NonNull EcPublicKey eDeviceKey,
                       @NonNull DataTransportOptions options,
                       @NonNull Listener listener,
                       @NonNull Executor executor) {
            mContext = context;
            mEDeviceKey = eDeviceKey;
            mOptions = options;
            mListener = listener;
            mExecutor = executor;
        }

        /**
         * Sets the connection methods to use.
         *
         * <p>This is used to indicate which connection methods should be used for QR engagement.
         *
         * @param connectionMethods a list of {@link ConnectionMethod} instances.
         * @return the builder.
         */
        public @NonNull Builder setConnectionMethods(@NonNull List<ConnectionMethod> connectionMethods) {
            mConnectionMethods = connectionMethods;
            return this;
        }

        /**
         * Sets data transports to use.
         *
         * @param transports a list of {@link DataTransport} instances.
         * @return the builder.
         */
        public @NonNull Builder setTransports(@NonNull List<DataTransport> transports) {
            mTransports = transports;
            return this;
        }

        /**
         * Builds the {@link QrEngagementHelper} and starts listening for connections.
         *
         * @return the helper, ready to be used.
         */
        public @NonNull QrEngagementHelper build() {
            return new QrEngagementHelper(mContext,
                    mEDeviceKey,
                    mConnectionMethods,
                    mTransports,
                    mOptions,
                    mListener,
                    mExecutor);
        }
    }
}