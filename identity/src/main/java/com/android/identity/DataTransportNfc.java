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
import android.nfc.NdefRecord;
import android.nfc.tech.IsoDep;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.identity.Constants.LoggingFlag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;

/**
 * NFC data transport
 */
class DataTransportNfc extends DataTransport {
    public static final int DEVICE_RETRIEVAL_METHOD_TYPE = 1;
    public static final int DEVICE_RETRIEVAL_METHOD_VERSION = 1;
    public static final int RETRIEVAL_OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH = 0;
    public static final int RETRIEVAL_OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH = 1;
    private static final String TAG = "DataTransportNfc";
    private static final byte[] STATUS_WORD_OK = {(byte) 0x90, (byte) 0x00};
    private static final byte[] STATUS_WORD_WRONG_LENGTH = {(byte) 0x67, (byte) 0x00};
    private static final byte[] STATUS_WORD_FILE_NOT_FOUND = {(byte) 0x6a, (byte) 0x82};
    IsoDep mIsoDep;
    ArrayList<byte[]> mListenerRemainingChunks;
    int mListenerTotalChunks;
    int mListenerRemainingBytesAvailable;
    boolean mEndTransceiverThread;
    ResponseInterface mResponseInterface;
    int mListenerLeReceived = -1;
    BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    boolean mListenerStillActive;
    ByteArrayOutputStream mIncomingMessage = new ByteArrayOutputStream();
    int numChunksReceived = 0;
    private DataRetrievalAddress mListeningAddress;

    public DataTransportNfc(@NonNull Context context) {
        super(context);
    }

    static void encodeInt(int dataType, int value, ByteArrayOutputStream baos) {
        if (value < 0x100) {
            baos.write(0x02); // Length
            baos.write(dataType);
            baos.write(value & 0xff);
        } else if (value < 0x10000) {
            baos.write(0x03); // Length
            baos.write(dataType);
            baos.write(value / 0x100);
            baos.write(value & 0xff);
        } else {
            baos.write(0x04); // Length
            baos.write(dataType);
            baos.write(value / 0x10000);
            baos.write((value / 0x100) & 0xff);
            baos.write(value & 0xff);
        }
    }

    public static @Nullable
    List<DataRetrievalAddress> parseNdefRecord(@NonNull NdefRecord record) {
        ByteBuffer payload = ByteBuffer.wrap(record.getPayload()).order(ByteOrder.LITTLE_ENDIAN);
        int version = payload.get();
        if (version != 0x01) {
            Log.w(TAG, "Expected version 0x01, found " + version);
            return null;
        }

        int cmdLen = payload.get() & 0xff;
        int cmdType = payload.get() & 0xff;
        if (cmdType != 0x01) {
            Log.w(TAG, "expected type 0x01, found " + cmdType);
            return null;
        }
        if (cmdLen < 2 || cmdLen > 3) {
            Log.w(TAG, "expected cmdLen in range 2-3, got " + cmdLen);
            return null;
        }

        int commandDataFieldMaxLength = 0;
        for (int n = 0; n < cmdLen - 1; n++) {
            commandDataFieldMaxLength *= 256;
            commandDataFieldMaxLength += payload.get() & 0xff;
        }

        int rspLen = payload.get() & 0xff;
        int rspType = payload.get() & 0xff;
        if (rspType != 0x02) {
            Log.w(TAG, "expected type 0x02, found " + rspType);
            return null;
        }
        if (rspLen < 2 || rspLen > 4) {
            Log.w(TAG, "expected rspLen in range 2-4, got " + rspLen);
            return null;
        }

        int responseDataFieldMaxLength = 0;
        for (int n = 0; n < rspLen - 1; n++) {
            responseDataFieldMaxLength *= 256;
            responseDataFieldMaxLength += payload.get() & 0xff;
        }

        List<DataRetrievalAddress> addresses = new ArrayList<>();
        addresses.add(new DataRetrievalAddressNfc(commandDataFieldMaxLength,
                responseDataFieldMaxLength));
        return addresses;
    }

