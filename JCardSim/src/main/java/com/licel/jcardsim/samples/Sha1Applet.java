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
package com.licel.jcardsim.samples;

import javacard.framework.*;
import javacard.security.MessageDigest;
import javacardx.apdu.ExtendedLength;


/**
 * Applet for calculating SHA1 digests.
 *
 * <p>Supported APDUs:</p>
 *
 * <ul>
 *     <li><code>CLA=0x80 INS=0</code> digest of <code>CData</code></li>
 *     <li><code>CLA=0x80 INS=2</code> echo input</li>
 *     <li><code>CLA=0x80 INS=4</code> echo value of <code>Le</code></li>
 *     <li><code>CLA=0x80 INS=8</code> return last digest</li>
 * </ul>
 */
public class Sha1Applet extends BaseApplet implements ExtendedLength {
    private static final byte CLA = (byte) 0x80;
    private static final byte INS_DIGEST = 0;
    private static final byte INS_ECHO = 2;
    private static final byte INS_LEN = 4;
    private static final byte INS_LAST_DIGEST = 6;

    private static final byte CLA_MASK = (byte) 0xF0;

    private MessageDigest digest;
    private byte[] lastDigest;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new Sha1Applet().register();
    }

    protected Sha1Applet() {
        digest = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
        lastDigest = JCSystem.makeTransientByteArray(digest.getLength(), JCSystem.CLEAR_ON_DESELECT);
    }

    public void process(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        final short readCount = apdu.setIncomingAndReceive();
        final short lc = apdu.getIncomingLength();
        final short offsetCData = apdu.getOffsetCdata();
        short read = readCount;
        while(read < lc) {
            read += apdu.receiveBytes(read);
        }

        if (selectingApplet()) {
            return;
        }

        if ((buffer[ISO7816.OFFSET_CLA] & CLA_MASK) != CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch (buffer[ISO7816.OFFSET_INS]) {
            case INS_DIGEST:
                short len = digest.doFinal(buffer, offsetCData, lc, lastDigest, (short)0);
                Util.arrayCopy(lastDigest, (short)0, buffer, (short) 0, len);
                apdu.setOutgoingAndSend((short)0, len);
                break;
            case INS_ECHO: {
                apdu.setOutgoingAndSend(offsetCData, lc);
                break;
            }
            case INS_LEN: {
                short le = apdu.setOutgoing();
                apdu.setOutgoingLength((short)4);

                Util.setShort(buffer, (short)0, lc);
                Util.setShort(buffer, (short)2, le);

                apdu.sendBytes((short)0, (short)4);
                break;
            }
            case INS_LAST_DIGEST: {
                Util.arrayCopy(lastDigest, (short)0, buffer, (short) 0, (short) lastDigest.length);
                apdu.setOutgoingAndSend((short)0, (short) lastDigest.length);
                break;
            }
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
