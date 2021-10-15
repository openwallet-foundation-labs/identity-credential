/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.security.identity;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.Ndef;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.security.identity.Constants.LoggingFlag;
import androidx.security.identity.DataTransportBle.BleOptions;
import java.nio.charset.StandardCharsets;
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
 * <p>Supported device engagement methods include QR code and NFC static hangover. Support for
 * NFC negotiated handover may be added in a future release.
 *
 * <p>Supported device retrieval transfer methods include BLE (only <em>mdoc central client
 * mode</em> at this time), Wifi Aware, and NFC.
 *
 * <p>Additional device engagement and device retrieval methods may be added in the future.
 */
public class VerificationHelper {

    private static final String TAG = "VerificationHelper";
    private final Context mContext;
    // The transport we selected to communicate with the mdoc.
    //
    DataTransport mDataTransport = null;
    // Call back events for the reader
    //
    Listener mListener = null;
    KeyPair mEphemeralKeyPair;
    SessionEncryptionReader mSessionEncryptionReader;
    private byte[] mDeviceEngagement = null;
    // The handover used
    //
    private byte[] mHandover = null;
    private Executor mDeviceResponseListenerExecutor = null;
    NfcAdapter mNfcAdapter;
    Activity mNfcAdapterActivity;
    IsoDep mNfcIsoDep;
    boolean mNfcAdapterReaderModeEnabled;
    private boolean mUseTransportSpecificSessionTermination = false;
    private boolean mSendSessionTerminationMessage = true;
    private @LoggingFlag int mLoggingFlags;

    /**
     * Creates a new VerificationHelper object.
     *
     * @param context the application context.
     */
    public VerificationHelper(@NonNull Context context) {
        mContext = context;
        mEphemeralKeyPair = Util.createEphemeralKeyPair();
        mSessionEncryptionReader = null;
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
        mLoggingFlags = loggingFlags;
    }


    /**
     * Sets the {@link NfcAdapter} to listen to for incoming device engagement.
     *
     * @param adapter a {@link NfcAdapter}.
     */
    public void setNfcAdapter(@NonNull NfcAdapter adapter, @NonNull Activity activity) {
        mNfcAdapter = adapter;
        mNfcAdapterActivity = activity;
    }

    /**
     * Starts listening to for device engagement.
     *
     * <p>Currently only NFC is supported for listening for device engagement.
     */
    public void verificationBegin() {
        if (mNfcAdapter != null) {
            if (!mNfcAdapter.isEnabled()) {
                Log.w(TAG, "Passing a NFC adapter which isn't enabled. Ignoring.");
            } else {
                int flags = NfcAdapter.FLAG_READER_NFC_A
                        | NfcAdapter.FLAG_READER_NFC_B
                        | NfcAdapter.FLAG_READER_NFC_F
                        | NfcAdapter.FLAG_READER_NFC_V
                        | NfcAdapter.FLAG_READER_NFC_BARCODE;
                mNfcAdapter.enableReaderMode(mNfcAdapterActivity,
                        new NfcAdapter.ReaderCallback() {
                            @Override
                            public void onTagDiscovered(Tag tag) {
                                Log.d(TAG, "Tag discovered!");
                                for (String tech : tag.getTechList()) {
                                    Log.d(TAG, " tech " + tech);
                                    if (tech.equals(Ndef.class.getName())) {
                                        Log.d(TAG, "Found ndef tech!");

                                        setNdefDeviceEngagement(Ndef.get(tag));

                                    } else if (tech.equals(IsoDep.class.getName())) {
                                        mNfcIsoDep = IsoDep.get(tag);
                                    }
                                }
                            }
                        },
                        flags,
                        null);
                mNfcAdapterReaderModeEnabled = true;
            }
        }
    }