    static public @Nullable
    List<DataRetrievalAddress> parseDeviceRetrievalMethod(int version, @NonNull DataItem[] items) {
        if (version > DEVICE_RETRIEVAL_METHOD_VERSION) {
            Log.w(TAG, "Unexpected version " + version + " for retrieval method");
            return null;
        }
        if (items.length < 3 || !(items[2] instanceof Map)) {
            Log.w(TAG, "Item 3 in device retrieval array is not a map");
        }
        Map options = ((Map) items[2]);

        long commandDataFieldMaxLength = Util.cborMapExtractNumber(options,
                RETRIEVAL_OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH);
        long responseDataFieldMaxLength = Util.cborMapExtractNumber(options,
                RETRIEVAL_OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH);

        if (commandDataFieldMaxLength > Integer.MAX_VALUE
            || commandDataFieldMaxLength <= 0
            || responseDataFieldMaxLength  > Integer.MAX_VALUE
            || responseDataFieldMaxLength  <= 0 ) {
            Log.w(TAG, "Invalid max length. Command max: " + commandDataFieldMaxLength +
                    ", response max: " + responseDataFieldMaxLength);
            return null;
        }

        List<DataRetrievalAddress> addresses = new ArrayList<>();
        addresses.add(new DataRetrievalAddressNfc((int)commandDataFieldMaxLength,
                (int)responseDataFieldMaxLength));
        return addresses;
    }

