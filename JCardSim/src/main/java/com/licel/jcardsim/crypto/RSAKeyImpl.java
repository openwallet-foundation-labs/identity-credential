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

import java.math.BigInteger;
import java.security.SecureRandom;
import javacard.security.CryptoException;
import javacard.security.KeyBuilder;
import javacard.security.RSAPrivateKey;
import javacard.security.RSAPublicKey;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;

/**
 * Implementation
 * <code>RSAPublic/RSAPrivate</code> based on BouncyCastle CryptoAPI.
 *
 * @see RSAPrivateKey
 * @see RSAPublicKey
 * @see RSAKeyParameters
 */
public class RSAKeyImpl extends KeyImpl implements RSAPrivateKey, RSAPublicKey {

    protected ByteContainer exponent = new ByteContainer();
    protected ByteContainer modulus = new ByteContainer();
    protected boolean isPrivate;

    /**
     * Construct not-initialized rsa key
     *
     * @param isPrivate true if private key
     * @param size key size it bits (modulus size)
     * @see KeyBuilder
     */
    public RSAKeyImpl(boolean isPrivate, short size) {
        this.isPrivate = isPrivate;
        this.size = size;
        type = isPrivate ? KeyBuilder.TYPE_RSA_PRIVATE : KeyBuilder.TYPE_RSA_PUBLIC;
    }

    /**
     * Construct and initialize rsa key with RSAKeyParameters. Use in
     * KeyPairImpl
     *
     * @see javacard.security.KeyPair
     * @see RSAKeyParameters
     * @param params key params from BouncyCastle API
     */
    public RSAKeyImpl(RSAKeyParameters params) {
        this(params.isPrivate(), (short) params.getModulus().bitLength());
        setParameters(params);
    }
    
    public void setParameters(CipherParameters params){
        modulus.setBigInteger(((RSAKeyParameters)params).getModulus());
        exponent.setBigInteger(((RSAKeyParameters)params).getExponent());
    }

    public short getExponent(byte[] buffer, short offset) {
        return exponent.getBytes(buffer, offset);
    }

    public short getModulus(byte[] buffer, short offset) {
        return modulus.getBytes(buffer, offset);
    }

    public void setExponent(byte[] buffer, short offset, short length) throws CryptoException {
        exponent.setBytes(buffer, offset, length);
    }

    public void setModulus(byte[] buffer, short offset, short length) throws CryptoException {
        modulus.setBytes(buffer, offset, length);
    }

    public void clearKey() {
        exponent.clear();
        modulus.clear();
    }

    public boolean isInitialized() {
        return (exponent.isInitialized() && modulus.isInitialized());
    }

    /**
     * Get
     * <code>RSAKeyParameters</code>
     *
     * @return parameters for use with BouncyCastle API
     * @see RSAKeyParameters
     */
    public CipherParameters getParameters() {
        if (!isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        return new RSAKeyParameters(isPrivate, modulus.getBigInteger(), exponent.getBigInteger());
    }

    /**
     * Get
     * <code>RSAKeyGenerationParameters</code>
     *
     * @param rnd Secure Random Generator
     * @return parameters for use with BouncyCastle API
     */
    public KeyGenerationParameters getKeyGenerationParameters(SecureRandom rnd) {
        if (!isPrivate && exponent.isInitialized()) {
            return new RSAKeyGenerationParameters(exponent.getBigInteger(),
                    rnd, size, 80);
        }
        return getDefaultKeyGenerationParameters(size, rnd);
    }

    /**
     * Get default
     * <code>RSAKeyGenerationParameters</code>
     *
     * @param keySize key size in bits
     * @param rnd Secure Random Generator
     * @return parameters for use with BouncyCastle API
     */
    static KeyGenerationParameters getDefaultKeyGenerationParameters(short keySize, SecureRandom rnd) {
        return new RSAKeyGenerationParameters(new BigInteger("10001", 16),
                rnd, keySize, 80);
    }
}
