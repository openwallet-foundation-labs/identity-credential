package com.android.identity;

import static java.lang.annotation.RetentionPolicy.SOURCE;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;

public class NfcEngagementHelper {
    private static final String TAG = "NfcEngagementHelper";

    private final PresentationSession mPresentationSession;
    private final Context mContext;
    private final KeyPair mEphemeralKeyPair;
    private List<ConnectionMethod> mStaticHandoverConnectionMethods;
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

    private byte[] mHandoverSelectMessage;
    private byte[] mHandoverRequestMessage;

    private static final int NEGOTIATED_HANDOVER_STATE_NOT_STARTED = 0;
    private static final int NEGOTIATED_HANDOVER_STATE_EXPECT_SERVICE_SELECT = 1;
    private static final int NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_REQUEST = 2;
    private static final int NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_SELECT = 3;

    @Retention(SOURCE)
    @IntDef(
            flag = true,
            value = {
                    NEGOTIATED_HANDOVER_STATE_NOT_STARTED,
                    NEGOTIATED_HANDOVER_STATE_EXPECT_SERVICE_SELECT,
                    NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_REQUEST,
                    NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_SELECT,
            })
    private @interface NegotiatedHandoverState {
    }

    private boolean mUsingNegotiatedHandover = false;
    private @NegotiatedHandoverState int mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED;

    private byte[] mSelectedNfcFile;
    private long mTimeStartedSettingUpTransports;
    private boolean mTransportsAreSettingUp;
    private boolean mTestingDoNotStartTransports = false;

    NfcEngagementHelper(@NonNull Context context,
                        @NonNull PresentationSession presentationSession,
                        @NonNull DataTransportOptions options,
                        @NonNull Listener listener,
                        @NonNull Executor executor) {
        mContext = context;
        mPresentationSession = presentationSession;
        mListener = listener;
        mExecutor = executor;
        mEphemeralKeyPair = mPresentationSession.getEphemeralKeyPair();
        mOptions = options;
        Logger.d(TAG, "Starting");
    }

    public void close() {
        mInhibitCallbacks = true;
        if (mTransports != null) {
            int numTransportsClosed = 0;
            for (DataTransport transport : mTransports) {
                transport.close();
                numTransportsClosed += 1;
            }
            Logger.d(TAG, String.format(Locale.US,"Closed %d transports", numTransportsClosed));
            mTransports = null;
        }
        mTransportsAreSettingUp = false;
        mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED;
        mSelectedNfcFile = null;
    }

    public @NonNull
    byte[] getDeviceEngagement() {
        return mEncodedDeviceEngagement;
    }

    public @NonNull
    byte[] getHandover() {
        return mEncodedHandover;
    }

    void testingDoNotStartTransports() {
        mTestingDoNotStartTransports = true;
    }

    // Used by both static and negotiated handover... safe to be called multiple times.
    private void setupTransports(@NonNull List<ConnectionMethod> connectionMethods) {
        if (mTransportsAreSettingUp) {
            return;
        }
        mTransportsAreSettingUp = true;

        if (mTestingDoNotStartTransports) {
            Logger.d(TAG, "Test mode, not setting up transports");
            return;
        }

        Logger.d(TAG, "Setting up transports");
        mTransports = new ArrayList<>();
        mTimeStartedSettingUpTransports = System.currentTimeMillis();

        byte[] encodedEDeviceKeyBytes = Util.cborEncode(Util.cborBuildTaggedByteString(
                Util.cborEncode(Util.cborBuildCoseKey(mEphemeralKeyPair.getPublic()))));

        // Need to disambiguate the connection methods here to get e.g. two ConnectionMethods
        // if both BLE modes are available at the same time.
        List<ConnectionMethod> disambiguatedMethods = ConnectionMethod.disambiguate(connectionMethods);
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
        long setupTimeMillis = System.currentTimeMillis() - mTimeStartedSettingUpTransports;
        Logger.d(TAG, String.format(Locale.US, "All transports set up in %d msec", setupTimeMillis));
    }

    public void nfcOnDeactivated(int reason) {
        Logger.d(TAG, String.format(Locale.US, "nfcOnDeactivated reason %d", reason));
    }

