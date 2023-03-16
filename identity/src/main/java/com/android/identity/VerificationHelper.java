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
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.util.Base64;
import android.util.Log;

import androidx.core.util.Pair;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Executor;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;

/**
 * Helper used for engaging with and receiving documents from a remote mdoc verifier device.
 *
 * <p>This class implements the interface between an <em>mdoc</em> and <em>mdoc verifier</em> using
 * the connection setup and device retrieval interfaces defined in
 * <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5:2021</a>.
 *
 * <p>Reverse engagement as per drafts of 18013-7 and 23220-4 is supported. These protocols
 * are not finalized so should only be used for testing.
 */
// Suppress with NotCloseable since we actually don't hold any resources needing to be
// cleaned up at object finalization time.
@SuppressWarnings("NotCloseable")
public class VerificationHelper {

    private static final String TAG = "VerificationHelper";
    private Context mContext;
    DataTransport mDataTransport;
    Listener mListener;
    KeyPair mEphemeralKeyPair;
    SessionEncryptionReader mSessionEncryptionReader;
    byte[] mDeviceEngagement;
    byte[] mEncodedSessionTranscript;
    Executor mListenerExecutor;
    IsoDep mNfcIsoDep;
    // The handover used
    //
    private boolean mUseTransportSpecificSessionTermination;
    private boolean mSendSessionTerminationMessage = true;
    private DataTransportOptions mOptions;

    // If this is non-null it means we're using Reverse Engagement
    //
    private List<ConnectionMethod> mReverseEngagementConnectionMethods;
    private List<DataTransport> mReverseEngagementListeningTransports;
    private int mNumReverseEngagementTransportsStillSettingUp;
    private List<ConnectionMethod> mConnectionMethodsForReaderEngagement;
    private EngagementGenerator mReaderEngagementGenerator;
    private byte[] mReaderEngagement;

    VerificationHelper() {
    }

    private void start() {
        if (mReverseEngagementConnectionMethods != null) {
            setupReverseEngagement();
        }
    }

    private void
    setupReverseEngagement() {
        Logger.d(TAG, "Setting up reverse engagement");

        mReverseEngagementListeningTransports = new ArrayList<>();
        // Need to disambiguate the connection methods here to get e.g. two ConnectionMethods
        // if both BLE modes are available at the same time.
        List<ConnectionMethod> disambiguatedMethods =
                ConnectionMethod.disambiguate(mReverseEngagementConnectionMethods);
        for (ConnectionMethod cm : disambiguatedMethods) {
            DataTransport transport = cm.createDataTransport(mContext, DataTransport.ROLE_MDOC_READER, mOptions);
            mReverseEngagementListeningTransports.add(transport);
            // TODO: we may want to have the DataTransport actually give us a ConnectionMethod,
            //   for example consider the case where a HTTP-based transport uses a cloud-service
            //   to relay messages.
        }

        // Careful, we're using the user-provided Executor below so these callbacks might happen
        // in another thread than we're in right now. For example this happens if using
        // ThreadPoolExecutor.
        //
        // In particular, it means we need locking around `mNumTransportsStillSettingUp`. We'll
        // use the monitor for the VerificationHelper object for to achieve that.
        //
        final VerificationHelper helper = this;
        mNumReverseEngagementTransportsStillSettingUp = 0;

        // Calculate ReaderEngagement as we're setting up methods
        mReaderEngagementGenerator = new EngagementGenerator(
                mEphemeralKeyPair.getPublic(),
                EngagementGenerator.ENGAGEMENT_VERSION_1_1);

        mConnectionMethodsForReaderEngagement = new ArrayList<>();
        synchronized (helper) {
            for (DataTransport transport : mReverseEngagementListeningTransports) {
                transport.setListener(new DataTransport.Listener() {
                    @Override
                    public void onConnectionMethodReady() {
                        Logger.d(TAG, "onConnectionMethodReady for " + transport);
                        synchronized (helper) {
                            mConnectionMethodsForReaderEngagement.add(transport.getConnectionMethod());
                            mNumReverseEngagementTransportsStillSettingUp -= 1;
                            if (mNumReverseEngagementTransportsStillSettingUp == 0) {
                                allReverseEngagementTransportsAreSetup();
                            }
                        }
                    }

                    @Override
                    public void onConnecting() {
                        Logger.d(TAG, "onConnecting for " + transport);
                        reverseEngagementPeerIsConnecting(transport);
                    }

                    @Override
                    public void onConnected() {
                        Logger.d(TAG, "onConnected for " + transport);
                        reverseEngagementPeerHasConnected(transport);
                    }

                    @Override
                    public void onDisconnected() {
                        Logger.d(TAG, "onListeningPeerDisconnected for " + transport);
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
                        handleOnMessageReceived();
                    }

                    @Override
                    public void onTransportSpecificSessionTermination() {
                        Logger.d(TAG, "Received transport-specific session termination");
                        transport.close();
                        reportDeviceDisconnected(true);
                    }

                }, mListenerExecutor);
                Logger.d(TAG, "Connecting transport " + transport);
                transport.connect();
                mNumReverseEngagementTransportsStillSettingUp += 1;
            }
        }
    }

    // TODO: handle the case where a transport never calls onConnectionMethodReady... that
    //  is, set up a timeout to call this.
    //
    void allReverseEngagementTransportsAreSetup() {
        Logger.d(TAG, "All reverse engagement listening transports are now set up");

        mReaderEngagementGenerator.setConnectionMethods(mConnectionMethodsForReaderEngagement);
        mReaderEngagement = mReaderEngagementGenerator.generate();
        mReaderEngagementGenerator = null;

        reportReaderEngagementReady(mReaderEngagement);
    }

