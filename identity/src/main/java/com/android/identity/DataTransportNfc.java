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
import android.nfc.tech.IsoDep;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

/**
 * NFC data transport
 */
class DataTransportNfc extends DataTransport implements NfcApduRouter.Listener {
    public static final int DEVICE_RETRIEVAL_METHOD_TYPE = 1;
    public static final int DEVICE_RETRIEVAL_METHOD_VERSION = 1;
    public static final int RETRIEVAL_OPTION_KEY_COMMAND_DATA_FIELD_MAX_LENGTH = 0;
    public static final int RETRIEVAL_OPTION_KEY_RESPONSE_DATA_FIELD_MAX_LENGTH = 1;
    private static final String TAG = "DataTransportNfc";
    private static final byte[] STATUS_WORD_OK = {(byte) 0x90, (byte) 0x00};
    private static final byte[] STATUS_WORD_WRONG_LENGTH = {(byte) 0x67, (byte) 0x00};
    private static final byte[] STATUS_WORD_FILE_NOT_FOUND = {(byte) 0x6a, (byte) 0x82};
    private final ConnectionMethodNfc mConnectionMethod;
    IsoDep mIsoDep;
    ArrayList<byte[]> mListenerRemainingChunks;
    int mListenerTotalChunks;
    int mListenerRemainingBytesAvailable;
    boolean mEndTransceiverThread;
    int mListenerLeReceived = -1;
    BlockingQueue<byte[]> mWriterQueue = new LinkedTransferQueue<>();
    boolean mListenerStillActive;
    ByteArrayOutputStream mIncomingMessage = new ByteArrayOutputStream();
    int numChunksReceived = 0;
    private NfcApduRouter mNfcApduRouter;

    public DataTransportNfc(@NonNull Context context,
                            @Role int role,
                            @NonNull ConnectionMethodNfc connectionMethod,
                            @NonNull DataTransportOptions options) {
        super(context, role, options);
        mConnectionMethod = connectionMethod;
    }

    @Override
    public void setEDeviceKeyBytes(@NonNull byte[] encodedEDeviceKeyBytes) {
        // Not used.
    }

    private void connectAsMdoc() {
        // From ISO 18013-5 8.3.3.1.2 Data retrieval using near field communication (NFC):
        //
        // NOTE 2: The minimum and maximum possible values for the command data field limit are
        // 'FF' and 'FF FF', i.e. the limit is between 255 and 65 535 bytes (inclusive). The
        // minimum and maximum possible values for the response data limit are '01 00' and
        // '01 00 00', i.e. the limit is between 256 and 65 536 bytes (inclusive).

        mListenerStillActive = true;
        setupListenerWritingThread();
    }