    public @NonNull byte[] nfcProcessCommandApdu(@NonNull byte[] apdu) {
        byte[] ret = null;

        if (Logger.isDebugEnabled()) {
            Logger.dHex(TAG, "nfcProcessCommandApdu: apdu", apdu);
        }

        int commandType = NfcUtil.nfcGetCommandType(apdu);
        switch (commandType) {
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
            default:
                Logger.w(TAG, String.format(Locale.US,
                        "nfcProcessCommandApdu: command type 0x%04x not handled", commandType));
                ret = NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED;
                break;
        }

        return ret;
    }

    private @NonNull
    byte[] handleSelectByAid(@NonNull byte[] apdu) {
        if (apdu.length < 12) {
            Logger.w(TAG, "handleSelectByAid: unexpected APDU length " + apdu.length);
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }
        if (Arrays.equals(Arrays.copyOfRange(apdu, 5, 12), NfcUtil.AID_FOR_TYPE_4_TAG_NDEF_APPLICATION)) {
            Logger.d(TAG, "handleSelectByAid: NDEF application selected");
            mUpdateBinaryData = null;
            return NfcUtil.STATUS_WORD_OK;
        }
        Logger.dHex(TAG, "handleSelectByAid: Unexpected AID selected in APDU", apdu);
        return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
    }

    private @NonNull
    byte[] calculateNegotiatedHandoverInitialNdefMessage() {
        // From 18013-5: When Negotiated Handover is used, the mdoc shall include the
        // "urn:nfc:sn:handover" service in a Service Parameter record in the Initial NDEF
        // message provided to the mdoc reader

        // From Connection Handover 1.5 section 4.1.2: For Negotiated Handover in
        // Reader/Writer Mode, handover messages SHALL be exchanged as described for the
        // Single response communication mode in [TNEP]. The Service name URI for the
        // service announced in the Service Parameter record SHALL be "urn:nfc:sn:handover"

        // From Tag NDEF Exchange Protocol 1.0 section 4.1.2: The Service Parameter Record
        // is a short NDEF Record that does not include an ID field, but its Type field
        // contains the NFC Forum Well Known Type (see [RTD]) “Tp”.
        //

        byte[] serviceNameUriUtf8 = "urn:nfc:sn:handover".getBytes(UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // t_wait = 2^(wt_int/4 - 1) milliseconds, where wt_int is six bits so
            //
            //     wt_int = 0   ->  t_wait = 2^(0/4 - 1) ms = 2^-1 ms = 0.50 ms
            //     wt_int = 1   ->  t_wait = 2^(1/4 - 1) ms = 2^-0.75 ms = 0.59 ms
            //     wt_int = 2   ->  t_wait = 2^(2/4 - 1) ms = 2^-0.50 ms = 0.71 ms
            //     ..
            //     wt_int = 8   ->  t_wait = 2^(8/4 - 1) ms = 2^1 ms = 2 ms
            //     ..
            //     wt_int = 32  ->  t_wait = 2^(32/4 - 1) ms = 2^7 ms = 128 ms
            //     ..
            //     wt_int = 48  ->  t_wait = 2^(48/4 - 1) ms = 2^11 ms = 2048 ms
            //     ..
            //     wt_int = 63  ->  t_wait = 2^(63/4 - 1) ms = 2^14.75 ms = 27554 ms
            //
            int wt_int = 0;
            // The payload of the record is defined in Tag NDEF Exchange Protocol 1.0 section 4.1.2:
            baos.write(0x10);   // TNEP version: 1.0
            baos.write(serviceNameUriUtf8.length);
            baos.write(serviceNameUriUtf8);
            baos.write(0x00);   // TNEP Communication Mode: Single Response communication mode
            baos.write(wt_int);    // Minimum Waiting Time
            baos.write(0x00);   // Maximum Number of Waiting Time Extensions
            baos.write(0xff);   // Maximum NDEF Message Size (upper 8 bits)
            baos.write(0xff);   // Maximum NDEF Message Size (lower 8 bits)
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        byte[] payload = baos.toByteArray();
        NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
                "Tp".getBytes(UTF_8),
                null,
                payload);
        NdefRecord[] arrayOfRecords = new NdefRecord[1];
        arrayOfRecords[0] = record;
        NdefMessage message = new NdefMessage(arrayOfRecords);
        return message.toByteArray();
    }

