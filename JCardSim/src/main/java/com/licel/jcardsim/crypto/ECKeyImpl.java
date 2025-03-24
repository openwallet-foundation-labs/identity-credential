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
import javacard.framework.JCSystem;
import javacard.security.CryptoException;
import javacard.security.ECKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECKeyParameters;
import org.bouncycastle.math.ec.ECCurve;

/**
 * Base class for
 * <code>ECPublicKeyImpl/ECPrivateKeyImpl</code> on BouncyCastle CryptoAPI.
 *
 * @see ECKey
 */
public abstract class ECKeyImpl extends KeyImpl implements ECKey {

    protected ByteContainer a = new ByteContainer();
    protected ByteContainer b = new ByteContainer();
    protected ByteContainer g = new ByteContainer();
    protected ByteContainer r = new ByteContainer();
    protected ByteContainer fp = new ByteContainer();
    protected short k;
    protected short e1;
    protected short e2;
    protected short e3;
    protected boolean isKInitialized;

    /**
     * Construct not-initialized ecc key
     *
     * @param keyType - key type
     * @param keySize - key size in bits
     * @see KeyPair
     * @see KeyBuilder
     */
    public ECKeyImpl(byte keyType, short keySize) {
        this.size = keySize;
        this.type = keyType;
        setDomainParameters(getDefaultsDomainParameters(type, size));
    }

    /**
     * Construct and initialize ecc key with ECKeyParameters. Use in KeyPairImpl
     *
     * @see KeyPair
     * @see ECKeyParameters
     * @param parameters key params from BouncyCastle API
     */
    public ECKeyImpl(ECKeyParameters parameters) {
        boolean isPrivate = parameters.isPrivate();
        boolean isF2M = parameters.getParameters().getCurve() instanceof ECCurve.F2m;
        type = isPrivate ? (isF2M ? KeyBuilder.TYPE_EC_F2M_PRIVATE : KeyBuilder.TYPE_EC_FP_PRIVATE)
                : (isF2M ? KeyBuilder.TYPE_EC_F2M_PUBLIC : KeyBuilder.TYPE_EC_FP_PUBLIC);
        size = (short) parameters.getParameters().getCurve().getFieldSize();
        setDomainParameters(parameters.getParameters());
    }

    public void clearKey() {
        a.clear();
        b.clear();
        g.clear();
        r.clear();
        fp.clear();
        k = 0;
        e1 = 0;
        e2 = 0;
        e3 = 0;
    }

    protected boolean isDomainParametersInitialized() {
        return (a.isInitialized() && b.isInitialized() && g.isInitialized() && r.isInitialized()
                && isKInitialized && (fp.isInitialized() || k != 0));
    }    

    public void setFieldFP(byte[] buffer, short offset, short length) throws CryptoException {
        fp.setBytes(buffer, offset, length);
    }

    public void setFieldF2M(short e) throws CryptoException {
        setFieldF2M(e, (short) 0, (short) 0);
    }

    public void setFieldF2M(short e1, short e2, short e3) throws CryptoException {
        this.e1 = e1;
        this.e2 = e2;
        this.e3 = e3;
    }

    public void setA(byte[] buffer, short offset, short length) throws CryptoException {
        a.setBytes(buffer, offset, length);
    }

    public void setB(byte[] buffer, short offset, short length) throws CryptoException {
        b.setBytes(buffer, offset, length);
    }

    public void setG(byte[] buffer, short offset, short length) throws CryptoException {
        g.setBytes(buffer, offset, length);
    }

    public void setR(byte[] buffer, short offset, short length) throws CryptoException {
        r.setBytes(buffer, offset, length);
    }

    public void setK(short K) {
        this.k = K;
        isKInitialized = true;
    }

    public short getField(byte[] buffer, short offset) throws CryptoException {
        return fp.getBytes(buffer, offset);
    }

    public short getA(byte[] buffer, short offset) throws CryptoException {
        return a.getBytes(buffer, offset);
    }

    public short getB(byte[] buffer, short offset) throws CryptoException {
        return b.getBytes(buffer, offset);
    }

    public short getG(byte[] buffer, short offset) throws CryptoException {
        return g.getBytes(buffer, offset);
    }

    public short getR(byte[] buffer, short offset) throws CryptoException {
        return r.getBytes(buffer, offset);
    }

    public short getK() throws CryptoException {
        if (!isKInitialized) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        return k;
    }