    void reverseEngagementPeerIsConnecting(@NonNull DataTransport transport) {
    }

    void reverseEngagementPeerHasConnected(@NonNull DataTransport transport) {
        // stop listening on other transports
        //
        Logger.d(TAG, "Peer has connected on transport " + transport
                + " - shutting down other transports");
        for (DataTransport t : mReverseEngagementListeningTransports) {
            if (t != transport) {
                t.setListener(null, null);
                t.close();
            }
        }
        mReverseEngagementListeningTransports.clear();
        mDataTransport = transport;

        // We're connected to the remote device but we don't want to let the application
        // know this until we've received the first message with DeviceEngagement CBOR...
    }


    /**
     * Processes a {@link Tag} received when in NFC reader mode.
     *
     * <p>Applications should call this method in their
     * {@link android.nfc.NfcAdapter.ReaderCallback#onTagDiscovered(Tag)} callback.
     *
     * @param tag the tag.
     * @throws IllegalStateException if called while not listening.
     */
    public void
    nfcProcessOnTagDiscovered(@NonNull Tag tag) {
        Logger.d(TAG, "Tag discovered!");

        // Find IsoDep since we're skipping NDEF checks and doing everything ourselves via APDUs
        for (String tech : tag.getTechList()) {
            if (tech.equals(IsoDep.class.getName())) {
                mNfcIsoDep = IsoDep.get(tag);
                // If we're doing QR code engagement _and_ NFC data transfer
                // it's possible that we're now in a state where we're
                // waiting for the reader to be in the NFC field... see
                // also comment in connect() for this case...
                if (mDataTransport instanceof DataTransportNfc) {
                    Logger.d(TAG, "NFC data transfer + QR engagement, "
                            + "reader is now in field");

                    startNfcDataTransport();

                    // At this point we're done, don't start NFC handover.
                    return;
                }
            }
        }

        if (mNfcIsoDep == null) {
            Logger.d(TAG, "no IsoDep technology found");
            return;
        }

        startNfcHandover();
    }

    private void startNfcDataTransport() {
        // connect() may block, run in thread
        Thread connectThread = new Thread() {
            @Override
            public void run() {
                try {
                    mNfcIsoDep.connect();
                    mNfcIsoDep.setTimeout(20 * 1000);  // 20 seconds
                } catch (IOException e) {
                    reportError(e);
                    return;
                }
                mListenerExecutor.execute(
                        () -> connectWithDataTransport(mDataTransport));
            }
        };
        connectThread.start();
    }

    /**
     * Set device engagement received via QR code.
     *
     * <p>This method parses the textual form of QR code device engagement as specified in
     * <em>ISO/IEC 18013-5</em> section 8.2 <em>Device Engagement</em>.
     *
     * <p>If a valid device engagement is received the
     * {@link Listener#onDeviceEngagementReceived(List)} will be called. If an error occurred it
     * is reported using the {@link Listener#onError(Throwable)} callback.
     *
     * <p>This method must be called before {@link #connect(ConnectionMethod)}.
     *
     * @param qrDeviceEngagement textual form of QR device engagement.
     * @throws IllegalStateException if called after {@link #connect(ConnectionMethod)}.
     */
    public void setDeviceEngagementFromQrCode(@NonNull String qrDeviceEngagement) {
        if (mDataTransport != null) {
            throw new IllegalStateException("Cannot be called after connect()");
        }
        Uri uri = Uri.parse(qrDeviceEngagement);
        if (uri != null && uri.getScheme() != null
                && uri.getScheme().equals("mdoc")) {
            byte[] encodedDeviceEngagement = Base64.decode(
                    uri.getEncodedSchemeSpecificPart(),
                    Base64.URL_SAFE | Base64.NO_PADDING);
            if (encodedDeviceEngagement != null) {
                Logger.dCbor(TAG, "Device Engagement from QR code", encodedDeviceEngagement);

                DataItem handover = SimpleValue.NULL;
                setDeviceEngagement(encodedDeviceEngagement, handover);

                EngagementParser engagementParser = new EngagementParser(encodedDeviceEngagement);
                EngagementParser.Engagement engagement = engagementParser.parse();

                List<byte[]> readerAvailableDeviceRetrievalMethods =
                        Util.extractDeviceRetrievalMethods(
                                encodedDeviceEngagement);

                List<ConnectionMethod> connectionMethods = engagement.getConnectionMethods();
                if (!connectionMethods.isEmpty()) {
                    reportDeviceEngagementReceived(connectionMethods);
                    return;
                }
            }
        }
        reportError(new IllegalArgumentException(
                "Invalid QR Code device engagement text: " + qrDeviceEngagement));
    }

    private @NonNull byte[] transceive(@NonNull IsoDep isoDep, @NonNull byte[] apdu)
            throws IOException {
        Logger.dHex(TAG, "transceive: Sending APDU", apdu);
        byte[] ret = isoDep.transceive(apdu);
        Logger.dHex(TAG, "q: Received APDU", ret);
        return ret;
    }

    private byte[] readBinary(@NonNull IsoDep isoDep, int offset, int size)
            throws IOException {
        byte[] apdu;
        byte[] ret;

        apdu = NfcUtil.createApduReadBinary(offset, size);
        ret = transceive(isoDep, apdu);
        if (ret.length < 2 || ret[ret.length - 2] != ((byte) 0x90) || ret[ret.length - 1] != ((byte) 0x00)) {
            Logger.eHex(TAG, "Error sending READ_BINARY command, ret", ret);
            return null;
        }
        return Arrays.copyOfRange(ret, 0, ret.length - 2);
    }

