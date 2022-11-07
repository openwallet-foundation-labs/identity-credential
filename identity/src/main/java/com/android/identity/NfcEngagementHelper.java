package com.android.identity;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;

public class NfcEngagementHelper implements NfcApduRouter.Listener {
    private static final String TAG = "NfcEngagementHelper";

    private final PresentationSession mPresentationSession;
    private final Context mContext;
    private final KeyPair mEphemeralKeyPair;
    private final NfcApduRouter mNfcApduRouter;
    private final List<ConnectionMethod> mConnectionMethods;
    private final DataTransportOptions mOptions;
    private Listener mListener;
    private final Executor mExecutor;
    private boolean mInhibitCallbacks;
    // Dynamically created when a NFC tag reader is in the field
    private ArrayList<DataTransport> mTransports;
    private int mNumTransportsStillSettingUp;
    private byte[] mEncodedDeviceEngagement;
    private byte[] mEncodedHandover;
    private boolean mReportedDeviceConnecting;
    private Queue<byte[]> mApduQueue = new ArrayDeque<>();

    private static final byte[] CAPABILITY_FILE_CONTENTS = new byte[]{
            (byte) 0x00, (byte) 0x0f,  // size of capability container '00 0F' = 15 bytes
            (byte) 0x20,               // mapping version v2.0
            (byte) 0x7f, (byte) 0xff,  // maximum response data length '7F FF'
            (byte) 0x7f, (byte) 0xff,  // maximum command data length '7F FF'
            (byte) 0x04, (byte) 0x06,  // NDEF File Control TLV
            (byte) 0xe1, (byte) 0x04,  // NDEF file identifier 'E1 04'
            (byte) 0x7f, (byte) 0xff,  // maximum NDEF file size '7F FF'
            (byte) 0x00,               // file read access condition (allow read)
            (byte) 0xff                // file write access condition (do not write)
    };

    private byte[] mSelectedNfcFile;
    private long mTimeStartedSettingUpTransports;
    private DataTransportNfc mNfcDataTransport;
    private boolean mTransportsAreUp;

    public NfcEngagementHelper(@NonNull Context context,
                               @NonNull PresentationSession presentationSession,
                               @NonNull List<ConnectionMethod> connectionMethods,
                               @NonNull DataTransportOptions options,
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
        mConnectionMethods = connectionMethods;
        mOptions = options;
    }

    public void close() {
        if (mNfcApduRouter != null) {
            mNfcApduRouter.removeListener(this, mExecutor);
        }
        mInhibitCallbacks = true;
        int numTransportsClosed = 0;
        if (mTransports != null) {
            for (DataTransport transport : mTransports) {
                transport.close();
                numTransportsClosed += 1;
            }
            mTransports = null;
        }
        mNfcDataTransport = null;
        Logger.d(TAG, String.format(Locale.US,"In close(), closed %d transports", numTransportsClosed));
    }

    public @NonNull
    byte[] getDeviceEngagement() {
        return mEncodedDeviceEngagement;
    }

    public @NonNull
    byte[] getHandover() {
        return mEncodedHandover;
    }

