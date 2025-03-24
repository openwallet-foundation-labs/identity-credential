/*
 * Copyright 2020 Licel Corporation.
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

import javacard.security.AESKey;
import javacard.security.Key;
import javacard.security.KeyBuilder;
import javacard.security.RandomData;

/**
 * @author LICEL LLC
 */
public class PersistentApplet extends BaseApplet {
    
    private final byte GET_DATA_INS = 0x01;
    private final byte GET_COUNTER = 0x02;
    private final byte INC_COUNTER = 0x03;
    private final byte GET_DESELECT_COUNTER = 0x04;
    
    private final short ARR_SIZE = 8;
    private final short AES_KEY_SIZE = 128;

    private byte[] byteArr;
    private Key[] keyArr;
    
    private byte counter = 0;
    private byte deSelectCounter = 0;
    
    RandomData gen;
    
    protected PersistentApplet(byte[] bArray, short bOffset, byte bLength) {
        gen = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);

        byteArr = new byte[ARR_SIZE];
        gen.generateData(byteArr, (short) 0, (short) byteArr.length);
        
        keyArr = new Key[ARR_SIZE];
        byte[] keyDataBuf = new byte[AES_KEY_SIZE / 8];
        for(int i = 0; i < keyArr.length; i++) {
            keyArr[i] = KeyBuilder.buildKey(KeyBuilder.TYPE_AES, AES_KEY_SIZE, false);
            gen.generateData(keyDataBuf, (short) 0, (short) keyDataBuf.length);
            
            ((AESKey) keyArr[i]).setKey(keyDataBuf, (short) 0);
        }                    
        register();
    }
    
    public static void install(byte[] bArray, short bOffset, byte bLength)
            throws ISOException {
        new PersistentApplet(bArray, bOffset,bLength);
    }
    
    public void deselect() {
        deSelectCounter++;
    }
    
    public void process(APDU apdu) {

        if(selectingApplet()) return;
        byte[] buffer = apdu.getBuffer();

        switch (buffer[ISO7816.OFFSET_INS]) {
            case GET_DATA_INS:
                apdu.setOutgoing();
                short totalLen = (short)(byteArr.length + (keyArr.length * AES_KEY_SIZE / 8));
                apdu.setOutgoingLength(totalLen);
                byte[] buf = new byte[totalLen];
                System.arraycopy(byteArr, (short) 0, buf, (short) 0, byteArr.length);
                for(int i = 0; i < keyArr.length; i++) {
                    ((AESKey) keyArr[i]).getKey(buf, (short) (byteArr.length + (i * AES_KEY_SIZE / 8)));
                }
                apdu.sendBytesLong(buf, (short) 0, (short) buf.length);
                break;
            case GET_COUNTER:
                buffer[0] = counter;
                apdu.setOutgoingAndSend((short) 0, (short) 1);
                break;
            case INC_COUNTER:
                counter++;
                break;
            case GET_DESELECT_COUNTER:
                buffer[0] = deSelectCounter;
                apdu.setOutgoingAndSend((short) 0, (short) 1);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
