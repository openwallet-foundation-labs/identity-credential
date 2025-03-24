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

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import javacard.framework.Util;
import javacard.security.CryptoException;
import javacard.security.InitializedMessageDigest;
import javacard.security.MessageDigest;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.*;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.Pack;

/**
 * Implementation
 * <code>MessageDigest</code> based
 * on BouncyCastle CryptoAPI.
 * @see MessageDigest
 * @see MD5Digest
 * @see RIPEMD160Digest
 * @see SHA1Digest
 */
public class MessageDigestImpl extends InitializedMessageDigest {

    private Digest engine;
    private byte algorithm;
    private short blockSize;
    private Class digestClass;
    private byte componentSize;
    private byte componentCount;
    private byte componentStartIdx;

    public MessageDigestImpl(byte algorithm) {
        this.algorithm = algorithm;
        componentStartIdx = 1;
        switch (algorithm) {
            case ALG_SHA:
                engine = new SHA1Digest();
                digestClass = engine.getClass();
                blockSize = (short) ((SHA1Digest)engine).getByteLength();
                break;
            case ALG_MD5:
                engine = new MD5Digest();
                digestClass = engine.getClass();
                blockSize = (short) ((MD5Digest)engine).getByteLength();
                break;
            case ALG_RIPEMD160:
                engine = new RIPEMD160Digest();
                digestClass = engine.getClass();
                blockSize = (short) ((RIPEMD160Digest)engine).getByteLength();
                componentStartIdx = 0;
                break;
            case ALG_SHA_224:
                engine = new SHA224Digest();
                digestClass = engine.getClass();
                blockSize = (short) ((SHA224Digest)engine).getByteLength();
                break;
            case ALG_SHA_256:
                engine = new SHA256Digest();
                digestClass = engine.getClass();
                blockSize = (short) ((SHA256Digest)engine).getByteLength();
                break;
            case ALG_SHA_384:
                engine = new SHA384Digest();
                digestClass = engine.getClass().getSuperclass();
                blockSize = (short) ((SHA384Digest)engine).getByteLength();
                break;
            case ALG_SHA_512:
                engine = new SHA512Digest();
                digestClass = engine.getClass().getSuperclass();
                blockSize = (short) ((SHA512Digest)engine).getByteLength();
                break;
            case ALG_SHA3_224:
                engine = new SHA3Digest(224);
                digestClass = engine.getClass().getSuperclass();
                blockSize = (short) ((SHA3Digest)engine).getByteLength();
                break;
            case ALG_SHA3_256:
                engine = new SHA3Digest(256);
                digestClass = engine.getClass().getSuperclass();
                blockSize = (short) ((SHA3Digest)engine).getByteLength();
                break;
            case ALG_SHA3_384:
                engine = new SHA3Digest(384);
                digestClass = engine.getClass().getSuperclass();
                blockSize = (short) ((SHA3Digest)engine).getByteLength();
                break;
            case ALG_SHA3_512:
                engine = new SHA3Digest(512);
                digestClass = engine.getClass().getSuperclass();
                blockSize = (short) ((SHA3Digest)engine).getByteLength();
                break;
            default:
                CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
                break;
        }

        componentSize = (byte)(blockSize == 64 ? 4 : 8);
        componentCount = getComponentCount(algorithm);
    }

    private byte getComponentCount(byte algorithm){
        switch(algorithm){
            case ALG_SHA:
            case ALG_MD5:
            case ALG_RIPEMD160:
            case ALG_SHA_256:
            case ALG_SHA_512:
                return (byte) (engine.getDigestSize() / componentSize);

            // From NIST FIPS 180-4, https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.180-4.pdf
            case ALG_SHA_224: // 5.3.2 SHA-224 Initial Hash Words
            case ALG_SHA_384: // 5.3.4 SHA-384 Initial Hash Words
                return 8;
        }

        return 0;
    }
    public byte getAlgorithm() {
        return algorithm;
    }

    public byte getLength() {
        return (byte) engine.getDigestSize();
    }

    public short doFinal(byte inBuff[], short inOffset, short inLength,
            byte outBuff[], short outOffset) {
        engine.update(inBuff, inOffset, inLength);
        return (short) engine.doFinal(outBuff, outOffset);
    }

