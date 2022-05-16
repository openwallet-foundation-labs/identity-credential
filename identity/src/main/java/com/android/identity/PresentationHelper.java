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

import android.content.Context;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.cardemulation.HostApduService;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.android.identity.Constants.BleDataRetrievalOption;
import com.android.identity.Constants.LoggingFlag;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Executor;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;

/**
 * Helper used for establishing engagement with, interacting with, and presenting credentials to a
 * remote <em>mdoc verifier</em> device.
 *
 * <p>This class implements the interface between an <em>mdoc</em> and <em>mdoc verifier</em> using
 * the connection setup and device retrieval interfaces defined in
 * <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5</a>.
 *
 * <p>Supported device engagement methods include QR code and NFC static handover. Support for
 * NFC negotiated handover may be added in a future release.
 *
 * <p>Supported device retrieval transfer methods include BLE (both <em>mdoc central client
 * mode</em> and <em>mdoc peripheral server</em>), Wifi Aware, and NFC.
 *
 * <p>Additional device engagement and device retrieval methods may be added in the future.
 *
 * <p>As with {@link PresentationSession}, instances of this class are only good for a single
 * session with a remote verifier. Once a session ends (indicated by e.g.
 * {@link Listener#onDeviceDisconnected(boolean)} or {@link Listener#onError(Throwable)} the
 * object should no longer be used.
 *
 * <p>For NFC device engagement the application should use a {@link HostApduService}
 * registered for AID <code>D2 76 00 00 85 01 01</code> (NFC Type 4 Tag) and for NFC data
 * transfer it should also be registered for AID <code>A0 00 00 02 48 04 00</code> (as per
 * ISO/IEC 18013-5 section 8.3.3.1.2). In both cases
 * {@link HostApduService#processCommandApdu(byte[], Bundle)} calls should be forwarded to
 * {@link #nfcProcessCommandApdu(HostApduService, byte[])} and
 * {@link HostApduService#onDeactivated(int)} calls should be forwarded to
 * {@link #nfcOnDeactivated(HostApduService, int)}.
 *
 * <p>Unlike {@link IdentityCredentialStore}, {@link IdentityCredential},
 * {@link WritableIdentityCredential}, and {@link PresentationSession} this class is never backed
 * by secure hardware and is entirely implemented in the Jetpack. The class does however depend
 * on data returned by
 * {@link PresentationSession#getCredentialData(String, CredentialDataRequest)} which may be
 * backed by secure hardware.
 */
// Suppress with NotCloseable since we actually don't hold any resources needing to be
// cleaned up at object finalization time.
@SuppressWarnings("NotCloseable")
public class PresentationHelper {
    /**
     * It is not known how the reader obtained connection information information.
     */
    public static final int DEVICE_ENGAGEMENT_METHOD_UNKNOWN = 0;
    /**
     * The reader connected via information conveyed in the QR code.
     */
    public static final int DEVICE_ENGAGEMENT_METHOD_QR_CODE = 1;
    /**
     * The reader connected via information conveyed via NFC.
     */
    public static final int DEVICE_ENGAGEMENT_METHOD_NFC = 2;
    private static final String TAG = "PresentationHelper";
    private static final int COMMAND_TYPE_OTHER = 0;
    private static final int COMMAND_TYPE_SELECT_BY_AID = 1;
    private static final int COMMAND_TYPE_SELECT_FILE = 2;
    private static final int COMMAND_TYPE_READ_BINARY = 3;
    private static final int COMMAND_TYPE_UPDATE_BINARY = 4;
    private static final int COMMAND_TYPE_ENVELOPE = 5;
    private static final int COMMAND_TYPE_RESPONSE = 6;
    private static final byte[] AID_FOR_MDL = Util.fromHex("D2760000850101");
    private static final byte[] AID_FOR_MDL_DATA_TRANSFER =
            Util.fromHex("A0000002480400");
    private static final int CAPABILITY_CONTAINER_FILE_ID = 0xe103;
    private static final int NDEF_FILE_ID = 0xe104;
    private static final byte[] CAPABILITY_FILE_CONTENTS = new byte[]{
            (byte) 0x00, (byte) 0x0f,  // size of capability container '00 0F' = 15 bytes
            (byte) 0x20,               // mapping version v2.0
            (byte) 0x7f, (byte) 0xFf,  // maximum response data length '7F FF'
            (byte) 0x7f, (byte) 0xFf,  // maximum command data length '7F FF'
            (byte) 0x04, (byte) 0x06,  // NDEF File Control TLV
            (byte) 0xe1, (byte) 0x04,  // NDEF file identifier 'E1 04'
            (byte) 0xff, (byte) 0xfe,  // maximum NDEF file size 'FF FE'
            (byte) 0x00,               // file read access condition (allow read)
            (byte) 0xff                // file write access condition (do not write)
    };
    private static final byte[] STATUS_WORD_INSTRUCTION_NOT_SUPPORTED = {(byte) 0x6d, (byte) 0x00};
    private static final byte[] STATUS_WORD_OK = {(byte) 0x90, (byte) 0x00};
    private static final byte[] STATUS_WORD_FILE_NOT_FOUND = {(byte) 0x6a, (byte) 0x82};
    private static final byte[] STATUS_WORD_END_OF_FILE_REACHED = {(byte) 0x62, (byte) 0x82};
    private static final byte[] STATUS_WORD_WRONG_PARAMETERS = {(byte) 0x6b, (byte) 0x00};
    private final KeyPair mEphemeralKeyPair;
    private final Context mContext;
    PresentationSession mPresentationSession;
    Listener mListener;
    Executor mDeviceRequestListenerExecutor;
    SessionEncryptionDevice mSessionEncryption;
    SessionEncryptionDevice mSessionEncryptionForNfc;
    DataTransport mActiveTransport;
    byte[] mEncodedDeviceEngagement;
    byte[] mEncodedDeviceEngagementForNfc;
    byte[] mEncodedHandover;
    byte[] mEncodedHandoverForNfc;
    int mNumTransportsStillSettingUp;
    boolean mReceivedSessionTerminated;
    int mDeviceEngagementMethod = DEVICE_ENGAGEMENT_METHOD_UNKNOWN;
    HostApduService mApduService;
    @LoggingFlag
    int mLoggingFlags = 0;
    private ArrayList<DataTransport> mTransports = new ArrayList<>();
    private boolean mReportedDeviceConnecting;
    private boolean mReportedEngagementDetected;
    private boolean mInhibitCallbacks;
    private byte[] mSelectedNfcFile;
    private boolean mUseTransportSpecificSessionTermination;
    private boolean mSendSessionTerminationMessage;
    Util.Logger mLog;

