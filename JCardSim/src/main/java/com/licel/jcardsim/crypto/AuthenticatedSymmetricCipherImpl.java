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
package com.licel.jcardsim.crypto;

import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.CryptoException;
import javacard.security.Key;
import javacard.security.KeyBuilder;
import javacardx.crypto.AEADCipher;
import javax.crypto.Cipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.modes.AEADBlockCipher;
import org.bouncycastle.crypto.modes.CCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.gcm.GCMUtil;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import org.bouncycastle.util.Arrays;


import javax.crypto.spec.GCMParameterSpec;

public class AuthenticatedSymmetricCipherImpl extends AEADCipher {
    byte algorithm;

    AEADBlockCipher engine;
    AEADParameters parameters;

    enum CipherState {
        Uninitialized,
        Initialized,
        Finalized
    };

    CipherState state;

    byte initMode;
    short initMsgLen;
    short totalMsgLen;
    short initAADLen;

    public AuthenticatedSymmetricCipherImpl(byte algorithm) {
        this.algorithm = algorithm;
        state = CipherState.Uninitialized;
    }

    /**
     * Gets the Cipher algorithm.
     * @return the algorithm code defined above; if the algorithm is not one of the pre-defined algorithms, 0 is returned.
     */
    @Override
    public byte getAlgorithm() {
        return algorithm;
    }

    /**
     * Gets the raw cipher algorithm. Pre-defined codes listed in CIPHER_* constants in this class e.g. CIPHER_AES_CBC.
     * @return the raw cipher algorithm code defined above; if the algorithm is not one of the pre-defined algorithms, 0 is returned.
     */
    @Override
    public byte getCipherAlgorithm() {
        switch(algorithm){
            case ALG_AES_CCM:
                return  CIPHER_AES_CCM;

            case ALG_AES_GCM:
                return CIPHER_AES_GCM;
        }
        return 0;
    }

