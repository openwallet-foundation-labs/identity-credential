package com.android.identity.android.util;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.android.mdoc.transport.ConnectionMethodTcp;
import com.android.identity.android.mdoc.transport.ConnectionMethodUdp;
import com.android.identity.android.mdoc.transport.DataTransportBle;
import com.android.identity.android.mdoc.transport.DataTransportNfc;
import com.android.identity.android.mdoc.transport.DataTransportOptions;
import com.android.identity.android.mdoc.transport.DataTransportTcp;
import com.android.identity.android.mdoc.transport.DataTransportUdp;
import com.android.identity.android.mdoc.transport.DataTransportWifiAware;
import com.android.identity.mdoc.connectionmethod.ConnectionMethod;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodHttp;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodNfc;
import com.android.identity.mdoc.connectionmethod.ConnectionMethodWifiAware;
import com.android.identity.util.Logger;
import com.android.identity.internal.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Number;

public class NfcUtil {
    private static final String TAG = "NfcUtil";

    // Defined by NFC Forum
    public static final byte[] AID_FOR_TYPE_4_TAG_NDEF_APPLICATION = Util.fromHex("D2760000850101");

    // Defined by 18013-5 Section 8.3.3.1.2 Data retrieval using near field communication (NFC)
    public static final byte[] AID_FOR_MDL_DATA_TRANSFER = Util.fromHex("A0000002480400");

    public static final int COMMAND_TYPE_OTHER = 0;
    public static final int COMMAND_TYPE_SELECT_BY_AID = 1;
    public static final int COMMAND_TYPE_SELECT_FILE = 2;
    public static final int COMMAND_TYPE_READ_BINARY = 3;
    public static final int COMMAND_TYPE_UPDATE_BINARY = 4;
    public static final int COMMAND_TYPE_ENVELOPE = 5;
    public static final int COMMAND_TYPE_RESPONSE = 6;
    public static final int CAPABILITY_CONTAINER_FILE_ID = 0xe103;
    public static final int NDEF_FILE_ID = 0xe104;
    public static final byte[] STATUS_WORD_INSTRUCTION_NOT_SUPPORTED = {(byte) 0x6d, (byte) 0x00};
    public static final byte[] STATUS_WORD_OK = {(byte) 0x90, (byte) 0x00};
    public static final byte[] STATUS_WORD_FILE_NOT_FOUND = {(byte) 0x6a, (byte) 0x82};
    public static final byte[] STATUS_WORD_END_OF_FILE_REACHED = {(byte) 0x62, (byte) 0x82};
    public static final byte[] STATUS_WORD_WRONG_PARAMETERS = {(byte) 0x6b, (byte) 0x00};
    public static final byte[] STATUS_WORD_WRONG_LENGTH = {(byte) 0x67, (byte) 0x00};

