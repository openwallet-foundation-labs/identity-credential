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
import javacard.security.DSAKey;
import javacard.security.KeyBuilder;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.params.DSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.DSAKeyParameters;
import org.bouncycastle.crypto.params.DSAParameters;
import org.bouncycastle.crypto.params.DSAValidationParameters;

/**
 * Base class for <code>DSAPublicKeyImpl/DSAPrivateKeyImpl</code>
 * on BouncyCastle CryptoAPI.
 * @see DSAKey
 */
public class DSAKeyImpl extends KeyImpl {

    protected ByteContainer p = new ByteContainer();
    protected ByteContainer q = new ByteContainer();
    protected ByteContainer g = new ByteContainer();
    protected boolean isPrivate;

    /**
     * Construct not-initialized dsa key
     * @param keyType - key type
     * @param keySize - key size in bits
     * @see javacard.security.KeyPair
     * @see KeyBuilder
     */
    public DSAKeyImpl(byte keyType, short keySize) {
        this.size = keySize;
        type = keyType;
    }

    /**
     * Construct and initialize dsa key with DSAKeyParameters.
     * Use in KeyPairImpl
     * @see javacard.security.KeyPair
     * @see DSAKeyParameters
     * @param params key params from BouncyCastle API
     */
    public DSAKeyImpl(DSAKeyParameters params) {
        this(params.isPrivate() ? KeyBuilder.TYPE_DSA_PRIVATE : KeyBuilder.TYPE_DSA_PUBLIC, (short) params.getParameters().getP().bitLength());
        setParameters(params);
    }

     public void setParameters(CipherParameters params){
        p.setBigInteger(((DSAKeyParameters)params).getParameters().getP());
        q.setBigInteger(((DSAKeyParameters)params).getParameters().getQ());
        g.setBigInteger(((DSAKeyParameters)params).getParameters().getG());
    }
    
    public void clearKey() {
        p.clear();
        q.clear();
        g.clear();
    }

    public boolean isInitialized() {
        return (p.isInitialized() && q.isInitialized() && g.isInitialized());
    }

    public void setP(byte[] buffer, short offset, short length) throws CryptoException {
        p.setBytes(buffer, offset, length);
    }

    public void setQ(byte[] buffer, short offset, short length) throws CryptoException {
        q.setBytes(buffer, offset, length);
    }

    public void setG(byte[] buffer, short offset, short length) throws CryptoException {
        g.setBytes(buffer, offset, length);
    }

    public short getP(byte[] buffer, short offset) {
        return p.getBytes(buffer, offset);
    }

    public short getQ(byte[] buffer, short offset) {
        return q.getBytes(buffer, offset);
    }

    public short getG(byte[] buffer, short offset) {
        return g.getBytes(buffer, offset);
    }

    /**
     * Get <code>DSAKeyParameters</code>
     * @return parameters for use with BouncyCastle API
     * @see DSAKeyParameters
     */
    public CipherParameters getParameters() {
        if (!isInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        return new DSAKeyParameters(isPrivate, new DSAParameters(p.getBigInteger(), q.getBigInteger(), g.getBigInteger()));
    }
    
 
    /**
     * Get
     * <code>DSAKeyGenerationParameters</code>
     *
     * @param rnd Secure Random Generator
     * @return parameters for use with BouncyCastle API
     */
    public KeyGenerationParameters getKeyGenerationParameters(SecureRandom rnd) {
        if (isInitialized()) {
            return new DSAKeyGenerationParameters(rnd, new DSAParameters(p.getBigInteger(), q.getBigInteger(), g.getBigInteger()));
        }
        return getDefaultKeyGenerationParameters(size, rnd);
    }

    
    /**
     * Get DSA KeyGeneration Defaults Parameters
     * {@link <a
     * href="http://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html#alg">...</a>}
     *
     * @param keySize key size in bits
     * @param rnd Secure Random Generator
     */
     static KeyGenerationParameters getDefaultKeyGenerationParameters(short keySize, SecureRandom rnd) {
        BigInteger p = null;
        BigInteger q = null;
        BigInteger g = null;
        BigInteger seed = null;
        short counter = 0;
        switch (keySize) {
            case 512:
                counter = 123;
                p = new BigInteger("fca682ce8e12caba26efccf7110e526db078b05edecbcd1eb4a208f3"
                        + "ae1617ae01f35b91a47e6df63413c5e12ed0899bcd132acd50d99151"
                        + "bdc43ee737592e17", 16);
                q = new BigInteger("962eddcc369cba8ebb260ee6b6a126d9346e38c5", 16);
                g = new BigInteger("678471b27a9cf44ee91a49c5147db1a9aaf244f05a434d6486931d2d"
                        + "14271b9e35030b71fd73da179069b32e2935630e1c2062354d0da20a"
                        + "6c416e50be794ca4", 16);
                seed = new BigInteger("b869c82b35d70e1b1ff91b28e37a62ecdc34409b", 16);
                break;
            case 768:
                counter = 263;
                p = new BigInteger("e9e642599d355f37c97ffd3567120b8e25c9cd43e927b3a9670fbec5"
                        + "d890141922d2c3b3ad2480093799869d1e846aab49fab0ad26d2ce6a"
                        + "22219d470bce7d777d4a21fbe9c270b57f607002f3cef8393694cf45"
                        + "ee3688c11a8c56ab127a3daf", 16);
                q = new BigInteger("9cdbd84c9f1ac2f38d0f80f42ab952e7338bf511", 16);
                g = new BigInteger("30470ad5a005fb14ce2d9dcd87e38bc7d1b1c5facbaecbe95f190aa7"
                        + "a31d23c4dbbcbe06174544401a5b2c020965d8c2bd2171d366844577"
                        + "1f74ba084d2029d83c1c158547f3a9f1a2715be23d51ae4d3e5a1f6a"
                        + "7064f316933a346d3f529252", 16);
                seed = new BigInteger("77d0f8c4dad15eb8c4f2f8d6726cefd96d5bb399", 16);
                break;
            case 1024:
                counter = 92;
                p = new BigInteger("fd7f53811d75122952df4a9c2eece4e7f611b7523cef4400c31e3f80"
                        + "b6512669455d402251fb593d8d58fabfc5f5ba30f6cb9b556cd7813b"
                        + "801d346ff26660b76b9950a5a49f9fe8047b1022c24fbba9d7feb7c6"
                        + "1bf83b57e7c6a8a6150f04fb83f6d3c51ec3023554135a169132f675"
                        + "f3ae2b61d72aeff22203199dd14801c7", 16);
                q = new BigInteger("9760508f15230bccb292b982a2eb840bf0581cf5", 16);
                g = new BigInteger("f7e1a085d69b3ddecbbcab5c36b857b97994afbbfa3aea82f9574c0b"
                        + "3d0782675159578ebad4594fe67107108180b449167123e84c281613"
                        + "b7cf09328cc8a6e13c167a8b547c8d28e0a3ae1e2bb3a675916ea37f"
                        + "0bfa213562f1fb627a01243bcca4f1bea8519089a883dfe15ae59f06"
                        + "928b665e807b552564014c3bfecf492a", 16);
                seed = new BigInteger("8d5155894229d5e689ee01e6018a237e2cae64cd", 16);
                break;
            default:
                CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
                break;
        }
        return new DSAKeyGenerationParameters(rnd,
                new DSAParameters(p, q, g, new DSAValidationParameters(seed.toByteArray(), counter)));

    }    
}
