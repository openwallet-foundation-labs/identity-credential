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
import javacard.security.DSAPublicKey;
import javacard.security.KeyBuilder;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.params.DSAKeyParameters;
import org.bouncycastle.crypto.params.DSAPublicKeyParameters;

/**
 * Implementation <code>DSAPublicKey</code> based
 * on BouncyCastle CryptoAPI.
 * @see DSAPublicKey
 * @see DSAPublicKeyParameters
 */
public class DSAPublicKeyImpl extends DSAKeyImpl implements DSAPublicKey{

    protected ByteContainer y = new ByteContainer();

    /**
     * Construct not-initialized dsa public key
     * @param keySize key size it bits
     * @see KeyBuilder
     */
    public DSAPublicKeyImpl(short keySize) {
        super(KeyBuilder.TYPE_DSA_PUBLIC, keySize);
    }

    /**
     * Construct and initialize ecc key with DSAPublicKeyParameters.
     * Use in KeyPairImpl
     * @see javacard.security.KeyPair
     * @see DSAPublicKeyParameters
     * @param params key params from BouncyCastle API
     */
    public DSAPublicKeyImpl(DSAPublicKeyParameters params) {
        super(params);
        setParameters(params);
    }

    public void setParameters(CipherParameters params) {
        super.setParameters(params);
        y.setBigInteger(((DSAPublicKeyParameters) params).getY());
    }
    
    public void setY(byte[] buffer, short offset, short length) throws CryptoException {
        y.setBytes(buffer, offset, length);
    }

    public short getY(byte[] buffer, short offset) {
        return y.getBytes(buffer, offset);
    }

    public boolean isInitialized() {
        return (super.isInitialized() && y.isInitialized());
    }

    public void clearKey() {
        super.clearKey();
        y.clear();
    }

    public CipherParameters getParameters() {
        if (!isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        return new DSAPublicKeyParameters(y.getBigInteger(), ((DSAKeyParameters) super.getParameters()).getParameters());
    }   
}