    public static int nfcGetCommandType(@NonNull byte[] apdu) {
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

    public static @NonNull byte[] createApduApplicationSelect(@NonNull byte[] aid) {
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

    public static @NonNull byte[] createApduSelectFile(int fileId) {
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

    public static @NonNull byte[] createApduReadBinary(int offset, int length) {
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

    public static @NonNull byte[] createApduUpdateBinary(int offset, byte[] data) {
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

    public static byte[] createNdefMessageServiceSelect(String serviceName) {
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
            @Nullable byte[] encodedReaderEngagement,
            @Nullable DataTransportOptions options) {
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
            Pair<NdefRecord, byte[]> records = toNdefRecord(cm, auxiliaryReferences, isHandoverSelect);
            if (records != null) {
                if (Logger.isDebugEnabled()) {
                    Logger.d(TAG, "ConnectionMethod " + cm + ": alternativeCarrierRecord: "
                            + Util.toHex(records.second) + " carrierConfigurationRecord: "
                            + Util.toHex(records.first.getPayload()));
                }
                alternativeCarrierRecords.add(records.second);
                carrierConfigurationRecords.add(records.first);
            } else {
                Logger.w(TAG, "Ignoring address " + cm + " which yielded no NDEF records");
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

    public static @NonNull
    byte[] createNdefMessageHandoverSelect(
            @NonNull List<ConnectionMethod> methods,
            @NonNull byte[] encodedDeviceEngagement,
            @Nullable DataTransportOptions options) {
        return createNdefMessageHandoverSelectOrRequest(methods, encodedDeviceEngagement, null, options);
    }

    public static @NonNull
    byte[] createNdefMessageHandoverRequest(
            @NonNull List<ConnectionMethod> methods,
            @Nullable byte[] encodedReaderEngagement,
            @Nullable DataTransportOptions options) {
        return createNdefMessageHandoverSelectOrRequest(methods, null, encodedReaderEngagement, options);
    }

    // Returns null if parsing fails, otherwise returns a ParsedHandoverSelectMessage instance
    public static @Nullable ParsedHandoverSelectMessage
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
                ConnectionMethod cm = fromNdefRecord(r, true);
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

    public static class ParsedHandoverSelectMessage {
        @NonNull
        public byte[] encodedDeviceEngagement = null;
        @NonNull
        public List<ConnectionMethod> connectionMethods = new ArrayList<>();
    }

    public static @Nullable
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


    public static @NonNull
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

    public static class ParsedServiceParameterRecord {
        public int tnepVersion;
        public @NonNull String serviceNameUri;
        public int tnepCommunicationMode;
        public double tWaitMillis;
        public int nWait;
        public int maxNdefSize;
    }

    public static @Nullable
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

    private static @Nullable
    ConnectionMethod getConnectionMethodFromDeviceEngagement(@NonNull byte[] encodedDeviceRetrievalMethod) {
        DataItem cmDataItem = Util.cborDecode(encodedDeviceRetrievalMethod);
        if (!(cmDataItem instanceof co.nstant.in.cbor.model.Array)) {
            throw new IllegalArgumentException("Top-level CBOR is not an array");
        }
        List<DataItem> items = ((Array) cmDataItem).getDataItems();
        if (items.size() != 3) {
            throw new IllegalArgumentException("Expected array with 3 elements, got " + items.size());
        }
        if (!(items.get(0) instanceof Number) || !(items.get(1) instanceof Number)) {
            throw new IllegalArgumentException("First two items are not numbers");
        }
        long type = ((Number) items.get(0)).getValue().longValue();
        if (!(items.get(2) instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalArgumentException("Third item is not a map");
        }
        switch ((int) type) {
            case ConnectionMethodTcp.METHOD_TYPE:
                return ConnectionMethodTcp.fromDeviceEngagementTcp(encodedDeviceRetrievalMethod);
            case ConnectionMethodUdp.METHOD_TYPE:
                return ConnectionMethodUdp.fromDeviceEngagementUdp(encodedDeviceRetrievalMethod);
        }
        Logger.w(TAG, String.format(Locale.US,
                "Unsupported ConnectionMethod type %d in DeviceEngagement", type));
        return null;
    }


    public static @Nullable
    ConnectionMethod fromNdefRecord(@NonNull NdefRecord record, boolean isForHandoverSelect) {
        // BLE Carrier Configuration record
        //
        if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA
                && Arrays.equals(record.getType(),
                "application/vnd.bluetooth.le.oob".getBytes(UTF_8))
                && Arrays.equals(record.getId(), "0".getBytes(UTF_8))) {
            return DataTransportBle.fromNdefRecord(record, isForHandoverSelect);
        }

        // Wifi Aware Carrier Configuration record
        //
        if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA
                && Arrays.equals(record.getType(),
                "application/vnd.wfa.nan".getBytes(UTF_8))
                && Arrays.equals(record.getId(), "W".getBytes(UTF_8))) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return DataTransportWifiAware.fromNdefRecord(record, isForHandoverSelect);
            } else {
                Logger.w(TAG, "Ignoring Wifi Aware Carrier Configuration since Wifi Aware "
                        + "is not available on this API level");
                return null;
            }
        }

        // NFC Carrier Configuration record
        //
        if (record.getTnf() == NdefRecord.TNF_EXTERNAL_TYPE
                && Arrays.equals(record.getType(),
                "iso.org:18013:nfc".getBytes(UTF_8))
                && Arrays.equals(record.getId(), "nfc".getBytes(UTF_8))) {
            return DataTransportNfc.fromNdefRecord(record, isForHandoverSelect);
        }

        // Generic Carrier Configuration record containing DeviceEngagement
        //
        if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA
                && Arrays.equals(record.getType(),
                "application/vnd.android.ic.dmr".getBytes(UTF_8))) {
            byte[] deviceRetrievalMethod = record.getPayload();
            return getConnectionMethodFromDeviceEngagement(deviceRetrievalMethod);
        }

        Logger.d(TAG, "Unknown NDEF record " + record);
        return null;
    }

    /**
     * Creates Carrier Reference and Auxiliary Data Reference records.
     *
     * <p>If this is to be included in a Handover Select method, pass <code>{"mdoc"}</code>
     * for <code>auxiliaryReferences</code>.
     *
     * @param auxiliaryReferences A list of references to include in the Alternative Carrier Record
     * @param isForHandoverSelect Set to <code>true</code> if this is for a Handover Select method,
     *                            and <code>false</code> if for Handover Request record.
     * @return <code>null</code> if the connection method doesn't support NFC handover, otherwise
     *         the NDEF record and the Alternative Carrier record.
     */
    public static @Nullable
    Pair<NdefRecord, byte[]> toNdefRecord(@NonNull ConnectionMethod connectionMethod,
                                          @NonNull List<String> auxiliaryReferences,
                                          boolean isForHandoverSelect) {
        if (connectionMethod instanceof ConnectionMethodBle) {
            return DataTransportBle.toNdefRecord((ConnectionMethodBle) connectionMethod,
                    auxiliaryReferences,
                    isForHandoverSelect);
        } else if (connectionMethod instanceof ConnectionMethodNfc) {
            return DataTransportNfc.toNdefRecord((ConnectionMethodNfc) connectionMethod,
                    auxiliaryReferences,
                    isForHandoverSelect);
        } else if (connectionMethod instanceof ConnectionMethodWifiAware) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return DataTransportWifiAware.toNdefRecord((ConnectionMethodWifiAware) connectionMethod,
                        auxiliaryReferences,
                        isForHandoverSelect);
            }
        } else if (connectionMethod instanceof ConnectionMethodTcp) {
            return DataTransportTcp.toNdefRecord((ConnectionMethodTcp) connectionMethod,
                    auxiliaryReferences,
                    isForHandoverSelect);
        } else if (connectionMethod instanceof ConnectionMethodUdp) {
            return DataTransportUdp.toNdefRecord((ConnectionMethodUdp) connectionMethod,
                    auxiliaryReferences,
                    isForHandoverSelect);
        }
        Logger.w(TAG, "toNdefRecord: Unsupported ConnectionMethod");
        return null;
    }
}
