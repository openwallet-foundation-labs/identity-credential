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

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import androidx.core.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.Executor;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
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
        // use the monitor for the PresentationHelper object for to achieve that.
        //
        final VerificationHelper helper = this;
        mNumReverseEngagementTransportsStillSettingUp = 0;

        // Calculate ReaderEngagement as we're setting up methods
        mReaderEngagementGenerator = new EngagementGenerator(
                mEphemeralKeyPair.getPublic(),
                EngagementGenerator.ENGAGEMENT_VERSION_1_1);

        synchronized (helper) {
            for (DataTransport transport : mReverseEngagementListeningTransports) {
                transport.setListener(new DataTransport.Listener() {
                    @Override
                    public void onConnectionMethodReady() {
                        Logger.d(TAG, "onConnectionMethodReady for " + transport);
                        synchronized (helper) {
                            mReaderEngagementGenerator.addConnectionMethod(
                                    transport.getConnectionMethod());
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
        for (String tech : tag.getTechList()) {
            Logger.d(TAG, "tech: " + tech);
            if (tech.equals(Ndef.class.getName())) {
                Logger.d(TAG, "Found ndef tech!");
                if (mDeviceEngagement != null) {
                    Logger.d(TAG, "Already have device engagement "
                            + "so not inspecting what was received via "
                            + "NFC");
                } else {
                    setNdefDeviceEngagement(Ndef.get(tag));
                }

            } else if (tech.equals(IsoDep.class.getName())) {
                mNfcIsoDep = IsoDep.get(tag);
                // If we're doing QR code engagement _and_ NFC data transfer
                // it's possible that we're now in a state where we're
                // waiting for the reader to be in the NFC field... see
                // also comment in connect() for this case...
                if (mDataTransport instanceof DataTransportNfc) {
                    Logger.d(TAG, "NFC data transfer + QR engagement, "
                            + "reader is now in field");
                    mListenerExecutor.execute(
                            () -> connectWithDataTransport(mDataTransport)
                    );
                }
            }
        }
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
                if (Logger.isDebugEnabled()) {
                    Logger.d(TAG, "Device Engagement from QR code: "
                            + Util.toHex(encodedDeviceEngagement));
                }

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

    void setNdefDeviceEngagement(@NonNull Ndef ndef) {
        byte[] handoverSelectMessage;
        byte[] encodedDeviceEngagement = null;
        boolean validHandoverSelectMessage = false;

        NdefMessage m = ndef.getCachedNdefMessage();

        handoverSelectMessage = m.toByteArray();
        if (Logger.isDebugEnabled()) {
            Logger.d(TAG, 
                    "In decodeNdefTag, handoverSelectMessage: " + Util.toHex(m.toByteArray()));
        }

        List<ConnectionMethod> parsedConnectionMethodsFromNfc = new ArrayList<>();
        for (NdefRecord r : m.getRecords()) {
            if (Logger.isDebugEnabled()) {
                Logger.d(TAG, "record: " + Util.toHex(r.getPayload()));
            }

            // Handle Handover Select record for NFC Forum Connection Handover specification
            // version 1.5 (encoded as 0x15 below).
            //
            if (r.getTnf() == NdefRecord.TNF_WELL_KNOWN
                    && Arrays.equals(r.getType(), "Hs".getBytes(UTF_8))) {
                byte[] payload = r.getPayload();
                if (payload.length >= 1 && payload[0] == 0x15) {
                    // The NDEF payload of the Handover Select Record SHALL consist of a single
                    // octet that contains the MAJOR_VERSION and MINOR_VERSION numbers,
                    // optionally followed by an embedded NDEF message.
                    //
                    // If present, the NDEF message SHALL consist of one of the following options:
                    // - One or more ALTERNATIVE_CARRIER_RECORDs
                    // - One or more ALTERNATIVE_CARRIER_RECORDs followed by an ERROR_RECORD
                    // - An ERROR_RECORD.
                    //
                    Logger.d(TAG, "Processing Handover Select message");
                    //byte[] ndefMessage = Arrays.copyOfRange(payload, 1, payload.length);
                    // TODO: check that the ALTERNATIVE_CARRIER_RECORD matches
                    //   the ALTERNATIVE_CARRIER_CONFIGURATION record retrieved below.
                    validHandoverSelectMessage = true;
                }
            }

            // DeviceEngagement record
            //
            if (r.getTnf() == NdefRecord.TNF_EXTERNAL_TYPE
                    && Arrays.equals(r.getType(),
                    "iso.org:18013:deviceengagement".getBytes(UTF_8))
                    && Arrays.equals(r.getId(), "mdoc".getBytes(UTF_8))) {
                encodedDeviceEngagement = r.getPayload();
                if (Logger.isDebugEnabled()) {
                    Logger.d(TAG, 
                            "Device Engagement from NFC: " + Util.toHex(encodedDeviceEngagement));
                }
            }

            // This parses the various carrier specific NDEF records, see
            // DataTransport.parseNdefRecord() for details.
            //
            if (r.getTnf() == NdefRecord.TNF_MIME_MEDIA) {
                ConnectionMethod cm = ConnectionMethod.fromNdefRecord(r);
                if (cm != null) {
                    parsedConnectionMethodsFromNfc.add(cm);
                }
            }

        }

        if (Logger.isDebugEnabled()) {
            for (ConnectionMethod cm : parsedConnectionMethodsFromNfc) {
                Logger.d(TAG, "Have connection method " + cm);
            }
        }

        if (validHandoverSelectMessage && !parsedConnectionMethodsFromNfc.isEmpty()) {
            Logger.d(TAG, "Reporting Device Engagement through NFC");
            DataItem readerHandover = new CborBuilder()
                    .addArray()
                    .add(handoverSelectMessage)    // Handover Select message
                    .add(SimpleValue.NULL)         // Handover Request message
                    .end()
                    .build().get(0);
            setDeviceEngagement(encodedDeviceEngagement, readerHandover);

            reportDeviceEngagementReceived(parsedConnectionMethodsFromNfc);
        } else {
            reportError(new IllegalArgumentException(
                    "Invalid Handover Select message: " + Util.toHex(m.toByteArray())));
        }
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
        Logger.d(TAG, "SessionTranscript:\n" + Util.toHex(mEncodedSessionTranscript));

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
        Logger.d(TAG, "MessageData = " + Util.toHex(data));
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

        Logger.d(TAG, "Extracted DeviceEngagement " + Util.toHex(encodedDeviceEngagement));

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

        Pair<byte[], OptionalInt> decryptedMessage = null;
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
            Logger.d(TAG, "SessionData with decrypted payload"
                    + " (" + decryptedMessage.first.length + " bytes)");
            if (Logger.isDebugEnabled()) {
                Util.dumpHex(TAG, "Received DeviceResponse", decryptedMessage.first);
            }
            reportResponseReceived(decryptedMessage.first);
        } else {
            // No data, so status must be set.
            if (!decryptedMessage.second.isPresent()) {
                mDataTransport.close();
                reportError(new Error("No data and no status in SessionData"));
            } else {
                int statusCode = decryptedMessage.second.getAsInt();
                Logger.d(TAG, "SessionData with status code " + statusCode);
                if (statusCode == 20) {
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
        if (Logger.isDebugEnabled()) {
            Logger.d(TAG, "reportReaderEngagementReady: " + Util.toHex(readerEngagement));
        }
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
                            null, OptionalInt.of(20));
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

        if (Logger.isDebugEnabled()) {
            Util.dumpHex(TAG, "Sending DeviceRequest", deviceRequestBytes);
        }

        byte[] message = mSessionEncryptionReader.encryptMessageToDevice(
                deviceRequestBytes, OptionalInt.empty());
        Log.d(TAG, "sending: " + Util.toHex(message));
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
