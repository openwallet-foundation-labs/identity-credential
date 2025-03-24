/*
 * Copyright 2020 Licel Corporation.
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
import java.security.SecureRandomSpi;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.prng.DigestRandomGenerator;
import org.bouncycastle.crypto.prng.RandomGenerator;

class SecureRandomNullProvider extends SecureRandom {

    public SecureRandomNullProvider() {
        super(new SecureRandomSpi() {
            RandomGenerator engine = new DigestRandomGenerator(new SHA1Digest());
            @Override
            protected void engineSetSeed(byte[] arg) {
                engine.addSeedMaterial(arg);
            }
            @Override
            protected void engineNextBytes(byte[] arg) {
                engine.nextBytes(arg);
            }
            @Override
            protected byte[] engineGenerateSeed(int len) {
                byte[] buf = new byte[len];
                engine.nextBytes(buf);
                return buf;
            }
        }, null);
    }
}
