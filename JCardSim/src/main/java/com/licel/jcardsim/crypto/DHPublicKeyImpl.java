/*
 * Copyright 2018 Licel Corporation.
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
import javacard.security.DHPublicKey;
import javacard.security.KeyBuilder;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.params.DHKeyParameters;
import org.bouncycastle.crypto.params.DHParameters;
import org.bouncycastle.crypto.params.DHPublicKeyParameters;

public final class DHPublicKeyImpl extends DHKeyImpl implements DHPublicKey {
    
    protected ByteContainer y = new ByteContainer();
    
    public DHPublicKeyImpl(short size) {
        this.size = size;
        type = KeyBuilder.TYPE_DH_PUBLIC;
    }
    
    public DHPublicKeyImpl(DHKeyParameters params) {
        setParameters(params);
    }
    
    @Override
    public void setParameters(CipherParameters params) {
        super.setParameters(((DHPublicKeyParameters) params).getParameters());
        y.setBigInteger(((DHPublicKeyParameters) params).getY());
    }
    
    public void setY(byte[] bytes, short offset, short length) throws CryptoException {
        y.setBytes(bytes, offset, length);
    }

    public short getY(byte[] bytes, short offset) {
        return y.getBytes(bytes, offset);
    }
    
    @Override
    public void clearKey() {
        super.clearKey();
        y.clear();
    }

    @Override
    public boolean isInitialized() {
        return (super.isInitialized() && y.isInitialized());
    }
    
    @Override
    public CipherParameters getParameters() {
        if (!isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        return new DHPublicKeyParameters(y.getBigInteger(), (DHParameters) super.getParameters());
    }
}