    /**
     * Creates a new {@link PresentationHelper}.
     *
     * @param context             the application context.
     * @param presentationSession a {@link PresentationSession} instance.
     */
    public PresentationHelper(@NonNull Context context,
            @NonNull PresentationSession presentationSession) {
        mPresentationSession = presentationSession;
        mContext = context;
        mEphemeralKeyPair = mPresentationSession.getEphemeralKeyPair();
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
        mLoggingFlags = loggingFlags;
        mLog.setLoggingFlags(loggingFlags);
    }

    // Note: The report*() methods are safe to call from any thread.

    void reportDeviceEngagementReady() {
        mLog.info("reportDeviceEngagementReady");
        final Listener listener = mListener;
        final Executor executor = mDeviceRequestListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onDeviceEngagementReady());
        }
    }

    void reportEngagementDetected() {
        mLog.info("reportEngagementDetected");
        final Listener listener = mListener;
        final Executor executor = mDeviceRequestListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onEngagementDetected());
        }
    }

    void reportDeviceConnecting() {
        mLog.info("reportDeviceConnecting");
        final Listener listener = mListener;
        final Executor executor = mDeviceRequestListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onDeviceConnecting());
        }
    }

    void reportDeviceConnected() {
        mLog.info("reportDeviceConnected");
        final Listener listener = mListener;
        final Executor executor = mDeviceRequestListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onDeviceConnected());
        }
    }

    void reportDeviceRequest(@DeviceEngagementMethod int deviceEngagementMethod,
            @NonNull byte[] deviceRequestBytes) {
        mLog.info("reportDeviceRequest: method: " + deviceEngagementMethod
                + ", deviceRequestBytes: " + deviceRequestBytes.length + " bytes");
        final Listener listener = mListener;
        final Executor executor = mDeviceRequestListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onDeviceRequest(
                    deviceEngagementMethod, deviceRequestBytes));
        }
    }

    void reportDeviceDisconnected(boolean transportSpecificTermination) {
        mLog.info("reportDeviceDisconnected: transportSpecificTermination: "
                + transportSpecificTermination);
        final Listener listener = mListener;
        final Executor executor = mDeviceRequestListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onDeviceDisconnected(
                    transportSpecificTermination));
        }
    }

    void reportError(@NonNull Throwable error) {
        mLog.info("reportError: error: ", error);
        final Listener listener = mListener;
        final Executor executor = mDeviceRequestListenerExecutor;
        if (!mInhibitCallbacks && listener != null && executor != null) {
            executor.execute(() -> listener.onError(error));
        }
    }

    /**
     * Used by PresentationHelperTest.java and testapp.
     *
     * @hide
     */
    public void addDataTransport(@NonNull DataTransport transport) {
        mTransports.add(transport);
    }

    /**
     * Begins the presentation.
     *
     * <p>This triggers the creation of one or more physical data transports and
     * device engagement listeners. The application can choose which data retrieval methods to
     * listen on using the {@code dataRetrievalListenerConfiguration} parameter.
     *
     * <p>Before calling this, an application should register a listener using
     * {@link #setListener(Listener, Executor)}.
     *
     * <p>When all transports have been set up, the
     * {@link Listener#onDeviceEngagementReady()} callback will be called.
     * At this point, {@link #getDeviceEngagementForQrCode()} can be called to obtain
     * the data to put in a QR code which can be displayed in the application.
     *
     * <p>For NFC engagement, the application should pass <code>APDUs</code> to
     * {@link #nfcProcessCommandApdu(HostApduService, byte[])}.
     *
     * <p>When a remote verifier device connects and makes a request for documents, the
     * {@link Listener#onDeviceRequest(int, byte[])}  callback will be invoked.
     *
     * @param dataRetrievalListenerConfiguration the data retrieval methods to start listening on.
     */
    public void startListening(
            @NonNull DataRetrievalListenerConfiguration dataRetrievalListenerConfiguration) {

        // The order here matters... it will be the same order in the array in the QR code
        // and we expect readers to pick the first one.
        //
        if (dataRetrievalListenerConfiguration.isBleEnabled()) {
            @BleDataRetrievalOption int opts =
                    dataRetrievalListenerConfiguration.getBleDataRetrievalOptions();

            // Use same UUID for both mdoc central client mode and mdoc peripheral server mode.
            // Why? Because it's required by ISO 18013-5 when using NFC static handover.
            UUID serviceUuid = UUID.randomUUID();
            // Option to indicate if L2CAP should be used if supported
            boolean supportL2CAP = (opts & Constants.BLE_DATA_RETRIEVAL_OPTION_L2CAP) !=0;

            if ((opts & Constants.BLE_DATA_RETRIEVAL_OPTION_MDOC_CENTRAL_CLIENT_MODE) != 0) {
                DataTransportBleCentralClientMode bleTransport =
                        new DataTransportBleCentralClientMode(mContext, mLoggingFlags);
                bleTransport.setServiceUuid(serviceUuid);
                bleTransport.setSupportL2CAP(supportL2CAP);
                mLog.info("Adding BLE mdoc central client mode transport");
                mTransports.add(bleTransport);
            }
            if ((opts & Constants.BLE_DATA_RETRIEVAL_OPTION_MDOC_PERIPHERAL_SERVER_MODE) != 0) {
                DataTransportBlePeripheralServerMode bleTransport =
                        new DataTransportBlePeripheralServerMode(mContext, mLoggingFlags);
                bleTransport.setServiceUuid(serviceUuid);
                bleTransport.setSupportL2CAP(supportL2CAP);
                mLog.info("Adding BLE mdoc peripheral server mode transport");
                mTransports.add(bleTransport);
            }
        }
        if (dataRetrievalListenerConfiguration.isWifiAwareEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mLog.info("Adding Wifi Aware transport");
                mTransports.add(new DataTransportWifiAware(mContext));
            } else {
                throw new IllegalArgumentException("Wifi Aware only available on API 29 or later");
            }
        }
        if (dataRetrievalListenerConfiguration.isNfcEnabled()) {
            mLog.info("Adding NFC transport");
            mTransports.add(new DataTransportNfc(mContext));
        }

        byte[] encodedEDeviceKeyBytes = Util.cborEncode(Util.cborBuildTaggedByteString(
                Util.cborEncode(Util.cborBuildCoseKey(mEphemeralKeyPair.getPublic()))));

        for (DataTransport t : mTransports) {
            t.setEDeviceKeyBytes(encodedEDeviceKeyBytes);
        }

        mNumTransportsStillSettingUp = 0;
        for (DataTransport transport : mTransports) {
            transport.setListener(new DataTransport.Listener() {
                @Override
                public void onListeningSetupCompleted(@Nullable DataRetrievalAddress address) {
                    mLog.info("onListeningSetupCompleted for " + transport);
                    mNumTransportsStillSettingUp -= 1;
                    if (mNumTransportsStillSettingUp == 0) {
                        allTransportsAreSetup();
                    }
                }

                @Override
                public void onListeningPeerConnecting() {
                    peerIsConnecting(transport);
                }

                @Override
                public void onListeningPeerConnected() {
                    mLog.info("onListeningPeerConnected for " + transport);
                    peerHasConnected(transport);
                }

                @Override
                public void onListeningPeerDisconnected() {
                    mLog.info("onListeningPeerDisconnected for " + transport);
                    transport.close();
                    if (!mReceivedSessionTerminated) {
                        reportError(
                                new Error("Peer disconnected without proper session termination"));
                    } else {
                        reportDeviceDisconnected(false);
                    }
                }

                @Override
                public void onConnectionResult(@Nullable Throwable error) {
                    mLog.info("onConnectionResult for " + transport);
                    if (error != null) {
                        throw new IllegalStateException("Unexpected onConnectionResult "
                                + "callback from transport " + transport, error);
                    }
                    throw new IllegalStateException("Unexpected onConnectionResult "
                            + "callback from transport " + transport);
                }

                @Override
                public void onConnectionDisconnected() {
                    mLog.info("onConnectionDisconnected for " + transport);
                    throw new IllegalStateException("Unexpected onConnectionDisconnected "
                            + "callback from transport " + transport);
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    transport.close();
                    reportError(error);
                }

                @Override
                public void onMessageReceived(@NonNull byte[] data) {
                    Pair<byte[], OptionalInt> decryptedMessage = null;
                    try {
                        decryptedMessage = mSessionEncryption.decryptMessageFromReader(data);
                        mDeviceEngagementMethod = DEVICE_ENGAGEMENT_METHOD_QR_CODE;
                    } catch (Exception e) {
                        transport.close();
                        reportError(new Error("Error decrypting message from reader", e));
                        return;
                    }
                    // If decryption failed it could be just because the reader engaged
                    // using NFC and not QR code... so try using the session encryption
                    // for NFC and see if that works...
                    if (decryptedMessage == null) {
                        if (mSessionEncryptionForNfc != null) {
                            try {
                                decryptedMessage =
                                    mSessionEncryptionForNfc.decryptMessageFromReader(data);
                            } catch (Exception e) {
                                transport.close();
                                reportError(new Error("Error decrypting message from reader", e));
                                return;
                            }
                            if (decryptedMessage != null) {
                                // This worked, switch to NFC for future messages..
                                //
                                mSessionEncryption = mSessionEncryptionForNfc;
                                mSessionEncryptionForNfc = null;
                                mDeviceEngagementMethod = DEVICE_ENGAGEMENT_METHOD_NFC;
                            }
                        }
                    }
                    if (decryptedMessage == null) {
                        mLog.info("Decryption failed!");
                        transport.close();
                        reportError(new Error("Error decrypting message from reader"));
                        return;
                    }

                    // If there's data in the message, assume it's DeviceRequest (ISO 18013-5
                    // currently does not define other kinds of messages).
                    //
                    if (decryptedMessage.first != null) {
                        // Only initialize the PresentationSession a single time.
                        //
                        if (mSessionEncryption.getNumMessagesEncrypted() == 0) {
                            mPresentationSession.setSessionTranscript(
                                    mSessionEncryption.getSessionTranscript());
                            try {
                                mPresentationSession.setReaderEphemeralPublicKey(
                                        mSessionEncryption.getEphemeralReaderPublicKey());
                            } catch (InvalidKeyException e) {
                                transport.close();
                                reportError(new Error("Reader ephemeral public key is invalid", e));
                                return;
                            }
                        }

                        if (mLog.isSessionEnabled()) {
                            Util.dumpHex(TAG, "Received DeviceRequest", decryptedMessage.first);
                        }

                        reportDeviceRequest(mDeviceEngagementMethod, decryptedMessage.first);
                    } else {
                        // No data, so status must be set.
                        if (!decryptedMessage.second.isPresent()) {
                            transport.close();
                            reportError(new Error("No data and no status in SessionData"));
                        } else {
                            int statusCode = decryptedMessage.second.getAsInt();

                            mLog.session("Message received from reader with status: " + statusCode);

                            if (statusCode == 20) {
                                mReceivedSessionTerminated = true;
                                transport.close();
                                reportDeviceDisconnected(false);
                            } else {
                                transport.close();
                                reportError(new Error("Expected status code 20, got "
                                        + statusCode + " instead"));
                            }
                        }
                    }

                }

                @Override
                public void onTransportSpecificSessionTermination() {
                    mLog.info("Received transport-specific session termination");
                    mReceivedSessionTerminated = true;
                    transport.close();
                    reportDeviceDisconnected(true);
                }

            }, mDeviceRequestListenerExecutor);
            mLog.info("Listening on transport " + transport);
            transport.listen();
            mNumTransportsStillSettingUp += 1;
        }

    }

    // TODO: handle the case where a transport never calls onListeningSetupCompleted... that
    //  is, set up a timeout to call this.
    //
    void allTransportsAreSetup() {
        mLog.info("All transports are now set up");

        // Calculate DeviceEngagement and Handover for QR code...
        //
        List<DataRetrievalAddress> listeningAddresses = new ArrayList<>();
        for (DataTransport transport : mTransports) {
            listeningAddresses.add(transport.getListeningAddress());
        }
        mEncodedDeviceEngagement = generateDeviceEngagement(listeningAddresses);
        mEncodedHandover = Util.cborEncode(SimpleValue.NULL);
        if (mLog.isEngagementEnabled()) {
            mLog.engagement("QR DE: " + Util.toHex(mEncodedDeviceEngagement));
            mLog.engagement("QR handover: " + Util.toHex(mEncodedHandover));
        }

        // Calculate DeviceEngagement and Handover for NFC static handover...
        //
        mEncodedDeviceEngagementForNfc = generateDeviceEngagement(null);
        mEncodedHandoverForNfc = Util.cborEncode(new CborBuilder()
                .addArray()
                .add(nfcCalculateHandover())   // Handover Select message
                .add(SimpleValue.NULL)         // Handover Request message
                .end()
                .build().get(0));
        if (mLog.isEngagementEnabled()) {
            mLog.engagement("NFC DE: " + Util.toHex(mEncodedDeviceEngagementForNfc));
            mLog.engagement("NFC handover: " + Util.toHex(mEncodedHandoverForNfc));
        }
        reportDeviceEngagementReady();
    }

    /**
     * Gets textual form of DeviceEngagement suitable for presentation in a QR code.
     *
     * <p>This returns the string <em>mdoc://</em> followed by the base64 encoding of the
     * bytes of the bytes of <code>DeviceEngagement</code> <a href="http://cbor.io/">CBOR</a>
     * for QR Code engagement as specified in <em>ISO/IEC 18013-5</em> section 8.2 <em>Device
     * eEgagement</em>.
     *
     * @return a string that can be displayed in a QR code.
     */
    public @NonNull
    String getDeviceEngagementForQrCode() {
        String base64EncodedDeviceEngagement =
                Base64.encodeToString(mEncodedDeviceEngagement,
                        Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        Uri uri = new Uri.Builder()
                .scheme("mdoc")
                .encodedOpaquePart(base64EncodedDeviceEngagement)
                .build();
        String uriString = uri.toString();
        mLog.info("qrCode URI: " + uriString);
        return uriString;
    }

    /**
     * @hide For use in PresentationHelperTest.java only.
     */
    public @NonNull
    byte[] getDeviceEngagementForQrCodeRaw() {
        return mEncodedDeviceEngagement;
    }

    /**
     * Gets the session transcript.
     *
     * <p>This must not be called until a message has been received from the mdoc verifier.
     *
     * <p>See <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5</a> for the
     * definition of the bytes in the session transcript.
     *
     * @return the session transcript.
     * @throws IllegalStateException if called before a message is received from the verifier.
     */
    public @NonNull
    byte[] getSessionTranscript() {
        if (mSessionEncryption == null) {
            throw new IllegalStateException("No message received from verifier");
        }
        return mSessionEncryption.getSessionTranscript();
    }

    void peerIsEngaging() {
        if (!mReportedEngagementDetected) {
            mReportedEngagementDetected = true;
            reportEngagementDetected();
        }
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
        mLog.info("Peer has connected on transport " + transport
                + " - shutting down other transports");
        for (DataTransport t : mTransports) {
            if (t != transport) {
                t.setListener(null, null);
                t.close();
            }
        }

        mActiveTransport = transport;
        mSessionEncryption = new SessionEncryptionDevice(
                mEphemeralKeyPair.getPrivate(),
                mEncodedDeviceEngagement,
                mEncodedHandover);
        mSessionEncryptionForNfc = new SessionEncryptionDevice(
                mEphemeralKeyPair.getPrivate(),
                mEncodedDeviceEngagementForNfc,
                mEncodedHandoverForNfc);

        reportDeviceConnected();
    }

    // The |listeningAddresses| parameter can only be null for NFC engagement.
    //
    private @NonNull
    byte[] generateDeviceEngagement(@Nullable List<DataRetrievalAddress> listeningAddresses) {

        DataItem eDeviceKeyBytes = Util.cborBuildTaggedByteString(
                Util.cborEncode(Util.cborBuildCoseKey(mEphemeralKeyPair.getPublic())));

        DataItem securityDataItem = new CborBuilder()
                .addArray()
                .add(1) // cipher suite
                .add(eDeviceKeyBytes)
                .end()
                .build().get(0);

        DataItem deviceRetrievalMethodsDataItem = null;
        if (listeningAddresses != null) {
            CborBuilder deviceRetrievalMethodsBuilder = new CborBuilder();
            ArrayBuilder<CborBuilder> arrayBuilder = deviceRetrievalMethodsBuilder.addArray();
            for (DataRetrievalAddress address : listeningAddresses) {
                address.addDeviceRetrievalMethodsEntry(arrayBuilder, listeningAddresses);
            }
            arrayBuilder.end();
            deviceRetrievalMethodsDataItem = deviceRetrievalMethodsBuilder.build().get(0);
        }

        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> map = builder.addMap();
        map.put(0, "1.0").put(new UnsignedInteger(1), securityDataItem);
        if (deviceRetrievalMethodsDataItem != null) {
            map.put(new UnsignedInteger(2), deviceRetrievalMethodsDataItem);
        }
        map.end();
        return Util.cborEncode(builder.build().get(0));
    }

    private int nfcGetCommandType(@NonNull byte[] apdu) {
        if (apdu.length < 3) {
            return COMMAND_TYPE_OTHER;
        }
        int ins = apdu[1] & 0xff;
        int p1 = apdu[2] & 0xff;
        if (ins == 0xA4) {
            if (p1 == 0x04) {
                return COMMAND_TYPE_SELECT_BY_AID;
            } else if (p1 == 0x00) {
                return COMMAND_TYPE_SELECT_FILE;
            }
        } else if (ins == 0xb0) {
            return COMMAND_TYPE_READ_BINARY;
        } else if (ins == 0xd6) {
            return COMMAND_TYPE_UPDATE_BINARY;
        } else if (ins == 0xc0) {
            return COMMAND_TYPE_RESPONSE;
        } else if (ins == 0xc3) {
            return COMMAND_TYPE_ENVELOPE;
        }
        return COMMAND_TYPE_OTHER;
    }

    /**
     * Processes the given APDU received from the verifier when performing NFC engagement and NFC
     * data transfer.
     *
     * <p>Part of this processing may include sending an appropriate response back to the
     * verifier using the passed-in {@link HostApduService}.
     *
     * <p>Applications should call this method in their
     * {@link HostApduService#processCommandApdu(byte[], Bundle)} callback.
     *
     * @param service the {@link HostApduService} owned by the application.
     * @param apdu    the APDU.
     */
    public void nfcProcessCommandApdu(@NonNull HostApduService service,
            @NonNull byte[] apdu) {
        byte[] ret = null;

        mApduService = service;

        peerIsEngaging();

        if (mLog.isTransportVerboseEnabled()) {
            mLog.transportVerbose("nfcProcessCommandApdu: command: " + Util.toHex(apdu));
        }

        switch (nfcGetCommandType(apdu)) {
            case COMMAND_TYPE_OTHER:
                ret = STATUS_WORD_INSTRUCTION_NOT_SUPPORTED;
                break;
            case COMMAND_TYPE_SELECT_BY_AID:
                ret = nfcEngagementHandleSelectByAid(apdu);
                break;
            case COMMAND_TYPE_SELECT_FILE:
                ret = nfcEngagementHandleSelectFile(apdu);
                break;
            case COMMAND_TYPE_READ_BINARY:
                ret = nfcEngagementHandleReadBinary(apdu);
                break;
            case COMMAND_TYPE_UPDATE_BINARY:
                ret = nfcEngagementHandleUpdateBinary(apdu);
                break;
            case COMMAND_TYPE_ENVELOPE:
                ret = nfcEngagementHandleEnvelope(apdu);
                break;
            case COMMAND_TYPE_RESPONSE:
                ret = nfcEngagementHandleResponse(apdu);
                break;
            default:
                ret = STATUS_WORD_INSTRUCTION_NOT_SUPPORTED;
                break;
        }

        if (ret != null) {
            if (mLog.isTransportVerboseEnabled()) {
                mLog.transportVerbose("APDU response: " + Util.toHex(ret));
            }
            service.sendResponseApdu(ret);
        }
    }

    private @NonNull
    byte[] nfcEngagementHandleSelectByAid(@NonNull byte[] apdu) {
        mLog.info("in nfcEngagementHandleSelectByAid");
        if (apdu.length < 12) {
            return STATUS_WORD_FILE_NOT_FOUND;
        }
        if (Arrays.equals(Arrays.copyOfRange(apdu, 5, 12), AID_FOR_MDL)) {
            mLog.info("NFC engagement AID selected");
            return STATUS_WORD_OK;
        } else if (Arrays.equals(Arrays.copyOfRange(apdu, 5, 12), AID_FOR_MDL_DATA_TRANSFER)) {
            if (mActiveTransport != null) {
                mLog.info("Rejecting NFC data transfer, another transport is already active");
                return STATUS_WORD_FILE_NOT_FOUND;
            }
            for (DataTransport t : mTransports) {
                if (t instanceof DataTransportNfc) {
                    ((DataTransportNfc) t).onDataTransferAidSelected(
                            new DataTransportNfc.ResponseInterface() {
                                @Override
                                public void sendResponseApdu(@NonNull byte[] responseApdu) {
                                    mApduService.sendResponseApdu(responseApdu);
                                }
                            });

                    mLog.info("NFC data transfer AID selected");
                    return STATUS_WORD_OK;
                }
            }
            mLog.info("Rejecting NFC data transfer since it wasn't set up");
            return STATUS_WORD_FILE_NOT_FOUND;
        }
        return STATUS_WORD_FILE_NOT_FOUND;
    }

    private @NonNull
    byte[] nfcCalculateStaticHandoverSelectPayload(
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
                    "ac".getBytes(StandardCharsets.UTF_8),
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
    byte[] nfcCalculateHandover() {
        Collection<NdefRecord> carrierConfigurationRecords = new ArrayList<>();
        List<byte[]> alternativeCarrierRecords = new ArrayList<>();

        List<DataRetrievalAddress> listeningAddresses = new ArrayList<>();
        for (DataTransport transport : mTransports) {
            listeningAddresses.add(transport.getListeningAddress());
        }
        for (DataRetrievalAddress address : listeningAddresses) {

            Pair<NdefRecord, byte[]> records = address.createNdefRecords(listeningAddresses);
            if (records != null) {
                if (mLog.isEngagementEnabled()) {
                    mLog.engagement("Address " + address + ": alternativeCarrierRecord: "
                                    + Util.toHex(records.second) + " carrierConfigurationRecord: "
                                    + Util.toHex(records.first.getPayload()));
                }
                alternativeCarrierRecords.add(records.second);
                carrierConfigurationRecords.add(records.first);
            } else {
                mLog.engagement("Address " + address + " yielded no NDEF records");
            }

        }

        NdefRecord[] arrayOfRecords = new NdefRecord[carrierConfigurationRecords.size() + 2];

        byte[] hsPayload = nfcCalculateStaticHandoverSelectPayload(alternativeCarrierRecords);
        arrayOfRecords[0] = new NdefRecord((short) 0x01,
                "Hs".getBytes(StandardCharsets.UTF_8),
                null,
                hsPayload);

        arrayOfRecords[1] = new NdefRecord((short) 0x04,
                "iso.org:18013:deviceengagement".getBytes(StandardCharsets.UTF_8),
                "mdoc".getBytes(StandardCharsets.UTF_8),
                mEncodedDeviceEngagementForNfc);

        int n = 2;
        for (NdefRecord record : carrierConfigurationRecords) {
            arrayOfRecords[n++] = record;
        }

        NdefMessage message = new NdefMessage(arrayOfRecords);
        return message.toByteArray();
    }

    private @NonNull
    byte[] nfcEngagementHandleSelectFile(@NonNull byte[] apdu) {
        mLog.info("in nfcEngagementHandleSelectFile");
        if (apdu.length < 7) {
            return STATUS_WORD_FILE_NOT_FOUND;
        }
        int fileId = (apdu[5] & 0xff) * 256 + (apdu[6] & 0xff);
        // We only support two files
        if (fileId == CAPABILITY_CONTAINER_FILE_ID) {
            mSelectedNfcFile = CAPABILITY_FILE_CONTENTS;
        } else if (fileId == NDEF_FILE_ID) {
            byte[] handoverMessage = nfcCalculateHandover();
            if (mLog.isEngagementEnabled()) {
                mLog.engagement("handoverMessage: " + Util.toHex(handoverMessage));
            }
            byte[] fileContents = new byte[handoverMessage.length + 2];
            fileContents[0] = (byte) (handoverMessage.length / 256);
            fileContents[1] = (byte) (handoverMessage.length & 0xff);
            System.arraycopy(handoverMessage, 0, fileContents, 2, handoverMessage.length);
            mSelectedNfcFile = fileContents;
        } else {
            return STATUS_WORD_FILE_NOT_FOUND;
        }
        return STATUS_WORD_OK;
    }

    private @NonNull
    byte[] nfcEngagementHandleReadBinary(@NonNull byte[] apdu) {
        mLog.session("in nfcEngagementHandleReadBinary");
        if (apdu.length < 5) {
            return STATUS_WORD_FILE_NOT_FOUND;
        }
        byte[] contents = mSelectedNfcFile;
        int offset = (apdu[2] & 0xff) * 256 + (apdu[3] & 0xff);
        int size = apdu[4] & 0xff;

        if (offset >= contents.length) {
            return STATUS_WORD_WRONG_PARAMETERS;
        }
        if ((offset + size) > contents.length) {
            return STATUS_WORD_END_OF_FILE_REACHED;
        }

        byte[] response = new byte[size + STATUS_WORD_OK.length];
        System.arraycopy(contents, offset, response, 0, size);
        System.arraycopy(STATUS_WORD_OK, 0, response, size, STATUS_WORD_OK.length);
        return response;
    }

    private @NonNull
    byte[] nfcEngagementHandleUpdateBinary(@NonNull byte[] unusedApdu) {
        mLog.info("in nfcEngagementHandleUpdateBinary");
        return STATUS_WORD_INSTRUCTION_NOT_SUPPORTED;
    }

    private @NonNull
    byte[] nfcEngagementHandleEnvelope(@NonNull byte[] apdu) {
        mLog.info("in nfcEngagementHandleEnvelope");
        if (!(mActiveTransport instanceof DataTransportNfc)) {
            reportError(new Error("Received NFC ENVELOPE but active transport isn't NFC."));
            return STATUS_WORD_INSTRUCTION_NOT_SUPPORTED;
        }
        ((DataTransportNfc) mActiveTransport).onEnvelopeApduReceived(apdu);
        // Response will be posted by onEnvelopeApduReceived() when needed...
        return null;
    }

    private @NonNull
    byte[] nfcEngagementHandleResponse(@NonNull byte[] apdu) {
        mLog.info("in nfcEngagementHandleResponse");
        if (!(mActiveTransport instanceof DataTransportNfc)) {
            reportError(new Error("Received NFC GET RESPONSE but active transport isn't NFC."));
            return STATUS_WORD_INSTRUCTION_NOT_SUPPORTED;
        }
        ((DataTransportNfc) mActiveTransport).onGetResponseApduReceived(apdu);
        // Response will be posted by onEnvelopeApduReceived() when needed...
        return STATUS_WORD_OK;
    }

    /**
     * Method to call when NFC link is deactivated.
     *
     * <p>Applications should call this method in their
     * {@link HostApduService#onDeactivated(int)} callback.
     *
     * @param service the {@link HostApduService} owned by the application.
     * @param reason the <code>reason</code> passed to {@link HostApduService#onDeactivated(int)}.
     */
    public void nfcOnDeactivated(@NonNull HostApduService service, int reason) {
        mLog.info("nfcOnDeactivated, reason: " + reason);
        mSelectedNfcFile = null;
    }

    /**
     * Send a response to the remote mdoc verifier.
     *
     * <p>This is typically called in response to the {@link Listener#onDeviceRequest(int, byte[])}
     * callback.
     *
     * <p>The <code>deviceResponseBytes</code> parameter should contain CBOR conforming to
     * <code>DeviceResponse</code> <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>. This
     * can be generated using {@link DeviceResponseGenerator}.
     *
     * @param deviceResponseBytes the response to send.
     */
    public void sendDeviceResponse(@NonNull byte[] deviceResponseBytes) {
        if (mLog.isSessionEnabled()) {
            Util.dumpHex(TAG, "Sending DeviceResponse", deviceResponseBytes);
        }
        byte[] encryptedData =
                mSessionEncryption.encryptMessageToReader(deviceResponseBytes, OptionalInt.empty());
        mActiveTransport.sendMessage(encryptedData);
    }

    /**
     * Sets the listener for listening to events from the remote mdoc verifier device.
     *
     * <p>This may be called multiple times but only the most recent listener will be used.
     *
     * @param listener the listener or <code>null</code> to stop listening.
     * @param executor a {@link Executor} to do the call in or <code>null</code> if
     *                 <code>listener</code> is <code>null</code>.
     * @throws IllegalStateException if {@link Executor} is {@code null} for a non-{@link null}
     * listener.
     */
    public void setListener(@Nullable Listener listener, @Nullable Executor executor) {
        if (listener != null && executor == null) {
            throw new IllegalStateException("Cannot have non-null listener with null executor");
        }
        mListener = listener;
        mDeviceRequestListenerExecutor = executor;
    }

    /**
     * Stops the presentation, shuts down all transports used, and stops listening on
     * transports previously brought into a listening state using
     * {@link #startListening(DataRetrievalListenerConfiguration)}.
     *
     * <p>If connected to a mdoc verifier also sends a session termination message prior to
     * disconnecting if applicable. See {@link #setSendSessionTerminationMessage(boolean)} and
     * {@link #setUseTransportSpecificSessionTermination(boolean) for how to configure this.
     *
     * <p>No callbacks will be done on a listener registered with
     * {@link #setListener(Listener, Executor)} after calling this.
     *
     * <p>This method is idempotent so it is safe to call multiple times.
     */
    public void disconnect() {
        mInhibitCallbacks = true;
        if (mActiveTransport != null) {
            // Only send session termination message if the session was actually established.
            boolean sessionEstablished = (mSessionEncryption.getNumMessagesDecrypted() > 0);
            if (mSendSessionTerminationMessage && sessionEstablished) {
                if (mUseTransportSpecificSessionTermination &&
                        mActiveTransport.supportsTransportSpecificTerminationMessage()) {
                    mLog.info("Sending transport-specific termination message");
                    mActiveTransport.sendTransportSpecificTerminationMessage();
                } else {
                    mLog.info("Sending generic session termination message");
                    byte[] sessionTermination = mSessionEncryption.encryptMessageToReader(
                            null, OptionalInt.of(20));
                    mActiveTransport.sendMessage(sessionTermination);
                }
            } else {
                mLog.info("Not sending session termination message");
            }
            mActiveTransport.close();
            mActiveTransport = null;
        }
        if (mTransports != null) {
            for (DataTransport transport : mTransports) {
                transport.close();
            }
            mTransports = null;
        }
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
     *
     * @throws IllegalStateException if called when there is no current connection available.
     */
    public boolean isTransportSpecificTerminationSupported() {
        if (mActiveTransport == null) {
            throw new IllegalStateException("There is no connection is not available");
        }
        return mActiveTransport.supportsTransportSpecificTerminationMessage();
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

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @IntDef(value = {DEVICE_ENGAGEMENT_METHOD_UNKNOWN,
            DEVICE_ENGAGEMENT_METHOD_QR_CODE,
            DEVICE_ENGAGEMENT_METHOD_NFC})
    public @interface DeviceEngagementMethod {
    }

    /**
     * Interface for listening to messages from the remote verifier device.
     *
     * <p>The callbacks on this interface are called in the following order:
     * <ul>
     *     <li>{@link Listener#onDeviceEngagementReady()} is called when the transports requested
     *     via {@link #startListening(DataRetrievalListenerConfiguration)} have all been set up.
     *     At this point it's safe for the application to call
     *     {@link #getDeviceEngagementForQrCode()} to get the data to put in a QR code which can
     *     be displayed in the application.
     *     <li>{@link Listener#onEngagementDetected()} is called at the first sign of engagement
     *     with a remote verifier device when NFC engagement is used. It is never called if QR
     *     engagement is used.
     *     <li>{@link Listener#onDeviceConnecting()} is called at the first sign of a remote
     *     verifier device. Depending on the transport in use it could be several seconds until
     *     the next callback is called.
     *     <li>{@link Listener#onDeviceConnected()} is called when a remote verifier device has
     *     connected.
     *     <li>{@link Listener#onDeviceRequest(int, byte[])} is called when a request has been
     *     received from the remote verifier. It may be called multiple times.
     *     For each request the application is expected to process the request and call
     *     {@link #sendDeviceResponse(byte[])} or {@link #disconnect()}.
     *     <li>{@link Listener#onDeviceDisconnected(boolean)} is called when the remote verifier
     *     disconnects properly.
     * </ul>
     *
     * <p>The {@link Listener#onError(Throwable)} callback can be called at any time - for
     * example - if the remote verifier disconnects without using session termination or if the
     * underlying transport encounters an unrecoverable error.
     */
    public interface Listener {
        /**
         * Called when all transports are set up.
         *
         * <p>After this callback, it's safe to call {@link #getDeviceEngagementForQrCode()}.
         */
        void onDeviceEngagementReady();

        /**
         * Called at the first sign of engagement with a remote verifier device.
         *
         * <p>This is only called for NFC engagement.
         *
         * <p>This callback exists so the application can convey progress to the user.
         */
        void onEngagementDetected();

        /**
         * Called at the first sign of a remote verifier device.
         *
         * <p>Depending on the transport in use it could be several seconds until
         * {@link #onDeviceConnected} is called.
         *
         * <p>This callback exists so the application can convey progress to the user.
         */
        void onDeviceConnecting();

        /**
         * Called when a remote verifier device has connected.
         */
        void onDeviceConnected();

        /**
         * Called when the remote verifier device sends a request.
         *
         * <p>The <code>deviceRequestBytes</code> parameter contains the bytes of
         * <code>DeviceRequest</code> <a href="http://cbor.io/">CBOR</a>
         * as specified in <em>ISO/IEC 18013-5</em> section 8.3 <em>Device Retrieval</em>.
         *
         * <p>The application should use {@link DeviceRequestParser} to parse the request and
         * {@link DeviceResponseGenerator} to generate a response to be sent using
         * {@link #sendDeviceResponse(byte[])}. Alternatively, the application may also choose to
         * terminate the session using {@link #disconnect()}.
         *
         * @param deviceEngagementMethod the engagement method used by the device
         * @param deviceRequestBytes     the device request.
         */
        void onDeviceRequest(@DeviceEngagementMethod int deviceEngagementMethod,
                @NonNull byte[] deviceRequestBytes);

        /**
         * Called when the remote verifier device disconnects normally, that is
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