    private byte[] ndefReadMessage(@NonNull IsoDep isoDep, int nWait)
            throws IOException {
        byte[] apdu;
        byte[] ret;

        // TODO: Allow up to nWait retries

        apdu = NfcUtil.createApduReadBinary(0x0000, 2);
        ret = transceive(isoDep, apdu);
        if (ret.length != 4 || ret[2] != ((byte) 0x90) || ret[3] != ((byte) 0x00)) {
            Logger.eHex(TAG, "Error sending second READ_BINARY command for length, ret", ret);
            return null;
        }
        int replyLen = ((int) ret[0] & 0xff) * 256 + ((int) ret[1] & 0xff);

        apdu = NfcUtil.createApduReadBinary(0x0002, replyLen);
        ret = transceive(isoDep, apdu);
        if (ret.length != replyLen + 2 || ret[replyLen] != ((byte) 0x90) || ret[replyLen + 1] != ((byte) 0x00)) {
            Logger.eHex(TAG, "Error sending second READ_BINARY command for payload, ret", ret);
            return null;
        }
        return Arrays.copyOfRange(ret, 0, ret.length - 2);
    }

    private byte[] ndefTransact(@NonNull IsoDep isoDep, @NonNull byte[] ndefMessage,
                                double tWaitMillis, int nWait)
            throws IOException {
        byte[] apdu;
        byte[] ret;

        Logger.dHex(TAG, "ndefTransact: writing NDEF message", ndefMessage);

        // See Type 4 Tag Technical Specification Version 1.2 section 7.5.5 NDEF Write Procedure
        // for how this is done.

        // Check to see if we can merge the three UPDATE_BINARY messages into a single message.
        // This is allowed as per [T4T] 7.5.5 NDEF Write Procedure:
        //
        //   If the entire NDEF Message can be written with a single UPDATE_BINARY
        //   Command, the Reader/Writer MAY write NLEN and ENLEN (Symbol 6), as
        //   well as the entire NDEF Message (Symbol 5) using a single
        //   UPDATE_BINARY Command. In this case the Reader/Writer SHALL
        //   proceed to Symbol 5 and merge Symbols 5 and 6 operations into a single
        //   UPDATE_BINARY Command.
        //
        // For debugging, this optimization can be turned off by setting this to |true|:
        final boolean bypassUpdateBinaryOptimization = false;

        if (!bypassUpdateBinaryOptimization && ndefMessage.length < 256 - 2) {
            Logger.d(TAG, "ndefTransact: using single UPDATE_BINARY command");
            byte[] data = new byte[ndefMessage.length + 2];
            data[0] = (byte) ((ndefMessage.length / 0x100) & 0xff);
            data[1] = (byte) (ndefMessage.length & 0xff);
            System.arraycopy(ndefMessage, 0, data, 2, ndefMessage.length);
            apdu = NfcUtil.createApduUpdateBinary(0x0000, data);
            ret = transceive(isoDep, apdu);
            if (!Arrays.equals(ret, NfcUtil.STATUS_WORD_OK)) {
                Logger.eHex(TAG, "Error sending combined UPDATE_BINARY command, ret", ret);
                return null;
            }
        } else {
            Logger.d(TAG, "ndefTransact: using 3+ UPDATE_BINARY commands");

            // First command is UPDATE_BINARY to reset length
            apdu = NfcUtil.createApduUpdateBinary(0x0000, new byte[]{0x00, 0x00});
            ret = transceive(isoDep, apdu);
            if (!Arrays.equals(ret, NfcUtil.STATUS_WORD_OK)) {
                Logger.eHex(TAG, "Error sending initial UPDATE_BINARY command, ret", ret);
                return null;
            }

            // Subsequent commands are UPDATE_BINARY with payload, chopped into bits no longer
            // than 255 bytes each
            int offset = 0;
            int remaining = ndefMessage.length;
            while (remaining > 0) {
                int numBytesToWrite = Math.min(remaining, 255);
                byte[] bytesToWrite = Arrays.copyOfRange(ndefMessage, offset, offset + numBytesToWrite);
                apdu = NfcUtil.createApduUpdateBinary(offset + 2, bytesToWrite);
                ret = transceive(isoDep, apdu);
                if (!Arrays.equals(ret, NfcUtil.STATUS_WORD_OK)) {
                    Logger.eHex(TAG, "Error sending UPDATE_BINARY command with payload, ret", ret);
                    return null;
                }
                remaining -= numBytesToWrite;
                offset += numBytesToWrite;
            }

            // Final command is UPDATE_BINARY to write the length
            byte[] encodedLength = new byte[]{
                    (byte) ((ndefMessage.length / 0x100) & 0xff),
                    (byte) (ndefMessage.length & 0xff)};
            apdu = NfcUtil.createApduUpdateBinary(0x0000, encodedLength);
            ret = transceive(isoDep, apdu);
            if (!Arrays.equals(ret, NfcUtil.STATUS_WORD_OK)) {
                Logger.eHex(TAG, "Error sending final UPDATE_BINARY command, ret", ret);
                return null;
            }
        }

        try {
            long waitMillis = (long) Math.ceil(tWaitMillis);  // Just round up to closest millisecond
            Logger.d(TAG, String.format(Locale.US, "ndefTransact: Sleeping %d ms", waitMillis));
            Thread.sleep(waitMillis);
        } catch (InterruptedException e) {
            throw new RuntimeException("Unexpected interrupt", e);
        }

        // Now read NDEF file...
        byte[] receivedNdefMessage = ndefReadMessage(isoDep, nWait);
        if (receivedNdefMessage == null) {
            return null;
        }
        Logger.dHex(TAG, "ndefTransact: read NDEF message", receivedNdefMessage);
        return receivedNdefMessage;
    }


