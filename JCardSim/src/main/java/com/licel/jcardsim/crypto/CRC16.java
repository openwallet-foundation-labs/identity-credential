/*
 * Copyright 2011 Licel LLC.
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
package com.licel.jcardsim.crypto;

import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.Checksum;
import javacard.security.CryptoException;

/*
 * Implementation <code>Checksum</code>
 * ISO/IEC 3309 compliant 16 bit CRC algorithm
 * on BouncyCastle CryptoAPI.
 * @see Checksum
 */
public class CRC16 extends Checksum {

    static final byte LENGTH = 2;
    private byte crc16[];

    public CRC16() {
        crc16 = JCSystem.makeTransientByteArray(LENGTH, JCSystem.CLEAR_ON_DESELECT);
    }

    public byte getAlgorithm() {
        return ALG_ISO3309_CRC16;
    }

    public void init(byte bArray[], short bOff, short bLen)
            throws CryptoException {
        if (bLen != LENGTH) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        Util.arrayCopyNonAtomic(bArray, bOff, crc16, (short) 0, bLen);
    }

    public short doFinal(byte inBuff[], short inOffset, short inLength, byte outBuff[], short outOffset) {
        update(inBuff, inOffset, inLength);
        short temp = Util.getShort(crc16, (short) 0);
        temp = (short) (~temp);
        Util.setShort(crc16, (short) 0, temp);
        Util.arrayCopy(crc16, (short) 0, outBuff, outOffset, (short) 2);
        Util.arrayFillNonAtomic(crc16, (short) 0, (short) LENGTH, (byte) 0);
        return LENGTH;
    }

    public void update(byte inBuff[], short inOffset, short inLength) {
        crc16(inBuff, inOffset, inLength);
    }

    void crc16(byte inBuf[], short inOff, short inLen) {
        short fcs = Util.getShort(crc16, (short) 0);
        for (short i = inOff; i < (short) (inOff + inLen); i++) {
            short d = (short) (inBuf[i] << 8);
            for (short k = 0; k < 8; k++) {
                if ((short) ((fcs ^ d) & 0x8000) != 0) {
                    fcs = (short) ((short) (fcs << 1) ^ 0x1021);
                } else {
                    fcs <<= 1;
                }
                d <<= 1;
            }

        }
        Util.setShort(crc16, (short) 0, fcs);
    }
}
