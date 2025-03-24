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

import java.math.BigInteger;
import java.security.SecureRandom;
import javacard.security.CryptoException;
import javacard.security.DHKey;
import javacard.security.KeyBuilder;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.params.DHKeyGenerationParameters;
import org.bouncycastle.crypto.params.DHKeyParameters;
import org.bouncycastle.crypto.params.DHParameters;
import org.bouncycastle.util.encoders.Hex;

public abstract class DHKeyImpl extends KeyImpl implements DHKey {
    
    public static final short LENGTH_DH_1536 = 1536;
    
    protected ByteContainer p = new ByteContainer();
    protected ByteContainer q = new ByteContainer();
    protected ByteContainer g = new ByteContainer();
        
    private static final String rfc2409_1024_p = "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
    + "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
    + "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381"
    + "FFFFFFFFFFFFFFFF";
    private static final String rfc2409_1024_g = "02";
    public static final DHParameters rfc2409_1024 = fromPG(rfc2409_1024_p, rfc2409_1024_g);
        
    private static final String rfc3526_1536_p = "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
    + "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
    + "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D"
    + "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" + "83655D23DCA3AD961C62F356208552BB9ED529077096966D"
    + "670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFF";
    private static final String rfc3526_1536_g = "02";
    public static final DHParameters rfc3526_1536 = fromPG(rfc3526_1536_p, rfc3526_1536_g);
    
    private static final String rfc3526_2048_p = "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
    + "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
    + "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D"
    + "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" + "83655D23DCA3AD961C62F356208552BB9ED529077096966D"
    + "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9"
    + "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" + "15728E5A8AACAA68FFFFFFFFFFFFFFFF";
    private static final String rfc3526_2048_g = "02";
    public static final DHParameters rfc3526_2048 = fromPG(rfc3526_2048_p, rfc3526_2048_g);

    public void setParameters(CipherParameters params) {
        DHParameters dhParam = (DHParameters) params;
        g.setBigInteger(dhParam.getG());
        p.setBigInteger(dhParam.getP());
        if(dhParam.getQ()!=null){
            q.setBigInteger(dhParam.getQ());
        }
    }
    
    public CipherParameters getParameters() {
        if (!isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        if(q.isInitialized())
            return new DHParameters(p.getBigInteger(), g.getBigInteger(), q.getBigInteger());
        return new DHParameters(p.getBigInteger(), g.getBigInteger());
    }
        
    public void setP(byte[] bytes, short offset, short length) throws CryptoException {
        p.setBytes(bytes, offset, length);
    }

    public void setQ(byte[] bytes, short offset, short length) throws CryptoException {
        q.setBytes(bytes, offset, length);
    }

    public void setG(byte[] bytes, short offset, short length) throws CryptoException {
        g.setBytes(bytes, offset, length);
    }

    public short getP(byte[] bytes, short offset) {
        return p.getBytes(bytes, offset);
    }

    public short getQ(byte[] bytes, short offset) {
        return q.getBytes(bytes, offset);
    }

    public short getG(byte[] bytes, short offset) {
        return g.getBytes(bytes, offset);
    }

    public void clearKey() {
        p.clear();
        q.clear();
        g.clear();
    }

    public boolean isInitialized() {
        return (p.isInitialized() && g.isInitialized());
    }
    
    public KeyGenerationParameters getKeyGenerationParameters(SecureRandom rnd) {
        if (p.isInitialized() && g.isInitialized()) {
            if(q.isInitialized())
                return new DHKeyGenerationParameters(rnd, new DHParameters(p.getBigInteger(), g.getBigInteger(), q.getBigInteger()));
            else
                return new DHKeyGenerationParameters(rnd, new DHParameters(p.getBigInteger(), g.getBigInteger()));
        }
        return getDefaultKeyGenerationParameters(size, rnd);
    }
        
    static KeyGenerationParameters getDefaultKeyGenerationParameters(short keySize, SecureRandom rnd) {
        switch(keySize) {
            case KeyBuilder.LENGTH_DH_1024:
                return new DHKeyGenerationParameters(rnd, rfc2409_1024);
            case LENGTH_DH_1536:
                return new DHKeyGenerationParameters(rnd, rfc3526_1536);
            case KeyBuilder.LENGTH_DH_2048:
                return new DHKeyGenerationParameters(rnd, rfc3526_2048);
            default:
                CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        return null;
    }
        
    private static DHParameters fromPG(String hexP, String hexG) {
        BigInteger p = new BigInteger(1, Hex.decode(hexP));
        BigInteger g = new BigInteger(1, Hex.decode(hexG));
        return new DHParameters(p, g);
    }
}
