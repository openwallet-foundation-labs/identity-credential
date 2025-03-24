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


/**
 * Dual interface applet.
 *
 * <p>Supported APDUs:</p>
 *
 * <ul>
 *     <li><code>CLA=0x80 INS=0</code> read value</li>
 *     <li>
 *         <code>CLA=0x80 INS=2</code> store value from <code>CData</code>.
 *         Only works on contacted interface
 *     </li>
 *     <li><code>CLA=0x80 INS=4</code> get interface information (protocol byte)</li>
 * </ul>
 */
public class DualInterfaceApplet extends BaseApplet {
    private static final byte CLA = (byte) 0x80;
    private static final byte INS_READ = 0;
    private static final byte INS_WRITE = 2;
    private static final byte INS_INFO = 4;

    private static final byte CLA_MASK = (byte) 0xF0;

    private static final byte _0 = 0;
    private static final byte _1 = 1;

    private final byte[] store;
    private short storeLen = 0;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new DualInterfaceApplet().register();
    }

    protected DualInterfaceApplet() {
        store = new byte[255];
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
            case INS_READ: {
                Util.arrayCopyNonAtomic(store, _0, buffer, _0, storeLen);
                apdu.setOutgoingAndSend(_0, storeLen);
                break;
            }
            case INS_WRITE: {
                if (isContacted()) {
                    storeLen = lc;
                    Util.arrayCopyNonAtomic(buffer, offsetCData, store, _0, storeLen);
                }
                else {
                    ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
                }
                break;
            }
            case INS_INFO: {
                buffer[0] = APDU.getProtocol();
                apdu.setOutgoingAndSend(_0, (short) 1);
                break;
            }
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    public boolean isContacted() {
        return APDU.getProtocol() == APDU.PROTOCOL_T0 || APDU.getProtocol() == APDU.PROTOCOL_T1;
    }
}