    /**
     * Set device engagement received via QR code.
     *
     * <p>This method parses the QR code device engagement.  If a valid device engagement is
     * received the {@link Listener#onDeviceEngagementReceived(List)} will be called.
     *
     * This method must be called before {@link #connect(byte[])}.
     *
     * @param qrDeviceEngagement text form qrCode device engagement
     * @return {@code true} if parsing succeeded, {@code false} otherwise.
     */
    public void setDeviceEngagementFromQrCode(@NonNull String qrDeviceEngagement) {
        Uri uri = Uri.parse(qrDeviceEngagement);
        if (uri != null && uri.getScheme() != null
                && uri.getScheme().equals("mdoc")) {
            byte[] encodedDeviceEngagement = Base64.decode(
                    uri.getEncodedSchemeSpecificPart(),
                    Base64.URL_SAFE | Base64.NO_PADDING);
            if (encodedDeviceEngagement != null) {
                Log.d(TAG, "DE: " + Util.toHex(encodedDeviceEngagement));

                byte[] encodedHandover = Util.cborEncode(SimpleValue.NULL);
                setDeviceEngagement(encodedDeviceEngagement, encodedHandover);
                List<byte[]> readerAvailableDeviceRetrievalMethods =
                        Util.extractDeviceRetrievalMethods(
                                encodedDeviceEngagement);

                if (!readerAvailableDeviceRetrievalMethods.isEmpty()) {
                    reportDeviceEngagementReceived(readerAvailableDeviceRetrievalMethods);
                    return;
                }
            }
        }
        reportError(new IllegalArgumentException("Invalid QR Code device engagement text."));
    }

    void setNdefDeviceEngagement(@NonNull Ndef ndef) {
        byte[] handoverSelectMessage;
        byte[] encodedDeviceEngagement = null;
        boolean validHandoverSelectMessage = false;
        List<byte[]> deviceRetrievalMethods = new ArrayList<>();


        NdefMessage m = ndef.getCachedNdefMessage();

        handoverSelectMessage = m.toByteArray();
        Log.d(TAG, "In decodeNdefTag, handoverSelectMessage: " + Util.toHex(m.toByteArray()));

        for (NdefRecord r : m.getRecords()) {
            Log.d(TAG, " record: " + Util.toHex(r.getPayload()));

            // Handover Select record
            //
            if (r.getTnf() == 0x01
                    && Arrays.equals(r.getType(), "Hs".getBytes(StandardCharsets.UTF_8))) {
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
                    Log.d(TAG, "Handover Select...");
                    byte[] ndefMessage = new byte[payload.length - 1];
                    System.arraycopy(payload, 1, ndefMessage, 0, payload.length - 1);
                    try {
                        NdefMessage message = new NdefMessage(ndefMessage);
                        for (NdefRecord embeddedRecord : message.getRecords()) {
                            // TODO: check that the ALTERNATIVE_CARRIER_RECORD matches
                            //   the ALTERNATIVE_CARRIER_CONFIGURATION record retrieved below.
                        }
                    } catch (FormatException e) {
                        Log.w(TAG, "Error parsing NdefMessage in Handover Select message");
                    }
                    validHandoverSelectMessage = true;
                }
            }

            // DeviceEngagement record
            //
            if (r.getTnf() == 0x04
                    && Arrays.equals(r.getType(),
                    "iso.org:18013:deviceengagement".getBytes(StandardCharsets.UTF_8))
                    && Arrays.equals(r.getId(), "mdoc".getBytes(StandardCharsets.UTF_8))) {
                encodedDeviceEngagement = r.getPayload();
                Log.d(TAG, "Woot, got DE: " + Util.toHex(encodedDeviceEngagement));
            }

            // BLE Carrier Configuration record
            //
            if (r.getTnf() == 0x02
                    && Arrays.equals(r.getType(),
                    "application/vnd.bluetooth.le.oob".getBytes(StandardCharsets.UTF_8))
                    && Arrays.equals(r.getId(), "0".getBytes(StandardCharsets.UTF_8))) {
                Log.d(TAG, "Woot, got BLE OOB " + Util.toHex(r.getPayload()));
                byte[] deviceRetrievalMethod = DataTransportBle.parseNdefRecord(r);
                if (deviceRetrievalMethod != null) {
                    Log.d(TAG, "WOOT we have a BLE DRM!");
                    deviceRetrievalMethods.add(deviceRetrievalMethod);
                }
            }

            // Wifi Aware Carrier Configuration record
            //
            if (r.getTnf() == 0x02
                    && Arrays.equals(r.getType(),
                    "application/vnd.wfa.nan".getBytes(StandardCharsets.UTF_8))
                    && Arrays.equals(r.getId(), "W".getBytes(StandardCharsets.UTF_8))) {
                Log.d(TAG, "Woot, got Wifi-Aware OOB " + Util.toHex(r.getPayload()));
                byte[] deviceRetrievalMethod = DataTransportWifiAware.parseNdefRecord(r);
                if (deviceRetrievalMethod != null) {
                    Log.d(TAG, "WOOT we have a Wifi-Aware DRM!");
                    deviceRetrievalMethods.add(deviceRetrievalMethod);
                }
            }

            // NFC Carrier Configuration record
            //
            if (r.getTnf() == 0x02
                    && Arrays.equals(r.getType(),
                    "iso.org:18013:nfc".getBytes(StandardCharsets.UTF_8))
                    && Arrays.equals(r.getId(), "nfc".getBytes(StandardCharsets.UTF_8))) {
                Log.d(TAG, "Woot, got NFC OOB " + Util.toHex(r.getPayload()));
                byte[] deviceRetrievalMethod = DataTransportNfc.parseNdefRecord(r);
                if (deviceRetrievalMethod != null) {
                    Log.d(TAG, "WOOT we have a NFC DRM!");
                    deviceRetrievalMethods.add(deviceRetrievalMethod);
                }
            }

            // Generic Carrier Configuration record
            //
            // (TODO: remove before landing b/c application/vnd.android.ic.dmr is not registered)
            //
            if (r.getTnf() == 0x02
                    && Arrays.equals(r.getType(),
                    "application/vnd.android.ic.dmr".getBytes(StandardCharsets.UTF_8))) {
                Log.d(TAG, "Woot, got generic DRM " + Util.toHex(r.getPayload()));
                byte[] deviceRetrievalMethod = r.getPayload();
                deviceRetrievalMethods.add(deviceRetrievalMethod);
            }
        }