    @Override
    public void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes) {
        // Not used.
    }

    @Override
    public @NonNull
    DataRetrievalAddress getListeningAddress() {
        return mListeningAddress;
    }

    @Override
    public void listen() {
        // From ISO 18013-5 8.3.3.1.2 Data retrieval using near field communication (NFC):
        //
        // NOTE 2: The minimum and maximum possible values for the command data field limit are
        // 'FF' and 'FF FF', i.e. the limit is between 255 and 65 535 bytes (inclusive). The
        // minimum and maximum possible values for the response data limit are '01 00' and
        // '01 00 00', i.e. the limit is between 256 and 65 536 bytes (inclusive).

        // TODO: get these from underlying hardware instead of assuming they're the top limit.
        mListeningAddress = new DataRetrievalAddressNfc(
            /* commandDataFieldMaxLength= */ 0xffff, /* responseDataFieldMaxLength= */ 0x10000);

        reportListeningSetupCompleted(mListeningAddress);

        mListenerStillActive = true;
        setupListenerWritingThread();
    }

    void setupListenerWritingThread() {
        Thread transceiverThread = new Thread() {
            @Override
            public void run() {
                while (mListenerStillActive) {
                    //Log.d(TAG, "Waiting for message to send");
                    byte[] messageToSend = null;
                    try {
                        messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        continue;
                    }
                    if (messageToSend == null) {
                        continue;
                    }
                    //Log.d(TAG, "Sending message " + SUtil.toHex(messageToSend));

                    if (mResponseInterface == null) {
                        reportError(new Error("ResponseInterface not set"));
                        return;
                    }
                    if (mListenerLeReceived == -1) {
                        reportError(new Error("ListenerLeReceived not set"));
                        return;
                    }

                    // First message we send will be a response to the reader's
                    // ENVELOPE command.. further messages will be in response
                    // the GET RESPONSE commands. So we chop up this data in chunks
                    // so it's easy to hand off responses...

                    ArrayList<byte[]> chunks = new ArrayList<>();
                    byte[] data = encapsulateInDo53(messageToSend);
                    int offset = 0;
                    int maxChunkSize = mListenerLeReceived;
                    do {
                        int size = data.length - offset;
                        if (size > maxChunkSize) {
                            size = maxChunkSize;
                        }
                        byte[] chunk = new byte[size];
                        System.arraycopy(data, offset, chunk, 0, size);
                        chunks.add(chunk);
                        offset += size;
                    } while (offset < data.length);

                    //Log.d(TAG, "Have " + chunks.size() + " chunks..");

                    mListenerRemainingChunks = chunks;
                    mListenerRemainingBytesAvailable = data.length;
                    mListenerTotalChunks = chunks.size();
                    sendNextChunk(false);
                }
            }
        };
        reportMessageProgress(0, mListenerTotalChunks);
        transceiverThread.start();
    }

    byte[] buildApduResponse(@NonNull byte[] data, int sw1, int sw2) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        baos.write(sw1);
        baos.write(sw2);
        byte[] ret = baos.toByteArray();
        //Log.d(TAG, "buildApduResponse: " + SUtil.toHex(ret));
        return ret;
    }

    /**
     * Called by reader when finding the {@link IsoDep} tag.
     *
     * @param isoDep the tag with {@link IsoDep} technology.
     */
    public void setIsoDep(@NonNull IsoDep isoDep) {
        mIsoDep = isoDep;
    }

    /**
     * Called by {@link PresentationHelper} implementation
     * when remote verifier device selects the data transfer AID.
     *
     * @param responseInterface A way for this implementation to post APDU responses.
     */
    public void onDataTransferAidSelected(@NonNull ResponseInterface responseInterface) {
        mResponseInterface = responseInterface;
        reportListeningPeerConnected();
    }

    private void listenerSendResponse(@NonNull byte[] apdu) {
        if (mResponseInterface == null) {
            Log.w(TAG, "Trying to send but ResponseInterface is null");
            return;
        }
        //Log.d(TAG, "listenerSendResponse: APDU: " + SUtil.toHex(apdu));
        mResponseInterface.sendResponseApdu(apdu);
    }

    /**
     * Called by {@link PresentationHelper} implementation
     * when receiving GET RESPONSE APDUs for data transfer.
     *
     * <p>Use the {@link ResponseInterface} passed to
     * {@link #onDataTransferAidSelected(ResponseInterface)} to post a response to this.
     *
     * @param apdu the APDU sent by the verifier device.
     */
    public void onGetResponseApduReceived(@NonNull byte[] apdu) {
        //Log.d(TAG, "onGetResponseApduReceived, APDU: " + SUtil.toHex(apdu));

        if (mListenerRemainingChunks == null || mListenerRemainingChunks.size() == 0) {
            reportError(new Error("GET RESPONSE but we have no outstanding chunks"));
            return;
        }

        sendNextChunk(true);
    }

    void sendNextChunk(boolean isForGetResponse) {
        byte[] chunk = mListenerRemainingChunks.remove(0);
        mListenerRemainingBytesAvailable -= chunk.length;

        boolean isLastChunk = (mListenerRemainingChunks.size() == 0);

        if (isLastChunk) {
            /* If Le ≥ the number of available bytes, the mdoc shall include all
             * available bytes in the response and set the status words to ’90 00’.
             */
            mResponseInterface.sendResponseApdu(buildApduResponse(chunk, 0x90, 0x00));
        } else {
            if (mListenerRemainingBytesAvailable <= mListenerLeReceived + 255) {
                /* If Le < the number of available bytes ≤ Le + 255, the mdoc shall
                 * include as many bytes in the response as indicated by Le and shall
                 * set the status words to ’61 XX’, where XX is the number of available
                 * bytes remaining. The mdoc reader shall respond with a GET RESPONSE
                 * command where Le is set to XX.
                 */
                int numBytesRemaining = mListenerRemainingBytesAvailable - mListenerLeReceived;
                mResponseInterface.sendResponseApdu(
                        buildApduResponse(chunk, 0x61, numBytesRemaining & 0xff));
            } else {
                /* If the number of available bytes > Le + 255, the mdoc shall include
                 * as many bytes in the response as indicated by Le and shall set the
                 * status words to ’61 00’. The mdoc readershall respond with a GET
                 * RESPONSE command where Le is set to the maximum length of the
                 * response data field that is supported by both the mdoc and the mdoc
                 * reader.
                 */
                mResponseInterface.sendResponseApdu(buildApduResponse(chunk, 0x61, 0x00));
            }
        }

        reportMessageProgress(mListenerRemainingChunks.size(), mListenerTotalChunks);

    }

    /**
     * Called by {@link PresentationHelper} implementation
     * when receiving ENVELOPE APDUs for data transfer.
     *
     * <p>Use the {@link ResponseInterface} passed to
     * {@link #onDataTransferAidSelected(ResponseInterface)} to post a response to this.
     *
     * @param apdu the APDU sent by the verifier device.
     */
    public void onEnvelopeApduReceived(@NonNull byte[] apdu) {
        //Log.d(TAG, "onEnvelopeApduReceived, APDU: " + SUtil.toHex(apdu));

        if (apdu.length < 7) {
            listenerSendResponse(STATUS_WORD_WRONG_LENGTH);
            return;
        }

        boolean moreChunksComing = false;

        int cla = apdu[0] & 0xff;
        if (cla == 0x10) {
            moreChunksComing = true;
        } else if (cla != 0x00) {
            reportError(new Error(String.format("Unexpected value 0x%02x in CLA of APDU", cla)));
            listenerSendResponse(STATUS_WORD_FILE_NOT_FOUND);
            return;
        }
        byte[] data = apduGetData(apdu);
        if (data == null) {
            reportError(new Error("Malformed APDU"));
            listenerSendResponse(STATUS_WORD_FILE_NOT_FOUND);
            return;
        }
        if (data.length == 0) {
            reportError(new Error("Received ENVELOPE with no data"));
            listenerSendResponse(STATUS_WORD_FILE_NOT_FOUND);
            return;
        }
        int le = apduGetLe(apdu);

        try {
            mIncomingMessage.write(data);
            numChunksReceived += 1;
        } catch (IOException e) {
            reportError(e);
            listenerSendResponse(STATUS_WORD_FILE_NOT_FOUND);
            return;
        }

        if (moreChunksComing) {
            /* For all ENVELOPE commands in a chain except the last one, Le shall be absent, since
             * no data is expected in the response to these commands.
             */
            if (le != 0) {
                reportError(new Error("More chunks are coming but LE is not zero"));
                listenerSendResponse(STATUS_WORD_FILE_NOT_FOUND);
                return;
            }
            listenerSendResponse(STATUS_WORD_OK);
            return;
        }

        /* For the last ENVELOPE command in a chain, Le shall be set to the maximum length
         * of the response data field that is supported by both the mdoc and the mdoc reader.
         *
         *  We'll need this for later.
         */
        if (mListenerLeReceived != 0) {
            mListenerLeReceived = le;
            //Log.d(TAG, "Received LE " + le);
        }

        byte[] encapsulatedMessage = mIncomingMessage.toByteArray();
        Log.d(TAG, String.format("Received %d bytes in %d chunk(s)",
                encapsulatedMessage.length, numChunksReceived));
        mIncomingMessage.reset();
        numChunksReceived = 0;
        byte[] message = extractFromDo53(encapsulatedMessage);
        if (message == null) {
            reportError(new Error("Error extracting message from DO53 encoding"));
            listenerSendResponse(STATUS_WORD_FILE_NOT_FOUND);
            return;
        }

        Log.d(TAG, String.format("reportMessage %d bytes", message.length));
        reportMessageReceived(message);
    }

    int apduGetLe(@NonNull byte[] apdu) {
        int dataLength = apduGetDataLength(apdu);
        boolean haveExtendedLc = (apdu[4] == 0x00);
        int dataOffset = (haveExtendedLc ? 7 : 5);

        int leOffset = dataOffset + dataLength;
        int leNumBytes = apdu.length - dataOffset - dataLength;
        if (leNumBytes < 0) {
            Log.w(TAG, "leNumBytes is negative");
            return 0;
        }
        if (leNumBytes == 0) {
            return 0;
        } else if (leNumBytes == 1) {
            if (apdu[leOffset] == 0x00) {
                return 0x100;
            }
            return apdu[leOffset] & 0xff;
        } else if (leNumBytes == 2) {
            if (!haveExtendedLc) {
                Log.w(TAG, "Don't have extended LC but leNumBytes is 2");
            }
            if (apdu[leOffset] == 0x00 && apdu[leOffset + 1] == 0x00) {
                return 0x10000;
            }
            int le = (apdu[leOffset] & 0xff) * 0x100;
            le += apdu[leOffset + 1] & 0xff;
            return le;
        } else if (leNumBytes == 3) {
            if (haveExtendedLc) {
                Log.w(TAG, "leNumBytes is 3 but we have extended LC");
            }
            if (apdu[leOffset] != 0x00) {
                Log.w(TAG, "Expected 0x00 for first LE byte");
            }
            if (apdu[leOffset + 1] == 0x00 && apdu[leOffset + 2] == 0x00) {
                return 0x10000;
            }
            int le = (apdu[leOffset + 1] & 0xff) * 0x100;
            le += apdu[leOffset + 2] & 0xff;
            return le;
        }
        Log.w(TAG, String.format("leNumBytes is %d bytes which is unsupported", leNumBytes));
        return 0;
    }

    int apduGetDataLength(@NonNull byte[] apdu) {
        int length = apdu[4] & 0xff;
        if (length == 0x00) {
            length = (apdu[5] & 0xff) * 256;
            length += apdu[6] & 0xff;
        }
        return length;
    }

    byte[] apduGetData(@NonNull byte[] apdu) {
        int length = apduGetDataLength(apdu);
        int offset = ((apdu[4] == 0x00) ? 7 : 5);
        if (apdu.length < offset + length) {
            return null;
        }
        byte[] data = new byte[length];
        System.arraycopy(apdu, offset, data, 0, length);
        return data;
    }

    byte[] buildApdu(int cla, int ins, int p1, int p2, @Nullable byte[] data, int le) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(cla);
        baos.write(ins);
        baos.write(p1);
        baos.write(p2);
        boolean hasExtendedLc = false;
        if (data == null) {
            baos.write(0);
        } else if (data.length < 256) {
            baos.write(data.length);
        } else {
            hasExtendedLc = true;
            baos.write(0x00);
            baos.write(data.length / 0x100);
            baos.write(data.length & 0xff);
        }
        if (data != null && data.length > 0) {
            try {
                baos.write(data);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        if (le > 0) {
            if (le == 256) {
                baos.write(0x00);
            } else if (le < 256) {
                baos.write(le);
            } else {
                if (!hasExtendedLc) {
                    baos.write(0x00);
                }
                if (le == 65536) {
                    baos.write(0x00);
                    baos.write(0x00);
                } else {
                    baos.write(le / 0x100);
                    baos.write(le & 0xff);
                }
            }
        }

        return baos.toByteArray();
    }

    byte[] encapsulateInDo53(byte[] data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x53);
        if (data.length < 0x80) {
            baos.write(data.length);
        } else if (data.length < 0x100) {
            baos.write(0x81);
            baos.write(data.length);
        } else if (data.length < 0x10000) {
            baos.write(0x82);
            baos.write(data.length / 0x100);
            baos.write(data.length & 0xff);
        } else if (data.length < 0x1000000) {
            baos.write(0x83);
            baos.write(data.length / 0x10000);
            baos.write((data.length / 0x100) & 0xff);
            baos.write(data.length & 0xff);
        } else {
            throw new IllegalStateException("Data length cannot be bigger than 0x1000000");
        }
        try {
            baos.write(data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return baos.toByteArray();
    }

    byte[] extractFromDo53(byte[] encapsulatedData) {
        if (encapsulatedData.length < 2) {
            Log.w(TAG, String.format("DO53 length %d, expected at least 2",
                    encapsulatedData.length));
            return null;
        }
        int tag = encapsulatedData[0] & 0xff;
        if (tag != 0x53) {
            Log.w(TAG, String.format("DO53 first byte is 0x%02x, expected 0x53", tag));
            return null;
        }
        int length = encapsulatedData[1] & 0xff;
        if (length > 0x83) {
            Log.w(TAG, String.format("DO53 first byte of length is 0x%02x", length));
            return null;
        }
        int offset = 2;
        if (length == 0x80) {
            Log.w(TAG, "DO53 first byte of length is 0x80");
            return null;
        } else if (length == 0x81) {
            length = encapsulatedData[2] & 0xff;
            offset = 3;
        } else if (length == 0x82) {
            length = (encapsulatedData[2] & 0xff) * 0x100;
            length += (encapsulatedData[3] & 0xff);
            offset = 4;
        } else if (length == 0x83) {
            length = (encapsulatedData[2] & 0xff) * 0x10000;
            length += (encapsulatedData[3] & 0xff) * 0x100;
            length += (encapsulatedData[4] & 0xff);
            offset = 5;
        }
        if (encapsulatedData.length != offset + length) {
            Log.w(TAG, String.format("Malformed BER-TLV encoding, %d %d %d",
                    encapsulatedData.length, offset, length));
            return null;
        }
        byte[] data = new byte[length];
        System.arraycopy(encapsulatedData, offset, data, 0, length);
        return data;
    }

    @Override
    public void connect(@NonNull DataRetrievalAddress genericAddress) {
        // TODO: genericAddress is instanceof DataRetrievalAddressNfc, but right now we're
        // not using its fields commandDataFieldMaxLength and responseDataFieldMaxLength.

        if (mIsoDep == null) {
            reportConnectionResult(new Error("NFC IsoDep not set"));
            return;
        }
        int maxTransceiveLength = mIsoDep.getMaxTransceiveLength();
        Log.d(TAG, "maxTransceiveLength: " + maxTransceiveLength);
        Log.d(TAG, "isExtendedLengthApduSupported: " + mIsoDep.isExtendedLengthApduSupported());

        Thread transceiverThread = new Thread() {
            @Override
            public void run() {
                try {
                    mIsoDep.connect();
                    mIsoDep.setTimeout(20 * 1000);  // 20 seconds

                    // We're up and running...
                    //
                    reportConnectionResult(null);

                    byte[] selectCommand = buildApdu(0x00, 0xa4, 0x04, 0x0c,
                            new byte[]{(byte) 0xa0, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                                    (byte) 0x48, (byte) 0x04, (byte) 0x00}, 0);
                    Log.d(TAG, "selectCommand: " + Util.toHex(selectCommand));
                    byte[] selectResponse = mIsoDep.transceive(selectCommand);
                    Log.d(TAG, "selectResponse: " + Util.toHex(selectResponse));
                    if (!Arrays.equals(selectResponse, new byte[]{(byte) 0x90, (byte) 0x00})) {
                        reportError(new Error("Unexpected response to AID SELECT"));
                        return;
                    }

                    while (!mEndTransceiverThread && mIsoDep.isConnected()) {
                        //Log.d(TAG, "Waiting for message to send");
                        byte[] messageToSend = null;
                        try {
                            messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                            if (messageToSend == null) {
                                continue;
                            }
                        } catch (InterruptedException e) {
                            continue;
                        }
                        //Log.d(TAG, "Sending message " + SUtil.toHex(messageToSend));

                        byte[] data = encapsulateInDo53(messageToSend);

                        // Less 7 for the APDU header and 3 for LE
                        //
                        int maxChunkSize = maxTransceiveLength - 10;
                        int offset = 0;
                        byte[] lastEnvelopeResponse = null;
                        do {
                            boolean moreChunksComing = (offset + maxChunkSize < data.length);
                            int size = data.length - offset;
                            if (size > maxChunkSize) {
                                size = maxChunkSize;
                            }

                            byte[] chunk = new byte[size];
                            System.arraycopy(data, offset, chunk, 0, size);

                            int le = 0;
                            if (!moreChunksComing) {
                                le = maxTransceiveLength;
                            }

                            //Log.d(TAG, String.format("chunk length 0x%04x : %s", size, SUtil
                            // .toHex(chunk)));

                            byte[] envelopeCommand = buildApdu(moreChunksComing ? 0x10 : 0x00,
                                    0xc3, 0x00, 0x00, chunk, le);

                            //Log.d(TAG, "envCommand " + SUtil.toHex(envelopeCommand));

                            byte[] envelopeResponse = mIsoDep.transceive(envelopeCommand);
                            /*
                            Instant t0 = Instant.now();
                            byte[] envelopeResponse = mIsoDep.transceive(envelopeCommand);
                            Instant t1 = Instant.now();

                            double durationSec = (t1.toEpochMilli() - t0.toEpochMilli())/1000.0;
                            int bitsPerSec = (int) ((envelopeCommand.length + envelopeResponse
                            .length)
                                                                * 8 / durationSec);
                            Log.d(TAG, String.format("transceive() took %.2f sec for %d + %d "
                                    + " bytes => %d bits/sec",
                                    durationSec,
                                    envelopeCommand.length,
                                    envelopeResponse.length,
                                    bitsPerSec));
                             */

                            offset += size;

                            if (moreChunksComing) {
                                // Don't care about response.
                                Log.d(TAG, "envResponse (more chunks coming) " + Util.toHex(
                                        envelopeResponse));
                            } else {
                                lastEnvelopeResponse = envelopeResponse;
                            }

                        } while (offset < data.length);

                        //Log.d(TAG,"envResponse (have all chunks) " + SUtil.toHex
                        // (lastEnvelopeResponse));
                        int erl = lastEnvelopeResponse.length;
                        if (erl < 2) {
                            reportError(new Error("APDU response smaller than expected"));
                            return;
                        }

                        byte[] encapsulatedMessage;
                        int status = (lastEnvelopeResponse[erl - 2] & 0xff) * 0x100
                                + (lastEnvelopeResponse[erl - 1] & 0xff);
                        if (status == 0x9000) {
                            // Woot, entire response fit in the response APDU
                            //
                            encapsulatedMessage = new byte[erl - 2];
                            System.arraycopy(lastEnvelopeResponse, 0, encapsulatedMessage, 0,
                                    erl - 2);
                        } else if ((status & 0xff00) == 0x6100) {
                            // More bytes are coming, have to use GET RESPONSE
                            //
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            baos.write(lastEnvelopeResponse, 0, erl - 2);

                            int leForGetResponse = maxTransceiveLength - 10;
                            if ((status & 0xff) != 0) {
                                leForGetResponse = status & 0xff;
                            }
                            while (true) {
                                byte[] grCommand = buildApdu(0x00,
                                        0xc0, 0x00, 0x00, null, leForGetResponse);

                                //Log.d(TAG, "envCommand " + SUtil.toHex(envelopeCommand));

                                byte[] grResponse = mIsoDep.transceive(grCommand);
                                /*
                                Instant t0 = Instant.now();
                                byte[] grResponse = mIsoDep.transceive(grCommand);
                                Instant t1 = Instant.now();

                                double durationSec =
                                        (t1.toEpochMilli() - t0.toEpochMilli()) / 1000.0;
                                int bitsPerSec = (int) ((grCommand.length + grResponse.length)
                                        * 8 / durationSec);
                                Log.d(TAG,
                                        String.format("gr transceive() took %.2f sec for %d + %d "
                                                        + " bytes => %d bits/sec",
                                                durationSec,
                                                grCommand.length,
                                                grResponse.length,
                                                bitsPerSec));
                                 */

                                int grrl = grResponse.length;
                                if (grrl < 2) {
                                    reportError(new Error("GetResponse APDU response smaller than "
                                            + "expected"));
                                    return;
                                }

                                int grrStatus = (grResponse[grrl - 2] & 0xff) * 0x100
                                        + (grResponse[grrl - 1] & 0xff);

                                baos.write(grResponse, 0, grrl - 2);

                                // TODO: add runaway check

                                if (grrStatus == 0x9000) {
                                    /* If Le ≥ the number of available bytes, the mdoc shall
                                    include all
                                     * available bytes in the response and set the status words
                                     to ’90 00’.
                                     */
                                    break;
                                } else if (grrStatus == 0x6100) {
                                    /* If the number of available bytes > Le + 255, the mdoc
                                    shall include
                                     * as many bytes in the response as indicated by Le and shall
                                      set the
                                     * status words to ’61 00’. The mdoc readershall respond with
                                      a GET
                                     * RESPONSE command where Le is set to the maximum length of the
                                     * response data field that is supported by both the mdoc and
                                      the mdoc
                                     * reader.
                                     */
                                    leForGetResponse = maxTransceiveLength - 10;
                                } else if ((grrStatus & 0xff00) == 0x6100) {
                                    /* If Le < the number of available bytes ≤ Le + 255, the mdoc
                                     shall
                                     * include as many bytes in the response as indicated by Le
                                     and shall
                                     * set the status words to ’61 XX’, where XX is the number of
                                      available
                                     * bytes remaining. The mdoc reader shall respond with a GET
                                     RESPONSE
                                     * command where Le is set to XX.
                                     */
                                    leForGetResponse = grrStatus & 0xff;
                                } else {
                                    reportError(new Error(
                                            String.format("Expecteded GetResponse APDU status "
                                                            + "0x%04x",
                                                    status)));
                                }
                            }
                            encapsulatedMessage = baos.toByteArray();
                        } else {
                            reportError(new Error(
                                    String.format("Expecteded APDU status 0x%04x", status)));
                            return;
                        }

                        byte[] message = extractFromDo53(encapsulatedMessage);
                        if (message == null) {
                            reportError(new Error("Error extracting message from DO53 encoding"));
                            return;
                        }

                        reportMessageReceived(message);

                    }


                    // TODO: report disconnect

                } catch (IOException e) {
                    reportError(e);
                }

                reportConnectionDisconnected();

                Log.d(TAG, "Ending transceiver thread");
                mIsoDep = null;
            }
        };
        transceiverThread.start();

    }

    @Override
    public void close() {
        Log.d(TAG, "close called");
        inhibitCallbacks();
        mEndTransceiverThread = true;
        mListenerStillActive = false;
    }

    @Override
    public void sendMessage(@NonNull byte[] data) {
        mWriterQueue.add(data);
    }

    @Override
    public void sendTransportSpecificTerminationMessage() {
        reportError(new Error("Transport-specific termination message not supported"));
    }

    @Override
    public boolean supportsTransportSpecificTerminationMessage() {
        return false;
    }

    public interface ResponseInterface {
        void sendResponseApdu(@NonNull byte[] responseApdu);
    }

    static class DataRetrievalAddressNfc extends DataRetrievalAddress {
        int commandDataFieldMaxLength;
        int responseDataFieldMaxLength;
        DataRetrievalAddressNfc(int commandDataFieldMaxLength, int responseDataFieldMaxLength) {
            this.commandDataFieldMaxLength = commandDataFieldMaxLength;
            this.responseDataFieldMaxLength = responseDataFieldMaxLength;
        }

        @Override
        @NonNull
        DataTransport createDataTransport(
                @NonNull Context context, @LoggingFlag int loggingFlags) {
            return new DataTransportNfc(context /*, loggingFlags*/);
        }

        @Override
        Pair<NdefRecord, byte[]> createNdefRecords(List<DataRetrievalAddress> listeningAddresses) {
            byte[] carrierDataReference = "nfc".getBytes(UTF_8);

            // This is defined by ISO 18013-5 8.2.2.2 Alternative Carrier Record for device
            // retrieval using NFC.
            //
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(0x01);  // Version
            encodeInt(0x01, commandDataFieldMaxLength, baos);
            encodeInt(0x02, responseDataFieldMaxLength, baos);
            byte[] oobData = baos.toByteArray();

            NdefRecord record = new NdefRecord((short) 0x02, // type = RFC 2046 (MIME)
                    "iso.org:18013:nfc".getBytes(UTF_8),
                    carrierDataReference,
                    oobData);

            // From 7.1 Alternative Carrier Record
            //
            baos = new ByteArrayOutputStream();
            baos.write(0x01); // CPS: active
            baos.write(carrierDataReference.length); // Length of carrier data reference
            try {
                baos.write(carrierDataReference);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            baos.write(0x01); // Number of auxiliary references
            byte[] auxReference = "mdoc".getBytes(UTF_8);
            baos.write(auxReference.length);  // Length of auxiliary reference 0 data
            baos.write(auxReference, 0, auxReference.length);
            byte[] acRecordPayload = baos.toByteArray();

            return new Pair<>(record, acRecordPayload);
        }

        @Override
        void addDeviceRetrievalMethodsEntry(ArrayBuilder<CborBuilder> arrayBuilder,
                List<DataRetrievalAddress> listeningAddresses) {
            arrayBuilder.addArray()
                    .add(DEVICE_RETRIEVAL_METHOD_TYPE)
                    .add(DEVICE_RETRIEVAL_METHOD_VERSION)
                    .addMap()
                    .put(RETRIEVAL_OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH,
                            commandDataFieldMaxLength)
                    .put(RETRIEVAL_OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH,
                            responseDataFieldMaxLength)
                    .end()
                    .end();
        }

        @Override
        public @NonNull
        String toString() {
            return "nfc:cmd_max_length=" + commandDataFieldMaxLength
                    + ":resp_max_length=" + responseDataFieldMaxLength;
        }
    }
}
