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

import java.security.SecureRandom;
import javacard.security.CryptoException;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.PrivateKey;
import javacard.security.PublicKey;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.generators.DHKeyPairGenerator;
import org.bouncycastle.crypto.generators.DSAKeyPairGenerator;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;

/**
 * Implementation
 * <code>KeyPair</code> based on BouncyCastle CryptoAPI.
 *
 * @see KeyPair
 * @see RSAKeyPairGenerator
 * @see DSAKeyPairGenerator
 * @see ECKeyPairGenerator
 */
public final class KeyPairImpl {

    byte algorithm;
    short keyLength;
    AsymmetricCipherKeyPairGenerator engine;
    PrivateKey privateKey;
    PublicKey publicKey;
    SecureRandom rnd = new SecureRandomNullProvider();
    KeyGenerationParameters keyGenerationParameters;

    /**
     * (Re)Initializes the key objects encapsulated in this
     * <code>KeyPair</code> instance with new key values. The initialized public
     * and private key objects encapsulated in this instance will then be
     * suitable for use with the
     * <code>Signature</code>,
     * <code>Cipher</code> and
     * <code>KeyAgreement</code> objects. An internal secure random number
     * generator is used during new key pair generation. <p>Notes:<ul>
     * <li><em>For the RSA algorithm, if the exponent value in the public key
     * object is pre-initialized, it will be retained. Otherwise, a default
     * value of 65537 will be used.</em> <li><em>For the DSA algorithm, if the
     * p, q and g parameters of the public key object are pre-initialized, they
     * will be retained. Otherwise, default precomputed parameter sets will be
     * used. The required default precomputed values are listed in </em>Appendix
     * B<em> of </em>Java Cryptography Architecture API Specification &amp;
     * Reference<em> document.</em> <li><em>For the EC case, if the Field, A, B,
     * G and R parameters of the key pair are pre-initialized, then they will be
     * retained. Otherwise default pre-specified values MAY be used (e.g. WAP
     * predefined curves), since computation of random generic EC keys is
     * infeasible on the smart card platform.</em> <li><em>If the time taken to
     * generate the key values is excessive, the implementation may
     * automatically request additional APDU processing time from the CAD.</em>
     * </ul>
     *
     * @throws CryptoException with the following reason codes:<ul>
     * <li><code>CryptoException.ILLEGAL_VALUE</code> if the exponent value
     * parameter in RSA or the p, q, g parameter set in DSA or the Field, A, B,
     * G and R parameter set in EC is invalid. </ul>
     * @see javacard.framework.APDU
     * @see javacard.security.Signature
     * @see javacardx.crypto.Cipher
     * @see javacard.security.RSAPublicKey
     * @see javacard.security.ECKey
     * @see javacard.security.DSAKey
     */
    public final void genKeyPair()
            throws CryptoException {
        initEngine();        
        createKeys();
        AsymmetricCipherKeyPair kp = engine.generateKeyPair();
        ((KeyWithParameters)publicKey).setParameters(kp.getPublic());
        ((KeyWithParameters)privateKey).setParameters(kp.getPrivate());
    }

    /**
     * Constructs a
     * <code>KeyPair</code> instance for the specified algorithm and keylength;
     * the encapsulated keys are uninitialized. To initialize the
     * <code>KeyPair</code> instance use the
     * <code>genKeyPair()</code> method.<p> The encapsulated key objects are of
     * the specified
     * <code>keyLength</code> size and implement the appropriate
     * <code>Key</code> interface associated with the specified algorithm
     * (example -
     * <code>RSAPublicKey</code> interface for the public key and
     * <code>RSAPrivateKey</code> interface for the private key within an
     * <code>ALG_RSA</code> key pair).
     * <p>Notes:</p> <p><em>The key objects
     * encapsulated in the generated </em><code>KeyPair</code><em> object need
     * not support the </em><code>KeyEncryption</code><em> interface.</em></p>
     *
     * @param algorithm the type of algorithm whose key pair needs to be
     * generated. Valid codes listed in <code>ALG_..</code> constants above.
     * {@link KeyPair}
     * @param keyLength the key size in bits. The valid key bit lengths are key
     * type dependent. See the <code>KeyBuilder</code> class.
     * @see KeyBuilder
     * @throws CryptoException with the following reason codes:<ul>
     * <li><code>CryptoException.NO_SUCH_ALGORITHM</code> if the requested
     * algorithm associated with the specified type, size of key is not
     * supported.</ul>
     * @see KeyBuilder
     * @see javacard.security.Signature
     * @see javacardx.crypto.KeyEncryption
     * @see javacardx.crypto.Cipher
     */
    public KeyPairImpl(byte algorithm, short keyLength)
            throws CryptoException {
        this.algorithm = algorithm;
        this.keyLength = keyLength;
        createKeys();
    }

    /**
     * Constructs a new
     * <code>KeyPair</code> object containing the specified public key and
     * private key. <p>Note that this constructor only stores references to the
     * public and private key components in the generated
     * <code>KeyPair</code> object. It does not throw an exception if the key
     * parameter objects are uninitialized.
     *
     * @param publicKey the public key.
     * @param privateKey the private key.
     * @throws CryptoException with the following reason codes:<ul>
     * <li><code>CryptoException.ILLEGAL_VALUE</code> if the input parameter key
     * objects are inconsistent with each other - i.e mismatched algorithm, size
     * etc. <li><code>CryptoException.NO_SUCH_ALGORITHM</code> if the algorithm
     * associated with the specified type, size of key is not supported. </ul>
     */
    public KeyPairImpl(PublicKey publicKey, PrivateKey privateKey)
            throws CryptoException {
        if (publicKey == null && privateKey == null) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        if ((publicKey != null) && !(publicKey instanceof KeyWithParameters)) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        this.publicKey = publicKey;
        if (this.publicKey != null) {
            selectAlgorithmByType(this.publicKey.getType());
        }
        if ((privateKey != null) && !(privateKey instanceof KeyWithParameters)) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        this.privateKey = privateKey;
        if (this.privateKey != null) {
            selectAlgorithmByType(this.privateKey.getType());
        }
    }

