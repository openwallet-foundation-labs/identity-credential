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

import javacard.framework.Util;
import javacard.security.CryptoException;
import javacard.security.Key;
import javacard.security.Signature;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA384Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.engines.DESEngine;
import org.bouncycastle.crypto.macs.CBCBlockCipherMac;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.macs.ISO9797Alg3Mac;
import org.bouncycastle.crypto.paddings.ISO7816d4Padding;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.ZeroBytePadding;
import org.bouncycastle.crypto.params.ParametersWithIV;

/**
 * Implementation
 * <code>Signature</code> with symmetric keys based
 * on BouncyCastle CryptoAPI.
 * @see Signature
 */
public class SymmetricSignatureImpl extends Signature {
    
    Mac engine;
    byte algorithm;
    boolean isInitialized;
    
    public SymmetricSignatureImpl(byte algorithm) {
        this.algorithm = algorithm;
    }
    
    public void init(Key theKey, byte theMode) throws CryptoException {
        init(theKey, theMode, null, (short) 0, (short) 0);
    }
    
    public void init(Key theKey, byte theMode, byte[] bArray, short bOff, short bLen) throws CryptoException {
        if (theKey == null) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        if (!theKey.isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        if (!(theKey instanceof SymmetricKeyImpl)) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        CipherParameters cipherParams = null;
        BlockCipher cipher = ((SymmetricKeyImpl) theKey).getCipher();
        if (bArray == null) {
            cipherParams = ((SymmetricKeyImpl) theKey).getParameters();
        } else {
            if (bLen != cipher.getBlockSize()) {
                CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
            }
            cipherParams = new ParametersWithIV(((SymmetricKeyImpl) theKey).getParameters(), bArray, bOff, bLen);
        }
        switch (algorithm) {
            case ALG_DES_MAC4_NOPAD:
                engine = new CBCBlockCipherMac(cipher, 32, null);
                break;
            case ALG_DES_MAC8_NOPAD:
                engine = new CBCBlockCipherMac(cipher, 64, null);
                break;
            case ALG_DES_MAC4_ISO9797_M1:
                engine = new CBCBlockCipherMac(cipher, 32, new ZeroBytePadding());
                break;
            case ALG_DES_MAC8_ISO9797_M1:
                engine = new CBCBlockCipherMac(cipher, 64, new ZeroBytePadding());
                break;
            case ALG_DES_MAC4_ISO9797_M2:
                engine = new CBCBlockCipherMac(cipher, 32, new ISO7816d4Padding());
                break;
            case ALG_DES_MAC8_ISO9797_M2:
                engine = new CBCBlockCipherMac(cipher, 64, new ISO7816d4Padding());
                break;
            case ALG_DES_MAC8_ISO9797_1_M2_ALG3:
                engine = new ISO9797Alg3Mac(new DESEngine(), 64, new ISO7816d4Padding());
                break;
            case ALG_DES_MAC4_PKCS5:
                engine = new CBCBlockCipherMac(cipher, 32, new PKCS7Padding());
                break;
            case ALG_DES_MAC8_PKCS5:
                engine = new CBCBlockCipherMac(cipher, 64, new PKCS7Padding());
                break;
            case ALG_AES_MAC_128_NOPAD:
                engine = new CBCBlockCipherMac(cipher, 128, null);
                break;
            case ALG_AES_CMAC_128:
                engine = new CMac(cipher, 128);
                break;
            case ALG_HMAC_SHA1:
                engine = new HMac(new SHA1Digest());
                break;
            case ALG_HMAC_SHA_256:
                engine = new HMac(new SHA256Digest());
                break;
            case ALG_HMAC_SHA_384:
                engine = new HMac(new SHA384Digest());
                break;
            case ALG_HMAC_SHA_512:
                engine = new HMac(new SHA512Digest());
                break;
            case ALG_HMAC_MD5:
                engine = new HMac(new MD5Digest());
                break;
            case ALG_HMAC_RIPEMD160:
                engine = new HMac(new RIPEMD160Digest());
                break;
            default:
                CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
                break;
        }
        engine.init(cipherParams);
        isInitialized = true;
    }
    
    public short getLength() throws CryptoException {
        if (!isInitialized) {
            CryptoException.throwIt(CryptoException.INVALID_INIT);
        }
        return (short) engine.getMacSize();
    }
    
    public byte getAlgorithm() {
        return algorithm;
    }
    
    public void update(byte[] inBuff, short inOffset, short inLength) throws CryptoException {
        if (!isInitialized) {
            CryptoException.throwIt(CryptoException.INVALID_INIT);
        }
        engine.update(inBuff, inOffset, inLength);
    }
    
    public short sign(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset) throws CryptoException {
        if (!isInitialized) {
            CryptoException.throwIt(CryptoException.INVALID_INIT);
        }
        if ((algorithm == ALG_DES_MAC8_NOPAD || algorithm == ALG_DES_MAC4_NOPAD)
            && ((inLength % 8) != 0)) {
            CryptoException.throwIt(CryptoException.ILLEGAL_USE);
        }
        engine.update(inBuff, inOffset, inLength);
        short processedBytes = (short) engine.doFinal(sigBuff, sigOffset);
        engine.reset();
        return processedBytes;
    }
    
    public boolean verify(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset, short sigLength) throws CryptoException {
        if (!isInitialized) {
            CryptoException.throwIt(CryptoException.INVALID_INIT);
        }
        if ((algorithm == ALG_DES_MAC8_NOPAD || algorithm == ALG_DES_MAC4_NOPAD)
            && ((inLength % 8) != 0)) {
            CryptoException.throwIt(CryptoException.ILLEGAL_USE);
        }
        engine.update(inBuff, inOffset, inLength);
        byte[] sig = new byte[getLength()];
        engine.doFinal(sig, (short) 0);
        engine.reset();
        return Util.arrayCompare(sig, (short) 0, sigBuff, sigOffset, (short) sig.length) == 0;
    }

    public void setInitialDigest(byte[] bytes, short s, short s1, byte[] bytes1, short s2, short s3) throws CryptoException {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

    public short signPreComputedHash(byte[] bytes, short s, short s1, byte[] bytes1, short s2) throws CryptoException {
        throw new UnsupportedOperationException("Not supported yet."); 
    }
    public boolean verifyPreComputedHash(byte[] bytes, short s, short s1, byte[] bytes1, short s2, short s3) throws CryptoException {
        throw new UnsupportedOperationException("Not supported yet."); 
    }
    public byte getPaddingAlgorithm() {
        throw new UnsupportedOperationException("Not supported yet."); 
    }
    public byte getMessageDigestAlgorithm() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public byte getCipherAlgorithm() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}