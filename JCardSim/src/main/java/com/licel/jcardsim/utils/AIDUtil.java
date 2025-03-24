/*
 * Copyright 2014 Robert Bachmann
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
package com.licel.jcardsim.utils;

import javacard.framework.AID;
import javacard.framework.ISO7816;
import org.bouncycastle.util.encoders.Hex;

import java.util.Comparator;

/**
 * Utility methods for dealing with AIDs.
 */
public final class AIDUtil {
    private static final Comparator<AID> aidComparator = new Comparator<AID>() {
        public int compare(AID aid1, AID aid2) {
            String s1 = (aid1 != null) ? AIDUtil.toString(aid1) : "";
            String s2 = (aid1 != null) ? AIDUtil.toString(aid2) : "";
            return s1.compareTo(s2);
        }
    };

    /**
     * Generate a SELECT APDU for <code>aid</code>
     * @param aid AID to be selected
     * @return SELECT APDU (CLA=0x00, INS=0xA4, P1=0x04, P2=0x00, Lc, AID, Le=0x00)
     * @throws java.lang.NullPointerException if <code>aid</code> is null
     */
    public static byte[] select(AID aid) {
        if (aid == null) {
            throw new NullPointerException("aid");
        }

        byte[] aidBuffer = new byte[16];
        byte length = aid.getBytes(aidBuffer, (short) 0);

        byte[] selectCmd = new byte[length + ISO7816.OFFSET_CDATA + 1];
        selectCmd[ISO7816.OFFSET_CLA] = ISO7816.CLA_ISO7816;
        selectCmd[ISO7816.OFFSET_INS] = ISO7816.INS_SELECT;
        selectCmd[ISO7816.OFFSET_P1] = 0x04;
        selectCmd[ISO7816.OFFSET_P2] = 0x00;
        selectCmd[ISO7816.OFFSET_LC] = length;
        System.arraycopy(aidBuffer, 0, selectCmd, ISO7816.OFFSET_CDATA, length);
        selectCmd[selectCmd.length - 1] = 0;

        return selectCmd;
    }

    /**
     * Generate a SELECT APDU for <code>aid</code>
     * @param aid AID to be selected
     * @return SELECT APDU (CLA=0x00, INS=0xA4, P1=0x04, P2=0x00, Lc, AID, Le=0x00)
     * @throws java.lang.IllegalArgumentException if <code>aid</code> is invalid
     * @throws java.lang.NullPointerException if <code>aid</code> is null
     */
    public static byte[] select(String aid) {
        if (aid == null) {
            throw new NullPointerException("aid");
        }

        byte[] aidBuffer = ByteUtil.byteArray(aid);
        if (aidBuffer.length > 16) {
            throw new IllegalArgumentException("AID must not be larger than 16 bytes: " + ByteUtil.hexString(aidBuffer));
        }

        byte[] selectCmd = new byte[aidBuffer.length + ISO7816.OFFSET_CDATA + 1];
        selectCmd[ISO7816.OFFSET_CLA] = ISO7816.CLA_ISO7816;
        selectCmd[ISO7816.OFFSET_INS] = ISO7816.INS_SELECT;
        selectCmd[ISO7816.OFFSET_P1] = 0x04;
        selectCmd[ISO7816.OFFSET_P2] = 0x00;
        selectCmd[ISO7816.OFFSET_LC] = (byte) aidBuffer.length;
        System.arraycopy(aidBuffer, 0, selectCmd, ISO7816.OFFSET_CDATA, aidBuffer.length);
        selectCmd[selectCmd.length - 1] = 0;

        return selectCmd;
    }

    /**
     * Create an AID from a byte array
     * @param aidBytes AID bytes
     * @return aid
     * @throws java.lang.NullPointerException if <code>aidBytes</code> is null
     * @throws java.lang.IllegalArgumentException if <code>aidBytes.length</code> is incorrect
     */
    public static AID create(byte[] aidBytes) {
        if (aidBytes == null) {
            throw new NullPointerException("aidString");
        }
        if (aidBytes.length < 5 || aidBytes.length > 16) {
            throw new IllegalArgumentException("AID size must be between 5 and 16 but was " +  aidBytes.length);
        }
        return new AID(aidBytes, (short) 0, (byte) aidBytes.length);
    }

    /**
     * Create an AID from a byte array
     * @param aidString AID bytes as hex string
     * @return aid
     * @throws java.lang.NullPointerException if <code>aidString</code> is null
     * @throws java.lang.IllegalArgumentException if length is incorrect
     */
    public static AID create(String aidString) {
        if (aidString == null) {
            throw new NullPointerException("aidString");
        }
        return create(Hex.decode(aidString));
    }

    /**
     * Convert AID to hex-string
     * @param aid AID to convert
     * @return hex string
     * @throws java.lang.NullPointerException if <code>aid</code> is null
     */
    public static String toString(AID aid) {
        if (aid == null) {
            throw new NullPointerException("aid");
        }
        byte[] buffer = new byte[16];
        short len = aid.getBytes(buffer, (short) 0);
        return ByteUtil.hexString(buffer, 0, len);
    }

    /**
     * @return a Comparator for AIDs
     */
    public static Comparator<AID> comparator() {
        return aidComparator;
    }

    private AIDUtil() {}
}