    private @NonNull
    byte[] handleSelectFile(@NonNull byte[] apdu) {
        if (apdu.length < 7) {
            Logger.w(TAG, "handleSelectFile: unexpected APDU length " + apdu.length);
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }
        int fileId = (apdu[5] & 0xff) * 256 + (apdu[6] & 0xff);
        Logger.d(TAG, String.format(Locale.US, "handleSelectFile: fileId 0x%04x", fileId));
        // We only support two files
        if (fileId == NfcUtil.CAPABILITY_CONTAINER_FILE_ID) {
            // This is defined in NFC Forum Type 4 Tag Technical Specification v1.2 table 6
            // and section 4.7.3 NDEF-File_Ctrl_TLV
            byte fileWriteAccessCondition = mUsingNegotiatedHandover ? 0x00 : (byte) 0xff;
            mSelectedNfcFile = new byte[]{
                    (byte) 0x00, (byte) 0x0f,  // size of capability container '00 0F' = 15 bytes
                    (byte) 0x20,               // mapping version v2.0
                    (byte) 0x7f, (byte) 0xff,  // maximum response data length '7F FF'
                    (byte) 0x7f, (byte) 0xff,  // maximum command data length '7F FF'
                    (byte) 0x04, (byte) 0x06,  // NDEF File Control TLV
                    (byte) 0xe1, (byte) 0x04,  // NDEF file identifier 'E1 04'
                    (byte) 0x7f, (byte) 0xff,  // maximum NDEF file size '7F FF'
                    (byte) 0x00,               // file read access condition (allow read)
                    fileWriteAccessCondition   // file write access condition (allow/disallow write)
            };
            Logger.d(TAG, "handleSelectFile: CAPABILITY file selected");
        } else if (fileId == NfcUtil.NDEF_FILE_ID) {
            if (mUsingNegotiatedHandover) {
                Logger.d(TAG, "handleSelectFile: NDEF file selected and using negotiated handover");
                byte[] message = calculateNegotiatedHandoverInitialNdefMessage();
                Logger.dHex(TAG, "handleSelectFile: Initial NDEF message", message);
                byte[] fileContents = new byte[message.length + 2];
                fileContents[0] = (byte) (message.length / 256);
                fileContents[1] = (byte) (message.length & 0xff);
                System.arraycopy(message, 0, fileContents, 2, message.length);
                mSelectedNfcFile = fileContents;
                mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_EXPECT_SERVICE_SELECT;
            } else {
                Logger.d(TAG, "handleSelectFile: NDEF file selected and using static handover - calculating handover message");
                mEncodedDeviceEngagement = generateDeviceEngagement();
                byte[] hsMessage = NfcUtil.createNdefMessageHandoverSelect(
                        mStaticHandoverConnectionMethods,
                        mEncodedDeviceEngagement);
                Logger.dHex(TAG, "handleSelectFile: Handover Select", hsMessage);
                byte[] fileContents = new byte[hsMessage.length + 2];
                fileContents[0] = (byte) (hsMessage.length / 256);
                fileContents[1] = (byte) (hsMessage.length & 0xff);
                System.arraycopy(hsMessage, 0, fileContents, 2, hsMessage.length);
                mSelectedNfcFile = fileContents;
                mHandoverSelectMessage = hsMessage;
                mHandoverRequestMessage = null;
                mEncodedHandover = Util.cborEncode(new CborBuilder()
                        .addArray()
                        .add(mHandoverSelectMessage)   // Handover Select message
                        .add(SimpleValue.NULL)  // Handover Request message
                        .end()
                        .build().get(0));
                Logger.dCbor(TAG, "NFC static DeviceEngagement", mEncodedDeviceEngagement);
                Logger.dCbor(TAG, "NFC static Handover", mEncodedHandover);

                // Technically we should ensure the transports are up until sending the response...
                setupTransports(mStaticHandoverConnectionMethods);
            }
        } else {
            Logger.w(TAG, "handleSelectFile: Unknown file selected with id 0x%04x");
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }
        return NfcUtil.STATUS_WORD_OK;
    }

