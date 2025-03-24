/*
 * Copyright 2012 Licel LLC.
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
 * Basic HelloWorld JavaCard Applet.
 * @author LICEL LLC
 */
public class HelloWorldApplet extends BaseApplet {

    /**
     * Instruction: say hello
     */
    private final static byte SAY_HELLO_INS = (byte) 0x01;
    /**
     * Instruction: say echo v2
     */
    private final static byte SAY_ECHO2_INS = (byte) 0x03;
    /**
     * Instruction: get install params
     */
    private final static byte SAY_IPARAMS_INS = (byte) 0x04;    
    /**
     * Instruction: NOP
     */
    private final static byte NOP_INS = (byte) 0x02;
    /**
     * Instruction: queue data and return 61xx
     */
    private final static byte SAY_CONTINUE_INS = (byte) 0x06;
    /**
     * Instruction: CKYListObjects (http://pki.fedoraproject.org/images/7/7a/CoolKeyApplet.pdf 2.6.17)
     */
    private final static byte LIST_OBJECTS_INS = (byte) 0x58;
    /**
     * Instruction: "Hello Java Card world!" + Application Specific SW 9XYZ
     */
    private final static byte APPLICATION_SPECIFIC_SW_INS = (byte) 0x7;
    /**
     * Instruction: return maximum data.
     */
    private final static byte MAXIMUM_DATA_INS = (byte) 0x8;
    /**
     * Byte array representing "Hello Java Card world!" string.
     */
    private static byte[] helloMessage = new byte[]{
        0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x20, // "Hello "
        0x77, 0x6F, 0x72, 0x6C, 0x64, 0x20, 0x21 // "world !"
    };

    private byte[] echoBytes;
    private byte[] initParamsBytes;
    private final byte[] transientMemory;
    private static final short LENGTH_ECHO_BYTES = 256;

    /**
     * Only this class's install method should create the applet object.
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     */
    protected HelloWorldApplet(byte[] bArray, short bOffset, byte bLength) {
        echoBytes = new byte[LENGTH_ECHO_BYTES];
        if (bLength > 0) {
            byte iLen = bArray[bOffset]; // aid length
            bOffset = (short) (bOffset + iLen + 1);
            byte cLen = bArray[bOffset]; // info length
            bOffset = (short) (bOffset + 3);
            byte aLen = bArray[bOffset]; // applet data length
            initParamsBytes = new byte[aLen];
            Util.arrayCopyNonAtomic(bArray, (short) (bOffset + 1), initParamsBytes, (short) 0, aLen);
        }
        transientMemory = JCSystem.makeTransientByteArray(LENGTH_ECHO_BYTES, JCSystem.CLEAR_ON_RESET);
        register();
    }    

    /**
     * This method is called once during applet instantiation process.
     * @param bArray the array containing installation parameters
     * @param bOffset the starting offset in bArray
     * @param bLength the length in bytes of the parameter data in bArray
     * @throws ISOException if the install method failed
     */
    public static void install(byte[] bArray, short bOffset, byte bLength)
            throws ISOException {
        new HelloWorldApplet(bArray, bOffset,bLength);
    }

    /**
     * This method is called each time the applet receives APDU.
     */
    public void process(APDU apdu) {
        // good practice
        if(selectingApplet()) return;
        byte[] buffer = apdu.getBuffer();
        // Now determine the requested instruction:
        switch (buffer[ISO7816.OFFSET_INS]) {
            case SAY_HELLO_INS:
                sayHello(apdu, (short)0x9000);
                return;
            case SAY_ECHO2_INS:
                sayEcho2(apdu);
                return;
            case SAY_IPARAMS_INS:
                sayIParams(apdu);
                return;
            case SAY_CONTINUE_INS:
                sayContinue(apdu);
                return;
            case LIST_OBJECTS_INS:
                listObjects(apdu);
                return;
            case APPLICATION_SPECIFIC_SW_INS:
                sayHello(apdu, (short)0x9B00);
                return;
            case MAXIMUM_DATA_INS:
                maximumData(apdu);
                return;
            case NOP_INS:
                return;
            default:
                // We do not support any other INS values
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    /**
     * Sends hello message to host using given APDU.
     *
     * @param apdu APDU that requested hello message
     * @param sw response sw code
     */
    private void sayHello(APDU apdu, short sw) {
        // Here all bytes of the APDU are stored
        byte[] buffer = apdu.getBuffer();
        // receive all bytes
        // if P1 = 0x01 (echo)
        short incomeBytes = apdu.setIncomingAndReceive();
        byte[] echo = transientMemory;
        short echoLength;
        if (buffer[ISO7816.OFFSET_P1] == 0x01) {
            echoLength = incomeBytes;
            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, echo, (short) 0, incomeBytes);
        } else {
            echoLength = (short) helloMessage.length;
            Util.arrayCopyNonAtomic(helloMessage, (short) 0, echo, (short) 0, (short) helloMessage.length);
        }
        // Tell JVM that we will send data
        apdu.setOutgoing();
        // Set the length of data to send
        apdu.setOutgoingLength(echoLength);
        // Send our message starting at 0 position
        apdu.sendBytesLong(echo, (short) 0, echoLength);
        // Set application specific sw
        if(sw!=0x9000) {
            ISOException.throwIt(sw);
        }
    }


    /**
     * echo v2
     */
    private void sayEcho2(APDU apdu) {
        byte buffer[] = apdu.getBuffer();

        short bytesRead = apdu.setIncomingAndReceive();
        short echoOffset = (short) 0;

        while (bytesRead > 0) {
            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, echoBytes, echoOffset, bytesRead);
            echoOffset += bytesRead;
            bytesRead = apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }

        apdu.setOutgoing();
        apdu.setOutgoingLength(echoOffset);
        // echo data
        apdu.sendBytesLong(echoBytes, (short) 0, echoOffset);

    }

    /**
     * echo install params
     */
    private void sayIParams(APDU apdu) {
        apdu.setOutgoing();
        apdu.setOutgoingLength((short)initParamsBytes.length);
        // echo install parmas
        apdu.sendBytesLong(initParamsBytes, (short) 0, (short)initParamsBytes.length);
    }   

    /**
     * send some hello data, and indicate there's more
     */
    private void sayContinue(APDU apdu) {
        byte[] echo = transientMemory;
        short echoLength = (short) 6;
        Util.arrayCopyNonAtomic(helloMessage, (short)0, echo, (short)0, (short)6);
        apdu.setOutgoing();
        apdu.setOutgoingLength(echoLength);
        apdu.sendBytesLong(echo, (short) 0, echoLength);
        ISOException.throwIt((short) (ISO7816.SW_BYTES_REMAINING_00 | 0x07));
    }


    /**
     * send the maximum amount of data the apdu will accept
     *
     * @param apdu APDU that requested hello message
     */
    private void maximumData(APDU apdu) {
        short maxData = APDU.getOutBlockSize();
        byte[] buffer = apdu.getBuffer();
        Util.arrayFillNonAtomic(buffer, (short) 0, maxData, (byte) 0);
        apdu.setOutgoingAndSend((short) 0, maxData);
    }
    
    // prototype
    private void listObjects(APDU apdu)
    {
        byte buffer[] = apdu.getBuffer();
        
	if (buffer[ISO7816.OFFSET_P2] != 0) {
            ISOException.throwIt((short)0x9C11);
        }
	
	byte expectedBytes = buffer[ISO7816.OFFSET_LC];
	
	if (expectedBytes < 14) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
	
	ISOException.throwIt((short)0x9C12);
    }    
}
