/*
 * Copyright 2022 Licel Corporation.
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
import javacardx.apdu.ExtendedLength;

/**
 * Applet for testing the extended APDU cases.
 *
 * Especial for zero-value of byte at Le/Lc position (offset = 4 ).
 *
 * <p>Supported only single APDU format:</p>
 * <code>CLA=0x80 INS=0xb4 P1=0 P2=0</code>
 * <ul>
 *     <li>Case 2, send back 0x5A which number of bytes according to Le</li>
 *     <li>Case 2E, send back 0x5A which number of bytes according to 3-byte Le</li>
 *     <li>Case 3E, receive and check data must be 0x5A which number of bytes according to 3-byte Lc /li>
 *     <li>Case 4, receive a zero-value byte and send back 0x5A which number of bytes according to Le/li>
 *     <li>Case 4E, receive and check data must be all 0x5A which number of bytes according to 3-byte Lc then send back 0x5A which number of bytes according to 2-byte Le/li>
 * </ul>
 */
public class ApduExtendedCasesApplet extends BaseApplet implements ExtendedLength {
    private static final byte CLA = (byte) 0x80;
    private static final byte INS = (byte) 0xb4;

    private static final byte P1 = (byte) 0;
    private static final byte P2 = (byte) 0;

    protected ApduExtendedCasesApplet(){
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength)
            throws ISOException {
        new ApduExtendedCasesApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if(selectingApplet()) {
            return;
        }

        byte[] buffer = apdu.getBuffer();

        if( buffer[ISO7816.OFFSET_CLA] != CLA){
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        if( (buffer[ISO7816.OFFSET_P1] != P1) || (buffer[ISO7816.OFFSET_P2] != P2)  ){
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
        }

        switch(buffer[ISO7816.OFFSET_INS]){
            case INS :
                processCommand(apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void processCommand(APDU apdu){

        byte[] buffer = apdu.getBuffer();

        short readCount = apdu.setIncomingAndReceive();
        short Lc = apdu.getIncomingLength();
        short offsetCData = apdu.getOffsetCdata();
        short read = readCount;
        while(read < Lc) {
            read += apdu.receiveBytes(read);
        }

        short Ne = apdu.setOutgoing();

        if( Lc == 0 ){
            if( Ne > 0 ){
                send0x5aData(apdu, Ne);
                return;
            }

            ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        }
        else{
            byte[] CDataBytes = JCSystem.makeTransientByteArray(read, JCSystem.CLEAR_ON_DESELECT);
            Util.arrayCopyNonAtomic(buffer, offsetCData, CDataBytes, (short) 0, readCount);
            if( CDataBytes.length == 1 ){
                if(CDataBytes[0] == 0){
                    send0x5aData(apdu, Ne);
                }
                else{
                    ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
                }
            }
            else{
                if(Ne > 0){
                    // Check content
                    for(short i = 0; i < Lc; i++){
                        if(CDataBytes[i] != 0x5a){
                            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
                        }
                    }
                    // Echo data back
                    apdu.setOutgoingLength(Ne);
                    apdu.sendBytesLong(CDataBytes, (short) 0, Ne);
                }
                else{
                    // Check content
                    for(short i = 0; i < Lc; i++){
                        if(CDataBytes[i] != 0x5a){
                            ISOException.throwIt(ISO7816.SW_DATA_INVALID);
                        }
                    }
                }
            }
        }
    }

    private static void send0x5aData(APDU apdu, short Ne) {
        byte[] tmp = JCSystem.makeTransientByteArray(Ne, JCSystem.CLEAR_ON_DESELECT);
        for (short i = 0; i < Ne; i++) {
            tmp[i] = 0x5a;
        }

        apdu.setOutgoingLength(Ne);
        apdu.sendBytesLong(tmp, (short) 0, Ne);
    }
}