    private void setupTransports() {
        if (mTransports != null) {
            return;
        }
        Logger.d(TAG, "Setting up transports");
        mTransports = new ArrayList<>();
        mTimeStartedSettingUpTransports = System.currentTimeMillis();

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
        // use the monitor for this object for to achieve that.
        //
        final NfcEngagementHelper helper = this;
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

    private @NonNull
    byte[] generateDeviceEngagement() {
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
        // This array is empty on NFC
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

    // TODO: handle the case where a transport never calls onConnectionMethodReady... that
    //  is, set up a timeout to call this.
    //
    void allTransportsAreSetup() {
        mTransportsAreUp = true;

        long setupTimeMillis = System.currentTimeMillis() - mTimeStartedSettingUpTransports;
        Logger.d(TAG, String.format(Locale.US, "All transports set up in %d msec", setupTimeMillis));

        mEncodedDeviceEngagement = generateDeviceEngagement();
        mEncodedHandover = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(calculateHandover())   // Handover Select message
                .add(SimpleValue.NULL)         // Handover Request message
                .end()
                .build().get(0));
        if (Logger.isDebugEnabled()) {
            Logger.d(TAG, "NFC DE: " + Util.toHex(mEncodedDeviceEngagement));
            Logger.d(TAG, "NFC handover: " + Util.toHex(mEncodedHandover));
        }

        // Finally, process any APDUs that might have been queued up...
        if (mApduQueue.size() > 0) {
            Logger.d(TAG, String.format("Processing %d queued APDUs", mApduQueue.size()));
            do {
                byte[] apdu = mApduQueue.poll();
                if (apdu == null) {
                    break;
                }
                processApdu(apdu);
            } while (true);
            Logger.d(TAG, "Done processing queued APDUs..");
        }
    }

    @Override
    public void onApduReceived(@NonNull byte[] aid, @NonNull byte[] apdu) {
        Logger.d(TAG, String.format(Locale.US, "onApduReceived aid=%s apdu=%s",
                Util.toHex(aid), Util.toHex(apdu)));

        if (!mTransportsAreUp) {
            Logger.d(TAG, "Deferring processing of APDUs since transports are not yet up");
            mApduQueue.add(apdu);
            setupTransports();
            return;
        }

        processApdu(apdu);
    }

    @Override
    public void onDeactivated(@NonNull byte[] aid, int reason) {
        Logger.d(TAG, String.format(Locale.US, "onDeactivated aid=%s reason=%d",
                Util.toHex(aid), reason));
        mSelectedNfcFile = null;
    }

    private void processApdu(@NonNull byte[] apdu) {
        byte[] ret = null;

        if (Logger.isDebugEnabled()) {
            Logger.d(TAG, "processApdu: " + Util.toHex(apdu));
        }

        switch (NfcUtil.nfcGetCommandType(apdu)) {
            case NfcUtil.COMMAND_TYPE_OTHER:
                ret = NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED;
                break;
            case NfcUtil.COMMAND_TYPE_SELECT_BY_AID:
                ret = handleSelectByAid(apdu);
                break;
            case NfcUtil.COMMAND_TYPE_SELECT_FILE:
                ret = handleSelectFile(apdu);
                break;
            case NfcUtil.COMMAND_TYPE_READ_BINARY:
                ret = handleReadBinary(apdu);
                break;
            case NfcUtil.COMMAND_TYPE_UPDATE_BINARY:
                ret = handleUpdateBinary(apdu);
                break;
            case NfcUtil.COMMAND_TYPE_ENVELOPE:
                ret = handleEnvelope(apdu);
                break;
            case NfcUtil.COMMAND_TYPE_RESPONSE:
                ret = handleResponse(apdu);
                break;
            default:
                ret = NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED;
                break;
        }

        if (ret != null) {
            if (Logger.isDebugEnabled()) {
                Logger.d(TAG, "APDU response: " + Util.toHex(ret));
            }
            mNfcApduRouter.sendResponseApdu(ret);
        }
    }

    private @NonNull
    byte[] handleSelectByAid(@NonNull byte[] apdu) {
        Logger.d(TAG, "in handleSelectByAid");
        if (apdu.length < 12) {
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }
        if (Arrays.equals(Arrays.copyOfRange(apdu, 5, 12), NfcApduRouter.AID_FOR_TYPE_4_TAG_NDEF_APPLICATION)) {
            Logger.d(TAG, "NFC engagement AID selected");
            return NfcUtil.STATUS_WORD_OK;
        } else if (Arrays.equals(Arrays.copyOfRange(apdu, 5, 12), NfcApduRouter.AID_FOR_MDL_DATA_TRANSFER)) {
            for (DataTransport t : mTransports) {
                if (t instanceof DataTransportNfc) {
                    Logger.d(TAG, "NFC data transfer AID selected");
                    DataTransportNfc dataTransportNfc = (DataTransportNfc) t;
                    // Hand over the APDU router to the NFC data transport
                    mNfcApduRouter.removeListener(this, mExecutor);
                    dataTransportNfc.setNfcApduRouter(mNfcApduRouter, mExecutor);
                    return NfcUtil.STATUS_WORD_OK;
                }
            }
            Logger.d(TAG, "Rejecting NFC data transfer since it wasn't set up");
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        } else {
            Logger.d(TAG, "Unexpected AID selected in APDU " + Util.toHex(apdu));
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }
    }

    private @NonNull
    byte[] calculateStaticHandoverSelectPayload(
            List<byte[]> alternativeCarrierRecords) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 6.2 Handover Select Record
        //
        // The NDEF payload of the Handover Select Record SHALL consist of a single octet that
        // contains the MAJOR_VERSION and MINOR_VERSION numbers, optionally followed by an embedded
        // NDEF message.
        //
        // If present, the NDEF message SHALL consist of one of the following options:
        // - One or more ALTERNATIVE_CARRIER_RECORDs
        // - One or more ALTERNATIVE_CARRIER_RECORDs followed by an ERROR_RECORD
        // - An ERROR_RECORD.
        //

        baos.write(0x15);  // version 1.5

        NdefRecord[] acRecords = new NdefRecord[alternativeCarrierRecords.size()];
        for (int n = 0; n < alternativeCarrierRecords.size(); n++) {
            byte[] acRecordPayload = alternativeCarrierRecords.get(n);
            acRecords[n] = new NdefRecord((short) 0x01,
                    "ac".getBytes(UTF_8),
                    null,
                    acRecordPayload);
        }
        NdefMessage hsMessage = new NdefMessage(acRecords);
        baos.write(hsMessage.toByteArray(), 0, hsMessage.getByteArrayLength());

        byte[] hsPayload = baos.toByteArray();
        return hsPayload;
    }

