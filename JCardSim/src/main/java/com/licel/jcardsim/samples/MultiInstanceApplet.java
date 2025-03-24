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
 * Multi instance sample applet.
 *
 * <p>Returns <code>FCI</code> (file control information) on SELECT</p>
 *
 * <p>Supported APDUs:</p>
 *
 * <ul>
 *     <li><code>CLA=0x80 INS=0</code> return AID</li>
 *     <li><code>CLA=0x80 INS=2</code> return instance count</li>
 *     <li><code>CLA=0x80 INS=4</code> lock the applet</li>
 * </ul>
 */
public class MultiInstanceApplet extends BaseApplet {
    private static final byte CLA = (byte) 0x80;
    private static final byte INS_GET_FULL_AID = 0;
    private static final byte INS_GET_COUNT = 2;
    private static final byte INS_MAKE_UNUSABLE = 4;

    private static final byte CLA_MASK = (byte) 0xF0;
    private static short instanceCounter = 0;

    private boolean locked = false;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new MultiInstanceApplet().register();
    }

    protected MultiInstanceApplet() {
        ++ instanceCounter;
    }

    @Override
    public boolean select() {
        return !locked;
    }

    public void process(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        final short readCount = apdu.setIncomingAndReceive();
        final short lc = apdu.getIncomingLength();
        final short offsetCData = apdu.getOffsetCdata();
        short read = readCount;
        while (read < lc) {
            read += apdu.receiveBytes(read);
        }

        if (selectingApplet()) {
            // return FCI with AID from SELECT
            byte dataSize = buffer[ISO7816.OFFSET_LC];
            buffer[1] = 0x6F; // Tag: File Control Information (FCI) Template
            buffer[2] = (byte) (dataSize + 2);
            buffer[3] = (byte) 0x84;  // Tag: Dedicated File (DF) Name
            buffer[4] = dataSize;
            apdu.setOutgoingAndSend((short) 1, (short)(dataSize + 4));
            return;
        }

        if ((buffer[ISO7816.OFFSET_CLA] & CLA_MASK) != CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch (buffer[ISO7816.OFFSET_INS]) {
            case INS_GET_FULL_AID: {
                short dataSize = JCSystem.getAID().getBytes(buffer, (short) 0);
                apdu.setOutgoingAndSend((short)0, dataSize);
                break;
            }
            case INS_GET_COUNT: {
                Util.setShort(buffer, (short)0, instanceCounter);
                apdu.setOutgoingAndSend((short)0, (short) 2);
            }
            case INS_MAKE_UNUSABLE: {
                locked = true;
                break;
            }
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    public void uninstall() {
        -- instanceCounter;
    }
}
