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
 * ISO/IEC 3309 compliant 32 bit CRC algorithm.
 * on BouncyCastle CryptoAPI.
 * @see Checksum
 */
public class CRC32 extends Checksum {

    final static byte LENGTH = 4;
    private byte crc32[];
    private final byte polynom[] = {
        4, -63, 29, -73
    };

    public CRC32() {
        crc32 = JCSystem.makeTransientByteArray(LENGTH, JCSystem.CLEAR_ON_DESELECT);
    }

    public byte getAlgorithm() {
        return ALG_ISO3309_CRC32;
    }

    public void init(byte bArray[], short bOff, short bLen)
            throws CryptoException {
        if (bLen != LENGTH) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        Util.arrayCopyNonAtomic(bArray, bOff, crc32, (short) 0, bLen);
    }

    public short doFinal(byte inBuff[], short inOffset, short inLength, byte outBuff[], short outOffset) {
        update(inBuff, inOffset, inLength);
        for (short i = 0; i < 4; i++) {
            crc32[i] ^= 0xff;
        }

        Util.arrayCopy(crc32, (short) 0, outBuff, outOffset, (short) 4);
        Util.arrayFillNonAtomic(crc32, (short) 0, LENGTH, (byte) 0);
        return LENGTH;
    }

    public void update(byte inBuff[], short inOffset, short inLength) {
        crc32(inBuff, inOffset, inLength);
    }

    private void crc32(byte inBuf[], short inOff, short inLen) {
        short fcs_h = Util.getShort(crc32, (short) 0);
        short fcs_l = Util.getShort(crc32, (short) 2);
        short poly_h = Util.getShort(polynom, (short) 0);
        short poly_l = Util.getShort(polynom, (short) 2);
        byte carry = 0;
        for (short i = inOff; i < (short) (inOff + inLen); i++) {
            short d_h = (short) (reflect8(inBuf[i]) << 8);
            for (short k = 0; k < 8; k++) {
                if (((fcs_h ^ d_h) & 0x8000) != 0) {
                    carry = 0;
                    short lfcs_h = shift(fcs_h);
                    if ((fcs_l & 0x8000) != 0) {
                        carry = 1;
                    }
                    short lfcs_l = shift(fcs_l);
                    if (carry == 1) {
                        lfcs_h++;
                    }
                    fcs_h = (short) (lfcs_h ^ poly_h);
                    fcs_l = (short) (lfcs_l ^ poly_l);
                } else {
                    carry = 0;
                    short lfcs_h = shift(fcs_h);
                    if ((fcs_l & 0x8000) != 0) {
                        carry = 1;
                    }
                    short lfcs_l = shift(fcs_l);
                    if (carry == 1) {
                        lfcs_h++;
                    }
                    fcs_h = lfcs_h;
                    fcs_l = lfcs_l;
                }
                d_h <<= 1;
            }

        }

        Util.setShort(crc32, (short) 2, reflect16(fcs_h));
        Util.setShort(crc32, (short) 0, reflect16(fcs_l));
    }

    private byte reflect8(byte input){
        byte reflected = 0;
        for( byte i = 0; i < 8; i++){
            if((input & (0x80 >> i)) > 0 )
                reflected |= (1 << i);
        }
        return reflected;
    }

    private short reflect16(short input){
        short reflected = 0;
        for( byte i = 0; i < 16; i++){
            if((input & (0x8000 >> i)) > 0 )
                reflected |= (1 << i);
        }
        return reflected;
    }

    short shift(short s) {
        return s <<= 1;
    }
}
