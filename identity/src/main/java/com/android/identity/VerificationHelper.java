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
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.identity.Constants.LoggingFlag;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.Executor;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;

/**
 * Class used for engaging with and receiving documents from a remote mdoc verifier device.
 *
 * <p>This class implements the interface between an <em>mdoc</em> and <em>mdoc verifier</em> using
 * the connection setup and device retrieval interfaces defined in
 * <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5</a>.
 *
 * <p>Supported device engagement methods include QR code and NFC static handover. Support for
 * NFC negotiated handover may be added in a future release.
 *
 * <p>Supported device retrieval transfer methods include BLE (both <em>mdoc central client
 * mode</em> and <em>mdoc peripheral server mode</em>), Wifi Aware, and NFC.
 *
 * <p>Additional device engagement and device retrieval methods may be added in the future.
 */
// Suppress with NotCloseable since we actually don't hold any resources needing to be
// cleaned up at object finalization time.
@SuppressWarnings("NotCloseable")
public class VerificationHelper {

    private static final String TAG = "VerificationHelper";
    private final Context mContext;
    DataTransport mDataTransport;
    Listener mListener;
    KeyPair mEphemeralKeyPair;
    SessionEncryptionReader mSessionEncryptionReader;
    byte[] mDeviceEngagement;
    Executor mDeviceResponseListenerExecutor;
    IsoDep mNfcIsoDep;
    DataRetrievalAddress mConnectWaitingForIsoDepAddress;
    // The handover used
    //
    private byte[] mHandover;
    private boolean mUseTransportSpecificSessionTermination;
    private boolean mSendSessionTerminationMessage = true;
    Util.Logger mLog;
    private boolean mIsListening;

    /**
     * Creates a new VerificationHelper object.
     *
     * @param context the application context.
     */
    public VerificationHelper(@NonNull Context context) {
        mContext = context;
        mEphemeralKeyPair = Util.createEphemeralKeyPair();
        mSessionEncryptionReader = null;
        mLog = new Util.Logger(TAG, 0);
    }

    /**
     * Configures the amount of logging messages to emit.
     *
     * <p>By default no logging messages are emitted except for warnings and errors. Applications
     * use this with caution as the emitted log messages may contain PII and secrets.
     *
     * @param loggingFlags One or more logging flags e.g. {@link Constants#LOGGING_FLAG_INFO}.
     */
    public void setLoggingFlags(@LoggingFlag int loggingFlags) {
        mLog.setLoggingFlags(loggingFlags);
    }

    /**
     * Starts listening for device engagement.
     *
     * <p>This should be called after constructing a new instance and configuring it using
     * {@link #setListener(Listener, Executor)}.
     *
     * <p>When engagement with a mdoc is detected the
     * {@link Listener#onDeviceEngagementReceived(List)} is called with a list of possible
     * transports and the application is expected to pick a transport and pass it to
     * {@link #connect(DataRetrievalAddress)}.
     *
     * If the application wishes to use NFC engagement it should set up a listener using
     * {@link NfcAdapter#enableReaderMode(Activity, NfcAdapter.ReaderCallback, int, Bundle)}
     * and pass the tag received in
     * {@link android.nfc.NfcAdapter.ReaderCallback#onTagDiscovered(Tag)}
     * to {@link #nfcProcessOnTagDiscovered(Tag)}. It should stop listening (e.g. call
     * {@link NfcAdapter#disableReaderMode(Activity)} once engagement has been detected or when
     * it decides to stop listening for engagement.
     *
     * If the application wishes to use QR engagement the application is
     * expected to capture the QR code itself and pass it using
     * {@link #setDeviceEngagementFromQrCode(String)}.
     */
    public void startListening() {
        mIsListening = true;
    }

