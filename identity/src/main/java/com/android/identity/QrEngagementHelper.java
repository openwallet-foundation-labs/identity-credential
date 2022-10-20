package com.android.identity;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
    private final DataRetrievalListenerConfiguration mDataRetrievalListenerConfiguration;
    private Listener mListener;
    private Executor mExecutor;
    private boolean mInhibitCallbacks;
    private ArrayList<DataTransport> mTransports = new ArrayList<>();
    private int mNumTransportsStillSettingUp;
    private byte[] mEncodedDeviceEngagement;
    private byte[] mEncodedHandover;
    private boolean mReportedDeviceConnecting;
    private int mNumEngagementApdusReceived;
    private int mNumDataTransferApdusReceived;

    public QrEngagementHelper(@NonNull Context context,
                              @NonNull PresentationSession presentationSession,
                              @NonNull DataRetrievalListenerConfiguration dataRetrievalListenerConfiguration,
                              @Nullable NfcApduRouter nfcApduRouter,
                              @NonNull Listener listener, @NonNull Executor executor) {
        mContext = context;
        mPresentationSession = presentationSession;
        mListener = listener;
        mExecutor = executor;
        mNfcApduRouter = nfcApduRouter;
        if (mNfcApduRouter != null) {
            mNfcApduRouter.addListener(this, executor);
        }
        mEphemeralKeyPair = mPresentationSession.getEphemeralKeyPair();

        mDataRetrievalListenerConfiguration = dataRetrievalListenerConfiguration;
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
        // The order here matters... it will be the same order in the array in the QR code
        // and we expect readers to pick the first one.
        //
        if (mDataRetrievalListenerConfiguration.isBleEnabled()) {
            @Constants.BleDataRetrievalOption int opts =
                    mDataRetrievalListenerConfiguration.getBleDataRetrievalOptions();

            boolean useL2CAPIfAvailable = (opts & Constants.BLE_DATA_RETRIEVAL_OPTION_L2CAP) != 0;

            boolean bleClearCache = (opts & Constants.BLE_DATA_RETRIEVAL_CLEAR_CACHE) != 0;

            if ((opts & Constants.BLE_DATA_RETRIEVAL_OPTION_MDOC_CENTRAL_CLIENT_MODE) != 0) {
                UUID serviceUuid = UUID.randomUUID();
                DataTransportBleCentralClientMode bleTransport =
                        new DataTransportBleCentralClientMode(mContext);
                bleTransport.setServiceUuid(serviceUuid);
                bleTransport.setUseL2CAPIfAvailable(useL2CAPIfAvailable);
                bleTransport.setClearCache(bleClearCache);
                Logger.d(TAG, "Adding BLE mdoc central client mode transport");
                mTransports.add(bleTransport);
            }
            if ((opts & Constants.BLE_DATA_RETRIEVAL_OPTION_MDOC_PERIPHERAL_SERVER_MODE) != 0) {
                UUID serviceUuid = UUID.randomUUID();
                DataTransportBlePeripheralServerMode bleTransport =
                        new DataTransportBlePeripheralServerMode(mContext);
                bleTransport.setServiceUuid(serviceUuid);
                bleTransport.setUseL2CAPIfAvailable(useL2CAPIfAvailable);
                Logger.d(TAG, "Adding BLE mdoc peripheral server mode transport");
                if (bleClearCache) {
                    Logger.d(TAG, "Ignoring bleClearCache flag since it only applies to "
                            + "BLE mdoc central client mode when acting as a holder");
                }
                mTransports.add(bleTransport);
            }
        }
        if (mDataRetrievalListenerConfiguration.isWifiAwareEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Logger.d(TAG, "Adding Wifi Aware transport");
                mTransports.add(new DataTransportWifiAware(mContext));
            } else {
                throw new IllegalArgumentException("Wifi Aware only available on API 29 or later");
            }
        }
        if (mDataRetrievalListenerConfiguration.isNfcEnabled()) {
            Logger.d(TAG, "Adding NFC transport");
            mTransports.add(new DataTransportNfc(mContext));
        }

        byte[] encodedEDeviceKeyBytes = Util.cborEncode(Util.cborBuildTaggedByteString(
                Util.cborEncode(Util.cborBuildCoseKey(mEphemeralKeyPair.getPublic()))));

        for (DataTransport t : mTransports) {
            t.setEDeviceKeyBytes(encodedEDeviceKeyBytes);
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
                    public void onListeningSetupCompleted(@Nullable DataRetrievalAddress address) {
                        Logger.d(TAG, "onListeningSetupCompleted for " + transport);
                        synchronized (helper) {
                            mNumTransportsStillSettingUp -= 1;
                            if (mNumTransportsStillSettingUp == 0) {
                                allTransportsAreSetup();
                            }
                        }
                    }

                    @Override
                    public void onListeningPeerConnecting() {
                        Logger.d(TAG, "onListeningPeerConnecting for " + transport);
                        peerIsConnecting(transport);
                    }

                    @Override
                    public void onListeningPeerConnected() {
                        Logger.d(TAG, "onListeningPeerConnected for " + transport);
                        peerHasConnected(transport);
                    }

                    @Override
                    public void onListeningPeerDisconnected() {
                        Logger.d(TAG, "onListeningPeerDisconnected for " + transport);
                        transport.close();
                    }

                    @Override
                    public void onConnectionResult(@Nullable Throwable error) {
                        Logger.d(TAG, "onConnectionResult for " + transport);
                        if (error != null) {
                            throw new IllegalStateException("Unexpected onConnectionResult "
                                    + "callback from transport " + transport, error);
                        }
                        throw new IllegalStateException("Unexpected onConnectionResult "
                                + "callback from transport " + transport);
                    }

                    @Override
                    public void onConnectionDisconnected() {
                        Logger.d(TAG, "onConnectionDisconnected for " + transport);
                        throw new IllegalStateException("Unexpected onConnectionDisconnected "
                                + "callback from transport " + transport);
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
                Logger.d(TAG, "Listening on transport " + transport);
                transport.listen();
                mNumTransportsStillSettingUp += 1;
            }
        }
    }

    private @NonNull
    byte[] generateDeviceEngagement(@NonNull List<DataRetrievalAddress> listeningAddresses) {
        DataItem eDeviceKeyBytes = Util.cborBuildTaggedByteString(
                Util.cborEncode(Util.cborBuildCoseKey(mEphemeralKeyPair.getPublic())));

        DataItem securityDataItem = new CborBuilder()
                .addArray()
                .add(1) // cipher suite
                .add(eDeviceKeyBytes)
                .end()
                .build().get(0);

        DataItem deviceRetrievalMethodsDataItem = null;
        CborBuilder deviceRetrievalMethodsBuilder = new CborBuilder();
        ArrayBuilder<CborBuilder> arrayBuilder = deviceRetrievalMethodsBuilder.addArray();
        for (DataRetrievalAddress address : listeningAddresses) {
            address.addDeviceRetrievalMethodsEntry(arrayBuilder, listeningAddresses);
        }
        arrayBuilder.end();
        deviceRetrievalMethodsDataItem = deviceRetrievalMethodsBuilder.build().get(0);

        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> map = builder.addMap();
        map.put(0, "1.0").put(new UnsignedInteger(1), securityDataItem);
        if (deviceRetrievalMethodsDataItem != null) {
            map.put(new UnsignedInteger(2), deviceRetrievalMethodsDataItem);
        }
        map.end();
        return Util.cborEncode(builder.build().get(0));
    }

    // TODO: handle the case where a transport never calls onListeningSetupCompleted... that
    //  is, set up a timeout to call this.
    //
    void allTransportsAreSetup() {
        Logger.d(TAG, "All transports are now set up");

        // Calculate DeviceEngagement and Handover for QR code...
        //
        List<DataRetrievalAddress> listeningAddresses = new ArrayList<>();
        for (DataTransport transport : mTransports) {
            listeningAddresses.add(transport.getListeningAddress());
        }
        mEncodedDeviceEngagement = generateDeviceEngagement(listeningAddresses);
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