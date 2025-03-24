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
import javacard.framework.service.ServiceException;
import javacard.security.CryptoException;
import javacardx.biometry.BioException;
import javacardx.biometry1toN.Bio1toNException;
import javacardx.external.ExternalException;
import javacardx.framework.string.StringException;
import javacardx.framework.tlv.TLVException;
import javacardx.framework.util.UtilException;

/**
 * Runtime exception applet.
 * This applet is intentionally implemented to throw various types of exception in APDU process.
 * Especial for CardRuntimeException subclasses that derived in the same level with ISOException.
 *
 * <p>Supported APDUs:</p>
 *
 * <ul>
 *     <li><code>CLA=1 INS=0</code> Throw <code>CryptoException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=1 INS=1</code> <code>CryptoException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 *
 *     <li><code>CLA=2 INS=0</code> Throw <code>APDUException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=2 INS=1</code> <code>APDUException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 *
 *     <li><code>CLA=3 INS=0</code> Throw <code>SystemException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=3 INS=1</code> <code>SystemException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 *
 *     <li><code>CLA=4 INS=0</code> Throw <code>ServiceException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=4 INS=1</code> <code>ServiceException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 *
 *     <li><code>CLA=5 INS=0</code> Throw <code>BioException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=5 INS=1</code> <code>BioException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 *
 *     <li><code>CLA=6 INS=0</code> Throw <code>Bio1toNException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=6 INS=1</code> <code>Bio1toNException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 *
 *     <li><code>CLA=7 INS=0</code> Throw <code>ExternalException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=7 INS=1</code> <code>ExternalException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 *
 *     <li><code>CLA=8 INS=0</code> Throw <code>PINException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=8 INS=1</code> <code>PINException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 *
 *     <li><code>CLA=9 INS=0</code> Throw <code>StringException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=9 INS=1</code> <code>StringException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 *
 *     <li><code>CLA=10 INS=0</code> Throw <code>TLVException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=10 INS=1</code> <code>TLVException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 *
 *     <li><code>CLA=11 INS=0</code> Throw <code>TransactionException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=11 INS=1</code> <code>TransactionException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 *
 *     <li><code>CLA=12 INS=0</code> Throw <code>UtilException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte</li>
 *     <li><code>CLA=12 INS=1</code> <code>UtilException</code> with reason code from <code>P1</code> as high order byte and <code>P2</code> as low order byte is thrown but it has catch exception then throw <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code> instead</li>
 */
public class RuntimeExceptionApplet extends  BaseApplet{
    private final static byte CLA_CRYPTO_EXCEPTION = 1;
    private final static byte CLA_APDU_EXCEPTION = 2;
    private final static byte CLA_SYSTEM_EXCEPTION = 3;
    private final static byte CLA_SERVICE_EXCEPTION = 4;
    private final static byte CLA_BIO_EXCEPTION = 5;
    private final static byte CLA_BIO_1_TO_N_EXCEPTION = 6;
    private final static byte CLA_EXTERNAL_EXCEPTION = 7;
    private final static byte CLA_PIN_EXCEPTION = 8;
    private final static byte CLA_STRING_EXCEPTION = 9;
    private final static byte CLA_TLV_EXCEPTION = 10;
    private final static byte CLA_TRANSACTION_EXCEPTION = 11;
    private final static byte CLA_UTIL_EXCEPTION = 12;

    private static final byte INS_HAS_CATCH_EXCEPTION = 1;

    public static void install(byte[] bArray, short bOffset, byte bLength)
            throws ISOException {
        new RuntimeExceptionApplet();
    }

