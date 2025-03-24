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
import javacard.security.AESKey;
import javacard.security.DESKey;
import javacard.security.Key;
import javacard.security.KeyBuilder;
import javacardx.crypto.Cipher;

/**
 * Symmetric Cipher Test Applet is designed to encrypt and decrypt data send from host.
 * It can operate either only AES or DES key/cipher per time by initiating from key setting.
 * Support 128 and 192 bit key size
 *
 * <p>Supported APDUs:</p>
 *
 * <ul>
 *     <li><code>CLA=0x10 INS=0x10</code> Set AES Key from <code>CData</code> with <code>P1</code> as key size 128 or 192 bits</li>
 *     <li><code>CLA=0x10 INS=0x11</code> Encrypt AES data in <code>CData</code> with cipher algorithm in <code>P1</code></li>
 *     <li><code>CLA=0x10 INS=0x12</code> Decrypt AES data in <code>CData</code>with cipher algorithm in <code>P1</code></li>
 *     <li><code>CLA=0x20 INS=0x10</code> Set DES Key from <code>CData</code> with <code>P1</code> as key size  DES3_2KEY or DES3_3KEY</li>
 *     <li><code>CLA=0x20 INS=0x11</code> Encrypt DES data in <code>CData</code> with cipher algorithm in <code>P1</code></li>
 *     <li><code>CLA=0x20 INS=0x12</code> Decrypt DES data in <code>CData</code>with cipher algorithm in <code>P1</code></li>
 * </ul>
 */
public class SymmetricCipherApplet extends  BaseApplet {
    private final static byte CLA_AES = 0x10;
    private final static byte CLA_DES = 0x20;
    private final static byte INS_AES_SET_KEY = 0x10;
    private final static byte INS_AES_ENCRYPT = 0x11;
    private final static byte INS_AES_DECRYPT = 0x12;

    private final static byte INS_DES_SET_KEY = 0x10;
    private final static byte INS_DES_ENCRYPT = 0x11;
    private final static byte INS_DES_DECRYPT = 0x12;

    private Key secreteKey = null;

    private Cipher cipher = null;

    private byte[] transientMemory = null;
    private final static short MAX_DATA_BYTE_SIZE = 32;
    public static void install(byte[] bArray, short bOffset, byte bLength)
            throws ISOException {
        new SymmetricCipherApplet();
    }

    protected SymmetricCipherApplet(){
        transientMemory = JCSystem.makeTransientByteArray(MAX_DATA_BYTE_SIZE, JCSystem.CLEAR_ON_DESELECT);
        register();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if(selectingApplet()) return;

        byte[] buffer = apdu.getBuffer();
        switch(buffer[ISO7816.OFFSET_CLA]){
            case CLA_AES:
                doAESMode(apdu);
                break;

            case CLA_DES :
                doDESMode(apdu);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
                break;
        }
    }

    private void doAESMode(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        switch(buffer[ISO7816.OFFSET_INS]){
            case INS_AES_SET_KEY:
                aesSetKey(apdu);
                break;
            case INS_AES_ENCRYPT:
                aesEncrypt(apdu);
                break;
            case INS_AES_DECRYPT:
                aesDecrypt(apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void aesSetKey(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        short keyBitLength = (short) (buffer[ISO7816.OFFSET_P1] & 0x00FF);
        if((keyBitLength != KeyBuilder.LENGTH_AES_128) &&
                (keyBitLength != KeyBuilder.LENGTH_AES_192)  ) {
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
        }

        byte apdu_Lc      = buffer[ISO7816.OFFSET_LC];
        byte[] aesKeyBytes = new byte[apdu_Lc];
        byte bytesRead = (byte) apdu.setIncomingAndReceive();

        byte bufferOffset = 0;

        while(bytesRead >0){
            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, aesKeyBytes, bufferOffset, bytesRead);
            bufferOffset += bytesRead;
            bytesRead = (byte) apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }

        secreteKey = KeyBuilder.buildKey(KeyBuilder.TYPE_AES, keyBitLength, false);
        ((AESKey) secreteKey).setKey(aesKeyBytes, (short) 0);
    }

    private void aesEncrypt(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        byte aesCipherAlg = buffer[ISO7816.OFFSET_P1];
        byte apdu_Lc = buffer[ISO7816.OFFSET_LC];

        if( apdu_Lc > MAX_DATA_BYTE_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] toBeEncryptedData = new byte[apdu_Lc];
        byte bytesRead = (byte) apdu.setIncomingAndReceive();
        byte bufferOffset = 0;

        while(bytesRead >0){
            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, toBeEncryptedData, bufferOffset, bytesRead);
            bufferOffset += bytesRead;
            bytesRead = (byte) apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }

        cipher = Cipher.getInstance(aesCipherAlg, false);
        cipher.init(secreteKey,Cipher.MODE_ENCRYPT);
        cipher.doFinal(toBeEncryptedData,(short)0, (short) toBeEncryptedData.length,transientMemory, (short) 0);

        short le = apdu.setOutgoing();
        apdu.setOutgoingLength(le);
        apdu.sendBytesLong(transientMemory, (short) 0, le);
    }

    private void aesDecrypt(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        byte aesCipherAlg = buffer[ISO7816.OFFSET_P1];

        byte apdu_Lc = buffer[ISO7816.OFFSET_LC];

        if( apdu_Lc > MAX_DATA_BYTE_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] encryptedData = new byte[apdu_Lc];
        byte bytesRead = (byte) apdu.setIncomingAndReceive();
        byte bufferOffset = 0;

        while(bytesRead >0){
            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, encryptedData, bufferOffset, bytesRead);
            bufferOffset += bytesRead;
            bytesRead = (byte) apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }

        cipher = Cipher.getInstance(aesCipherAlg, false);
        cipher.init(secreteKey,Cipher.MODE_DECRYPT);
        cipher.doFinal(encryptedData,(short)0, (short) encryptedData.length,transientMemory, (short) 0);

        short le = apdu.setOutgoing();
        apdu.setOutgoingLength(le);
        apdu.sendBytesLong(transientMemory, (short) 0, le);
    }