    public void update(byte inBuff[], short inOffset, short inLength) {
        engine.update(inBuff, inOffset, inLength);
    }

    public void reset() {
        engine.reset();
    }

    public void setInitialDigest(byte[] initialDigestBuf, short initialDigestOffset,
            short initialDigestLength, byte[] digestedMsgLenBuf, short digestedMsgLenOffset,
            short digestedMsgLenLength) throws CryptoException {
        // initialDigestLength must be == Intermediate State of hash value size
        if (initialDigestLength != getIntermediateStateSize()) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        if ( !checkSupportDigestedMsgLenLength(digestedMsgLenLength) ) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        long byteCountLo = 0;
        long byteCountHi = 0;
        for (short i = 0; i < digestedMsgLenLength; i++) {
            if( i < 8 )
                byteCountLo = (byteCountLo << 8) + (digestedMsgLenBuf[digestedMsgLenOffset + i] & 0xff);
            else
                byteCountHi = (byteCountHi << 8) + (digestedMsgLenBuf[digestedMsgLenOffset + i] & 0xff);
        }

        // byte count % block size must be == 0
        if (byteCountLo % blockSize != 0) {
            CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }

        // Set initial state for SHA3-224,SHA3-256,SHA3-384 and SHA3-512, BouncyCastle specific
        if( (algorithm == ALG_SHA3_224) || (algorithm == ALG_SHA3_256) || (algorithm == ALG_SHA3_384) || (algorithm == ALG_SHA3_512) ){
            try {
                Field statesField = KeccakDigest.class.getDeclaredField("state");
                statesField.setAccessible(true);
                long[] states = long[].class.cast(statesField.get(engine));
                for (byte i = 0; i < states.length; i++) {
                    states[i] = Pack.bigEndianToLong(initialDigestBuf,i*(Long.SIZE/Byte.SIZE));
                }
            } catch (Exception e) {
                CryptoException.throwIt(CryptoException.ILLEGAL_USE);
            }
        }
        else{
            // Set hash state for SHA1, MD5, RIPEMD160, SHA-224, SHA-256, SHA-384 and SHA-512, BouncyCastle specific
            try {
                for (byte i = 0; i < componentCount; i++) {
                    // some reflection work
                    Field h = digestClass.getDeclaredField("H" + (i + componentStartIdx));
                    h.setAccessible(true);
                    if (componentSize == 4) {
                        h.setInt(engine, Pack.bigEndianToInt(initialDigestBuf, initialDigestOffset + i * componentSize));
                    } else {
                        h.setLong(engine, Pack.bigEndianToLong(initialDigestBuf, initialDigestOffset + i * componentSize));
                    }
                }

                // set byteCount
                // Check if SHA-384 and SHA-512
                if( (algorithm == ALG_SHA_384) || (algorithm == ALG_SHA_512)  ){
                    Field byteCount1Field = digestClass.getDeclaredField("byteCount1");
                    byteCount1Field.setAccessible(true);
                    byteCount1Field.setLong(engine, byteCountLo);

                    Field byteCount2Field = digestClass.getDeclaredField("byteCount2");
                    byteCount2Field.setAccessible(true);
                    byteCount2Field.setLong(engine, byteCountHi);
                }
                else{
                    Field byteCountField = digestClass.getSuperclass().getDeclaredField("byteCount");
                    byteCountField.setAccessible(true);
                    byteCountField.setLong(engine, byteCountLo);
                }

            } catch (Exception e) {
                CryptoException.throwIt(CryptoException.ILLEGAL_USE);
            }
        }
    }