    private @NonNull
    byte[] handleReadBinary(@NonNull byte[] apdu) {
        if (apdu.length < 5) {
            Logger.w(TAG, "handleReadBinary: unexpected APDU length " + apdu.length);
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }
        if (mSelectedNfcFile == null) {
            Logger.w(TAG, "handleReadBinary: no file selected -> STATUS_WORD_FILE_NOT_FOUND");
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
        Logger.d(TAG, String.format(Locale.US, "offset %d size %d contentSize %d", offset, size, contents.length));
        if (offset >= contents.length) {
            Logger.w(TAG, String.format(Locale.US,
                    "handleReadBinary: starting offset %d beyond file end %d -> "
                    + "STATUS_WORD_WRONG_PARAMETERS",
                    offset, contents.length));
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS;
        }
        if ((offset + size) > contents.length) {
            Logger.w(TAG, String.format(Locale.US,
                    "handleReadBinary: ending offset %d beyond file end %d -> "
                            + "STATUS_WORD_END_OF_FILE_REACHED",
                    offset + size, contents.length));
            return NfcUtil.STATUS_WORD_END_OF_FILE_REACHED;
        }

        byte[] response = new byte[size + NfcUtil.STATUS_WORD_OK.length];
        System.arraycopy(contents, offset, response, 0, size);
        System.arraycopy(NfcUtil.STATUS_WORD_OK, 0, response, size, NfcUtil.STATUS_WORD_OK.length);
        Logger.d(TAG, String.format(Locale.US,
                "handleReadBinary: returning %d bytes from offset %d (file size %d)",
                size, offset, contents.length));
        return response;
    }


    private byte[] mUpdateBinaryData = null;

    private @NonNull
    byte[] handleUpdateBinary(@NonNull byte[] apdu) {
        if (apdu.length < 5) {
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }
        int offset = (apdu[2] & 0xff) * 256 + (apdu[3] & 0xff);
        int size = apdu[4] & 0xff;
        Logger.d(TAG, String.format(Locale.US,
                "handleUpdateBinary: offset=%d size=%d apdu.length=%d",
                offset, size, apdu.length));

        int dataSize = apdu.length - 5;
        if (dataSize != size) {
            Logger.e(TAG, String.format(Locale.US,
                    "Expected length embedded in APDU to be %d but found %d", dataSize, size));
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }

        // This code implements the procedure specified by
        //
        //  Type 4 Tag Technical Specification Version 1.2 section 7.5.5 NDEF Write Procedure

        byte[] payload = new byte[dataSize];
        System.arraycopy(apdu, 5, payload, 0, dataSize);
        Logger.dHex(TAG,"handleUpdateBinary: payload", payload);
        if (offset == 0) {
            if (payload.length == 2) {
                if (payload[0] == 0x00 && payload[1] == 0x00) {
                    Logger.d(TAG, "handleUpdateBinary: Reset length message");
                    if (mUpdateBinaryData != null) {
                        Logger.w(TAG, "Got reset but we are already active");
                        return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
                    }
                    mUpdateBinaryData = new byte[0];
                    return NfcUtil.STATUS_WORD_OK;
                } else {
                    int length = (apdu[5] & 0xff) * 256 + (apdu[6] & 0xff);
                    Logger.d(TAG, String.format(Locale.US,
                            "handleUpdateBinary: Update length message with length %d", length));

                    if (mUpdateBinaryData == null) {
                        Logger.w(TAG, "Got length but we are not active");
                        return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
                    }

                    if (length != mUpdateBinaryData.length) {
                        Logger.w(TAG, String.format(Locale.US,
                                "Length %d doesn't match received data of %d bytes",
                                length, mUpdateBinaryData.length));
                        return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
                    }

                    // At this point we got the whole NDEF message that the reader wanted to send.
                    byte[] ndefMessage = mUpdateBinaryData;
                    mUpdateBinaryData = null;
                    return handleUpdateBinaryNdefMessage(ndefMessage);
                }
            } else {
                if (mUpdateBinaryData != null) {
                    Logger.w(TAG, "Got data in single UPDATE_BINARY but we are already active");
                    return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
                }

                Logger.dHex(TAG, "handleUpdateBinary: single UPDATE_BINARY message with payload: ", payload);

                byte[] ndefMessage = Arrays.copyOfRange(payload, 2, payload.length);
                return handleUpdateBinaryNdefMessage(ndefMessage);
            }
        } else if (offset == 1) {
            Logger.w(TAG, String.format(Locale.US, "Unexpected offset %d", offset));
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        } else {
            // offset >= 2

            if (mUpdateBinaryData == null) {
                Logger.w(TAG, "Got data but we are not active");
                return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
            }

            Logger.dHex(TAG, String.format(Locale.US,
                    "handleUpdateBinary: Data message offset %d with payload: ", offset), payload);

            int newLength = offset - 2 + payload.length;
            if (mUpdateBinaryData.length < newLength) {
                mUpdateBinaryData = Arrays.copyOf(mUpdateBinaryData, newLength);
            }
            System.arraycopy(payload, 0, mUpdateBinaryData, offset - 2, payload.length);

            return NfcUtil.STATUS_WORD_OK;
        }
    }

    private @NonNull
    byte[] handleUpdateBinaryNdefMessage(@NonNull byte[] ndefMessage) {
        // Falls through here only if we have a full NDEF message.
        if (mNegotiatedHandoverState == NEGOTIATED_HANDOVER_STATE_EXPECT_SERVICE_SELECT) {
            return handleServiceSelect(ndefMessage);
        } else if (mNegotiatedHandoverState == NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_REQUEST) {
            return handleHandoverRequest(ndefMessage);
        } else {
            Logger.w(TAG, "Unexpected state " + mNegotiatedHandoverState);
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND;
        }
    }

    private @NonNull
    byte[] handleServiceSelect(@NonNull byte[] ndefMessagePayload) {
        Logger.dHex(TAG, "handleServiceSelect: payload", ndefMessagePayload);
        // NDEF message specified in NDEF Exchange Protocol 1.0: 4.2.2 Service Select Record
        NdefMessage message = null;
        try {
            message = new NdefMessage(ndefMessagePayload);
        } catch (FormatException e) {
            Logger.e(TAG, "handleServiceSelect: Error parsing NdefMessage", e);
            mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED;
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS;
        }
        NdefRecord[] records = message.getRecords();
        if (records.length != 1) {
            Logger.e(TAG, "handleServiceSelect: Expected one NdefRecord, found " + records.length);
            mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED;
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS;
        }
        NdefRecord record = records[0];
        byte[] expectedPayload = " urn:nfc:sn:handover".getBytes(UTF_8);
        expectedPayload[0] = (byte) (expectedPayload.length - 1);
        if (record.getTnf() != NdefRecord.TNF_WELL_KNOWN
                || !Arrays.equals(record.getType(), "Ts".getBytes(UTF_8))
                || record.getPayload() == null
                || !Arrays.equals(record.getPayload(), expectedPayload)) {
            Logger.e(TAG, "handleServiceSelect: NdefRecord is malformed");
            mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED;
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS;
        }

        Logger.d(TAG, "Service Select NDEF message has been validated");

        // From NDEF Exchange Protocol 1.0: 4.3 TNEP Status Message
        // If the NFC Tag Device has received a Service Select Message with a known
        // Service, it will return a TNEP Status Message to confirm a successful
        // Service selection.

        byte[] statusMessage = calculateStatusMessage(0x00);
        Logger.dHex(TAG, "handleServiceSelect: Status message", statusMessage);
        byte[] fileContents = new byte[statusMessage.length + 2];
        fileContents[0] = (byte) (statusMessage.length / 256);
        fileContents[1] = (byte) (statusMessage.length & 0xff);
        System.arraycopy(statusMessage, 0, fileContents, 2, statusMessage.length);
        mSelectedNfcFile = fileContents;
        mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_REQUEST;
        return NfcUtil.STATUS_WORD_OK;
    }

    private @NonNull
    byte[] handleHandoverRequest(@NonNull byte[] ndefMessagePayload) {
        Logger.dHex(TAG, "handleHandoverRequest: payload", ndefMessagePayload);
        NdefMessage message = null;
        try {
            message = new NdefMessage(ndefMessagePayload);
        } catch (FormatException e) {
            Logger.e(TAG, "handleHandoverRequest: Error parsing NdefMessage", e);
            mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED;
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS;
        }
        NdefRecord[] records = message.getRecords();
        if (records.length < 2) {
            Logger.e(TAG, "handleServiceSelect: Expected at least two NdefRecords, found " + records.length);
            mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED;
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS;
        }

        List<ConnectionMethod> parsedCms = new ArrayList<>();
        for (NdefRecord r : records) {
            // Handle Handover Request record for NFC Forum Connection Handover specification
            // version 1.5 (encoded as 0x15 below).
            //
            if (r.getTnf() == NdefRecord.TNF_WELL_KNOWN
                    && Arrays.equals(r.getType(), "Hr".getBytes(UTF_8))) {
                byte[] payload = r.getPayload();
                if (payload.length >= 1 && payload[0] == 0x15) {
                    byte[] hrEmbMessageData = Arrays.copyOfRange(payload, 1, payload.length);
                    NdefMessage hrEmbMessage = null;
                    try {
                        hrEmbMessage = new NdefMessage(hrEmbMessageData);
                    } catch (FormatException e) {
                        Logger.e(TAG, "handleHandoverRequest: Error parsing embedded HR NdefMessage", e);
                        mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED;
                        return NfcUtil.STATUS_WORD_WRONG_PARAMETERS;
                    }
                }
                NdefRecord[] hrEmbMessageRecords = message.getRecords();
                // TODO: actually look at these records...
            }

            // This parses the various carrier specific NDEF records, see
            // DataTransport.parseNdefRecord() for details.
            //
            if ((r.getTnf() == NdefRecord.TNF_MIME_MEDIA) ||
                    (r.getTnf() == NdefRecord.TNF_EXTERNAL_TYPE)) {
                ConnectionMethod cm = ConnectionMethod.fromNdefRecord(r, false);
                if (cm != null) {
                    Logger.d(TAG, "Found connectionMethod: " + cm);
                    parsedCms.add(cm);
                }
            }
        }

        if (parsedCms.size() < 1) {
            Logger.w(TAG, "No connection methods found. Bailing.");
            mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED;
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS;
        }
        List<ConnectionMethod> disambiguatedCms = ConnectionMethod.disambiguate(parsedCms);
        for (ConnectionMethod cm : disambiguatedCms) {
            Logger.d(TAG, "Have connectionMethod: " + cm);
        }

        // For now, just pick the first method...
        ConnectionMethod method = disambiguatedCms.get(0);
        List<ConnectionMethod> listWithSelectedConnectionMethod = new ArrayList<>();
        listWithSelectedConnectionMethod.add(method);

        mEncodedDeviceEngagement = generateDeviceEngagement();
        byte[] hsMessage = NfcUtil.createNdefMessageHandoverSelect(
                listWithSelectedConnectionMethod,
                mEncodedDeviceEngagement);
        byte[] fileContents = new byte[hsMessage.length + 2];
        fileContents[0] = (byte) (hsMessage.length / 256);
        fileContents[1] = (byte) (hsMessage.length & 0xff);
        System.arraycopy(hsMessage, 0, fileContents, 2, hsMessage.length);
        mSelectedNfcFile = fileContents;
        mNegotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_SELECT;

        mHandoverSelectMessage = hsMessage;
        mHandoverRequestMessage = ndefMessagePayload;
        mEncodedHandover = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(mHandoverSelectMessage)   // Handover Select message
                .add(mHandoverRequestMessage)  // Handover Request message
                .end()
                .build().get(0));
        Logger.dCbor(TAG, "NFC negotiated DeviceEngagement", mEncodedDeviceEngagement);
        Logger.dCbor(TAG, "NFC negotiated Handover", mEncodedHandover);

        // Technically we should ensure the transports are up until sending the response...
        setupTransports(listWithSelectedConnectionMethod);
        return NfcUtil.STATUS_WORD_OK;
    }