    // Returns the bytes of the Handover Select message...
    //
    private @NonNull
    byte[] calculateHandover() {
        List<NdefRecord> carrierConfigurationRecords = new ArrayList<>();
        List<byte[]> alternativeCarrierRecords = new ArrayList<>();

        // Need to disambiguate the connection methods here to get e.g. two ConnectionMethods
        // if both BLE modes are available at the same time.
        List<ConnectionMethod> disambiguatedMethods = ConnectionMethod.disambiguate(mConnectionMethods);
        for (ConnectionMethod cm : disambiguatedMethods) {

            Pair<NdefRecord, byte[]> records = cm.toNdefRecord();
            if (records != null) {
                if (Logger.isDebugEnabled()) {
                    Logger.d(TAG, "ConnectionMethod " + cm + ": alternativeCarrierRecord: "
                            + Util.toHex(records.second) + " carrierConfigurationRecord: "
                            + Util.toHex(records.first.getPayload()));
                }
                alternativeCarrierRecords.add(records.second);
                carrierConfigurationRecords.add(records.first);
            } else {
                Logger.d(TAG, "Address " + cm + " yielded no NDEF records");
            }

        }

        NdefRecord[] arrayOfRecords = new NdefRecord[carrierConfigurationRecords.size() + 2];

        byte[] hsPayload = calculateStaticHandoverSelectPayload(alternativeCarrierRecords);
        arrayOfRecords[0] = new NdefRecord((short) 0x01,
                "Hs".getBytes(UTF_8),
                null,
                hsPayload);

        arrayOfRecords[1] = new NdefRecord((short) 0x04,
                "iso.org:18013:deviceengagement".getBytes(UTF_8),
                "mdoc".getBytes(UTF_8),
                mEncodedDeviceEngagement);

        int n = 2;
        for (NdefRecord record : carrierConfigurationRecords) {
            arrayOfRecords[n++] = record;
        }

        NdefMessage message = new NdefMessage(arrayOfRecords);
        return message.toByteArray();
    }