        if (validHandoverSelectMessage && deviceRetrievalMethods.size() > 0) {
            Log.d(TAG, "Received DE through NFC");
            byte[] readerHandover = Util.cborEncode(new CborBuilder()
                    .addArray()
                    .add(handoverSelectMessage)    // Handover Select message
                    .add(SimpleValue.NULL)         // Handover Request message
                    .end()
                    .build().get(0));
            setDeviceEngagement(encodedDeviceEngagement, readerHandover);
            reportDeviceEngagementReceived(deviceRetrievalMethods);
        } else {
            reportError(new IllegalArgumentException("Ndef tag is not valid."));
        }
    }

    private void setDeviceEngagement(@NonNull byte[] deviceEngagement, @NonNull byte[] handover) {
        mDeviceEngagement = deviceEngagement;
        mHandover = handover;

        mSessionEncryptionReader = new SessionEncryptionReader(
                mEphemeralKeyPair.getPrivate(),
                mEphemeralKeyPair.getPublic(),
                mDeviceEngagement,
                mHandover);
    }

    /**
     * Establishes connection to remote mdoc using the given <code>deviceRetrievalMethod</code>.
     *
     * <p>This method should be called after receiving the
     * {@link Listener#onDeviceEngagementReceived(List)} callback.
     *
     * @param deviceRetrievalMethod selected transfer method
     */
    public void connect(@NonNull byte[] deviceRetrievalMethod) {
        switch (Util.getDeviceRetrievalMethodType(deviceRetrievalMethod)) {
            case DataTransportBle.DEVICE_RETRIEVAL_METHOD_TYPE:
                BleOptions bleOptions = DataTransportBle.parseDeviceRetrievalMethod(deviceRetrievalMethod);
                // As per 18013-5 section a reader should always prefer mdoc central client mode if it's
                // available.
                if (bleOptions.supportsCentralClientMode) {
                    mDataTransport = new DataTransportBleCentralClientMode(mContext, mLoggingFlags);
                } else if (bleOptions.supportsPeripheralServerMode) {
                    mDataTransport = new DataTransportBlePeripheralServerMode(mContext, mLoggingFlags);
                } else {
                    throw new IllegalArgumentException(
                        "Neither mdoc central client nor mdoc peripheral server mode is true");
                }
                break;
            case DataTransportWifiAware.DEVICE_RETRIEVAL_METHOD_TYPE:
                mDataTransport = new DataTransportWifiAware(mContext);
                break;
            case DataTransportNfc.DEVICE_RETRIEVAL_METHOD_TYPE:
                mDataTransport = new DataTransportNfc(mContext);
                if (mNfcIsoDep == null) {
                    Log.e(TAG, "IsoDep is null for NFC transport");
                    throw new IllegalArgumentException("IsoDep is null for NFC transport");
                }
                // TODO: remove, see comment in DataTransportNfc.java
                ((DataTransportNfc) mDataTransport).setIsoDep(mNfcIsoDep);
                break;
            case DataTransportTcp.DEVICE_RETRIEVAL_METHOD_TYPE:
                mDataTransport = new DataTransportTcp(mContext);
                break;
            default:
                Log.e(TAG, "Unsupported Transport Selected: " + deviceRetrievalMethod);
                throw new IllegalArgumentException("Unsupported Transport Selected");
        }

        mDataTransport.setListener(new DataTransport.Listener() {
            @Override
            public void onListeningSetupCompleted(@Nullable byte[] encodedDeviceRetrievalMethod) {
                Log.d(TAG, "onListeningSetupCompleted for " + mDataTransport);
            }

            @Override
            public void onListeningPeerConnecting() {
                Log.d(TAG, "onListeningPeerConnecting for " + mDataTransport);
            }

            @Override
            public void onListeningPeerConnected() {
                Log.d(TAG, "onListeningPeerConnected for " + mDataTransport);
            }

            @Override
            public void onListeningPeerDisconnected() {
                Log.d(TAG, "onListeningPeerDisconnected for " + mDataTransport);
                reportDeviceDisconnected(false);
            }

            @Override
            public void onConnectionResult(@Nullable Throwable error) {
                Log.d(TAG, "onConnectionResult for " + mDataTransport + ": " + error);
                if (error != null) {
                    mDataTransport.close();
                    reportError(error);
                } else {
                    reportDeviceConnected();
                }
            }

            @Override
            public void onConnectionDisconnected() {
                Log.d(TAG, "onConnectionDisconnected for " + mDataTransport);
                mDataTransport.close();
                reportError(new IllegalStateException("Error: Disconnected"));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                Log.w(TAG, "onError for " + mDataTransport + ": " + error);
                mDataTransport.close();
                reportError(error);
                error.printStackTrace();
            }

            @Override
            public void onMessageReceived(@NonNull byte[] data) {
                //Log.d(TAG, "onMessageReceived for " + this + ": " + Util.toHex(data));
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
                }

                // If there's data in the message, assume it's DeviceResponse (ISO 18013-5
                // currently does not define other kinds of messages).
                //
                if (decryptedMessage.first != null) {
                    //Log.d(TAG, "Device Response: " + Util.toHex(decryptedMessage.first));
                    Util.dumpHex(decryptedMessage.first);
                    reportResponseReceived(decryptedMessage.first);
                } else {
                    // No data, so status must be set.
                    if (!decryptedMessage.second.isPresent()) {
                        mDataTransport.close();
                        reportError(new Error("No data and no status in SessionData"));
                    } else {
                        int statusCode = decryptedMessage.second.getAsInt();
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
                Log.e(TAG, "Received onTransportSpecificSessionTermination");
                mDataTransport.close();
                reportDeviceDisconnected(true);
            }

        }, mDeviceResponseListenerExecutor);

        try {
            DataItem deDataItem = Util.cborDecode(mDeviceEngagement);
            DataItem eDeviceKeyBytesDataItem = Util.cborMapExtractArray(deDataItem, 1).get(1);
            byte[] encodedEDeviceKeyBytes = Util.cborEncode(eDeviceKeyBytesDataItem);
            mDataTransport.setEDeviceKeyBytes(encodedEDeviceKeyBytes);
            mDataTransport.connect(deviceRetrievalMethod);
        } catch (Exception e) {
            Log.e(TAG, "Error connecting", e);
            reportError(e);
        }

    }

    void reportDeviceDisconnected(boolean transportSpecificTermination) {
        if (mListener != null) {
            mDeviceResponseListenerExecutor.execute(
                    () -> mListener.onDeviceDisconnected(transportSpecificTermination));
        }
    }

    void reportResponseReceived(@NonNull byte[] deviceResponseBytes) {
        if (mListener != null) {
            mDeviceResponseListenerExecutor.execute(
                    () -> mListener.onResponseReceived(deviceResponseBytes));
        }
    }

    void reportDeviceConnected() {
        if (mListener != null) {
            mDeviceResponseListenerExecutor.execute(
                    () -> mListener.onDeviceConnected());
        }
    }

    void reportDeviceEngagementReceived(@NonNull List<byte[]> deviceRetrievalMethods) {
        if (mListener != null) {
            mDeviceResponseListenerExecutor.execute(
                    () -> mListener.onDeviceEngagementReceived(
                            deviceRetrievalMethods));
        }
    }

    void reportError(Throwable error) {
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
     * <p>This method is idempotent, e.g. it is safe to call multiple times.
     */
    public void disconnect() {
        if (mNfcAdapterReaderModeEnabled) {
            mNfcAdapter.disableReaderMode(mNfcAdapterActivity);
            mNfcAdapterReaderModeEnabled = false;
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
     * <p>Should be called in response to {@link Listener#onDeviceConnected()} callback.
     *
     * <p>The <code>deviceReqeustBytes</code> parameter must be <code>DeviceRequest</code>
     * <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>. The
     * {@link DeviceRequestGenerator} class can be used to generate this.
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
     */
    public @NonNull byte[] getSessionTranscript() {
        return mSessionEncryptionReader.getSessionTranscript();
    }

    /**
     * Gets the ephemeral key used by the reader for session encryption.
     *
     * <p>This is made available because it's needed for mdoc verification when using the MAC
     * mechanism.
     *
     * @return
     */
    public @NonNull PrivateKey getEphemeralReaderKey() {
        return mEphemeralKeyPair.getPrivate();
    }

    /**
     * Sets the listener for listening to events from the remote mdoc device.
     *
     * <p>This may be called multiple times but only the most recent listener will be used.
     *
     * @param listener the listener or <code>null</code> to stop listening.
     * @param executor a {@link Executor} to do the call in or <code>null</code> if
     *                 <code>listener</code> is <code>null</code>.
     */
    public void setListener(@Nullable Listener listener,
            @Nullable Executor executor) {
        if (listener != null && executor == null) {
            Log.e(TAG, "Cannot have non-null listener with null executor");
        }
        mListener = listener;
        mDeviceResponseListenerExecutor = executor;
    }

    /**
     * Sets whether to use transport-specific session termination.
     *
     * <p>By default this is set to <code>false</code>.
     *
     * <p>This is currently only supported for BLE. Use
     * {@link #isTransportSpecificTerminationSupported()} to determine if it's available for the
     * current transport.
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
     * <p>This is only supported if connected via BLE.
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
     * {@link #disconnect()} is called.
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
         * {@link #connect(byte[])} in response to this callback.
         *
         * TODO: add parameter "List<byte[]> alternativeCarrierConfigurations"
         *  and also similar in connect().
         */
        void onDeviceEngagementReceived(@NonNull List<byte[]> deviceRetrievalMethods);

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
         * <p>When this is called the verification object should no longer be used.
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
         * <p>If this is called the verification object should no longer be used.
         *
         * @param error the error.
         */
        void onError(@NonNull Throwable error);
    }

}
