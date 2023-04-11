package com.android.identity;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class NfcUtil {
    private static final String TAG = "NfcUtil";

    // Defined by NFC Forum
    public static final byte[] AID_FOR_TYPE_4_TAG_NDEF_APPLICATION = Util.fromHex("D2760000850101");

    // Defined by 18013-5 Section 8.3.3.1.2 Data retrieval using near field communication (NFC)
    public static final byte[] AID_FOR_MDL_DATA_TRANSFER = Util.fromHex("A0000002480400");

    static final int COMMAND_TYPE_OTHER = 0;
    static final int COMMAND_TYPE_SELECT_BY_AID = 1;
    static final int COMMAND_TYPE_SELECT_FILE = 2;
    static final int COMMAND_TYPE_READ_BINARY = 3;
    static final int COMMAND_TYPE_UPDATE_BINARY = 4;
    static final int COMMAND_TYPE_ENVELOPE = 5;
    static final int COMMAND_TYPE_RESPONSE = 6;
    static final int CAPABILITY_CONTAINER_FILE_ID = 0xe103;
    static final int NDEF_FILE_ID = 0xe104;
    static final byte[] STATUS_WORD_INSTRUCTION_NOT_SUPPORTED = {(byte) 0x6d, (byte) 0x00};
    static final byte[] STATUS_WORD_OK = {(byte) 0x90, (byte) 0x00};
    static final byte[] STATUS_WORD_FILE_NOT_FOUND = {(byte) 0x6a, (byte) 0x82};
    static final byte[] STATUS_WORD_END_OF_FILE_REACHED = {(byte) 0x62, (byte) 0x82};
    static final byte[] STATUS_WORD_WRONG_PARAMETERS = {(byte) 0x6b, (byte) 0x00};
    static final byte[] STATUS_WORD_WRONG_LENGTH = {(byte) 0x67, (byte) 0x00};

    static int nfcGetCommandType(@NonNull byte[] apdu) {
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

    static @NonNull byte[] createApduApplicationSelect(@NonNull byte[] aid) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x00);
        baos.write(0xa4);
        baos.write(0x04);
        baos.write(0x00);
        baos.write(0x07);
        try {
            baos.write(aid);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return baos.toByteArray();
    }

    static @NonNull byte[] createApduSelectFile(int fileId) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x00);
        baos.write(0xa4);
        baos.write(0x00);
        baos.write(0x0c);
        baos.write(0x02);
        baos.write(fileId / 0x100);
        baos.write(fileId & 0xff);
        return baos.toByteArray();
    }

    static @NonNull byte[] createApduReadBinary(int offset, int length) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x00);
        baos.write(0xb0);
        baos.write(offset / 0x100);
        baos.write(offset & 0xff);
        if (length == 0) {
            throw new IllegalArgumentException("Length cannot be zero");
        } else if (length < 0x100) {
            baos.write(length & 0xff);
        } else {
            baos.write(0x00);
            baos.write(length / 0x100);
            baos.write(length & 0xff);
        }
        return baos.toByteArray();
    }

    static @NonNull byte[] createApduUpdateBinary(int offset, byte[] data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x00);
        baos.write(0xd6);
        baos.write(offset / 0x100);
        baos.write(offset & 0xff);
        if (data.length >= 0x100) {
            throw new IllegalArgumentException("Data must be shorter than 0x100 bytes");
        }
        baos.write(data.length & 0xff);
        try {
            baos.write(data);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return baos.toByteArray();
    }

    static byte[] createNdefMessageServiceSelect(String serviceName) {
        // [TNEP] section 4.2.2 Service Select Record
        byte[] payload = (" " + serviceName).getBytes(UTF_8);
        payload[0] = (byte) (payload.length - 1);
        NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
                "Ts".getBytes(UTF_8),
                null,
                payload);
        return new NdefMessage(new NdefRecord[] {record}).toByteArray();
    }

    private static @NonNull
    byte[] calculateHandoverSelectPayload(@NonNull List<byte[]> alternativeCarrierRecords) {
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
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
        return baos.toByteArray();
    }


    private static @NonNull
    byte[] createNdefMessageHandoverSelectOrRequest(
            @NonNull List<ConnectionMethod> methods,
            @Nullable byte[] encodedDeviceEngagement,
            @Nullable byte[] encodedReaderEngagement) {
        boolean isHandoverSelect = false;
        if (encodedDeviceEngagement != null) {
            isHandoverSelect = true;
            if (encodedReaderEngagement != null) {
                throw new IllegalArgumentException("Cannot have readerEngagement in Handover Select");
            }
        }

        List<String> auxiliaryReferences = new ArrayList<>();
        if (isHandoverSelect) {
            auxiliaryReferences.add("mdoc");
        }

        List<NdefRecord> carrierConfigurationRecords = new ArrayList<>();
        List<byte[]> alternativeCarrierRecords = new ArrayList<>();

        // TODO: we actually need to do the reverse disambiguation to e.g. merge two
        //  disambiguated BLE ConnectionMethods...
        for (ConnectionMethod cm : methods) {
            Pair<NdefRecord, byte[]> records = cm.toNdefRecord(auxiliaryReferences, isHandoverSelect);
            if (records != null) {
                if (Logger.isDebugEnabled()) {
                    Logger.d(TAG, "ConnectionMethod " + cm + ": alternativeCarrierRecord: "
                            + Util.toHex(records.second) + " carrierConfigurationRecord: "
                            + Util.toHex(records.first.getPayload()));
                }
                alternativeCarrierRecords.add(records.second);
                carrierConfigurationRecords.add(records.first);
            } else {
                Logger.d(TAG, "Ignoring address " + cm + " which yielded no NDEF records");
            }
        }

        List<NdefRecord> records = new ArrayList<>();
        byte[] hsPayload = calculateHandoverSelectPayload(alternativeCarrierRecords);
        records.add(new NdefRecord(
                NdefRecord.TNF_WELL_KNOWN,
                (isHandoverSelect ? "Hs" : "Hr").getBytes(UTF_8),
                null,
                hsPayload));

        if (encodedDeviceEngagement != null) {
            records.add(new NdefRecord(
                    NdefRecord.TNF_EXTERNAL_TYPE,
                    "iso.org:18013:deviceengagement".getBytes(UTF_8),
                    "mdoc".getBytes(UTF_8),
                    encodedDeviceEngagement));
        }

        if (encodedReaderEngagement != null) {
            records.add(new NdefRecord(
                    NdefRecord.TNF_EXTERNAL_TYPE,
                    "iso.org:18013:readerengagement".getBytes(UTF_8),
                    "mdocreader".getBytes(UTF_8),
                    encodedReaderEngagement));
        }

        for (NdefRecord record : carrierConfigurationRecords) {
            records.add(record);
        }

        NdefMessage message = new NdefMessage(records.toArray(new NdefRecord[0]));
        return message.toByteArray();
    }

    static @NonNull
    byte[] createNdefMessageHandoverSelect(
            @NonNull List<ConnectionMethod> methods,
            @NonNull byte[] encodedDeviceEngagement) {
        return createNdefMessageHandoverSelectOrRequest(methods, encodedDeviceEngagement, null);
    }

    static @NonNull
    byte[] createNdefMessageHandoverRequest(
            @NonNull List<ConnectionMethod> methods,
            @Nullable byte[] encodedReaderEngagement) {
        return createNdefMessageHandoverSelectOrRequest(methods, null, encodedReaderEngagement);
    }

    // Returns null if parsing fails, otherwise returns a ParsedHandoverSelectMessage instance
    static @Nullable ParsedHandoverSelectMessage
    parseHandoverSelectMessage(@NonNull byte[] ndefMessage) {
        NdefMessage m = null;
        try {
            m = new NdefMessage(ndefMessage);
        } catch (FormatException e) {
            Logger.w(TAG, "Error parsing NDEF message", e);
            return null;
        }

        boolean validHandoverSelectMessage = false;
        ParsedHandoverSelectMessage ret = new ParsedHandoverSelectMessage();
        for (NdefRecord r : m.getRecords()) {
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
                ret.encodedDeviceEngagement = r.getPayload();
            }

            // This parses the various carrier specific NDEF records, see
            // DataTransport.parseNdefRecord() for details.
            //
            if ((r.getTnf() == NdefRecord.TNF_MIME_MEDIA) ||
                    (r.getTnf() == NdefRecord.TNF_EXTERNAL_TYPE)) {
                ConnectionMethod cm = ConnectionMethod.fromNdefRecord(r, true);
                if (cm != null) {
                    ret.connectionMethods.add(cm);
                }
            }
        }

        if (!validHandoverSelectMessage) {
            Logger.w(TAG, "Hs record not found");
            return null;
        }
        if (ret.encodedDeviceEngagement == null) {
            Logger.w(TAG, "DeviceEngagement record not found");
            return null;
        }
        return ret;
    }

    static class ParsedHandoverSelectMessage {
        @NonNull byte[] encodedDeviceEngagement = null;
        @NonNull List<ConnectionMethod> connectionMethods = new ArrayList<>();
    }

    static @Nullable
    NdefRecord findServiceParameterRecordWithName(@NonNull byte[] ndefMessage, @NonNull String serviceName) {
        NdefMessage m = null;
        try {
            m = new NdefMessage(ndefMessage);
        } catch (FormatException e) {
            throw new IllegalArgumentException("Error parsing NDEF message", e);
        }

        byte[] snUtf8 = serviceName.getBytes(UTF_8);
        for (NdefRecord r : m.getRecords()) {
            byte[] p = r.getPayload();
            if (r.getTnf() == NdefRecord.TNF_WELL_KNOWN
                    && Arrays.equals("Tp".getBytes(UTF_8), r.getType())
                    && p != null
                    && p.length > snUtf8.length + 2
                    && p[0] == 0x10
                    && p[1] == snUtf8.length
                    && Arrays.equals(snUtf8, Arrays.copyOfRange(p, 2, 2 + snUtf8.length))) {
                return r;
            }
        }
        return null;
    }


    static @NonNull
    ParsedServiceParameterRecord parseServiceParameterRecord(NdefRecord serviceParameterRecord) {
        if (serviceParameterRecord.getTnf() != NdefRecord.TNF_WELL_KNOWN) {
            throw new IllegalArgumentException("Record is not well known");
        }
        if (!Arrays.equals("Tp".getBytes(UTF_8), serviceParameterRecord.getType())) {
            throw new IllegalArgumentException("Expected type Tp");
        }

        // See [TNEP] 4.1.2 Service Parameter Record for the payload
        byte[] p = serviceParameterRecord.getPayload();
        if (p.length < 1) {
            throw new IllegalArgumentException("Unexpected length of Service Parameter Record");
        }
        int serviceNameLen = p[1];
        if (p.length != serviceNameLen + 7) {
            throw new IllegalArgumentException("Unexpected length of body in Service Parameter Record");
        }

        ParsedServiceParameterRecord ret = new ParsedServiceParameterRecord();
        ret.tnepVersion = p[0]  & 0xff;
        ret.serviceNameUri = new String(p, 2, serviceNameLen, US_ASCII);
        ret.tnepCommunicationMode = p[2 + serviceNameLen]  & 0xff;
        int wt_int = p[3 + serviceNameLen]  & 0xff;
        ret.tWaitMillis = Math.pow(2, wt_int/4 - 1);
        ret.nWait = p[4 + serviceNameLen] & 0xff;
        ret.maxNdefSize = (p[5 + serviceNameLen] & 0xff)*0x100 + (p[6 + serviceNameLen]  & 0xff);

        return ret;
    }

    static class ParsedServiceParameterRecord {
        int tnepVersion;
        @NonNull String serviceNameUri;
        int tnepCommunicationMode;
        double tWaitMillis;
        int nWait;
        int maxNdefSize;
    }

    static @Nullable
    NdefRecord findTnepStatusRecord(@NonNull byte[] ndefMessage) {
        NdefMessage m = null;
        try {
            m = new NdefMessage(ndefMessage);
        } catch (FormatException e) {
            throw new IllegalArgumentException("Error parsing NDEF message", e);
        }

        for (NdefRecord r : m.getRecords()) {
            byte[] p = r.getPayload();
            if (r.getTnf() == NdefRecord.TNF_WELL_KNOWN
                    && Arrays.equals("Te".getBytes(UTF_8), r.getType())) {
                return r;
            }
        }
        return null;
    }

}
