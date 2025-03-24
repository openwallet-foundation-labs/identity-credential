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

import com.licel.jcardsim.utils.AIDUtil;

import javacard.framework.*;

/**
 * Global array client applet.
 *
 * <p>Supported APDUs:</p>
 *
 * <ul>
 *     <li><code>CLA=0x10 INS=1</code> Read global byte array with size from <code>Le</code></li>
 *     <li><code>CLA=0x10 INS=2</code> Write global byte array value from <code>CData</code></li>
 * </ul>
 */
public class GlobalArrayClientApplet extends BaseApplet{
    private final static byte CLA = 0x10;
    private final static byte INS_READ_GLOBAL_ARRAY_BYTE = 0x01;
    private final static byte INS_WRITE_GLOBAL_ARRAY_BYTE = 0x02;

    private static final short MAX_ALLOWED_GLOBAL_ARRAY_SIZE_BYTES = 64;

    private AID serverAppletAID;

    /**
     * This method is called once during applet instantiation process.
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     * @throws ISOException if the install method failed
     */
    public static void install(byte[] bArray, short bOffset, byte bLength)
            throws ISOException {
        new GlobalArrayClientApplet(bArray,bOffset,bLength);
    }

    protected GlobalArrayClientApplet(byte[] bArray, short bOffset, byte bLength){
        byte aidLen = bArray[bOffset];
        byte[] aidBytes = new byte[aidLen];
        Util.arrayCopyNonAtomic(bArray, (short) (bOffset+1), aidBytes, (short) 0, aidLen);

        serverAppletAID = AIDUtil.create(aidBytes);

        register();
    }


    public void process(APDU apdu) {
        if(selectingApplet())
            return;

        byte[] buffer = apdu.getBuffer();

        if( buffer[ISO7816.OFFSET_CLA] != CLA)
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);

        switch(buffer[ISO7816.OFFSET_INS]){
            case INS_READ_GLOBAL_ARRAY_BYTE:
                readGlobalArrayByte(apdu);
                return;

            case INS_WRITE_GLOBAL_ARRAY_BYTE:
                writeGlobalArrayByte(apdu);
                return;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
    
    private void readGlobalArrayByte(APDU apdu){
        GlobalArrayAccess shared = (GlobalArrayAccess)JCSystem.getAppletShareableInterfaceObject(serverAppletAID, (byte) 0);
        byte[] globalArrayByte = (byte[]) shared.getGlobalArrayRef();

        short le = apdu.setOutgoing();
        apdu.setOutgoingLength(le);
        apdu.sendBytesLong(globalArrayByte, (short) 0, le);
    }

    private void writeGlobalArrayByte(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte numBytes = buffer[ISO7816.OFFSET_LC];
        if( (numBytes > MAX_ALLOWED_GLOBAL_ARRAY_SIZE_BYTES) || (numBytes == 0) ){
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        GlobalArrayAccess shared = (GlobalArrayAccess)JCSystem.getAppletShareableInterfaceObject(serverAppletAID, (byte) 0);
        byte[] globalArrayByte = (byte[]) shared.getGlobalArrayRef();

        byte bytesRead = (byte)apdu.setIncomingAndReceive();
        byte bufferOffset = 0;

        while(bytesRead >0){
            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, globalArrayByte, bufferOffset, bytesRead);
            bufferOffset += bytesRead;
            bytesRead = (byte)apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }
    }

}