    private void doDESMode(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        switch(buffer[ISO7816.OFFSET_INS]){
            case INS_DES_SET_KEY:
                desSetKey(apdu);
                break;
            case INS_DES_ENCRYPT:
                desEncrypt(apdu);
                break;
            case INS_DES_DECRYPT:
                desDecrypt(apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void desSetKey(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        short keyBitLength = (short) (buffer[ISO7816.OFFSET_P1] & 0x00FF);
        if((keyBitLength != KeyBuilder.LENGTH_DES) &&
                (keyBitLength != KeyBuilder.LENGTH_DES3_2KEY) &&
                (keyBitLength != KeyBuilder.LENGTH_DES3_3KEY)    ) {
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
        }

        byte apdu_Lc      = buffer[ISO7816.OFFSET_LC];
        byte[] desKeyBytes = new byte[apdu_Lc];
        byte bytesRead = (byte) apdu.setIncomingAndReceive();

        byte bufferOffset = 0;

        while(bytesRead >0){
            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, desKeyBytes, bufferOffset, bytesRead);
            bufferOffset += bytesRead;
            bytesRead = (byte) apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }

        secreteKey = KeyBuilder.buildKey(KeyBuilder.TYPE_DES, keyBitLength, false);
        ((DESKey) secreteKey).setKey(desKeyBytes, (short) 0);
    }

    private void desEncrypt(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        byte desCipherAlg = buffer[ISO7816.OFFSET_P1];
        byte apdu_Lc = buffer[ISO7816.OFFSET_LC];

        if( apdu_Lc > MAX_DATA_BYTE_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] toBeEncryptedData = new byte[apdu_Lc];
        byte bytesRead = (byte) apdu.setIncomingAndReceive();
        byte bufferOffset = 0;

        while(bytesRead >0){
            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, toBeEncryptedData, bufferOffset, bytesRead);
            bufferOffset += bytesRead;
            bytesRead = (byte) apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }

        cipher = Cipher.getInstance(desCipherAlg, false);
        cipher.init(secreteKey,Cipher.MODE_ENCRYPT);
        cipher.doFinal(toBeEncryptedData,(short)0, (short) toBeEncryptedData.length,transientMemory, (short) 0);

        short le = apdu.setOutgoing();
        apdu.setOutgoingLength(le);
        apdu.sendBytesLong(transientMemory, (short) 0, le);
    }

    private void desDecrypt(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        byte desCipherAlg = buffer[ISO7816.OFFSET_P1];

        byte apdu_Lc = buffer[ISO7816.OFFSET_LC];

        if( apdu_Lc > MAX_DATA_BYTE_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] encryptedData = new byte[apdu_Lc];
        byte bytesRead = (byte) apdu.setIncomingAndReceive();
        byte bufferOffset = 0;

        while(bytesRead >0){
            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, encryptedData, bufferOffset, bytesRead);
            bufferOffset += bytesRead;
            bytesRead = (byte) apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }

        cipher = Cipher.getInstance(desCipherAlg, false);
        cipher.init(secreteKey,Cipher.MODE_DECRYPT);
        cipher.doFinal(encryptedData,(short)0, (short) encryptedData.length,transientMemory, (short) 0);

        short le = apdu.setOutgoing();
        apdu.setOutgoingLength(le);
        apdu.sendBytesLong(transientMemory, (short) 0, le);
    }
}
