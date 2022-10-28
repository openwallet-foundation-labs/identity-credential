package com.android.identity;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Executor;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;

public class QrEngagementHelper implements NfcApduRouter.Listener {
    private static final String TAG = "QrEngagementHelper";

    private final PresentationSession mPresentationSession;
    private final Context mContext;
    private final KeyPair mEphemeralKeyPair;
    private final NfcApduRouter mNfcApduRouter;
    private final DataTransportOptions mOptions;
    private Listener mListener;
    private Executor mExecutor;
    private boolean mInhibitCallbacks;
    private ArrayList<DataTransport> mTransports = new ArrayList<>();
    private List<ConnectionMethod> mConnectionMethods = new ArrayList<>();
    private int mNumTransportsStillSettingUp;
    private byte[] mEncodedDeviceEngagement;
    private byte[] mEncodedHandover;
    private boolean mReportedDeviceConnecting;
    private int mNumEngagementApdusReceived;
    private int mNumDataTransferApdusReceived;

    public QrEngagementHelper(@NonNull Context context,
                              @NonNull PresentationSession presentationSession,
                              @NonNull List<ConnectionMethod> connectionMethods,
                              @NonNull DataTransportOptions options,
                              @Nullable NfcApduRouter nfcApduRouter,
                              @NonNull Listener listener,
                              @NonNull Executor executor) {
        mContext = context;
        mPresentationSession = presentationSession;
        mListener = listener;
        mExecutor = executor;
        mNfcApduRouter = nfcApduRouter;
        if (mNfcApduRouter != null) {
            mNfcApduRouter.addListener(this, executor);
        }
        mEphemeralKeyPair = mPresentationSession.getEphemeralKeyPair();
        mConnectionMethods = connectionMethods;
        mOptions = options;
        startListening();
    }

    public void close() {
        if (mNfcApduRouter != null) {
            mNfcApduRouter.removeListener(this, mExecutor);
        }
        mInhibitCallbacks = true;
        if (mTransports != null) {
            for (DataTransport transport : mTransports) {
                transport.close();
            }
            mTransports = null;
        }
    }

    @Override
    public void onApduReceived(@NonNull byte[] aid, @NonNull byte[] apdu) {
        Logger.d(TAG, String.format(Locale.US, "onApduReceived aid=%s apdu=%s",
                Util.toHex(aid), Util.toHex(apdu)));
        if (Arrays.equals(aid, NfcApduRouter.AID_FOR_TYPE_4_TAG_NDEF_APPLICATION)) {
            mNumEngagementApdusReceived += 1;
        } else if (Arrays.equals(aid, NfcApduRouter.AID_FOR_MDL_DATA_TRANSFER)) {
            mNumDataTransferApdusReceived += 1;
        }

        Logger.d(TAG, String.format(Locale.US,
                        "mNumEngagementApdusReceived=%d mNumDataTransferApdusReceived=%d",
                        mNumEngagementApdusReceived, mNumDataTransferApdusReceived));

        /*
        // This is for the case of QR engagement to NFC data transfer... we have to be
        // careful and only react on the SELECT APPLICATION command if we received no
        // previous APDUs for the engagement service.
        //
        if (mNumEngagementApdusReceived == 0 && mNumDataTransferApdusReceived == 1) {
            switch (NfcUtil.nfcGetCommandType(apdu)) {
                case NfcUtil.COMMAND_TYPE_SELECT_BY_AID:
                    if (Arrays.equals(Arrays.copyOfRange(apdu, 5, 12),
                            NfcApduRouter.AID_FOR_MDL_DATA_TRANSFER)) {
                        for (DataTransport t : mTransports) {
                            if (t instanceof DataTransportNfc) {
                                Logger.d(TAG, "NFC data transfer AID selected");
                                DataTransportNfc dataTransportNfc = (DataTransportNfc) t;
                                // Hand over the APDU router to the NFC data transport
                                mNfcApduRouter.removeListener(this, mExecutor);
                                dataTransportNfc.setNfcApduRouter(mNfcApduRouter, mExecutor);
                                mNfcApduRouter.sendResponseApdu(NfcUtil.STATUS_WORD_OK);
                            }
                        }
                    }
                    break;
            }
        }

         */
    }

