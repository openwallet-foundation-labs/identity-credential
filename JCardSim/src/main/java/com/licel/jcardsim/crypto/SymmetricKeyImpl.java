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
import javacard.security.*;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.DESEngine;
import org.bouncycastle.crypto.engines.DESedeEngine;
import org.bouncycastle.crypto.engines.SEEDEngine;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * Implementation of secret key.
 * @see DESKey
 * @see AESKey
 * @see HMACKey
 * @see KoreanSEEDKey
 */
public class SymmetricKeyImpl extends KeyImpl implements DESKey, AESKey, HMACKey, KoreanSEEDKey {

    protected ByteContainer key;

    /**
     * Create new instance of <code>SymmetricKeyImpl</code>
     * @param keyType keyType interface
     * @param keySize keySize in bits
     * @see KeyBuilder
     */
    public SymmetricKeyImpl(byte keyType, short keySize) {
        this.size = keySize;
        this.type = keyType;
        switch (keyType) {
            case KeyBuilder.TYPE_DES_TRANSIENT_DESELECT:
            case KeyBuilder.TYPE_AES_TRANSIENT_DESELECT:
            case KeyBuilder.TYPE_HMAC_TRANSIENT_DESELECT:
            case KeyBuilder.TYPE_KOREAN_SEED_TRANSIENT_DESELECT:
                key = new ByteContainer(JCSystem.MEMORY_TYPE_TRANSIENT_DESELECT);
                break;
            case KeyBuilder.TYPE_DES_TRANSIENT_RESET:
            case KeyBuilder.TYPE_AES_TRANSIENT_RESET:
            case KeyBuilder.TYPE_HMAC_TRANSIENT_RESET:
            case KeyBuilder.TYPE_KOREAN_SEED_TRANSIENT_RESET:
                key = new ByteContainer(JCSystem.MEMORY_TYPE_TRANSIENT_RESET);
                break;
            case KeyBuilder.TYPE_DES:
            case KeyBuilder.TYPE_AES:
            case KeyBuilder.TYPE_HMAC:
            case KeyBuilder.TYPE_KOREAN_SEED:
                key = new ByteContainer(JCSystem.MEMORY_TYPE_PERSISTENT);
                break;
        }
    }

    /**
     * Clears the key and sets its initialized state to false.
     */
    public void clearKey() {
        key.clear();
    }

    /**
     * Sets the <code>Key</code> data.
     */
    public void setKey(byte[] keyData, short kOff) throws CryptoException, NullPointerException, ArrayIndexOutOfBoundsException {
        key.setBytes(keyData, kOff, (short) (size / 8));
    }

    /**
     * Sets the <code>Key</code> data.
     */
    public void setKey(byte[] keyData, short kOff, short kLen) throws CryptoException, NullPointerException, ArrayIndexOutOfBoundsException {
        key.setBytes(keyData, kOff, kLen);
    }
    /**
     * Returns the <code>Key</code> data in plain text.
     */
    public byte getKey(byte[] keyData, short kOff) {
        return (byte) key.getBytes(keyData, kOff);
    }

     public void setParameters(CipherParameters params){
       key.setBytes(((KeyParameter)params).getKey());
    }
    
    /**
     * Return the BouncyCastle <code>KeyParameter</code> of the key
     * @return parameter of the key
     * @throws CryptoException if key not initialized
     * @see KeyParameter
     */
    public CipherParameters getParameters() throws CryptoException {
        if (!key.isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        return new KeyParameter(key.getBytes(JCSystem.CLEAR_ON_RESET));
    }

    /**
     * Return the BouncyCastle <code>BlockCipher</code> for using with this key
     * @return <code>BlockCipher</code> for this key, or null for HMACKey
     * @throws CryptoException if key not initialized
     * @see BlockCipher
     */
    public BlockCipher getCipher() throws CryptoException {
        if (!key.isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        BlockCipher cipher = null;
        switch (type) {
            case KeyBuilder.TYPE_DES:
            case KeyBuilder.TYPE_DES_TRANSIENT_DESELECT:
            case KeyBuilder.TYPE_DES_TRANSIENT_RESET:
                if (size == KeyBuilder.LENGTH_DES) {
                    cipher = new DESEngine();
                }
                if (size == KeyBuilder.LENGTH_DES3_2KEY || size == KeyBuilder.LENGTH_DES3_3KEY) {
                    cipher = new DESedeEngine();
                }
                break;
            case KeyBuilder.TYPE_AES:
            case KeyBuilder.TYPE_AES_TRANSIENT_DESELECT:
            case KeyBuilder.TYPE_AES_TRANSIENT_RESET:
                cipher = new AESEngine();
                break;

            case KeyBuilder.TYPE_KOREAN_SEED:
            case KeyBuilder.TYPE_KOREAN_SEED_TRANSIENT_DESELECT:
            case KeyBuilder.TYPE_KOREAN_SEED_TRANSIENT_RESET:
                cipher = new SEEDEngine();
                break;
        }
        return cipher;
    }

    public boolean isInitialized() {
        return key.isInitialized();
    }

    public KeyGenerationParameters getKeyGenerationParameters(SecureRandom rnd) {
        return null;
    }

}