    private void startNfcHandover() {
        Logger.d(TAG, "Starting NFC handover thread");

        long timeMillisBegin = System.currentTimeMillis();

        // TODO: need settings UI where the user can specify which methods to offer when
        //   using NFC negotiated handover.
        List<ConnectionMethod> connectionMethods = new ArrayList<>();
        connectionMethods.add(new ConnectionMethodBle(
                false,
                true,
                null,
                UUID.randomUUID()));

        // TODO: also start these connection methods early...

        final IsoDep isoDep = mNfcIsoDep;
        Thread transceiverThread = new Thread() {
            @Override
            public void run() {
                byte[] ret;
                byte[] apdu;

                try {
                    isoDep.connect();
                    isoDep.setTimeout(20 * 1000);  // 20 seconds

                    apdu = NfcUtil.createApduApplicationSelect(NfcUtil.AID_FOR_TYPE_4_TAG_NDEF_APPLICATION);
                    ret = transceive(isoDep, apdu);
                    if (!Arrays.equals(ret, NfcUtil.STATUS_WORD_OK)) {
                        Logger.eHex(TAG, "NDEF application selection failed, ret", ret);
                        throw new IllegalStateException("NDEF application selection failed");
                    }

                    apdu = NfcUtil.createApduSelectFile(NfcUtil.CAPABILITY_CONTAINER_FILE_ID);
                    ret = transceive(isoDep, apdu);
                    if (!Arrays.equals(ret, NfcUtil.STATUS_WORD_OK)) {
                        Logger.eHex(TAG, "Error selecting capability file, ret", ret);
                        throw new IllegalStateException("Error selecting capability file");
                    }

                    // CC file is 15 bytes long
                    byte[] ccFile = readBinary(isoDep, 0, 15);
                    if (ccFile == null) {
                        throw new IllegalStateException("Error reading CC file");
                    }
                    if (ccFile.length < 15) {
                        throw new IllegalStateException(
                                String.format(Locale.US, "CC file is %d bytes, expected 15",
                                        ccFile.length));
                    }

                    // TODO: look at mapping version in ccFile

                    int ndefFileId = ((int) ccFile[9] & 0xff) * 256 + ((int) ccFile[10] & 0xff);
                    Logger.d(TAG, String.format(Locale.US, "NDEF file id: 0x%04x", ndefFileId));

                    apdu = NfcUtil.createApduSelectFile(NfcUtil.NDEF_FILE_ID);
                    ret = transceive(isoDep, apdu);
                    if (!Arrays.equals(ret, NfcUtil.STATUS_WORD_OK)) {
                        Logger.eHex(TAG, "Error selecting NDEF file, ret", ret);
                        throw new IllegalStateException("Error selecting NDEF file");
                    }

                    // First see if we should use negotiated handover..
                    byte[] initialNdefMessage = ndefReadMessage(isoDep, 0);

                    NdefRecord handoverServiceRecord = NfcUtil.findServiceParameterRecordWithName(initialNdefMessage,
                            "urn:nfc:sn:handover");
                    if (handoverServiceRecord == null) {
                        long elapsedTime = System.currentTimeMillis() - timeMillisBegin;
                        Logger.d(TAG, String.format(Locale.US,
                                "Time spent in NFC static handover: %d ms", elapsedTime));

                        Logger.d(TAG, "No urn:nfc:sn:handover record found - assuming NFC static handover");
                        NfcUtil.ParsedHandoverSelectMessage hs = NfcUtil.parseHandoverSelectMessage(initialNdefMessage);
                        if (hs == null) {
                            throw new IllegalStateException("Error parsing Handover Select message");
                        }

                        if (hs.connectionMethods.isEmpty()) {
                            throw new IllegalStateException("No connection methods in Handover Select");
                        }

                        if (Logger.isDebugEnabled()) {
                            for (ConnectionMethod cm : hs.connectionMethods) {
                                Logger.d(TAG, "Connection method from static handover: " + cm);
                            }
                        }

                        Logger.d(TAG, "Reporting Device Engagement through NFC");
                        DataItem readerHandover = new CborBuilder()
                                .addArray()
                                .add(initialNdefMessage)   // Handover Select message
                                .add(SimpleValue.NULL)     // Handover Request message
                                .end()
                                .build().get(0);
                        setDeviceEngagement(hs.encodedDeviceEngagement, readerHandover);
                        reportDeviceEngagementReceived(hs.connectionMethods);
                        return;
                    }

                    Logger.d(TAG, "Service Parameter for urn:nfc:sn:handover found - negotiated handover");

                    NfcUtil.ParsedServiceParameterRecord spr = NfcUtil.parseServiceParameterRecord(handoverServiceRecord);

                    Logger.d(TAG, String.format(Locale.US,
                            "tWait is %.1f ms, nWait is %d, maxNdefSize is %d",
                            spr.tWaitMillis, spr.nWait, spr.maxNdefSize));

                    // Select the service, the resulting NDEF message is specified in
                    // in Tag NDEF Exchange Protocol Technical Specification Version 1.0
                    // section 4.3 TNEP Status Message
                    ret = ndefTransact(isoDep,
                            NfcUtil.createNdefMessageServiceSelect("urn:nfc:sn:handover"),
                            spr.tWaitMillis, spr.nWait);
                    if (ret == null) {
                        throw new IllegalStateException("Service selection: no response");
                    }
                    NdefRecord tnepStatusRecord = NfcUtil.findTnepStatusRecord(ret);
                    if (tnepStatusRecord == null) {
                        throw new IllegalArgumentException("Service selection: no TNEP status record");
                    }
                    byte[] tnepStatusPayload = tnepStatusRecord.getPayload();
                    if (tnepStatusPayload == null || tnepStatusPayload.length != 1) {
                        throw new IllegalArgumentException("Service selection: Malformed payload for TNEP status record");
                    }
                    int statusType = tnepStatusPayload[0] & 0x0ff;
                    // Status type is defined in 4.3.3 Status Type
                    if (statusType != 0x00) {
                        throw new IllegalArgumentException("Service selection: Unexpected status type " + statusType);
                    }

                    // Now send Handover Request, the resulting NDEF message is Handover Response..
                    byte[] hrMessage = NfcUtil.createNdefMessageHandoverRequest(
                            connectionMethods,
                            null); // TODO: pass ReaderEngagement message
                    Logger.dHex(TAG, "Handover Request sent", hrMessage);
                    byte[] hsMessage = ndefTransact(isoDep, hrMessage, spr.tWaitMillis, spr.nWait);
                    if (hsMessage == null) {
                        throw new IllegalStateException("Handover Request failed");
                    }
                    Logger.dHex(TAG, "Handover Select received", hsMessage);

                    long elapsedTime = System.currentTimeMillis() - timeMillisBegin;
                    Logger.d(TAG, String.format(Locale.US,
                            "Time spent in NFC negotiated handover: %d ms", elapsedTime));

                    byte[] encodedDeviceEngagement = null;
                    List<ConnectionMethod> parsedCms = new ArrayList<>();
                    NdefMessage ndefHsMessage = new NdefMessage(hsMessage);
                    for (NdefRecord r : ndefHsMessage.getRecords()) {
                        // DeviceEngagement record
                        //
                        if (r.getTnf() == NdefRecord.TNF_EXTERNAL_TYPE
                                && Arrays.equals(r.getType(),
                                "iso.org:18013:deviceengagement".getBytes(UTF_8))
                                && Arrays.equals(r.getId(), "mdoc".getBytes(UTF_8))) {
                            encodedDeviceEngagement = r.getPayload();
                            Logger.dCbor(TAG, "Device Engagement from NFC negotiated handover",
                                    encodedDeviceEngagement);
                        } else if ((r.getTnf() == NdefRecord.TNF_MIME_MEDIA) ||
                                (r.getTnf() == NdefRecord.TNF_EXTERNAL_TYPE)) {
                            ConnectionMethod cm = ConnectionMethod.fromNdefRecord(r, true);
                            if (cm != null) {
                                parsedCms.add(cm);
                                Logger.d(TAG, "CM: " + cm);
                            }
                        }
                    }
                    if (encodedDeviceEngagement == null) {
                        throw new IllegalStateException("Device Engagement not found in HS message");
                    }
                    if (parsedCms.size() < 1) {
                        throw new IllegalStateException("No Alternative Carriers in HS message");
                    }

                    // TODO: use selected CMs to pick from the list we offered... why would we
                    //  have to do this? Because some mDL / wallets don't return the UUID in
                    //  the HS message.
                    //  For now just assume we only offered a single CM and the other side accepted.
                    //
                    parsedCms = connectionMethods;

                    DataItem handover = new CborBuilder()
                            .addArray()
                            .add(hsMessage)   // Handover Select message
                            .add(hrMessage)   // Handover Request message
                            .end()
                            .build().get(0);
                    setDeviceEngagement(encodedDeviceEngagement, handover);

                    reportDeviceEngagementReceived(parsedCms);

                } catch (Throwable t) {
                    reportError(t);
                }
            }
        };
        transceiverThread.start();
    }