    protected RuntimeExceptionApplet(){
    register();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if(selectingApplet()) return;

        byte[] buffer = apdu.getBuffer();

        switch(buffer[ISO7816.OFFSET_CLA]){
            case CLA_CRYPTO_EXCEPTION:
                throwCryptoException(apdu);
                break;

            case CLA_APDU_EXCEPTION:
                throwAPDUException(apdu);
                break;

            case CLA_SYSTEM_EXCEPTION:
                throwSystemException(apdu);
                break;

            case CLA_SERVICE_EXCEPTION:
                throwServiceException(apdu);
                break;

            case CLA_BIO_EXCEPTION:
                throwBioException(apdu);
                break;

            case CLA_BIO_1_TO_N_EXCEPTION:
                throwBio1toNException(apdu);
                break;

            case CLA_EXTERNAL_EXCEPTION:
                throwExternalException(apdu);
                break;

            case CLA_PIN_EXCEPTION:
                throwPINException(apdu);
                break;

            case CLA_STRING_EXCEPTION:
                throwStringException(apdu);
                break;

            case CLA_TLV_EXCEPTION:
                throwTLVException(apdu);
                break;
            case CLA_TRANSACTION_EXCEPTION:
                throwTransactionException(apdu);
                break;

            case CLA_UTIL_EXCEPTION:
                throwUtilException(apdu);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
    }

    private void throwCryptoException(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short cryptoExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                CryptoException.throwIt(cryptoExReasonCode);
            } catch (CryptoException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        } else {
            CryptoException.throwIt(cryptoExReasonCode);
        }
    }

    private void throwAPDUException(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short apduExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                APDUException.throwIt(apduExReasonCode);
            } catch (APDUException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        } else {
            APDUException.throwIt(apduExReasonCode);
        }
    }

    private void throwSystemException(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short systemExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                SystemException.throwIt(systemExReasonCode);
            } catch (SystemException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        }
        else {
            SystemException.throwIt(systemExReasonCode);
        }
    }

    private void throwServiceException(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short serviceExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                ServiceException.throwIt(serviceExReasonCode);
            } catch (ServiceException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        }
        else {
            ServiceException.throwIt(serviceExReasonCode);
        }
    }

    private void throwBioException(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short bioExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                BioException.throwIt(bioExReasonCode);
            } catch (BioException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        }
        else {
            BioException.throwIt(bioExReasonCode);
        }
    }

    private void throwBio1toNException(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short bio1toNExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                Bio1toNException.throwIt(bio1toNExReasonCode);
            } catch (Bio1toNException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        }
        else {
            Bio1toNException.throwIt(bio1toNExReasonCode);
        }
    }

    private void throwExternalException(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short externalExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                ExternalException.throwIt(externalExReasonCode);
            } catch (ExternalException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        }
        else {
            ExternalException.throwIt(externalExReasonCode);
        }
    }

    private void throwPINException(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short pinExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                PINException.throwIt(pinExReasonCode);
            } catch (PINException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        }
        else {
            PINException.throwIt(pinExReasonCode);
        }
    }
    private void throwStringException(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short stringExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                StringException.throwIt(stringExReasonCode);
            } catch (StringException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        }
        else {
            StringException.throwIt(stringExReasonCode);
        }
    }
    private void throwTLVException(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short tlvExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                TLVException.throwIt(tlvExReasonCode);
            } catch (TLVException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        }
        else {
            TLVException.throwIt(tlvExReasonCode);
        }
    }

    private void throwTransactionException(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short transactionExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                TransactionException.throwIt(transactionExReasonCode);
            } catch (TransactionException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        }
        else {
            TransactionException.throwIt(transactionExReasonCode);
        }
    }

    private void throwUtilException(APDU apdu){
        byte[] buffer = apdu.getBuffer();
        byte hasExceptionCatch = buffer[ISO7816.OFFSET_INS];
        short utilExReasonCode = Util.makeShort(buffer[ISO7816.OFFSET_P1], buffer[ISO7816.OFFSET_P2]);

        if (hasExceptionCatch == INS_HAS_CATCH_EXCEPTION) {
            try {
                UtilException.throwIt(utilExReasonCode);
            } catch (UtilException e) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        }
        else {
            UtilException.throwIt(utilExReasonCode);
        }
    }
}