    @Override
    public void onDeactivated(@NonNull byte[] aid, int reason) {
        Logger.d(TAG, String.format(Locale.US, "onDeactivated aid=%s reason=%d",
                Util.toHex(aid), reason));
        if (Arrays.equals(aid, NfcApduRouter.AID_FOR_TYPE_4_TAG_NDEF_APPLICATION)) {
            mNumEngagementApdusReceived = 0;
        } else if (Arrays.equals(aid, NfcApduRouter.AID_FOR_MDL_DATA_TRANSFER)) {
            mNumDataTransferApdusReceived = 0;
        }
    }

    /**
     * Used by PresentationHelperTest.java.
     */
    void addDataTransport(@NonNull DataTransport transport) {
        mTransports.add(transport);
    }

    /**
     * Called by constructor and also used by PresentationHelperTest.java.
     */
    void startListening() {
        byte[] encodedEDeviceKeyBytes = Util.cborEncode(Util.cborBuildTaggedByteString(
                Util.cborEncode(Util.cborBuildCoseKey(mEphemeralKeyPair.getPublic()))));

        // Need to disambiguate the connection methods here to get e.g. two ConnectionMethods
        // if both BLE modes are available at the same time.
        List<ConnectionMethod> disambiguatedMethods = ConnectionMethod.disambiguate(mConnectionMethods);
        for (ConnectionMethod cm : disambiguatedMethods) {
            DataTransport transport = cm.createDataTransport(mContext, DataTransport.ROLE_MDOC, mOptions);
            transport.setEDeviceKeyBytes(encodedEDeviceKeyBytes);
            mTransports.add(transport);
            Logger.d(TAG, "Added transport for " + cm);
        }

        // Careful, we're using the user-provided Executor below so these callbacks might happen
        // in another thread than we're in right now. For example this happens if using
        // ThreadPoolExecutor.
        //
        // In particular, it means we need locking around `mNumTransportsStillSettingUp`. We'll
        // use the monitor for the PresentationHelper object for to achieve that.
        //
        final QrEngagementHelper helper = this;
        mNumTransportsStillSettingUp = 0;

        synchronized (helper) {
            for (DataTransport transport : mTransports) {
                transport.setListener(new DataTransport.Listener() {
                    @Override
                    public void onConnectionMethodReady() {
                        Logger.d(TAG, "onConnectionMethodReady for " + transport);
                        synchronized (helper) {
                            mNumTransportsStillSettingUp -= 1;
                            if (mNumTransportsStillSettingUp == 0) {
                                allTransportsAreSetup();
                            }
                        }
                    }

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
                mNumTransportsStillSettingUp += 1;
            }
        }
    }

    // TODO: handle the case where a transport never calls onConnectionMethodReady... that
    //  is, set up a timeout to call this.
    //
    void allTransportsAreSetup() {
        Logger.d(TAG, "All transports are now set up");

        // Calculate DeviceEngagement and Handover for QR code...
        //
        // TODO: Figure out when we need to use version "1.1".
        //
        EngagementGenerator engagementGenerator =
                new EngagementGenerator(mEphemeralKeyPair.getPublic(),
                        EngagementGenerator.ENGAGEMENT_VERSION_1_0);
        for (ConnectionMethod cm : mConnectionMethods) {
            engagementGenerator.addConnectionMethod(cm);
        }
        mEncodedDeviceEngagement = engagementGenerator.generate();
        mEncodedHandover = Util.cborEncode(SimpleValue.NULL);
        if (Logger.isDebugEnabled()) {
            Logger.d(TAG, "QR DE: " + Util.toHex(mEncodedDeviceEngagement));
            Logger.d(TAG, "QR handover: " + Util.toHex(mEncodedHandover));
        }

        reportDeviceEngagementReady();
    }


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

    public @NonNull
    byte[] getDeviceEngagement() {
        return mEncodedDeviceEngagement;
    }

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
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(listener::onDeviceEngagementReady);
        }
    }

    void reportDeviceConnecting() {
        Logger.d(TAG, "reportDeviceConnecting");
        final Listener listener = mListener;
        final Executor executor = mExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(listener::onDeviceConnecting);
        }
    }

    void reportDeviceConnected(DataTransport transport) {
        Logger.d(TAG, "reportDeviceConnected");
        final Listener listener = mListener;
        final Executor executor = mExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onDeviceConnected(transport));
        }
    }

    void reportError(@NonNull Throwable error) {
        Logger.d(TAG, "reportError: error: ", error);
        final Listener listener = mListener;
        final Executor executor = mExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onError(error));
        }
    }

    public interface Listener {
        void onDeviceEngagementReady();
        void onDeviceConnecting();
        void onDeviceConnected(DataTransport transport);
        void onError(@NonNull Throwable error);
    }
}