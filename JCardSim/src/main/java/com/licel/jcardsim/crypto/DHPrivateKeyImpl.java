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
import javacard.security.DHPrivateKey;
import javacard.security.KeyBuilder;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.params.DHKeyParameters;
import org.bouncycastle.crypto.params.DHParameters;
import org.bouncycastle.crypto.params.DHPrivateKeyParameters;

public final class DHPrivateKeyImpl extends DHKeyImpl implements DHPrivateKey {
        
    protected ByteContainer x = new ByteContainer();
    
    public DHPrivateKeyImpl(short size) {
        this.size = size;
        type = KeyBuilder.TYPE_DH_PRIVATE;
    }
    
    public DHPrivateKeyImpl(DHKeyParameters params) {
        setParameters(params);
    }
    
    @Override
    public void setParameters(CipherParameters params) {
        super.setParameters(((DHPrivateKeyParameters) params).getParameters());
        x.setBigInteger(((DHPrivateKeyParameters) params).getX());
    }
    
    public void setX(byte[] bytes, short offset, short length) throws CryptoException {
        x.setBytes(bytes, offset, length);
    }

    public short getX(byte[] bytes, short offset) {
        return x.getBytes(bytes, offset);
    }
    
    @Override
    public void clearKey() {
        super.clearKey();
        x.clear();
    }

    @Override
    public boolean isInitialized() {
        return (super.isInitialized() && x.isInitialized());
    }
    
    @Override
    public CipherParameters getParameters() {
        if (!isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        return new DHPrivateKeyParameters(x.getBigInteger(), (DHParameters) super.getParameters());
    }
}