    private boolean checkSupportDigestedMsgLenLength(short digestedMsgLenLength){
        if (digestedMsgLenLength == 0 ){
            return false;
        }

        switch(algorithm){
            case ALG_SHA:
            case ALG_MD5:
            case ALG_RIPEMD160:
            case ALG_SHA_224:
            case ALG_SHA_256:
                if (digestedMsgLenLength > 8 ){
                    return false;
                }
                break;

            case ALG_SHA_384:
            case ALG_SHA_512:
                if (digestedMsgLenLength > 16 ){
                    return false;
                }
                break;

            case ALG_SHA3_224:
            case ALG_SHA3_256:
            case ALG_SHA3_384:
            case ALG_SHA3_512:
                // No limit for SHA3
                return true;
        }

        return true;
    }
    void getIntermediateDigest(byte[] intermediateDigest, int off) {
        if( (algorithm == ALG_SHA3_224) || (algorithm == ALG_SHA3_256) || (algorithm == ALG_SHA3_384) || (algorithm == ALG_SHA3_512) ){
            // Get intermediate state for SHA3-224,SHA3-256,SHA3-384 and SHA3-512, BouncyCastle specific
            try {
                Field statesField = KeccakDigest.class.getDeclaredField("state");
                statesField.setAccessible(true);
                long[] states = long[].class.cast(statesField.get(engine));
                for (byte i = 0; i < states.length; i++) {
                    Pack.longToBigEndian(states[i],intermediateDigest,i*(Long.SIZE/Byte.SIZE));
                }
            } catch (Exception e) {
                CryptoException.throwIt(CryptoException.ILLEGAL_USE);
            }
        }
        else{
            // Get hash state for SHA1, MD5, RIPEMD160, SHA-224, SHA-256, SHA-384 and SHA-512, BouncyCastle specific
            try {
                for (byte i = 0; i < componentCount; i++) {
                    // some reflection work
                    Field h = digestClass.getDeclaredField("H" + (i + componentStartIdx));
                    h.setAccessible(true);
                    if (componentSize == 4) {
                        Pack.intToBigEndian(h.getInt(engine), intermediateDigest, off + i * componentSize);
                    } else {
                        Pack.longToBigEndian(h.getLong(engine), intermediateDigest, off + i * componentSize);
                    }
                }
            } catch (Exception e) {
                CryptoException.throwIt(CryptoException.ILLEGAL_USE);
            }
        }
    }

    short getBlockSize(){
        return blockSize;
    }

    short getIntermediateStateSize(){
        switch(algorithm){
            // https://docs.oracle.com/javacard/3.0.5/api/javacard/security/MessageDigest.html#ALG_SHA
            case ALG_SHA: return 20;

            // https://docs.oracle.com/javacard/3.0.5/api/javacard/security/MessageDigest.html#ALG_MD5
            case ALG_MD5: return 16;

            //https://docs.oracle.com/javacard/3.0.5/api/javacard/security/MessageDigest.html#ALG_RIPEMD160
            case ALG_RIPEMD160 : return 20;

            //https://docs.oracle.com/javacard/3.0.5/api/javacard/security/MessageDigest.html#ALG_SHA_224
            case ALG_SHA_224 : return 32;

            //https://docs.oracle.com/javacard/3.0.5/api/javacard/security/MessageDigest.html#ALG_SHA_256
            case ALG_SHA_256 : return 32;

            //https://docs.oracle.com/javacard/3.0.5/api/javacard/security/MessageDigest.html#ALG_SHA_384
            case ALG_SHA_384: return 64;

            //https://docs.oracle.com/javacard/3.0.5/api/javacard/security/MessageDigest.html#ALG_SHA_512
            case ALG_SHA_512: return 64;

            // Javacard 3.0.5 api document represent not correct information for SHA3 Message digests.
            // Javacard 3.1.0 api document and NIST- FIPS 202 are reference instead.
            //https://docs.oracle.com/en/java/javacard/3.1/jc_api_srvc/api_classic/javacard/security/MessageDigest.html#ALG_SHA3_224
            case ALG_SHA3_224: return 200;

            //https://docs.oracle.com/en/java/javacard/3.1/jc_api_srvc/api_classic/javacard/security/MessageDigest.html#ALG_SHA3_256
            case ALG_SHA3_256: return 200;

            //https://docs.oracle.com/en/java/javacard/3.1/jc_api_srvc/api_classic/javacard/security/MessageDigest.html#ALG_SHA3_384
            case ALG_SHA3_384: return 200;

            //https://docs.oracle.com/en/java/javacard/3.1/jc_api_srvc/api_classic/javacard/security/MessageDigest.html#ALG_SHA3_512
            case ALG_SHA3_512: return 200;
        }
        return 0;
    }

}
