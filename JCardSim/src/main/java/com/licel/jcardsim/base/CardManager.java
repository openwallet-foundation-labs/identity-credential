/*
 * Copyright 2014 Licel LLC.
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
package com.licel.jcardsim.base;

import com.licel.jcardsim.io.JavaCardInterface;
import javacard.framework.AID;
import javacard.framework.ISO7816;
import javacard.framework.SystemException;
import javacard.framework.Util;
/**
 * CardManager.
 */
public class CardManager implements CardManagerInterface {
    private static CardManagerInterface impl;
    static {
        System.out.println("Trying to load an instance of com.licel.globalplatform.CardManager");
        try {
            impl = (CardManagerInterface)Class.forName("com.licel.globalplatform.CardManager").newInstance();
            System.out.println("Succesfully loaded the instance!");
        } catch (Throwable ex) {
            System.out.println("Failed to load the instance! Will use the default CardManager");
            impl = new CardManager();
        }
    }
    public static byte[] dispatchApdu(JavaCardInterface sim, byte[] capdu) {
        return impl.dispatchApduImpl(sim, capdu);
    }
    
    public byte[] dispatchApduImpl(JavaCardInterface sim, byte[] capdu) {
        byte[] theSW = new byte[2];
        if (capdu[ISO7816.OFFSET_CLA] == (byte)0x80 && capdu[ISO7816.OFFSET_INS] == (byte)0xb8) {
            // handle CREATE APPLET command
            // command format:
            // CLA    INS  P0    P1
            // 0x8x, 0xb8, 0x00, 0x00
            // Lc field
            // AID length field
            // AID field
            // parameter length field
            // [parameters]
            // Le field          
            // parameters
            try {
                AID aid = new AID(capdu, (short)(ISO7816.OFFSET_CDATA + 1), capdu[ISO7816.OFFSET_CDATA]);
                sim.createApplet(aid, capdu, ISO7816.OFFSET_CDATA, capdu[ISO7816.OFFSET_LC]);
                byte[] response = new byte[capdu[ISO7816.OFFSET_CDATA] + 2];
                aid.getBytes(response, (short) 0);
                Util.setShort(response, (short) (response.length - 2), ISO7816.SW_NO_ERROR);
                return response;
            } catch (SystemException e) {
                Util.setShort(theSW, (short) 0, e.getReason());
                return theSW;
            }
            // forward -> applet. TODO: more clean implementation    
        } else {
            return sim.transmitCommand(capdu);
        }
    }
}
