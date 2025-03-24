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

import javacard.security.CryptoException;
import javacard.security.DSAPrivateKey;
import javacard.security.KeyBuilder;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.params.DSAKeyParameters;
import org.bouncycastle.crypto.params.DSAPrivateKeyParameters;

/**
 * Implementation <code>DSAPrivateKey</code> based
 * on BouncyCastle CryptoAPI.
 * @see DSAPrivateKey
 * @see DSAPrivateKeyParameters
 */
public class DSAPrivateKeyImpl extends DSAKeyImpl implements DSAPrivateKey{

    protected ByteContainer x = new ByteContainer();

    /**
     * Construct not-initialized dsa private key
     * @param keySize key size it bits
     * @see KeyBuilder
     */
    public DSAPrivateKeyImpl(short keySize) {
        super(KeyBuilder.TYPE_DSA_PRIVATE, keySize);
    }

    /**
     * Construct and initialize dsa key with DSAPrivateKeyParameters.
     * Use in KeyPairImpl
     * @see javacard.security.KeyPair
     * @see DSAPrivateKeyParameters
     * @param params key params from BouncyCastle API
     */
    public DSAPrivateKeyImpl(DSAPrivateKeyParameters params) {
        super(params);
        setParameters(params);
    }

    public void setParameters(CipherParameters params) {
        super.setParameters(params);
        x.setBigInteger(((DSAPrivateKeyParameters) params).getX());
    }
    
    public void setX(byte[] buffer, short offset, short length) throws CryptoException {
        x.setBytes(buffer, offset, length);
    }

    public short getX(byte[] buffer, short offset) {
        return x.getBytes(buffer, offset);
    }

    public boolean isInitialized() {
        return (super.isInitialized() && x.isInitialized());
    }

    public void clearKey() {
        super.clearKey();
        x.clear();
    }

    public CipherParameters getParameters() {
        if (!isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        return new DSAPrivateKeyParameters(x.getBigInteger(), ((DSAKeyParameters) super.getParameters()).getParameters());
    }
}