    void setupListenerWritingThread() {
        Thread transceiverThread = new Thread() {
            @Override
            public void run() {
                while (mListenerStillActive) {
                    //Logger.d(TAG, "Waiting for message to send");
                    byte[] messageToSend = null;
                    try {
                        messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        continue;
                    }
                    if (messageToSend == null) {
                        continue;
                    }
                    Logger.d(TAG, "Sending message " + Util.toHex(messageToSend));

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

                    Logger.d(TAG, "Have " + chunks.size() + " chunks..");

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
        //Logger.d(TAG, "buildApduResponse: " + SUtil.toHex(ret));
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
    
    private void listenerSendResponse(@NonNull byte[] apdu) {
        //Logger.d(TAG, "listenerSendResponse: APDU: " + SUtil.toHex(apdu));
        mNfcApduRouter.sendResponseApdu(apdu);
    }

    public void setNfcApduRouter(NfcApduRouter nfcApduRouter, Executor executor) {
        mNfcApduRouter = nfcApduRouter;
        mNfcApduRouter.addListener(this, executor);

        reportConnected();
    }

    @Override
    public void onApduReceived(@NonNull byte[] aid, @NonNull byte[] apdu) {
        byte[] ret = null;

        Logger.d(TAG, String.format(Locale.US, "onApduReceived aid=%s apdu=%s",
                Util.toHex(aid), Util.toHex(apdu)));

        switch (NfcUtil.nfcGetCommandType(apdu)) {
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

    @Override
    public void onDeactivated(@NonNull byte[] aid, int reason) {
        Logger.d(TAG, String.format(Locale.US, 
                "onDeactivated aid=%s reason=%d", Util.toHex(aid), reason));
        reportDisconnected();
    }

    void sendNextChunk(boolean isForGetResponse) {
        byte[] chunk = mListenerRemainingChunks.remove(0);
        mListenerRemainingBytesAvailable -= chunk.length;

        boolean isLastChunk = (mListenerRemainingChunks.size() == 0);

        Logger.d(TAG, String.format(Locale.US, "isForGetResponse=%b isLastChunk=%b chunk=%s",
                isForGetResponse, isLastChunk, Util.toHex(chunk)));

        if (isLastChunk) {
            /* If Le ≥ the number of available bytes, the mdoc shall include all
             * available bytes in the response and set the status words to ’90 00’.
             */
            mNfcApduRouter.sendResponseApdu(buildApduResponse(chunk, 0x90, 0x00));
        } else {
            if (mListenerRemainingBytesAvailable <= mListenerLeReceived + 255) {
                /* If Le < the number of available bytes ≤ Le + 255, the mdoc shall
                 * include as many bytes in the response as indicated by Le and shall
                 * set the status words to ’61 XX’, where XX is the number of available
                 * bytes remaining. The mdoc reader shall respond with a GET RESPONSE
                 * command where Le is set to XX.
                 */
                int numBytesRemaining = mListenerRemainingBytesAvailable - mListenerLeReceived;
                mNfcApduRouter.sendResponseApdu(
                        buildApduResponse(chunk, 0x61, numBytesRemaining & 0xff));
            } else {
                /* If the number of available bytes > Le + 255, the mdoc shall include
                 * as many bytes in the response as indicated by Le and shall set the
                 * status words to ’61 00’. The mdoc readershall respond with a GET
                 * RESPONSE command where Le is set to the maximum length of the
                 * response data field that is supported by both the mdoc and the mdoc
                 * reader.
                 */
                mNfcApduRouter.sendResponseApdu(buildApduResponse(chunk, 0x61, 0x00));
            }
        }

        reportMessageProgress(mListenerRemainingChunks.size(), mListenerTotalChunks);
    }


    private @NonNull
    byte[] handleEnvelope(@NonNull byte[] apdu) {
        Logger.d(TAG, "in handleEnvelope");

        if (apdu.length < 7) {
            return STATUS_WORD_WRONG_LENGTH;
        }

        boolean moreChunksComing = false;

        int cla = apdu[0] & 0xff;
        if (cla == 0x10) {
            moreChunksComing = true;
        } else if (cla != 0x00) {
            reportError(new Error(String.format("Unexpected value 0x%02x in CLA of APDU", cla)));
            return STATUS_WORD_FILE_NOT_FOUND;
        }
        byte[] data = apduGetData(apdu);
        if (data == null) {
            reportError(new Error("Malformed APDU"));
            return STATUS_WORD_FILE_NOT_FOUND;
        }
        if (data.length == 0) {
            reportError(new Error("Received ENVELOPE with no data"));
            return STATUS_WORD_FILE_NOT_FOUND;
        }
        int le = apduGetLe(apdu);

        try {
            mIncomingMessage.write(data);
            numChunksReceived += 1;
        } catch (IOException e) {
            reportError(e);
            return STATUS_WORD_FILE_NOT_FOUND;
        }

        if (moreChunksComing) {
            /* For all ENVELOPE commands in a chain except the last one, Le shall be absent, since
             * no data is expected in the response to these commands.
             */
            if (le != 0) {
                reportError(new Error("More chunks are coming but LE is not zero"));
                return STATUS_WORD_FILE_NOT_FOUND;
            }
            return STATUS_WORD_OK;
        }

        /* For the last ENVELOPE command in a chain, Le shall be set to the maximum length
         * of the response data field that is supported by both the mdoc and the mdoc reader.
         *
         *  We'll need this for later.
         */
        if (mListenerLeReceived != 0) {
            mListenerLeReceived = le;
            //Logger.d(TAG, "Received LE " + le);
        }

        byte[] encapsulatedMessage = mIncomingMessage.toByteArray();
        Logger.d(TAG, String.format("Received %d bytes in %d chunk(s)",
                encapsulatedMessage.length, numChunksReceived));
        mIncomingMessage.reset();
        numChunksReceived = 0;
        byte[] message = extractFromDo53(encapsulatedMessage);
        if (message == null) {
            reportError(new Error("Error extracting message from DO53 encoding"));
            return STATUS_WORD_FILE_NOT_FOUND;
        }

        Logger.d(TAG, String.format("reportMessage %d bytes", message.length));
        reportMessageReceived(message);
        // Defer response...
        return null;
    }

    private @NonNull
    byte[] handleResponse(@NonNull byte[] apdu) {
        Logger.d(TAG, "in handleResponse");

        if (mListenerRemainingChunks == null || mListenerRemainingChunks.size() == 0) {
            reportError(new Error("GET RESPONSE but we have no outstanding chunks"));
            return null;
        }

        sendNextChunk(true);

        return null;
    }


    int apduGetLe(@NonNull byte[] apdu) {
        int dataLength = apduGetDataLength(apdu);
        boolean haveExtendedLc = (apdu[4] == 0x00);
        int dataOffset = (haveExtendedLc ? 7 : 5);

        int leOffset = dataOffset + dataLength;
        int leNumBytes = apdu.length - dataOffset - dataLength;
        if (leNumBytes < 0) {
            Logger.w(TAG, "leNumBytes is negative");
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
                Logger.w(TAG, "Don't have extended LC but leNumBytes is 2");
            }
            if (apdu[leOffset] == 0x00 && apdu[leOffset + 1] == 0x00) {
                return 0x10000;
            }
            int le = (apdu[leOffset] & 0xff) * 0x100;
            le += apdu[leOffset + 1] & 0xff;
            return le;
        } else if (leNumBytes == 3) {
            if (haveExtendedLc) {
                Logger.w(TAG, "leNumBytes is 3 but we have extended LC");
            }
            if (apdu[leOffset] != 0x00) {
                Logger.w(TAG, "Expected 0x00 for first LE byte");
            }
            if (apdu[leOffset + 1] == 0x00 && apdu[leOffset + 2] == 0x00) {
                return 0x10000;
            }
            int le = (apdu[leOffset + 1] & 0xff) * 0x100;
            le += apdu[leOffset + 2] & 0xff;
            return le;
        }
        Logger.w(TAG, String.format("leNumBytes is %d bytes which is unsupported", leNumBytes));
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
            Logger.w(TAG, String.format("DO53 length %d, expected at least 2",
                    encapsulatedData.length));
            return null;
        }
        int tag = encapsulatedData[0] & 0xff;
        if (tag != 0x53) {
            Logger.w(TAG, String.format("DO53 first byte is 0x%02x, expected 0x53", tag));
            return null;
        }
        int length = encapsulatedData[1] & 0xff;
        if (length > 0x83) {
            Logger.w(TAG, String.format("DO53 first byte of length is 0x%02x", length));
            return null;
        }
        int offset = 2;
        if (length == 0x80) {
            Logger.w(TAG, "DO53 first byte of length is 0x80");
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
            Logger.w(TAG, String.format("Malformed BER-TLV encoding, %d %d %d",
                    encapsulatedData.length, offset, length));
            return null;
        }
        byte[] data = new byte[length];
        System.arraycopy(encapsulatedData, offset, data, 0, length);
        return data;
    }

    @Override
    public void connect() {
        if (mRole == ROLE_MDOC) {
            connectAsMdoc();
        } else {
            connectAsMdocReader();
        }
        reportConnectionMethodReady();
    }

    private void connectAsMdocReader() {
        if (mIsoDep == null) {
            reportError(new Error("NFC IsoDep not set"));
            return;
        }
        int maxTransceiveLength = mIsoDep.getMaxTransceiveLength();
        Logger.d(TAG, "maxTransceiveLength: " + maxTransceiveLength);
        Logger.d(TAG, "isExtendedLengthApduSupported: " + mIsoDep.isExtendedLengthApduSupported());

        Thread transceiverThread = new Thread() {
            @Override
            public void run() {
                try {
                    mIsoDep.connect();
                    mIsoDep.setTimeout(20 * 1000);  // 20 seconds

                    // We're up and running...
                    //
                    reportConnected();

                    byte[] selectCommand = buildApdu(0x00, 0xa4, 0x04, 0x0c,
                            new byte[]{(byte) 0xa0, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                                    (byte) 0x48, (byte) 0x04, (byte) 0x00}, 0);
                    Logger.d(TAG, "selectCommand: " + Util.toHex(selectCommand));
                    byte[] selectResponse = mIsoDep.transceive(selectCommand);
                    Logger.d(TAG, "selectResponse: " + Util.toHex(selectResponse));
                    if (!Arrays.equals(selectResponse, new byte[]{(byte) 0x90, (byte) 0x00})) {
                        reportError(new Error("Unexpected response to AID SELECT"));
                        return;
                    }

                    while (!mEndTransceiverThread && mIsoDep.isConnected()) {
                        //Logger.d(TAG, "Waiting for message to send");
                        byte[] messageToSend = null;
                        try {
                            messageToSend = mWriterQueue.poll(1000, TimeUnit.MILLISECONDS);
                            if (messageToSend == null) {
                                continue;
                            }
                        } catch (InterruptedException e) {
                            continue;
                        }
                        //Logger.d(TAG, "Sending message " + SUtil.toHex(messageToSend));

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

                            //Logger.d(TAG, String.format("chunk length 0x%04x : %s", size, SUtil
                            // .toHex(chunk)));

                            byte[] envelopeCommand = buildApdu(moreChunksComing ? 0x10 : 0x00,
                                    0xc3, 0x00, 0x00, chunk, le);

                            //Logger.d(TAG, "envCommand " + SUtil.toHex(envelopeCommand));

                            byte[] envelopeResponse = mIsoDep.transceive(envelopeCommand);
                            /*
                            Instant t0 = Instant.now();
                            byte[] envelopeResponse = mIsoDep.transceive(envelopeCommand);
                            Instant t1 = Instant.now();

                            double durationSec = (t1.toEpochMilli() - t0.toEpochMilli())/1000.0;
                            int bitsPerSec = (int) ((envelopeCommand.length + envelopeResponse
                            .length)
                                                                * 8 / durationSec);
                            Logger.d(TAG, String.format("transceive() took %.2f sec for %d + %d "
                                    + " bytes => %d bits/sec",
                                    durationSec,
                                    envelopeCommand.length,
                                    envelopeResponse.length,
                                    bitsPerSec));
                             */

                            if (Logger.isDebugEnabled()) {
                                Logger.d(TAG, "Received " + Util.toHex(envelopeResponse));
                            }

                            offset += size;

                            if (moreChunksComing) {
                                // Don't care about response.
                                Logger.d(TAG, "envResponse (more chunks coming) " + Util.toHex(
                                        envelopeResponse));
                            } else {
                                lastEnvelopeResponse = envelopeResponse;
                            }

                        } while (offset < data.length);

                        //Logger.d(TAG,"envResponse (have all chunks) " + SUtil.toHex
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

                                //Logger.d(TAG, "envCommand " + SUtil.toHex(envelopeCommand));

                                byte[] grResponse = mIsoDep.transceive(grCommand);
                                /*
                                Instant t0 = Instant.now();
                                byte[] grResponse = mIsoDep.transceive(grCommand);
                                Instant t1 = Instant.now();

                                double durationSec =
                                        (t1.toEpochMilli() - t0.toEpochMilli()) / 1000.0;
                                int bitsPerSec = (int) ((grCommand.length + grResponse.length)
                                        * 8 / durationSec);
                                Logger.d(TAG,
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

                reportDisconnected();

                Logger.d(TAG, "Ending transceiver thread");
                mIsoDep = null;
            }
        };
        transceiverThread.start();

    }

    @Override
    public void close() {
        Logger.d(TAG, "close called");
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

    @Override
    public @NonNull ConnectionMethod getConnectionMethod() {
        return mConnectionMethod;
    }
}