    /**
     * Get
     * <code>ECDomainParameters</code>
     *
     * @return parameters for use with BouncyCastle API
     * @see ECDomainParameters
     */
    public ECDomainParameters getDomainParameters() {
        if (!isDomainParametersInitialized()) {
            CryptoException.throwIt(CryptoException.UNINITIALIZED_KEY);
        }
        ECCurve curve = null;
        if (fp.isInitialized()) {
            curve = new ECCurve.Fp(fp.getBigInteger(), a.getBigInteger(), b.getBigInteger());
        } else {
            curve = new ECCurve.F2m(size, e1, e2, e3, a.getBigInteger(), b.getBigInteger(),
                    r.getBigInteger(), BigInteger.valueOf(k));
        }
        return new ECDomainParameters(curve, curve.decodePoint(g.getBytes(JCSystem.CLEAR_ON_RESET)),
                r.getBigInteger(), BigInteger.valueOf(k));
    }

    /**
     * Set
     * <code>ECDomainParameters</code> for EC curve
     *
     * @param parameters
     * @see ECDomainParameters
     */
    final void setDomainParameters(ECDomainParameters parameters) {
        a.setBigInteger(parameters.getCurve().getA().toBigInteger());
        b.setBigInteger(parameters.getCurve().getB().toBigInteger());
        // generator
        g.setBytes(parameters.getG().getEncoded(false));
        // order
        r.setBigInteger(parameters.getN());
        // cofactor
        setK(parameters.getH().shortValue());
        if (parameters.getCurve() instanceof ECCurve.Fp) {
            ECCurve.Fp ecfp = (ECCurve.Fp) parameters.getCurve();
            fp.setBigInteger(ecfp.getQ());
        } else {
            ECCurve.F2m ecf2m = (ECCurve.F2m) parameters.getCurve();
            setFieldF2M((short) ecf2m.getK1(), (short) ecf2m.getK2(), (short) ecf2m.getK3());
        }
    }

    /**
     * Get
     * <code>ECKeyGenerationParameters</code>
     *
     * @param rnd Secure Random Generator
     * @return parameters for use with BouncyCastle API
     */
    public KeyGenerationParameters getKeyGenerationParameters(SecureRandom rnd) {
        if (isDomainParametersInitialized()) {
            return new ECKeyGenerationParameters(getDomainParameters(), rnd);
        }
        return new ECKeyGenerationParameters(getDefaultsDomainParameters(type, size), rnd);
    }

    /**
     * Get default
     * <code>ECKeyGenerationParameters</code>
     *
     * @param algorithm
     * @param keySize key size in bits
     * @param rnd Secure Random Generator
     * @return parameters for use with BouncyCastle API
     */
    static KeyGenerationParameters getDefaultKeyGenerationParameters(byte algorithm, short keySize, SecureRandom rnd) {
        byte keyType = algorithm == KeyPair.ALG_EC_FP ? KeyBuilder.TYPE_EC_FP_PUBLIC : KeyBuilder.TYPE_EC_F2M_PUBLIC;
        return new ECKeyGenerationParameters(getDefaultsDomainParameters(keyType, keySize), rnd);
    }

    /**
     * Get defaults
     * <code>ECDomainParameters</code> for EC curve
     * {@link <a href="http://www.secg.org/collateral/sec2_final.pdf">...</a>}
     *
     * @return parameters for use with BouncyCastle API
     * @see ECDomainParameters
     */
    static ECDomainParameters getDefaultsDomainParameters(byte keyType, short keySize) {
        String curveName = "";
        switch (keySize) {
            case 113:
            case 131:
            case 163:
            case 193:
            case 233:
            case 283:
            case 409:
            case 571:
                if ((keyType != KeyBuilder.TYPE_EC_F2M_PRIVATE) & (keyType != KeyBuilder.TYPE_EC_F2M_PUBLIC)) {
                    CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
                }
                curveName = "sect" + keySize + "r1";
                break;
            case 112:
            case 128:
            case 160:
            case 192:
            case 224:
            case 256:
            case 384:
            case 521:
                if ((keyType != KeyBuilder.TYPE_EC_FP_PRIVATE) & (keyType != KeyBuilder.TYPE_EC_FP_PUBLIC)) {
                    CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
                }
                curveName = "secp" + keySize + "r1";
                break;
            default:
                CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
                break;
        }
        X9ECParameters x9params = SECNamedCurves.getByName(curveName);
        return new ECDomainParameters(
                x9params.getCurve(),
                x9params.getG(), // G
                x9params.getN(), x9params.getH(), x9params.getSeed());
    }
    public void copyDomainParametersFrom(ECKey eckey) throws CryptoException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