    /**
     * Gets the padding algorithm. Pre-defined codes listed in PAD_* constants in this class e.g. PAD_NULL.
     * @return the padding algorithm code defined in the Cipher class; if the algorithm is not one of the pre-defined algorithms, 0 is returned.
     */
    @Override
    public byte getPaddingAlgorithm() {
        return 0;
    }
    /**
     * Initializes the Cipher object with the appropriate Key.
     * This method should be used for algorithms which do not need initialization parameters or use default parameter values.
     * init() must be used to update the Cipher object with a new key.
     * If the Key object is modified after invoking the init() method, the behavior of the update() and doFinal() methods is unspecified.
     * The Key is checked for consistency with the Cipher algorithm. For example, the key type must be matched.
     * For elliptic curve algorithms, the key must represent a valid point on the curve's domain parameters.
     * Additional key component/domain parameter strength checks are implementation specific.
     * Note:
     *      <li>AES, DES, triple DES and Korean SEED algorithms in CBC mode will use 0 for initial vector(IV) if this method is used.</li>
     *      <li>For optimal performance, when the theKey parameter is a transient key, the implementation should, whenever possible, use transient space for internal storage.</li>
     *      <li>AEADCipher in GCM mode will use 0 for initial vector(IV) if this method is used.</li>
     * @param theKey the key object to use for encrypting or decrypting
     * @param theMode one of MODE_DECRYPT or MODE_ENCRYPT
     * @throws CryptoException- with the following reason codes:
     *      <li>CryptoException.ILLEGAL_VALUE if theMode option is an undefined value or
     *      if the Key is inconsistent with the Cipher implementation.</li>
     *      <li>CryptoException.UNINITIALIZED_KEY if theKey instance is uninitialized.</li>
     *      <li>CryptoException.INVALID_INIT if this method is called for an offline mode of encryption</li>
     */
    @Override
    public void init(Key theKey, byte theMode) throws CryptoException {
        // Support only GCM which can operate in online mode
        if (algorithm != ALG_AES_GCM) {
            CryptoException.throwIt(CryptoException.INVALID_INIT);
        }

        if( (theMode != MODE_DECRYPT) && (theMode != MODE_ENCRYPT)){
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        // AEADCipher in GCM mode will use 0 for initial vector(IV) if this method is used
        byte[] iv = new byte[12];
        Arrays.fill(iv, (byte) 0);

        selectCipherEngine(theKey);

        ParametersWithIV parametersWithIV = new ParametersWithIV(((SymmetricKeyImpl) theKey).getParameters(),iv);
        try{
            engine.init(theMode == MODE_ENCRYPT, parametersWithIV);
        }
        catch (Exception ex){
            ex.printStackTrace();
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        initMode = theMode;
        state = CipherState.Initialized;
    }

    /**
     * Initializes the Cipher object with the appropriate Key and algorithm specific parameters.
     * init() must be used to update the Cipher object with a new key.
     * If the Key object is modified after invoking the init() method, the behavior of the update() and doFinal() methods is unspecified.
     *
     * The Key is checked for consistency with the Cipher algorithm. For example, the key type must be matched.
     * For elliptic curve algorithms, the key must represent a valid point on the curve's domain parameters.
     * Additional key component/domain parameter strength checks are implementation specific.
     *
     * Note:
     * <li>DES and triple DES algorithms in CBC mode expect an 8-byte parameter value for the initial vector(IV) in bArray.</li>
     * <li>AES algorithms in CBC mode expect a 16-byte parameter value for the initial vector(IV) in bArray.</li>
     * <li>Korean SEED algorithms in CBC mode expect a 16-byte parameter value for the initial vector(IV) in bArray.</li>
     * <li>AES algorithms in ECB mode, DES algorithms in ECB mode, Korean SEED algorithm in ECB mode, RSA and DSA algorithms throw CryptoException.ILLEGAL_VALUE.</li>
     * <li>For optimal performance, when the theKey parameter is a transient key, the implementation should, whenever possible, use transient space for internal storage.</li>
     *
     * @param theKey the key object to use for encrypting or decrypting.
     * @param theMode one of MODE_DECRYPT or MODE_ENCRYPT
     * @param bArray byte array containing algorithm specific initialization info
     * @param bOff offset within bArray where the algorithm specific data begins
     * @param bLen byte length of algorithm specific parameter data
     * @throws CryptoException with the following reason codes:
     *         <li>CryptoException.ILLEGAL_VALUE if theMode option is an undefined value or
     *         if a byte array parameter option is not supported by the algorithm or
     *         if the bLen is an incorrect byte length for the algorithm specific data or
     *         if the Key is inconsistent with the Cipher implementation.</li>
     *         <li>CryptoException.UNINITIALIZED_KEY if theKey instance is uninitialized.</li>
     *         <li>CryptoException.INVALID_INIT if this method is called for an offline mode of encryption</li>
     */
    @Override
    public void init(Key theKey, byte theMode, byte[] bArray, short bOff, short bLen) throws CryptoException {
        // Support only GCM which can operate as an online mode of encryption
        if (algorithm != ALG_AES_GCM){
            CryptoException.throwIt(CryptoException.INVALID_INIT);
        }

        if( (theMode != MODE_DECRYPT) && (theMode != MODE_ENCRYPT)){
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        // Supports only the 12 byte IV length, which is the value recommended by NIST Special Publication 800-38D 5.2.1.1 Input Data
        // https://docs.oracle.com/javacard/3.0.5/guide/supported-cryptography-classes.htm#JCUGC356
        if( bLen != 12 ){
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        selectCipherEngine(theKey);
        byte[] iv = JCSystem.makeTransientByteArray(bLen, JCSystem.CLEAR_ON_RESET);
        Util.arrayCopyNonAtomic(bArray, bOff, iv, (short) 0, bLen);
        ParametersWithIV parametersWithIV = new ParametersWithIV(((SymmetricKeyImpl) theKey).getParameters(),iv);
        try{
            engine.init(theMode == MODE_ENCRYPT, parametersWithIV);
        }
        catch (Exception ex){
            ex.printStackTrace();
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        initMode = theMode;
        state = CipherState.Initialized;
    }

    /**
     * Initializes this Cipher instance to encrypt or decrypt a with the given key, nonce, AAD size and message size.
     * This method should only be called for offline cipher mode encryption such as Cipher#ALG_AES_CCM.
     * In offline cipher mode encryption the length of the authentication data, message size and authentication tag must be known in advance.
     *
     * @see javacardx.crypto.AEADCipher#init(Key, byte, byte[], short, short, short, short, short)
     * @param theKey the key object to use for encrypting or decrypting
     * @param theMode one of MODE_DECRYPT or MODE_ENCRYPT
     * @param nonceBuf a buffer holding the nonce
     * @param nonceOff the offset in the buffer of the nonce
     * @param nonceLen the length in the buffer of the nonce
     * @param adataLen the length of the authenticated data as presented in the updateAAD method
     * @param messageLen the length of the message as presented in the update and doFinal methods
     * @param tagSize the size in bytes of the authentication tag
     * @throws CryptoException with the following reason codes:
     *         <li>CryptoException.ILLEGAL_VALUE if any of the values are outside the accepted range</li>
     *         <li>CryptoException.UNINITIALIZED_KEY if theKey instance is uninitialized.</li>
     *         <li>CryptoException.INVALID_INIT if this method is called for an online mode of encryption</li>
     */
    @Override
    public void init(Key theKey, byte theMode, byte[] nonceBuf, short nonceOff, short nonceLen, short adataLen, short messageLen, short tagSize) throws CryptoException {
        if( (theMode != MODE_DECRYPT) && (theMode != MODE_ENCRYPT)){
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        // Check if this method is called for an online mode of encryption
        if( (messageLen == 0) || (tagSize == 0) ){
            CryptoException.throwIt(CryptoException.INVALID_INIT);
        }

        // Supports only the 12 byte IV length, which is the value recommended by NIST Special Publication 800-38D 5.2.1.1 Input Data
        // https://docs.oracle.com/javacard/3.0.5/guide/supported-cryptography-classes.htm#JCUGC356
        if( nonceLen != 12 ){
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        selectCipherEngine(theKey);

        byte[] iv_nonce = JCSystem.makeTransientByteArray(nonceLen, JCSystem.CLEAR_ON_RESET);
        Util.arrayCopyNonAtomic(nonceBuf, nonceOff, iv_nonce, (short) 0, nonceLen);

        parameters = new AEADParameters((KeyParameter) ((SymmetricKeyImpl) theKey).getParameters(),tagSize * Byte.SIZE, iv_nonce );

        try{
            engine.init(theMode == MODE_ENCRYPT, parameters);
        }
        catch (Exception ex){
            ex.printStackTrace();
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        initMode = theMode;
        initMsgLen = messageLen;
        initAADLen = adataLen;
        totalMsgLen = 0;
        state = CipherState.Initialized;
    }

    /**
     * Continues a multi-part update of the Additional Associated Data (AAD) that will be verified by the authentication tag.
     * The data is not included with the ciphertext by this method.
     *
     * @param aadBuf the buffer containing the AAD data
     * @param aadOff the offset of the AAD data in the buffer
     * @param aadLen the length in bytes of the AAD data in the buffer
     * @throws CryptoException with the following reason codes:
     *     <li>ILLEGAL_USE if updating the AAD value is conflicting with the state of this cipher</li>
     *     <li>ILLEGAL_VALUE for CCM if the AAD size is different from the AAD size given in the initial block used as IV</li>
     */
    @Override
    public void updateAAD(byte[] aadBuf, short aadOff, short aadLen) throws CryptoException {
        if ( state == CipherState.Uninitialized ){
            CryptoException.throwIt(CryptoException.ILLEGAL_USE);
        }

        if( algorithm == ALG_AES_CCM){
           if( aadLen != initAADLen )
               CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        engine.processAADBytes(aadBuf,aadOff,aadLen);
    }

    /**
     * Generates encrypted/decrypted output from input data. This method is intended for multiple-part encryption/decryption operations.
     * This method requires temporary storage of intermediate results.
     * In addition, if the input data length is not block aligned (multiple of block size) then additional internal storage may be allocated at this time to store a partial input data block.
     * This may result in additional resource consumption and/or slow performance.
     *
     * This method should only be used if all the input data required for the cipher is not available in one byte array.
     * If all the input data required for the cipher is located in a single byte array, use of the doFinal() method to process all of the input data is recommended.
     * The doFinal() method must be invoked to complete processing of any remaining input data buffered by one or more calls to the update() method.
     * SensitiveResult class, if supported by the platform.
     *
     * @param inBuff the input buffer of data to be encrypted/decrypted
     * @param inOffset the offset into the input buffer at which to begin encryption/decryption
     * @param inLength the byte length to be encrypted/decrypted
     * @param outBuff the output buffer, may be the same as the input buffer
     * @param outOffset  the offset into the output buffer where the resulting ciphertext/plaintext begins
     * @return number of bytes output in outBuff
     * @throws CryptoException with the following reason codes:
     *        <li>CryptoException.INVALID_INIT if this Cipher object is not initialized.</li>
     *        <li>CryptoException.UNINITIALIZED_KEY if key not initialized.</li>
     *        <li>CryptoException.ILLEGAL_USE</li>
     *             <li>for CCM if AAD is not provided while it is indicated in the initial block used as IV</li>
     *             <li>for CCM if the payload exceeds the payload size given in the initial block used as IV</li>
     */
    @Override
    public short update(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset) throws CryptoException {
        if (state == CipherState.Uninitialized) {
            CryptoException.throwIt(CryptoException.INVALID_INIT);
        }

        int processBuffSize = engine.getUpdateOutputSize(inLength);

        byte[] processBuff = new byte[processBuffSize];
        short processedBytes = (short)engine.processBytes(inBuff, inOffset, inLength, processBuff, 0);
        Util.arrayCopyNonAtomic(processBuff, (short) 0,outBuff,outOffset, processedBytes);

        totalMsgLen += inLength;
        return processedBytes;
    }

    /**
     * Generates encrypted/decrypted output from all/last input data. This method must be invoked to complete a cipher operation.
     * This method processes any remaining input data buffered by one or more calls to the update() method as well as input data supplied in the inBuff parameter.
     * A call to this method also resets this Cipher object to the state it was in when previously initialized via a call to init().
     * That is, the object is reset and available to encrypt or decrypt (depending on the operation mode that was specified in the call to init()) more data.
     * In addition, note that the initial vector(IV) used in AES, DES and Korean SEED algorithms will be reset to 0.
     *
     * @param inBuff the input buffer of data to be encrypted/decrypted
     * @param inOffset the offset into the input buffer at which to begin encryption/decryption
     * @param inLength the byte length to be encrypted/decrypted
     * @param outBuff the output buffer, may be the same as the input buffer
     * @param outOffset the offset into the output buffer where the resulting output data begins
     * @return number of bytes output in outBuff
     * @throws CryptoException with the following reason codes:
     *         <li>INVALID_INIT if this Cipher object is not initialized.</li>
     *         <li>UNINITIALIZED_KEY if key not initialized.</li>
     *         <li>ILLEGAL_USE</li>
     *               <li>for CCM if all Additional Authenticated Data (AAD) was not provided</li>
     *               <li>for CCM if the total message size provided is not identical to the messageLen parameter given in the init method</li>
     */
    @Override
    public short doFinal(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset) throws CryptoException {
        if (state == CipherState.Uninitialized) {
            CryptoException.throwIt(CryptoException.INVALID_INIT);
        }

        if (algorithm == ALG_AES_CCM) {
            if( engine.getMac().length == 0 ){
                CryptoException.throwIt(CryptoException.ILLEGAL_USE);
            }

            totalMsgLen += inLength;
            if( totalMsgLen != initMsgLen){
                CryptoException.throwIt(CryptoException.ILLEGAL_USE);
            }
        }

        int processBuffSize = engine.getOutputSize(inLength);
        byte[] processBuff = new byte[processBuffSize];

        try {
            short processedBytes = (short) engine.processBytes(inBuff, inOffset, inLength, processBuff, 0);
            processedBytes += engine.doFinal(processBuff, processedBytes);
            // Work around to make this implementation similar to JavaCard (12/06/2023)
            // Change start
            if (initMode == MODE_ENCRYPT) {
                byte[] tag = engine.getMac();
                processedBytes -= tag.length;
            }
            // Change end
            Util.arrayCopyNonAtomic(processBuff, (short) 0,outBuff,outOffset, processedBytes);
            state = CipherState.Finalized;
            return processedBytes;

        } catch (Exception ex) {
            ex.printStackTrace();
            CryptoException.throwIt(CryptoException.ILLEGAL_USE);
        }

        return -1;

    }

    /**
     * Retrieves tagLen bytes from the calculated authentication tag. Depending on the algorithm, only certain tag lengths may be supported.
     * Note:
     * This method may only be called for MODE_ENCRYPT after doFinal has been called.
     * In addition to returning a short result, this method sets the result in an internal state which can be rechecked using assertion methods of the SensitiveResult class, if supported by the platform.
     * @param tagBuf the buffer that will contain the authentication tag
     * @param tagOff the offset of the authentication tag in the buffer
     * @param tagLen the length in bytes of the authentication tag in the buffer
     * @return the tag length, as given by tagLen (for convenience)
     * @throws CryptoException with the following reason codes
     *         <li>ILLEGAL_USE if doFinal has not been called</li>
     *         <li>ILLEGAL_VALUE if the tag length is not supported</li>
     */
    @Override
    public short retrieveTag(byte[] tagBuf, short tagOff, short tagLen) throws CryptoException {
        if( state != CipherState.Finalized ){
            CryptoException.throwIt(CryptoException.ILLEGAL_USE);
        }

        if( initMode != MODE_ENCRYPT){
            CryptoException.throwIt(CryptoException.ILLEGAL_USE);
        }

        if( !checkSupportTagLength(tagLen)) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        byte[] mac = engine.getMac();

        Util.arrayCopyNonAtomic(mac, (short) 0,tagBuf, tagOff, tagLen);

        return tagLen;
    }

    /**
     * Verifies the authentication tag using the number of bits set in requiredTagLen bits.
     * Depending on the algorithm, only certain tag lengths may be supported. For all algorithms the tag length must be a multiple of 8 bits.
     * Note:
     *     <li>This method may only be called for MODE_DECRYPT after doFinal has been called.</li>
     * In addition to returning a boolean result, this method sets the result in an internal state which can be rechecked using assertion methods of the SensitiveResult class, if supported by the platform.
     * @param receivedTagBuf the buffer that will contain the received authentication tag
     * @param receivedTagOff the offset of the received authentication tag in the buffer
     * @param receivedTagLen the length in bytes of the received authentication tag in the buffer
     * @param requiredTagLen the required length in bytes of the received authentication tag, usually a constant value
     * @return Tag verification result
     * @throws CryptoException with the following reason codes:
     *         <li>ILLEGAL_USE if doFinal has not been called</li>
     *         <li>ILLEGAL_VALUE if the tag length is not supported</li>
     */
    @Override
    public boolean verifyTag(byte[] receivedTagBuf, short receivedTagOff, short receivedTagLen, short requiredTagLen) throws CryptoException {
        if( state != CipherState.Finalized ){
            CryptoException.throwIt(CryptoException.ILLEGAL_USE);
        }

        if( initMode != MODE_DECRYPT){
            CryptoException.throwIt(CryptoException.ILLEGAL_USE);
        }
        
        if( !checkSupportTagLength(requiredTagLen)){
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        // doFinal already does the validation of tag.
        // Work around to make this implementation similar to JavaCard (12/06/2023)
        // Change start
        // byte[] mac = engine.getMac();
        // return Arrays.areEqual(mac,0,requiredTagLen,receivedTagBuf,receivedTagOff,receivedTagOff + receivedTagLen);
        // Change end
        return true;
    }

    private void selectCipherEngine(Key theKey) {
        if (theKey == null) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        if (!theKey.isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        if (!(theKey instanceof SymmetricKeyImpl)) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        SymmetricKeyImpl key = (SymmetricKeyImpl) theKey;

        switch (algorithm) {

            case ALG_AES_CCM:
                try{
                    engine = new CCMBlockCipher(key.getCipher());
                }
                catch (Exception ex){
                    ex.printStackTrace();
                    CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
                }
                break;

            case ALG_AES_GCM:
                try{
                    engine = new GCMBlockCipher(key.getCipher());
                }
                catch (Exception ex){
                    ex.printStackTrace();
                    CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
                }
                break;

            default:
                CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
                break;
        }
    }

    private boolean checkSupportTagLength(short tagLen){
        short tagLenInBits = (short) (tagLen * Byte.SIZE);

        if( tagLenInBits == 128) return true;
        if( tagLenInBits == 120) return true;
        if( tagLenInBits == 112) return true;
        if( tagLenInBits == 104) return true;
        if( tagLenInBits == 96) return true;
        if( tagLenInBits == 64) return true;
        if( tagLenInBits == 32) return true;

        return false;
    }
}
