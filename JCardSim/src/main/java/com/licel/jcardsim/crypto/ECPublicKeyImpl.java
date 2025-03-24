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

import javacard.framework.JCSystem;
import javacard.security.CryptoException;
import javacard.security.ECPublicKey;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;

/**
 * Implementation <code>ECPublicKey</code> based
 * on BouncyCastle CryptoAPI.
 * @see ECPublicKey
 * @see ECPublicKeyParameters
 */
public class ECPublicKeyImpl extends ECKeyImpl implements ECPublicKey {

    protected ByteContainer w = new ByteContainer();

    /**
     * Construct not-initialized ecc public key
     * @param keyType key type
     * @param keySize key size it bits
     * @see javacard.security.KeyBuilder
     */
    public ECPublicKeyImpl(byte keyType, short keySize) {
        super(keyType, keySize);
    }

    /**
     * Construct and initialize ecc key with ECPublicKeyParameters.
     * Use in KeyPairImpl
     * @see javacard.security.KeyPair
     * @see ECPublicKeyParameters
     * @param params key params from BouncyCastle API
     */
    public ECPublicKeyImpl(ECPublicKeyParameters params) {
        super(params);
        setParameters(params);
    }
 
     public void setParameters(CipherParameters params){
        w.setBytes(((ECPublicKeyParameters)params).getQ().getEncoded(false));
    }
    

    public void setW(byte[] buffer, short offset, short length) throws CryptoException {
        w.setBytes(buffer, offset, length);
    }

    public short getW(byte[] buffer, short offset) throws CryptoException {
        return w.getBytes(buffer, offset);
    }

    public boolean isInitialized() {
        return (isDomainParametersInitialized() && w.isInitialized());
    }

    public void clearKey() {
        super.clearKey();
        w.clear();
    }

    /**
     * Get <code>ECPublicKeyParameters</code>
     * @return parameters for use with BouncyCastle API
     * @see ECPublicKeyParameters
     */
    public CipherParameters getParameters() {
        if (!isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        ECDomainParameters dp = getDomainParameters();
        return new ECPublicKeyParameters(dp.getCurve().decodePoint(w.getBytes(JCSystem.CLEAR_ON_RESET)), dp);
    }
}