    /**
     * Processes a {@link Tag} received when in NFC reader mode.
     *
     * <p>Applications should call this method in their
     * {@link android.nfc.NfcAdapter.ReaderCallback#onTagDiscovered(Tag)} callback.
     *
     * <p>This should only be called after calling {@link #startListening()} and not after
     * {@link #disconnect()}
     *
     * @param tag the tag.
     * @throws IllegalStateException if called while not listening.
     */
    public void
    nfcProcessOnTagDiscovered(@NonNull Tag tag) {
        if (!mIsListening) {
            throw new IllegalStateException("Not currently listening");
        }
        mLog.engagement("Tag discovered!");
        for (String tech : tag.getTechList()) {
            mLog.engagement("tech: " + tech);
            if (tech.equals(Ndef.class.getName())) {
                mLog.engagement("Found ndef tech!");
                if (mDeviceEngagement != null) {
                    mLog.engagement("Already have device engagement "
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
                if (mConnectWaitingForIsoDepAddress != null) {
                    mLog.engagement("NFC data transfer + QR engagement, "
                            + "reader is now in field");
                    mDeviceResponseListenerExecutor.execute(
                            () -> connect(mConnectWaitingForIsoDepAddress)
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
     * <p>This method must be called before {@link #connect(DataRetrievalAddress)}.
     *
     * @param qrDeviceEngagement textual form of QR device engagement.
     * @throws IllegalStateException if called after {@link #connect(DataRetrievalAddress)}.
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
                if (mLog.isEngagementEnabled()) {
                    mLog.engagement(
                            "Device Engagement from QR code: " + Util.toHex(
                                    encodedDeviceEngagement));
                }

                byte[] encodedHandover = Util.cborEncode(SimpleValue.NULL);
                setDeviceEngagement(encodedDeviceEngagement, encodedHandover);
                List<byte[]> readerAvailableDeviceRetrievalMethods =
                        Util.extractDeviceRetrievalMethods(
                                encodedDeviceEngagement);

                List<DataRetrievalAddress> addresses = new ArrayList<>();
                for (byte[] deviceRetrievalMethod : readerAvailableDeviceRetrievalMethods) {
                    List<DataRetrievalAddress> addressesFromMethod =
                            DataTransport.parseDeviceRetrievalMethod(deviceRetrievalMethod);
                    if (addressesFromMethod == null) {
                        Log.w(TAG, "Ignoring unknown device retrieval method with payload "
                                + Util.toHex(deviceRetrievalMethod));
                    } else {
                        addresses.addAll(addressesFromMethod);
                    }
                }
                if (mLog.isEngagementEnabled()) {
                    for (DataRetrievalAddress address : addresses) {
                        mLog.engagement("Have address: " + address);
                    }
                }
                if (!addresses.isEmpty()) {
                    reportDeviceEngagementReceived(addresses);
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
        if (mLog.isEngagementEnabled()) {
            mLog.engagement(
                    "In decodeNdefTag, handoverSelectMessage: " + Util.toHex(m.toByteArray()));
        }

        List<DataRetrievalAddress> addresses = new ArrayList<>();
        for (NdefRecord r : m.getRecords()) {
            if (mLog.isEngagementEnabled()) {
                mLog.engagement("record: " + Util.toHex(r.getPayload()));
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
                    mLog.engagement("Processing Handover Select message");
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
                if (mLog.isEngagementEnabled()) {
                    mLog.engagement(
                            "Device Engagement from NFC: " + Util.toHex(encodedDeviceEngagement));
                }
            }

            // This parses the various carrier specific NDEF records, see
            // DataTransport.parseNdefRecord() for details.
            //
            if (r.getTnf() == NdefRecord.TNF_MIME_MEDIA) {
                List<DataRetrievalAddress> addressesFromMethod = DataTransport.parseNdefRecord(r);
                if (addressesFromMethod != null) {
                    addresses.addAll(addressesFromMethod);
                } else {
                    Log.w(TAG, "Ignoring unrecognized NdefRecord: " + r);
                }
            }

        }

        if (mLog.isEngagementEnabled()) {
            for (DataRetrievalAddress address : addresses) {
                mLog.engagement("Have address " + address);
            }
        }

        if (validHandoverSelectMessage && !addresses.isEmpty()) {
            mLog.engagement("Reporting Device Engagement through NFC");
            byte[] readerHandover = Util.cborEncode(new CborBuilder()
                    .addArray()
                    .add(handoverSelectMessage)    // Handover Select message
                    .add(SimpleValue.NULL)         // Handover Request message
                    .end()
                    .build().get(0));
            setDeviceEngagement(encodedDeviceEngagement, readerHandover);
            reportDeviceEngagementReceived(addresses);
        } else {
            reportError(new IllegalArgumentException(
                    "Invalid Handover Select message: " + Util.toHex(m.toByteArray())));
        }
    }

    private void setDeviceEngagement(@NonNull byte[] deviceEngagement, @NonNull byte[] handover) {
        if (mDeviceEngagement != null) {
            throw new IllegalStateException("Device Engagement already set");
        }
        mDeviceEngagement = deviceEngagement;
        mHandover = handover;

        mSessionEncryptionReader = new SessionEncryptionReader(
                mEphemeralKeyPair.getPrivate(),
                mEphemeralKeyPair.getPublic(),
                mDeviceEngagement,
                mHandover);
    }

    /**
     * Establishes connection to remote mdoc using the given <code>DataRetrievalAddress</code>.
     *
     * <p>This method is usually called after receiving the
     * {@link Listener#onDeviceEngagementReceived(List)} callback with one of the addresses from
     * said callback.
     *
     * @param address the address/method to connect to.
     */
    public void connect(@NonNull DataRetrievalAddress address) {

        mDataTransport = address.createDataTransport(mContext, mLog.getLoggingFlags());
        if (mDataTransport instanceof DataTransportNfc) {
            if (mNfcIsoDep == null) {
                // This can happen if using NFC data transfer with QR code engagement
                // which is allowed by ISO 18013-5:2021 (even though it's really
                // weird). In this case we just sit and wait until the tag (reader)
                // is detected... once detected, this routine can just call connect()
                // again.
                mConnectWaitingForIsoDepAddress = address;
                mLog.engagement("In connect() with NFC data transfer but no ISO dep has been set. "
                        + "Assuming QR engagement, waiting for mdoc to move into field");
                reportMoveIntoNfcField();
                return;
            }
            ((DataTransportNfc) mDataTransport).setIsoDep(mNfcIsoDep);
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
            public void onListeningSetupCompleted(@Nullable DataRetrievalAddress address) {
                mLog.info("onListeningSetupCompleted for " + mDataTransport);
            }

            @Override
            public void onListeningPeerConnecting() {
                mLog.info("onListeningPeerConnecting for " + mDataTransport);
            }

            @Override
            public void onListeningPeerConnected() {
                mLog.info("onListeningPeerConnected for " + mDataTransport);
            }

            @Override
            public void onListeningPeerDisconnected() {
                mLog.info("onListeningPeerDisconnected for " + mDataTransport);
                reportDeviceDisconnected(false);
            }

            @Override
            public void onConnectionResult(@Nullable Throwable error) {
                mLog.info("onConnectionResult for " + mDataTransport + ": " + error);
                if (error != null) {
                    mDataTransport.close();
                    reportError(error);
                } else {
                    reportDeviceConnected();
                }
            }

            @Override
            public void onConnectionDisconnected() {
                mLog.info("onConnectionDisconnected for " + mDataTransport);
                mDataTransport.close();
                reportError(new IllegalStateException("Error: Disconnected"));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                mLog.info("onError for " + mDataTransport + ": " + error);
                mDataTransport.close();
                reportError(error);
                error.printStackTrace();
            }

            @Override
            public void onMessageReceived(@NonNull byte[] data) {
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
                    mLog.info("SessionData with decrypted payload"
                            + " (" + decryptedMessage.first.length + " bytes)");
                    if (mLog.isSessionEnabled()) {
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
                        mLog.info("SessionData with status code " + statusCode);
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

            @Override
            public void onTransportSpecificSessionTermination() {
                mLog.info("Received onTransportSpecificSessionTermination");
                mDataTransport.close();
                reportDeviceDisconnected(true);
            }

        }, mDeviceResponseListenerExecutor);

        try {
            DataItem deDataItem = Util.cborDecode(mDeviceEngagement);
            DataItem eDeviceKeyBytesDataItem = Util.cborMapExtractArray(deDataItem, 1).get(1);
            byte[] encodedEDeviceKeyBytes = Util.cborEncode(eDeviceKeyBytesDataItem);
            mDataTransport.setEDeviceKeyBytes(encodedEDeviceKeyBytes);
            mDataTransport.connect(address);
        } catch (Exception e) {
            reportError(e);
        }

    }

    void reportDeviceDisconnected(boolean transportSpecificTermination) {
        mLog.info("reportDeviceDisconnected: transportSpecificTermination: "
                + transportSpecificTermination);
        if (mListener != null) {
            mDeviceResponseListenerExecutor.execute(
                    () -> mListener.onDeviceDisconnected(transportSpecificTermination));
        }
    }

    void reportResponseReceived(@NonNull byte[] deviceResponseBytes) {
        mLog.info("reportResponseReceived (" + deviceResponseBytes.length + " bytes)");
        if (mListener != null) {
            mDeviceResponseListenerExecutor.execute(
                    () -> mListener.onResponseReceived(deviceResponseBytes));
        }
    }

    void reportMoveIntoNfcField() {
        mLog.info("reportMoveIntoNfcField");
        if (mListener != null) {
            mDeviceResponseListenerExecutor.execute(
                    () -> mListener.onMoveIntoNfcField());
        }
    }

    void reportDeviceConnected() {
        mLog.info("reportDeviceConnected");
        if (mListener != null) {
            mDeviceResponseListenerExecutor.execute(
                    () -> mListener.onDeviceConnected());
        }
    }

    void reportDeviceEngagementReceived(@NonNull List<DataRetrievalAddress> addresses) {
        if (mLog.isInfoEnabled()) {
            mLog.info("reportDeviceEngagementReceived");
            for (DataRetrievalAddress address : addresses) {
                mLog.info("  address: " + address);
            }
        }
        if (mListener != null) {
            mDeviceResponseListenerExecutor.execute(
                    () -> mListener.onDeviceEngagementReceived(addresses));
        }
    }

    void reportError(Throwable error) {
        mLog.info("reportError: error: " + error);
        if (mListener != null) {
            mDeviceResponseListenerExecutor.execute(() -> mListener.onError(error));
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
        mIsListening = false;
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
     * <p>Should be called in response to {@link Listener#onDeviceConnected()} callback.
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

        if (mLog.isSessionEnabled()) {
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
        if (mSessionEncryptionReader == null) {
            throw new IllegalStateException("Not engaging with mdoc device");
        }
        return mSessionEncryptionReader.getSessionTranscript();
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
     * Sets the listener for listening to events from the remote mdoc device.
     *
     * <p>This may be called multiple times but only the most recent listener will be used.
     *
     * @param listener the listener or <code>null</code> to stop listening.
     * @param executor an {@link Executor} to do the call in or <code>null</code> if
     *                 <code>listener</code> is <code>null</code>.
     * @throws IllegalStateException if {@link Executor} is {@code null} for a non-{@code null}
     * listener.
     */
    public void setListener(@Nullable Listener listener,
            @Nullable Executor executor) {
        if (listener != null && executor == null) {
            throw new IllegalStateException("Cannot have non-null listener with null executor");
        }
        mListener = listener;
        mDeviceResponseListenerExecutor = executor;
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
     * @param useTransportSpecificSessionTermination Whether to use transport-specific session
     *                                               termination.
     * @throws IllegalStateException if {@link #isTransportSpecificTerminationSupported()}
     *   indicates that this is not available for the current transport.
     */
    public void setUseTransportSpecificSessionTermination(
            boolean useTransportSpecificSessionTermination) {
        if (!isTransportSpecificTerminationSupported()) {
            throw new IllegalStateException("Transport-specific session termination is not "
                    + "supported");
        }
        mUseTransportSpecificSessionTermination = useTransportSpecificSessionTermination;
    }

    /**
     * Returns whether transport specific termination is available for the current connection.
     *
     * See {@link #setUseTransportSpecificSessionTermination(boolean)} for more information about
     * what transport specific session termination is.
     *
     * @return <code>true</code> if transport specific termination is available.
     */
    public boolean isTransportSpecificTerminationSupported() {
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
         * Called when a valid device engagement is received from QR code of NFC.
         *
         * <p>This is called either in response to {@link #setDeviceEngagementFromQrCode(String)}
         * or as a result of a NFC tap. The application should call
         * {@link #connect(DataRetrievalAddress)} in response to this callback.
         */
        void onDeviceEngagementReceived(@NonNull List<DataRetrievalAddress> addresses);

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

}