    private @NonNull byte[] calculateStatusMessage(int statusCode) {
        byte[] payload = new byte[] {(byte) statusCode};
        NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
                "Te".getBytes(UTF_8),
                null,
                payload);
        NdefRecord[] arrayOfRecords = new NdefRecord[1];
        arrayOfRecords[0] = record;
        NdefMessage message = new NdefMessage(arrayOfRecords);
        return message.toByteArray();
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

    public static class Builder {
        NfcEngagementHelper mHelper;

        public Builder(@NonNull Context context,
                       @NonNull PresentationSession presentationSession,
                       @NonNull DataTransportOptions options,
                       @NonNull Listener listener, @NonNull Executor executor) {
            mHelper = new NfcEngagementHelper(context,
                    presentationSession,
                    options,
                    listener,
                    executor);
        }

        public Builder useStaticHandover(@NonNull List<ConnectionMethod> connectionMethods) {
            mHelper.mStaticHandoverConnectionMethods = ConnectionMethod.combine(connectionMethods);
            return this;
        }

        public Builder useNegotiatedHandover() {
            mHelper.mUsingNegotiatedHandover = true;
            return this;
        }

        public @NonNull NfcEngagementHelper build() {
            if (mHelper.mUsingNegotiatedHandover && mHelper.mStaticHandoverConnectionMethods != null) {
                throw new IllegalStateException("Can't use static and negotiated handover at the same time.");
            }
            return mHelper;
        }
    }
}