    /**
     * Returns a reference to the public key component of this
     * <code>KeyPair</code> object.
     *
     * @return a reference to the public key.
     */
    public PublicKey getPublic() {
        return publicKey;
    }

    /**
     * Returns a reference to the private key component of this
     * <code>KeyPair</code> object.
     *
     * @return a reference to the private key.
     */
    public PrivateKey getPrivate() {
        return privateKey;
    }

    private void selectAlgorithmByType(byte keyType) {
        switch (keyType) {
            case KeyBuilder.TYPE_RSA_PRIVATE:
            case KeyBuilder.TYPE_RSA_PUBLIC:
                algorithm = KeyPair.ALG_RSA;
                break;
            case KeyBuilder.TYPE_RSA_CRT_PRIVATE:
                algorithm = KeyPair.ALG_RSA_CRT;
                break;
            case KeyBuilder.TYPE_EC_F2M_PUBLIC:
            case KeyBuilder.TYPE_EC_F2M_PRIVATE:
                algorithm = KeyPair.ALG_EC_F2M;
                break;
            case KeyBuilder.TYPE_EC_FP_PUBLIC:
            case KeyBuilder.TYPE_EC_FP_PRIVATE:
                algorithm = KeyPair.ALG_EC_FP;
                break;
            case KeyBuilder.TYPE_DSA_PUBLIC:
            case KeyBuilder.TYPE_DSA_PRIVATE:
                algorithm = KeyPair.ALG_DSA;
                break;
            case KeyBuilder.TYPE_DH_PUBLIC:
            case KeyBuilder.TYPE_DH_PRIVATE:
                algorithm = KeyPair.ALG_DH;
                break;    
        }
    }

    /**
     * Init key pair generation engine
     */
    private void initEngine() {
        // only public key params, see specification
        if (publicKey != null) {
            keyGenerationParameters = ((KeyImpl) publicKey).getKeyGenerationParameters(rnd);
        }
        switch (algorithm) {
            case KeyPair.ALG_RSA:
            case KeyPair.ALG_RSA_CRT:
                if (keyGenerationParameters == null) {
                    keyGenerationParameters = RSAKeyImpl.getDefaultKeyGenerationParameters(keyLength, rnd);
                }
                engine = new RSAKeyPairGenerator();
                break;
            //
            case KeyPair.ALG_DSA:
                if (keyLength < 512 || keyLength > 1024 || keyLength % 64 != 0) {
                    CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
                }
                if (keyGenerationParameters == null) {
                    keyGenerationParameters = DSAKeyImpl.getDefaultKeyGenerationParameters(keyLength, rnd);
                }
                engine = new DSAKeyPairGenerator();
                break;

            // ecc
            case KeyPair.ALG_EC_F2M:
            case KeyPair.ALG_EC_FP:
                if (keyGenerationParameters == null) {
                    keyGenerationParameters = ECKeyImpl.getDefaultKeyGenerationParameters(algorithm, keyLength, rnd);
                }
                engine = new ECKeyPairGenerator();
                break;
            //dh
            case KeyPair.ALG_DH:
                if (keyGenerationParameters == null) {
                    keyGenerationParameters = DHKeyImpl.getDefaultKeyGenerationParameters(keyLength, rnd);
                }
                engine = new DHKeyPairGenerator();
                break;
                
            default:
                CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
                break;
        }
        engine.init(keyGenerationParameters);

    }
    
    /*
     * Create uninitialized keys
     */
    private void createKeys() {
        byte privateKeyType = 0;
        byte publicKeyType = 0;
        switch (algorithm) {
            case KeyPair.ALG_RSA:
                publicKeyType = KeyBuilder.TYPE_RSA_PUBLIC;
                privateKeyType = KeyBuilder.TYPE_RSA_PRIVATE;
                break;
            case KeyPair.ALG_RSA_CRT:
                publicKeyType = KeyBuilder.TYPE_RSA_PUBLIC;
                privateKeyType = KeyBuilder.TYPE_RSA_CRT_PRIVATE;
                break;
            case KeyPair.ALG_EC_FP:
                publicKeyType = KeyBuilder.TYPE_EC_FP_PUBLIC;
                privateKeyType = KeyBuilder.TYPE_EC_FP_PRIVATE;
                break;
            case KeyPair.ALG_EC_F2M:
                publicKeyType = KeyBuilder.TYPE_EC_F2M_PUBLIC;
                privateKeyType = KeyBuilder.TYPE_EC_F2M_PRIVATE;
                break;
            case KeyPair.ALG_DSA:
                publicKeyType = KeyBuilder.TYPE_DSA_PUBLIC;
                privateKeyType = KeyBuilder.TYPE_DSA_PRIVATE;
                break;
            case KeyPair.ALG_DH:
                publicKeyType = KeyBuilder.TYPE_DH_PUBLIC;
                privateKeyType = KeyBuilder.TYPE_DH_PRIVATE;
                break;
            default:
                CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
                break;
        }
        if(publicKey!=null && keyLength == 0){
            keyLength = publicKey.getSize();
        }
        if(publicKey == null){
            publicKey = (PublicKey) KeyBuilder.buildKey(publicKeyType, keyLength, false);
        }    
        if(privateKey == null){
            privateKey = (PrivateKey) KeyBuilder.buildKey(privateKeyType, keyLength, false);
        }
    }
}