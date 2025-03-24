/*
 * Copyright 2011 Licel LLC.
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

import java.security.SecureRandom;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.CryptoException;
import javacard.security.Key;
import javacardx.crypto.Cipher;
import org.bouncycastle.crypto.AsymmetricBlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.encodings.OAEPEncoding;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.paddings.BlockCipherPadding;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.prng.DigestRandomGenerator;
import org.bouncycastle.crypto.prng.RandomGenerator;

/*
 * Implementation <code>Cipher</code> with asymmetric keys based
 * on BouncyCastle CryptoAPI.
 * @see Cipher
 */
public class AsymmetricCipherImpl extends Cipher {

    byte algorithm;
    AsymmetricBlockCipher engine;
    BlockCipherPadding paddingEngine;
    boolean isInitialized;
    byte[] buffer;
    short bufferPos;

    byte initMode;

    public AsymmetricCipherImpl(byte algorithm) {
        this.algorithm = algorithm;
        switch (algorithm) {
            case ALG_RSA_NOPAD:
                engine = new RSAEngine();
                paddingEngine = null;
                break;
            case ALG_RSA_PKCS1:
                engine = new PKCS1Encoding(new RSAEngine());
                paddingEngine = null;
                break;
            case ALG_RSA_PKCS1_OAEP:
                engine = new OAEPEncoding(new RSAEngine());
                paddingEngine = null;
                break;
            default:
                CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
                break;
        }
    }

    public void init(Key theKey, byte theMode) throws CryptoException {
        if (theKey == null) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        if (!theKey.isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        if (!(theKey instanceof KeyWithParameters)) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        ParametersWithRandom params = new ParametersWithRandom(((KeyWithParameters) theKey).getParameters(), new SecureRandomNullProvider());
        engine.init(theMode == MODE_ENCRYPT, params);
        buffer = JCSystem.makeTransientByteArray((short) engine.getInputBlockSize(), JCSystem.CLEAR_ON_DESELECT);
        initMode = theMode;
        bufferPos = 0;
        isInitialized = true;
    }

    public void init(Key theKey, byte theMode, byte[] bArray, short bOff, short bLen) throws CryptoException {
        CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
    }

    public byte getAlgorithm() {
        return algorithm;
    }

    public short doFinal(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset) throws CryptoException {
        if (!isInitialized) {
            CryptoException.throwIt(CryptoException.INVALID_INIT);
        }

        if( initMode == MODE_ENCRYPT ) {
            if ((outBuff.length - outOffset) < engine.getOutputBlockSize()) {
                CryptoException.throwIt(CryptoException.ILLEGAL_USE);
            }
        }
        else{
            if ((inBuff.length - inOffset) < engine.getInputBlockSize()) {
                CryptoException.throwIt(CryptoException.ILLEGAL_USE);
            }
        }
        update(inBuff, inOffset, inLength, outBuff, outOffset);
        if (algorithm == ALG_RSA_NOPAD) {
            if ((bufferPos < engine.getInputBlockSize()) && (paddingEngine == null)) {
                CryptoException.throwIt(CryptoException.ILLEGAL_USE);
            } else if (bufferPos < engine.getInputBlockSize()) {
                paddingEngine.addPadding(buffer, bufferPos);
            }
        }
        try {
            byte[] data = engine.processBlock(buffer, (short) 0, bufferPos);
            Util.arrayCopyNonAtomic(data, (short) 0, outBuff, outOffset, (short) data.length);
            bufferPos = 0;
            return (short) data.length;
        } catch (InvalidCipherTextException ex) {
            CryptoException.throwIt(CryptoException.ILLEGAL_USE);
        }
        return -1;
    }

    public short update(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset) throws CryptoException {
        if (!isInitialized) {
            CryptoException.throwIt(CryptoException.INVALID_INIT);
        }
        if (inLength > (buffer.length - bufferPos)) {
            CryptoException.throwIt(CryptoException.ILLEGAL_USE);
        }
        bufferPos = (short) (bufferPos + Util.arrayCopyNonAtomic(inBuff, inOffset, buffer, bufferPos, inLength));
        return bufferPos;
    }
    public byte getPaddingAlgorithm() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    public byte getCipherAlgorithm() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
