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

import org.bouncycastle.crypto.Digest;

public class BouncyCastlePrecomputedOrDigestProxy implements Digest {
    
    private final Digest parentDigest;
    private byte[] precomputedDigestValue = null;

    public BouncyCastlePrecomputedOrDigestProxy(Digest parentDigest) {
        this.parentDigest = parentDigest;
    }

    @Override
    public String getAlgorithmName() {
        return parentDigest.getAlgorithmName();
    }

    @Override
    public int getDigestSize() {
        return parentDigest.getDigestSize();
    }

    @Override
    public void update(byte in) {
        if (precomputedDigestValue == null) {
            parentDigest.update(in);
        }
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        if (precomputedDigestValue == null) {
            parentDigest.update(in, inOff, len);
        }
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        if (precomputedDigestValue == null) {
            return parentDigest.doFinal(out, outOff);
        }
        System.arraycopy(precomputedDigestValue, 0, out, outOff, precomputedDigestValue.length);
        int len = precomputedDigestValue.length;
        precomputedDigestValue = null;
        return len;
    }

    public void setPrecomputedValue(byte[] in, int inOff, int inLength) {
        int digestSize = getDigestSize();
        if (inLength!= digestSize) {
            throw new IllegalArgumentException();
        }
        precomputedDigestValue = new byte[digestSize];
        System.arraycopy(in, inOff, precomputedDigestValue, 0, digestSize);
    }

    @Override
    public void reset() {
        precomputedDigestValue = null;
        parentDigest.reset();
    }
}