    private void setDeviceEngagement(@NonNull byte[] deviceEngagement, @NonNull DataItem handover) {
        if (mDeviceEngagement != null) {
            throw new IllegalStateException("Device Engagement already set");
        }
        mDeviceEngagement = deviceEngagement;

        EngagementParser engagementParser = new EngagementParser(deviceEngagement);
        EngagementParser.Engagement engagement = engagementParser.parse();
        PublicKey eDeviceKey = engagement.getESenderKey();

        byte[] encodedEReaderKeyPub = Util.cborEncode(Util.cborBuildCoseKey(mEphemeralKeyPair.getPublic()));
        mEncodedSessionTranscript = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(Util.cborBuildTaggedByteString(mDeviceEngagement))
                .add(Util.cborBuildTaggedByteString(encodedEReaderKeyPub))
                .add(handover)
                .end()
                .build().get(0));
        Logger.dCbor(TAG, "SessionTranscript", mEncodedSessionTranscript);

        mSessionEncryptionReader = new SessionEncryptionReader(
                mEphemeralKeyPair.getPrivate(),
                mEphemeralKeyPair.getPublic(),
                eDeviceKey,
                mEncodedSessionTranscript);
        if (mReaderEngagement != null) {
            // No need to include EReaderKey in first message...
            mSessionEncryptionReader.setSendSessionEstablishment(false);
        }
    }

    /**
     * Establishes connection to remote mdoc using the given {@link ConnectionMethod}.
     *
     * <p>This method should be called after receiving the
     * {@link Listener#onDeviceEngagementReceived(List)} callback with one of the addresses from
     * said callback.
     *
     * @param connectionMethod the address/method to connect to.
     */
    public void connect(@NonNull ConnectionMethod connectionMethod) {
        connectWithDataTransport(connectionMethod.createDataTransport(mContext, DataTransport.ROLE_MDOC_READER, mOptions));
    }

    private void connectWithDataTransport(DataTransport transport) {
        mDataTransport = transport;
        if (mDataTransport instanceof DataTransportNfc) {
            if (mNfcIsoDep == null) {
                // This can happen if using NFC data transfer with QR code engagement
                // which is allowed by ISO 18013-5:2021 (even though it's really
                // weird). In this case we just sit and wait until the tag (reader)
                // is detected... once detected, this routine can just call connect()
                // again.
                Logger.d(TAG, "In connect() with NFC data transfer but no ISO dep has been set. "
                        + "Assuming QR engagement, waiting for mdoc to move into field");
                reportMoveIntoNfcField();
                return;
            }
            ((DataTransportNfc) mDataTransport).setIsoDep(mNfcIsoDep);
        } else if (mDataTransport instanceof DataTransportBle) {
            // Helpful warning
            if (mOptions.getBleClearCache() && mDataTransport instanceof DataTransportBleCentralClientMode) {
                Logger.d(TAG, "Ignoring bleClearCache flag since it only applies to "
                        + "BLE mdoc peripheral server mode when acting as a reader");
            }
        }

        // Careful, we're using the user-provided Executor below so these callbacks might happen
        // in another thread than we're in right now. For example this happens if using
        // ThreadPoolExecutor.
        //
        // If it turns out that we're going to access shared state we might need locking /
        // synchronization.
        //

        mDataTransport.setListener(new DataTransport.Listener() {
            @Override
            public void onConnectionMethodReady() {
                Logger.d(TAG, "onConnectionMethodReady for " + mDataTransport);
            }

            @Override
            public void onConnecting() {
                Logger.d(TAG, "onConnecting for " + mDataTransport);
            }

            @Override
            public void onConnected() {
                Logger.d(TAG, "onConnected for " + mDataTransport);
                reportDeviceConnected();
            }

            @Override
            public void onDisconnected() {
                Logger.d(TAG, "onDisconnected for " + mDataTransport);
                mDataTransport.close();
                reportDeviceDisconnected(false);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                Logger.d(TAG, "onError for " + mDataTransport + ": " + error);
                mDataTransport.close();
                reportError(error);
            }

            @Override
            public void onMessageReceived() {
                handleOnMessageReceived();
            }

            @Override
            public void onTransportSpecificSessionTermination() {
                Logger.d(TAG, "Received onTransportSpecificSessionTermination");
                mDataTransport.close();
                reportDeviceDisconnected(true);
            }

        }, mListenerExecutor);

        try {
            DataItem deDataItem = Util.cborDecode(mDeviceEngagement);
            DataItem eDeviceKeyBytesDataItem = Util.cborMapExtractArray(deDataItem, 1).get(1);
            byte[] encodedEDeviceKeyBytes = Util.cborEncode(eDeviceKeyBytesDataItem);
            mDataTransport.setEDeviceKeyBytes(encodedEDeviceKeyBytes);
            mDataTransport.connect();
        } catch (Exception e) {
            reportError(e);
        }
    }

    private void handleReverseEngagementMessageData(@NonNull byte[] data) {
        Logger.dCbor(TAG, "MessageData", data);
        byte[] encodedDeviceEngagement = null;
        DataItem handover = null;
        try {
            DataItem map = Util.cborDecode(data);
            encodedDeviceEngagement = Util.cborMapExtractByteString(map, "deviceEngagementBytes");
        } catch (Exception e) {
            mDataTransport.close();
            reportError(new Error("Error extracting DeviceEngagement from MessageData", e));
            return;
        }

        Logger.dCbor(TAG, "Extracted DeviceEngagement", encodedDeviceEngagement);

        // 18013-7 says to use ReaderEngagementBytes for Handover when ReaderEngagement
        // is available and neither QR or NFC is used.
        handover = Util.cborBuildTaggedByteString(mReaderEngagement);

        setDeviceEngagement(encodedDeviceEngagement, handover);

        // Tell the application it can start sending requests...
        reportDeviceConnected();
    }

    private void handleOnMessageReceived() {
        byte[] data = mDataTransport.getMessage();
        if (data == null) {
            reportError(new Error("onMessageReceived but no message"));
            return;
        }

        if (mDeviceEngagement == null) {
            // DeviceEngagement is delivered in the first message...
            handleReverseEngagementMessageData(data);
            return;
        }

        if (mSessionEncryptionReader == null) {
            reportError(new IllegalStateException("Message received but no session "
                    + "establishment with the remote device."));
            return;
        }

        Pair<byte[], OptionalLong> decryptedMessage = null;
        try {
            decryptedMessage = mSessionEncryptionReader.decryptMessageFromDevice(data);
        } catch (Exception e) {
            mDataTransport.close();
            reportError(new Error("Error decrypting message from device", e));
            return;
        }

        // If there's data in the message, assume it's DeviceResponse (ISO 18013-5
        // currently does not define other kinds of messages).
        //
        if (decryptedMessage.first != null) {
            Logger.dCbor(TAG, "DeviceResponse received", decryptedMessage.first);
            reportResponseReceived(decryptedMessage.first);
        } else {
            // No data, so status must be set.
            if (!decryptedMessage.second.isPresent()) {
                mDataTransport.close();
                reportError(new Error("No data and no status in SessionData"));
            } else {
                long statusCode = decryptedMessage.second.getAsLong();
                Logger.d(TAG, "SessionData with status code " + statusCode);
                if (statusCode == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION) {
                    mDataTransport.close();
                    reportDeviceDisconnected(false);
                } else {
                    mDataTransport.close();
                    reportError(new Error("Expected status code 20, got "
                            + statusCode + " instead"));
                }
            }
        }
    }

    void reportDeviceDisconnected(boolean transportSpecificTermination) {
        Logger.d(TAG, "reportDeviceDisconnected: transportSpecificTermination: "
                + transportSpecificTermination);
        if (mListener != null) {
            mListenerExecutor.execute(
                    () -> mListener.onDeviceDisconnected(transportSpecificTermination));
        }
    }

    void reportResponseReceived(@NonNull byte[] deviceResponseBytes) {
        Logger.d(TAG, "reportResponseReceived (" + deviceResponseBytes.length + " bytes)");
        if (mListener != null) {
            mListenerExecutor.execute(
                    () -> mListener.onResponseReceived(deviceResponseBytes));
        }
    }

    void reportMoveIntoNfcField() {
        Logger.d(TAG, "reportMoveIntoNfcField");
        if (mListener != null) {
            mListenerExecutor.execute(
                    () -> mListener.onMoveIntoNfcField());
        }
    }

    void reportDeviceConnected() {
        Logger.d(TAG, "reportDeviceConnected");
        if (mListener != null) {
            mListenerExecutor.execute(
                    () -> mListener.onDeviceConnected());
        }
    }

    void reportReaderEngagementReady(@NonNull byte[] readerEngagement) {
        Logger.dCbor(TAG, "reportReaderEngagementReady", readerEngagement);
        if (mListener != null) {
            mListenerExecutor.execute(
                    () -> mListener.onReaderEngagementReady(readerEngagement));
        }
    }

    void reportDeviceEngagementReceived(@NonNull List<ConnectionMethod> connectionMethods) {
        if (Logger.isDebugEnabled()) {
            Logger.d(TAG, "reportDeviceEngagementReceived");
            for (ConnectionMethod cm : connectionMethods) {
                Logger.d(TAG, "  ConnectionMethod: " + cm);
            }
        }
        if (mListener != null) {
            mListenerExecutor.execute(
                    () -> mListener.onDeviceEngagementReceived(connectionMethods));
        }
    }

    void reportError(Throwable error) {
        Logger.d(TAG, "reportError: error: " + error);
        if (mListener != null) {
            mListenerExecutor.execute(() -> mListener.onError(error));
        }
    }

    /**
     * Ends the session with the remote device.
     *
     * <p>By default, ending a session involves sending a message to the remote device with empty
     * data and the status code set to 20, meaning <em>session termination</em> as per
     * <em>ISO/IEC 18013-5</em>. This can be configured using
     * {@link #setSendSessionTerminationMessage(boolean)} and
     * {@link #setUseTransportSpecificSessionTermination(boolean)}.
     *
     * <p>Some transports - such as BLE - supports a transport-specific session termination
     * message instead of the generic one. By default this is not used but it can be enabled using
     * {@link #setUseTransportSpecificSessionTermination(boolean)}.
     *
     * <p>After calling this the current object can no longer be used to send requests.
     *
     * <p>This method is idempotent so it is safe to call multiple times.
     */
    public void disconnect() {
        if (mReverseEngagementListeningTransports != null) {
            for (DataTransport transport : mReverseEngagementListeningTransports) {
                transport.close();
            }
            mReverseEngagementListeningTransports = null;
        }

        if (mDataTransport != null) {
            // Only send session termination message if the session was actually established.
            boolean sessionEstablished = (mSessionEncryptionReader.getNumMessagesEncrypted() > 0);
            if (mSendSessionTerminationMessage && sessionEstablished) {
                if (mUseTransportSpecificSessionTermination &&
                        mDataTransport.supportsTransportSpecificTerminationMessage()) {
                    Log.d(TAG, "Sending transport-specific termination message");
                    mDataTransport.sendTransportSpecificTerminationMessage();
                } else {
                    Log.d(TAG, "Sending generic session termination message");
                    byte[] sessionTermination = mSessionEncryptionReader.encryptMessageToDevice(
                            null, OptionalLong.of(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION));
                    mDataTransport.sendMessage(sessionTermination);
                }
            } else {
                Log.d(TAG, "Not sending session termination message");
            }
            Log.d(TAG, "Shutting down transport");
            mDataTransport.close();
            mDataTransport = null;
        }
    }

    /**
     * Sends a request to the remote mdoc device.
     *
     * <p>The <code>deviceRequestBytes</code> parameter must be <code>DeviceRequest</code>
     * <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>. The
     * {@link DeviceRequestGenerator} class can be used to generate this.
     *
     * <p>If a response to the request is received from the remote mdoc device, the
     * {@link Listener#onResponseReceived(byte[])} method is invoked. This will usually take
     * several seconds as it typically involves authenticating the holder and asking for their
     * consent to release identity data.
     *
     * <p>This is usually called in response to {@link Listener#onDeviceConnected()} callback.
     *
     * @param deviceRequestBytes request message to the remote device
     */
    public void sendRequest(@NonNull byte[] deviceRequestBytes) {
        if (mDeviceEngagement == null) {
            throw new IllegalStateException("Device engagement is null");
        }
        if (mEphemeralKeyPair == null) {
            throw new IllegalStateException("New object must be created");
        }
        if (mDataTransport == null) {
            throw new IllegalStateException("Not connected to a remote device");
        }

        Logger.dCbor(TAG, "DeviceRequest to send", deviceRequestBytes);

        byte[] message = mSessionEncryptionReader.encryptMessageToDevice(
                deviceRequestBytes, OptionalLong.empty());
        Logger.dCbor(TAG, "SessionData to send", message);
        mDataTransport.sendMessage(message);
    }

    /**
     * Gets the session transcript.
     *
     * <p>This must not be called until engagement has been established with the mdoc device.
     *
     * @return the session transcript.
     * @throws IllegalStateException if called before engaging with mdoc device.
     */
    public @NonNull
    byte[] getSessionTranscript() {
        if (mEncodedSessionTranscript == null) {
            throw new IllegalStateException("Not engaging with mdoc device");
        }
        return mEncodedSessionTranscript;
    }

    /**
     * Gets the ephemeral key used by the reader for session encryption.
     *
     * <p>This is made available because it's needed for mdoc verification when using the MAC
     * mechanism.
     */
    public @NonNull
    PrivateKey getEphemeralReaderKey() {
        return mEphemeralKeyPair.getPrivate();
    }

    /**
     * Sets whether to use transport-specific session termination.
     *
     * <p>By default this is set to <code>false</code>.
     *
     * <p>As per <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5</a>
     * transport-specific session-termination is currently only supported for BLE. The
     * {@link #isTransportSpecificTerminationSupported()} method can be used to determine whether
     * it's available for the current transport.
     *
     * <p>If {@link #isTransportSpecificTerminationSupported()} indicates that this is not
     * available for the current transport this is a noop.
     *
     * @param useTransportSpecificSessionTermination Whether to use transport-specific session
     *                                               termination.
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
        if (mDataTransport == null) {
            return false;
        }
        return mDataTransport.supportsTransportSpecificTerminationMessage();
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
     * Interface for listening to messages from the remote mdoc device.
     */
    public interface Listener {

        /**
         * Called when using reverse engagement and the reader engagement is ready.
         *
         * The app can display this as QR code (for 23220-4) or send it to a remote
         * user agent as an mdoc:// URI (for 18013-7).
         *
         * @param readerEngagement the bytes of reader engagement.
         */
        void onReaderEngagementReady(@NonNull byte[] readerEngagement);

        /**
         * Called when a valid device engagement is received from QR code of NFC.
         *
         * <p>This is called either in response to {@link #setDeviceEngagementFromQrCode(String)}
         * or as a result of a NFC tap. The application should call
         * {@link #connect(ConnectionMethod)} in response to this callback to establish a
         * connection.
         *
         * <p>If reverse engagement is used, this is never called.
         *
         * @param connectionMethods a list of connection methods that can be used to establish
         *                          a connection to the mdoc.
         */
        void onDeviceEngagementReceived(@NonNull List<ConnectionMethod> connectionMethods);

        /**
         * Called when NFC data transfer has been selected but the mdoc reader device isn't yet
         * in the NFC field of the mdoc device.
         *
         * <p>The application should instruct the user to move the mdoc reader device into
         * the NFC field.
         */
        void onMoveIntoNfcField();

        /**
         * Called when connected to a remote mdoc device.
         *
         * <p>At this point the application can start sending requests using
         * {@link #sendRequest(byte[])}.
         */
        void onDeviceConnected();

        /**
         * Called when the remote mdoc device disconnects normally, that is
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
         * Called when the remote mdoc device sends a response.
         *
         * The <code>deviceResponseBytes</code> parameter contains the bytes of the
         * <code>DeviceResponse</code> <a href="http://cbor.io/">CBOR</a>
         * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>. The
         * class {@link DeviceResponseParser} can be used to parse these bytes.
         *
         * @param deviceResponseBytes the device response.
         */
        void onResponseReceived(@NonNull byte[] deviceResponseBytes);

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
     * Builder for {@link VerificationHelper}.
     */
    public static class Builder {
        private VerificationHelper mHelper;

        /**
         * Creates a new Builder for {@link VerificationHelper}.
         *
         * @param context application context.
         * @param listener listener.
         * @param executor executor.
         */
        public Builder(@NonNull Context context,
                       @NonNull Listener listener,
                       @NonNull Executor executor) {
            mHelper = new VerificationHelper();
            mHelper.mContext = context;
            mHelper.mListener = listener;
            mHelper.mListenerExecutor = executor;
            mHelper.mEphemeralKeyPair = Util.createEphemeralKeyPair();
        }

        /**
         * Sets the options to use when setting up transports.
         *
         * @param options the options to use.
         * @return the builder.
         */
        public @NonNull Builder setDataTransportOptions(DataTransportOptions options) {
            mHelper.mOptions = options;
            return this;
        }

        /**
         * Configures the verification helper to use reverse engagement.
         *
         * @param connectionMethods a list of connection methods to offer via reverse engagement
         * @return the builder.
         */
        public @NonNull Builder setUseReverseEngagement(@NonNull List<ConnectionMethod> connectionMethods) {
            mHelper.mReverseEngagementConnectionMethods = connectionMethods;
            return this;
        }

        /**
         * Builds a {@link VerificationHelper} with the configuration specified in the builder.
         *
         * <p>If using normal engagement and the application wishes to use NFC engagement it
         * should use {@link NfcAdapter} and pass received tags to
         * {@link #nfcProcessOnTagDiscovered(Tag)}. For QR engagement the application is
         * expected to capture the QR code itself and pass it using
         * {@link #setDeviceEngagementFromQrCode(String)}. When engagement with a mdoc is detected
         * {@link Listener#onDeviceEngagementReceived(List)} is called with a list of possible
         * transports and the application is expected to pick a transport and pass it to
         * {@link #connect(ConnectionMethod)}.
         *
         * <p>If using reverse engagement, the application should wait for the
         * {@link Listener#onReaderEngagementReady(byte[])} callback and then convey the
         * reader engagement to the mdoc, for example via QR code or sending an mdoc:// URI
         * to a suitable user agent. After this, application should wait for the
         * {@link Listener#onDeviceConnected()} callback which is called when the mdoc
         * connects via one of the connection methods specified using
         * {@link #setUseReverseEngagement(List)}.
         *
         * <p>When the application is done interacting with the mdoc it should call
         * {@link #disconnect()}.
         *
         * @return A {@link VerificationHelper}.
         */
        public @NonNull VerificationHelper build() {
            mHelper.start();
            return mHelper;
        }
    }

}
