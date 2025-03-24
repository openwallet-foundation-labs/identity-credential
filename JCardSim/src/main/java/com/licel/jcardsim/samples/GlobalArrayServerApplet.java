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

/**
 * Global array server applet.
 *
 * <p>Supported APDUs:</p>
 *
 * <ul>
 *     <li><code>CLA=0x10 INS=1</code> Create global byte array with size form <code>P1</code> and fill each byte with data from <code>P2</code></li>
 *     <li><code>CLA=0x10 INS=2</code> Store global byte array value from <code>CData</code></li>
 * </ul>
 */

public class GlobalArrayServerApplet extends BaseApplet implements GlobalArrayAccess{
    private final static byte CLA = 0x10; 
    private final static byte INS_INIT_GLOBAL_ARRAY_BYTE = 0x01;
    private final static byte INS_WRITE_GLOBAL_ARRAY_BYTE = 0x02;

    private final byte[] transientMemory;
    private static final short MAX_ALLOWED_GLOBAL_ARRAY_SIZE_BYTES = 64;

    private Object globalArray = null;

    protected GlobalArrayServerApplet(){
        transientMemory = JCSystem.makeTransientByteArray(MAX_ALLOWED_GLOBAL_ARRAY_SIZE_BYTES, JCSystem.CLEAR_ON_DESELECT);
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength)
            throws ISOException {
        new GlobalArrayServerApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if(selectingApplet()) {
            return;
        }

        byte[] buffer = apdu.getBuffer();

        // Verify CLA
        if( buffer[ISO7816.OFFSET_CLA] != CLA){
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch(buffer[ISO7816.OFFSET_INS]){
            case INS_INIT_GLOBAL_ARRAY_BYTE:
                initGlobalArrayByte(apdu);
                return;
            case INS_WRITE_GLOBAL_ARRAY_BYTE:
                writeGlobalArrayByte(apdu);
                return;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);

        }
    } 

    public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
        return this;
    }


    private void initGlobalArrayByte(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        byte size = buffer[ISO7816.OFFSET_P1];

        if( (size > MAX_ALLOWED_GLOBAL_ARRAY_SIZE_BYTES) || (size == 0) ) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        byte init_val = buffer[ISO7816.OFFSET_P2];
        globalArray = JCSystem.makeGlobalArray( JCSystem.ARRAY_TYPE_BYTE,size);
        for (byte i=0; i<size; i++ ) {
            ((byte[])globalArray)[i] = init_val;
        }
    }

    private void writeGlobalArrayByte(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte numBytes = buffer[ISO7816.OFFSET_LC];
        if( (numBytes > MAX_ALLOWED_GLOBAL_ARRAY_SIZE_BYTES) || (numBytes == 0) ){
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        byte bytesRead = (byte)apdu.setIncomingAndReceive();
        byte bufferOffset = 0;

        while(bytesRead >0){
            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, transientMemory, bufferOffset, bytesRead);
            bufferOffset += bytesRead;
            bytesRead = (byte)apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }

        for (byte i=0; i<numBytes; i++ ) {
            ((byte[])globalArray)[i] = transientMemory[i];
        }
    }

    @Override
    public Object getGlobalArrayRef() {
        return globalArray;
    }


}
