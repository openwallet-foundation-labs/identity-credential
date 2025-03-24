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
import javacard.framework.Util;
import javacard.security.CryptoException;
import javacard.security.KeyAgreement;
import javacard.security.PrivateKey;
import org.bouncycastle.crypto.BasicAgreement;
import org.bouncycastle.crypto.agreement.DHBasicAgreement;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.agreement.ECDHCBasicAgreement;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.params.DHKeyParameters;
import org.bouncycastle.crypto.params.DHParameters;
import org.bouncycastle.crypto.params.DHPublicKeyParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;

/**
 * Implementation <code>KeyAgreement</code> based
 * on BouncyCastle CryptoAPI.
 * @see KeyAgreement
 * @see ECDHBasicAgreement
 * @see ECDHCBasicAgreement
 */
public class KeyAgreementImpl extends KeyAgreement {

    BasicAgreement engine;
    SHA1Digest digestEngine;
    
    byte algorithm;
    PrivateKey privateKey;

    public KeyAgreementImpl(byte algorithm) {
        this.algorithm = algorithm;
        switch (algorithm) {
            case ALG_EC_SVDP_DH: // no break
            case ALG_EC_SVDP_DH_PLAIN:
                engine = new ECDHBasicAgreement();
                break;
            case ALG_EC_SVDP_DHC: // no break
            case ALG_EC_SVDP_DHC_PLAIN:
                engine = new ECDHCBasicAgreement();
                break;
            case ALG_EC_SVDP_DH_PLAIN_XY:
                engine = new ECDHFullAgreement();
                break;
            case ALG_DH_PLAIN:
                engine = new DHBasicAgreement();
                break;
            case ALG_EC_PACE_GM:
                engine = new ECGMAgreement();
                break;
            default:
                CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
                break;
        }
        digestEngine = new SHA1Digest();
    }

    public void init(PrivateKey privateKey) throws CryptoException {
        if (privateKey == null) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        if ((!(privateKey instanceof ECPrivateKeyImpl)) && (!(privateKey instanceof DHPrivateKeyImpl))) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        if(privateKey instanceof ECPrivateKeyImpl) {
            engine.init(((ECPrivateKeyImpl) privateKey).getParameters());
            this.privateKey = privateKey;
        } else {
            engine.init(((DHPrivateKeyImpl) privateKey).getParameters());
            this.privateKey = privateKey;
        }
    }

    public byte getAlgorithm() {
        return algorithm;
    }

    public short generateSecret(byte[] publicData,
            short publicOffset,
            short publicLength,
            byte[] secret,
            short secretOffset) throws CryptoException {
        if(algorithm == ALG_DH_PLAIN) {
            BigInteger pubKey = (new ByteContainer(publicData, publicOffset, publicLength)).getBigInteger();
            DHParameters baseParam = ((DHKeyParameters) ((DHPrivateKeyImpl) privateKey).getParameters()).getParameters();
            BigInteger retAgreement = engine.calculateAgreement(new DHPublicKeyParameters(pubKey, baseParam));
            return (new ByteContainer(retAgreement)).getBytes(secret, secretOffset);
        } else {
            byte[] publicKey = new byte[publicLength];
            Util.arrayCopyNonAtomic(publicData, publicOffset, publicKey, (short) 0, publicLength);
            ECPublicKeyParameters ecp = new ECPublicKeyParameters(
                    ((ECPrivateKeyParameters) ((ECPrivateKeyImpl) privateKey).getParameters()).getParameters().getCurve().decodePoint(publicKey), ((ECPrivateKeyParameters) ((ECPrivateKeyImpl) privateKey).getParameters()).getParameters());
            byte[] num = engine.calculateAgreement(ecp).toByteArray();

            byte[] result;
            if (algorithm != ALG_EC_SVDP_DH_PLAIN_XY && algorithm != ALG_EC_PACE_GM) {
                // truncate/zero-pad to field size as per the spec:
                int fieldSize = ((ECPrivateKeyImpl) privateKey).getDomainParameters().getCurve().getFieldSize();
                result = new byte[(fieldSize + 7) / 8];
                int numBytes = Math.min(num.length, result.length);
                Util.arrayCopyNonAtomic(
                        num,    (short)(   num.length - numBytes),
                        result, (short)(result.length - numBytes),
                        (short)numBytes);
                Util.arrayFillNonAtomic(result, (short)0, (short)(result.length - numBytes), (byte)0);
            } else {
                // keep the whole result:
                result = num;
            }

            // post-process output key based on agreement type
            switch (this.algorithm) {
                case ALG_EC_SVDP_DH: // no break
                case ALG_EC_SVDP_DHC: 
                    // apply SHA1-hash (see spec)
                    byte[] hashResult = new byte[20];
                    digestEngine.update(result, 0, result.length);
                    digestEngine.doFinal(hashResult, 0);
                    Util.arrayCopyNonAtomic(hashResult, (short) 0, secret, secretOffset, (short) hashResult.length);
                    return (short) hashResult.length;
                case ALG_EC_SVDP_DHC_PLAIN: // no break
                case ALG_EC_SVDP_DH_PLAIN: // no break
                case ALG_EC_SVDP_DH_PLAIN_XY: // no break
                case ALG_EC_PACE_GM:
                    // plain output
                    Util.arrayCopyNonAtomic(result, (short) 0, secret, secretOffset, (short) result.length);
                    return (short) result.length;
                default:
                    CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
                    break;
            }
        }
        
        return (short) -1;
    }

    /**
     * BouncyCastle doesn't offer ECDH Agreement that provides both coordinates.
     * This is needed for <code>ALG_EC_SVDP_DH_PLAIN_XY</code>.
     * So do it here instead and squeeze the resulting point through byte encodingä
     * in a BigInteger.
     */
    static class ECDHFullAgreement implements BasicAgreement {
        private ECPrivateKeyParameters key;

        public ECDHFullAgreement() {
        }

        public void init(CipherParameters privateKey) {
            this.key = (ECPrivateKeyParameters)privateKey;
        }

        public int getFieldSize() {
            return (this.key.getParameters().getCurve().getFieldSize() + 7) / 8;
        }

        public BigInteger calculateAgreement(CipherParameters publicKey) {
            ECPublicKeyParameters pub = (ECPublicKeyParameters)publicKey;
            ECPoint result = pub.getQ().multiply(this.key.getD());
            return new BigInteger(1, result.getEncoded(false));
        }
    }

    /**
     * BouncyCastle doesn't offer KeyAgreement analogous to <code>ALG_EC_PACE_GM</code>.
     * So do it here instead and squeeze the resulting point through byte encoding
     * in a BigInteger.
     */
    static class ECGMAgreement implements BasicAgreement {
        private ECPrivateKeyParameters key;

        public ECGMAgreement() {
        }

        public void init(CipherParameters privateKey) {
            this.key = (ECPrivateKeyParameters) privateKey;
        }

        public int getFieldSize() {
            return (this.key.getParameters().getCurve().getFieldSize() + 7) / 8;
        }

        public BigInteger calculateAgreement(CipherParameters publicKey) {
            ECPublicKeyParameters pub = (ECPublicKeyParameters) publicKey;
            ECPoint result = this.key.getParameters().getG().multiply(this.key.getD()).add(pub.getQ());
            return new BigInteger(1, result.getEncoded(false));
        }
    }
}