    private @NonNull
    byte[] handleSelectFile(@NonNull byte[] apdu) {
        Logger.d(TAG, "in handleSelectFile");
        if (apdu.length < 7) {
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }
        int fileId = (apdu[5] & 0xff) * 256 + (apdu[6] & 0xff);
        // We only support two files
        if (fileId == NfcUtil.CAPABILITY_CONTAINER_FILE_ID) {
            mSelectedNfcFile = CAPABILITY_FILE_CONTENTS;
        } else if (fileId == NfcUtil.NDEF_FILE_ID) {
            byte[] handoverMessage = calculateHandover();
            if (Logger.isDebugEnabled()) {
                Logger.d(TAG, "handoverMessage: " + Util.toHex(handoverMessage));
            }
            byte[] fileContents = new byte[handoverMessage.length + 2];
            fileContents[0] = (byte) (handoverMessage.length / 256);
            fileContents[1] = (byte) (handoverMessage.length & 0xff);
            System.arraycopy(handoverMessage, 0, fileContents, 2, handoverMessage.length);
            mSelectedNfcFile = fileContents;
        } else {
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }
        return NfcUtil.STATUS_WORD_OK;
    }

    private @NonNull
    byte[] handleReadBinary(@NonNull byte[] apdu) {
        if (apdu.length < 5) {
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }
        byte[] contents = mSelectedNfcFile;
        int offset = (apdu[2] & 0xff) * 256 + (apdu[3] & 0xff);
        int size = apdu[4] & 0xff;
        if (size == 0) {
            // Handle Extended Length encoding
            if (apdu.length < 7) {
                return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
            }
            size = (apdu[5] & 0xff) * 256;
            size += apdu[6] & 0xff;
        }
        Logger.d(TAG, String.format(Locale.US, "nfcEngagementHandleReadBinary: offset=%d size=%d", offset, size));

        if (offset >= contents.length) {
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS;
        }
        if ((offset + size) > contents.length) {
            return NfcUtil.STATUS_WORD_END_OF_FILE_REACHED;
        }

        byte[] response = new byte[size + NfcUtil.STATUS_WORD_OK.length];
        System.arraycopy(contents, offset, response, 0, size);
        System.arraycopy(NfcUtil.STATUS_WORD_OK, 0, response, size, NfcUtil.STATUS_WORD_OK.length);
        return response;
    }

    private @NonNull
    byte[] handleUpdateBinary(@NonNull byte[] unusedApdu) {
        Logger.d(TAG, "in handleUpdateBinary");
        return NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED;
    }

    private @NonNull
    byte[] handleEnvelope(@NonNull byte[] apdu) {
        Logger.d(TAG, "in handleEnvelope");
        if (mNfcDataTransport == null) {
            reportError(new Error("Received NFC ENVELOPE but active transport isn't NFC."));
            return NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED;
        }
        //mNfcDataTransport.onEnvelopeApduReceived(apdu);
        // Response will be posted by onEnvelopeApduReceived() when needed...
        return null;
    }

    private @NonNull
    byte[] handleResponse(@NonNull byte[] apdu) {
        Logger.d(TAG, "in handleResponse");
        if (mNfcDataTransport == null) {
            reportError(new Error("Received NFC GET RESPONSE but active transport isn't NFC."));
            return NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED;
        }
        //mNfcDataTransport.onGetResponseApduReceived(apdu);
        // Response will be posted by onEnvelopeApduReceived() when needed...
        return NfcUtil.STATUS_WORD_OK;
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
            t.setListener(null, null);
            if (t != transport) {
                t.close();
            }
        }
        mTransports.clear();

        reportDeviceConnected(transport);
    }


    // Note: The report*() methods are safe to call from any thread.

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
        void onDeviceConnecting();
        void onDeviceConnected(DataTransport transport);
        void onError(@NonNull Throwable error);
    }
}