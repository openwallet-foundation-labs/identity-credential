package com.android.identity;

import androidx.annotation.NonNull;

class NfcUtil {


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
}
