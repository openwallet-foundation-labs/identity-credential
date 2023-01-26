/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.identity.wwwreader;

//import android.content.Context;
//import android.security.keystore.KeyProperties;
//import android.util.Log;

//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;

//import androidx.annotation.VisibleForTesting;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequenceGenerator;
import org.bouncycastle.util.BigIntegers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.TimeZone;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.AbstractFloat;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.DoublePrecisionFloat;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.SimpleValueType;
import co.nstant.in.cbor.model.SpecialType;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

/**
 * Utility functions.
 */
public class Util {
    private static final String TAG = "Util";
    private static final long COSE_LABEL_ALG = 1;
    private static final long COSE_LABEL_X5CHAIN = 33;  // temporary identifier
    // From RFC 8152: Table 5: ECDSA Algorithm Values
    private static final long COSE_ALG_ECDSA_256 = -7;
    private static final long COSE_ALG_ECDSA_384 = -35;
    private static final long COSE_ALG_ECDSA_512 = -36;
    private static final long COSE_ALG_HMAC_256_256 = 5;
    private static final long CBOR_SEMANTIC_TAG_ENCODED_CBOR = 24;
    private static final long COSE_KEY_KTY = 1;
    private static final long COSE_KEY_TYPE_EC2 = 2;
    private static final long COSE_KEY_EC2_CRV = -1;
    private static final long COSE_KEY_EC2_X = -2;
    private static final long COSE_KEY_EC2_Y = -3;
    private static final long COSE_KEY_EC2_CRV_P256 = 1;

    // Not called.
    private Util() {
    }

    /* TODO: add cborBuildDate() which generates a full-date where
     *
     *  full-date = #6.1004(tstr),
     *
     * and where tag 1004 is specified in RFC 8943.
     */
    public static
    byte[] fromHex(String stringWithHex) {
        int stringLength = stringWithHex.length();
        if ((stringLength % 2) != 0) {
            throw new IllegalArgumentException("Invalid length of hex string: " + stringLength);
        }
        int numBytes = stringLength / 2;
        byte[] data = new byte[numBytes];
        for (int n = 0; n < numBytes; n++) {
            String byteStr = stringWithHex.substring(2 * n, 2 * n + 2);
            data[n] = (byte) Integer.parseInt(byteStr, 16);
        }
        return data;
    }

    static
    String toHex(byte[] bytes) {
        return toHex(bytes, 0, bytes.length);
    }

    static String toHex(byte[] bytes, int from, int to) {
        if (from < 0 || to > bytes.length || from > to) {
            String msg = String.format(Locale.US, "Expected 0 <= from <= to <= %d, got %d, %d.",
                    bytes.length, from, to);
            throw new IllegalArgumentException(msg);
        }
        StringBuilder sb = new StringBuilder();
        for (int n = from; n < to; n++) {
            byte b = bytes[n];
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
    static void dumpHex(String tag, String message, byte[] bytes) {
        Log.i(tag, message + " (" + bytes.length + " bytes)");
        final int chunkSize = 1024;
        for (int offset = 0; offset < bytes.length; offset += chunkSize) {
            String s = toHex(bytes, offset, Math.min(bytes.length, offset + chunkSize));
            Log.i(tag, "data: " + s);
        }
    }
    */

    static
    String base16(byte[] bytes) {
        return toHex(bytes).toUpperCase(Locale.ROOT);
    }

    public static
    byte[] cborEncode(DataItem dataItem) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new CborEncoder(baos).encode(dataItem);
        } catch (CborException e) {
            // This should never happen and we don't want cborEncode() to throw since that
            // would complicate all callers. Log it instead.
            throw new IllegalStateException("Unexpected failure encoding data", e);
        }
        return baos.toByteArray();
    }

    static
    byte[] cborEncodeWithoutCanonicalizing(DataItem dataItem) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new CborEncoder(baos).nonCanonical().encode(dataItem);
        } catch (CborException e) {
            // This should never happen and we don't want cborEncode() to throw since that
            // would complicate all callers. Log it instead.
            throw new IllegalStateException("Unexpected failure encoding data", e);
        }
        return baos.toByteArray();
    }

    static
    byte[] cborEncodeBoolean(boolean value) {
        return cborEncode(new CborBuilder().add(value).build().get(0));
    }

    static
    byte[] cborEncodeString(String value) {
        return cborEncode(new CborBuilder().add(value).build().get(0));
    }

    static
    byte[] cborEncodeNumber(long value) {
        return cborEncode(new CborBuilder().add(value).build().get(0));
    }

    static
    byte[] cborEncodeBytestring(byte[] value) {
        return cborEncode(new CborBuilder().add(value).build().get(0));
    }

    static
    byte[] cborEncodeDateTime(Timestamp timestamp) {
        return cborEncode(cborBuildDateTime(timestamp));
    }

    /**
     * Returns #6.0(tstr) where tstr is the ISO 8601 encoding of the given point in time.
     * Only supports UTC times.
     */
    static  
    DataItem cborBuildDateTime(Timestamp timestamp) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date val = new Date(timestamp.toEpochMilli());
        String dateString = df.format(val);
        DataItem dataItem = new UnicodeString(dateString);
        dataItem.setTag(0);
        return dataItem;
    }

    public static  
    DataItem cborDecode(  byte[] encodedBytes) {
        ByteArrayInputStream bais = new ByteArrayInputStream(encodedBytes);
        List<DataItem> dataItems = null;
        try {
            dataItems = new CborDecoder(bais).decode();
        } catch (CborException e) {
            throw new IllegalArgumentException("Error decoding CBOR", e);
        }
        if (dataItems.size() != 1) {
            throw new IllegalArgumentException("Unexpected number of items, expected 1 got "
                    + dataItems.size());
        }
        return dataItems.get(0);
    }

    static boolean cborDecodeBoolean(  byte[] data) {
        SimpleValue simple = (SimpleValue) cborDecode(data);
        return simple.getSimpleValueType() == SimpleValueType.TRUE;
    }

    /**
     * Accepts a {@code DataItem}, attempts to cast it to a {@code Number}, then returns the value
     * Throws {@code IllegalArgumentException} if the {@code DataItem} is not a {@code Number}. This
     * method also checks bounds, and if the given data item is too large to fit in a long, it
     * throws {@code ArithmeticException}.
     */
    static long checkedLongValue(DataItem item) {
        final BigInteger bigNum = castTo(Number.class, item).getValue();
        final long result = bigNum.longValue();
        if (!bigNum.equals(BigInteger.valueOf(result))) {
            throw new ArithmeticException("Expected long value, got '" + bigNum + "'");
        }
        return result;
    }

    static  
    String cborDecodeString(  byte[] data) {
        return checkedStringValue(cborDecode(data));
    }

    /**
     * Accepts a {@code DataItem}, attempts to cast it to a {@code UnicodeString}, then returns the
     * value. Throws {@code IllegalArgumentException} if the {@code DataItem} is not a
     * {@code UnicodeString}.
     */
    static String checkedStringValue(DataItem item) {
        return castTo(UnicodeString.class, item).getString();
    }

    static long cborDecodeLong(  byte[] data) {
        return checkedLongValue(cborDecode(data));
    }

    static  
    byte[] cborDecodeByteString(  byte[] data) {
        DataItem dataItem = cborDecode(data);
        return castTo(ByteString.class, dataItem).getBytes();
    }

    static  
    Timestamp cborDecodeDateTime(  byte[] data) {
        return cborDecodeDateTime(cborDecode(data));
    }

    static  
    Timestamp cborDecodeDateTime(DataItem di) {
        if (!(di instanceof co.nstant.in.cbor.model.UnicodeString)) {
            throw new IllegalArgumentException("Passed in data is not a Unicode-string");
        }
        if (!di.hasTag() || di.getTag().getValue() != 0) {
            throw new IllegalArgumentException("Passed in data is not tagged with tag 0");
        }
        String dateString = checkedStringValue(di);

        // Manually parse the timezone
        TimeZone parsedTz = TimeZone.getTimeZone("UTC");
        if (!dateString.endsWith("Z")) {
            String timeZoneSubstr = dateString.substring(dateString.length() - 6);
            parsedTz = TimeZone.getTimeZone("GMT" + timeZoneSubstr);
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        df.setTimeZone(parsedTz);
        Date date = null;
        try {
            date = df.parse(dateString);
        } catch (ParseException e) {
            throw new RuntimeException("Error parsing string", e);
        }

        return Timestamp.ofEpochMilli(date.getTime());
    }

    /**
     * Similar to a typecast of {@code value} to the given type {@code clazz}, except:
     * <ul>
     *   <li>Throws {@code IllegalArgumentException} instead of {@code ClassCastException} if
     *       {@code !clazz.isAssignableFrom(value.getClass())}.</li>
     *   <li>Also throws {@code IllegalArgumentException} if {@code value == null}.</li>
     * </ul>
     */
    static  <T extends V, V> T castTo(Class<T> clazz, V value) {
        if (value == null || !clazz.isAssignableFrom(value.getClass())) {
            String valueStr = (value == null) ? "null" : value.getClass().toString();
            throw new IllegalArgumentException("Expected type " + clazz + ", got type " + valueStr);
        } else {
            return (T) value;
        }
    }

    /**
     * Helper function to check if a given certificate chain is valid.
     *
     * NOTE NOTE NOTE: We only check that the certificates in the chain sign each other. We
     * <em>specifically</em> don't check that each certificate is also a CA certificate.
     * 
     * @param certificateChain the chain to validate.
     * @return <code>true</code> if valid, <code>false</code> otherwise.
     */
    static boolean validateCertificateChain(
              Collection<X509Certificate> certificateChain) {
        // First check that each certificate signs the previous one...
        X509Certificate prevCertificate = null;
        for (X509Certificate certificate : certificateChain) {
            if (prevCertificate != null) {
                // We're not the leaf certificate...
                //
                // Check the previous certificate was signed by this one.
                try {
                    prevCertificate.verify(certificate.getPublicKey());
                } catch (CertificateException
                        | InvalidKeyException
                        | NoSuchAlgorithmException
                        | NoSuchProviderException
                        | SignatureException e) {
                    return false;
                }
            } else {
                // we're the leaf certificate so we're not signing anything nor
                // do we need to be e.g. a CA certificate.
            }
            prevCertificate = certificate;
        }
        return true;
    }

    /**
     * Computes an HKDF.
     *
     * This is based on https://github.com/google/tink/blob/master/java/src/main/java/com/google
     * /crypto/tink/subtle/Hkdf.java
     * which is also Copyright (c) Google and also licensed under the Apache 2 license.
     * 
     * @param macAlgorithm the MAC algorithm used for computing the Hkdf. I.e., "HMACSHA1" or
     *                     "HMACSHA256".
     * @param ikm          the input keying material.
     * @param salt         optional salt. A possibly non-secret random value. If no salt is
     *                     provided (i.e. if
     *                     salt has length 0) then an array of 0s of the same size as the hash
     *                     digest is used as salt.
     * @param info         optional context and application specific information.
     * @param size         The length of the generated pseudorandom string in bytes. The maximal
     *                     size is
     *                     255.DigestSize, where DigestSize is the size of the underlying HMAC.
     * @return size pseudorandom bytes.
     */
    static  
    byte[] computeHkdf(
              String macAlgorithm,   final byte[] ikm,   final byte[] salt,
              final byte[] info, int size) {
        Mac mac = null;
        try {
            mac = Mac.getInstance(macAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No such algorithm: " + macAlgorithm, e);
        }
        if (size > 255 * mac.getMacLength()) {
            throw new IllegalArgumentException("size too large");
        }
        try {
            if (salt == null || salt.length == 0) {
                // According to RFC 5869, Section 2.2 the salt is optional. If no salt is provided
                // then HKDF uses a salt that is an array of zeros of the same length as the hash
                // digest.
                mac.init(new SecretKeySpec(new byte[mac.getMacLength()], macAlgorithm));
            } else {
                mac.init(new SecretKeySpec(salt, macAlgorithm));
            }
            byte[] prk = mac.doFinal(ikm);
            byte[] result = new byte[size];
            int ctr = 1;
            int pos = 0;
            mac.init(new SecretKeySpec(prk, macAlgorithm));
            byte[] digest = new byte[0];
            while (true) {
                mac.update(digest);
                mac.update(info);
                mac.update((byte) ctr);
                digest = mac.doFinal();
                if (pos + digest.length < size) {
                    System.arraycopy(digest, 0, result, pos, digest.length);
                    pos += digest.length;
                    ctr++;
                } else {
                    System.arraycopy(digest, 0, result, pos, size - pos);
                    break;
                }
            }
            return result;
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Error MACing", e);
        }
    }

    private static byte[] coseBuildToBeSigned(byte[] encodedProtectedHeaders,
            byte[] payload,
            byte[] detachedContent) {
        CborBuilder sigStructure = new CborBuilder();
        ArrayBuilder<CborBuilder> array = sigStructure.addArray();

        array.add("Signature1");
        array.add(encodedProtectedHeaders);

        // We currently don't support Externally Supplied Data (RFC 8152 section 4.3)
        // so external_aad is the empty bstr
        byte[] emptyExternalAad = new byte[0];
        array.add(emptyExternalAad);

        // Next field is the payload, independently of how it's transported (RFC
        // 8152 section 4.4). Since our API specifies only one of |data| and
        // |detachedContent| can be non-empty, it's simply just the non-empty one.
        if (payload != null && payload.length > 0) {
            array.add(payload);
        } else {
            array.add(detachedContent);
        }
        array.end();
        return cborEncode(sigStructure.build().get(0));
    }

    private static byte[] signatureCoseToDer(byte[] signature) {
        // r and s are always positive and may use all bits so use the constructor which
        // parses them as unsigned.
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(
                signature, 0, signature.length / 2));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(
                signature, signature.length / 2, signature.length));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DERSequenceGenerator seq = new DERSequenceGenerator(baos);
            seq.addObject(new ASN1Integer(r));
            seq.addObject(new ASN1Integer(s));
            seq.close();
        } catch (IOException e) {
            throw new IllegalStateException("Error generating DER signature", e);
        }
        return baos.toByteArray();
    }

    /**
     * Currently only ECDSA signatures are supported.
     *
     * TODO: add support and tests for Ed25519 and Ed448.
     */
    static boolean coseSign1CheckSignature(  DataItem coseSign1,
              byte[] detachedContent,   PublicKey publicKey) {
        if (coseSign1.getMajorType() != MajorType.ARRAY) {
            throw new IllegalArgumentException("Data item is not an array");
        }
        List<DataItem> items = ((co.nstant.in.cbor.model.Array) coseSign1).getDataItems();
        if (items.size() < 4) {
            throw new IllegalArgumentException("Expected at least four items in COSE_Sign1 array");
        }
        if (items.get(0).getMajorType() != MajorType.BYTE_STRING) {
            throw new IllegalArgumentException("Item 0 (protected headers) is not a byte-string");
        }
        byte[] encodedProtectedHeaders = ((co.nstant.in.cbor.model.ByteString) items.get(
                0)).getBytes();
        byte[] payload = new byte[0];
        if (items.get(2).getMajorType() == MajorType.SPECIAL) {
            if (((co.nstant.in.cbor.model.Special) items.get(2)).getSpecialType()
                    != SpecialType.SIMPLE_VALUE) {
                throw new IllegalArgumentException(
                        "Item 2 (payload) is a special but not a simple value");
            }
            SimpleValue simple = (co.nstant.in.cbor.model.SimpleValue) items.get(2);
            if (simple.getSimpleValueType() != SimpleValueType.NULL) {
                throw new IllegalArgumentException(
                        "Item 2 (payload) is a simple but not the value null");
            }
        } else if (items.get(2).getMajorType() == MajorType.BYTE_STRING) {
            payload = ((co.nstant.in.cbor.model.ByteString) items.get(2)).getBytes();
        } else {
            throw new IllegalArgumentException("Item 2 (payload) is not nil or byte-string");
        }
        if (items.get(3).getMajorType() != MajorType.BYTE_STRING) {
            throw new IllegalArgumentException("Item 3 (signature) is not a byte-string");
        }
        byte[] coseSignature = ((co.nstant.in.cbor.model.ByteString) items.get(3)).getBytes();

        byte[] derSignature = signatureCoseToDer(coseSignature);

        int dataLen = payload.length;
        int detachedContentLen = (detachedContent != null ? detachedContent.length : 0);
        if (dataLen > 0 && detachedContentLen > 0) {
            throw new IllegalArgumentException("data and detachedContent cannot both be non-empty");
        }

        DataItem protectedHeaders = cborDecode(encodedProtectedHeaders);
        long alg = cborMapExtractNumber((Map) protectedHeaders, COSE_LABEL_ALG);
        String signature;
        if (alg == COSE_ALG_ECDSA_256) {
            signature = "SHA256withECDSA";
        } else if (alg == COSE_ALG_ECDSA_384) {
            signature = "SHA384withECDSA";
        } else if (alg == COSE_ALG_ECDSA_512) {
            signature = "SHA512withECDSA";
        } else {
            throw new IllegalArgumentException("Unsupported COSE alg " + alg);
        }

        byte[] toBeSigned = Util.coseBuildToBeSigned(encodedProtectedHeaders, payload,
                detachedContent);

        try {
            // Use BouncyCastle provider for verification since it supports a lot more curves than
            // the default provider, including the brainpool curves
            //
            Signature verifier = Signature.getInstance(signature,
                    new org.bouncycastle.jce.provider.BouncyCastleProvider());
            verifier.initVerify(publicKey);
            verifier.update(toBeSigned);
            return verifier.verify(derSignature);
        } catch (SignatureException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Error verifying signature", e);
        }
    }

    private static  
    byte[] coseBuildToBeMACed(  byte[] encodedProtectedHeaders,
              byte[] payload,
              byte[] detachedContent) {
        CborBuilder macStructure = new CborBuilder();
        ArrayBuilder<CborBuilder> array = macStructure.addArray();

        array.add("MAC0");
        array.add(encodedProtectedHeaders);

        // We currently don't support Externally Supplied Data (RFC 8152 section 4.3)
        // so external_aad is the empty bstr
        byte[] emptyExternalAad = new byte[0];
        array.add(emptyExternalAad);

        // Next field is the payload, independently of how it's transported (RFC
        // 8152 section 4.4). Since our API specifies only one of |data| and
        // |detachedContent| can be non-empty, it's simply just the non-empty one.
        if (payload != null && payload.length > 0) {
            array.add(payload);
        } else {
            array.add(detachedContent);
        }

        return cborEncode(macStructure.build().get(0));
    }

    static  
    DataItem coseMac0(  SecretKey key, byte[] data, byte[] detachedContent) {

        int dataLen = (data != null ? data.length : 0);
        int detachedContentLen = (detachedContent != null ? detachedContent.length : 0);
        if (dataLen > 0 && detachedContentLen > 0) {
            throw new IllegalArgumentException("data and detachedContent cannot both be non-empty");
        }

        CborBuilder protectedHeaders = new CborBuilder();
        MapBuilder<CborBuilder> protectedHeadersMap = protectedHeaders.addMap();
        protectedHeadersMap.put(COSE_LABEL_ALG, COSE_ALG_HMAC_256_256);
        byte[] protectedHeadersBytes = cborEncode(protectedHeaders.build().get(0));

        byte[] toBeMACed = coseBuildToBeMACed(protectedHeadersBytes, data, detachedContent);

        byte[] mac;
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(key);
            m.update(toBeMACed);
            mac = m.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unexpected error", e);
        }

        CborBuilder builder = new CborBuilder();
        ArrayBuilder<CborBuilder> array = builder.addArray();
        array.add(protectedHeadersBytes);
        /* MapBuilder<ArrayBuilder<CborBuilder>> unprotectedHeaders = */
        array.addMap();
        if (data == null || data.length == 0) {
            array.add(new SimpleValue(SimpleValueType.NULL));
        } else {
            array.add(data);
        }
        array.add(mac);

        return builder.build().get(0);
    }

    static  
    byte[] coseMac0GetTag(  DataItem coseMac0) {
        List<DataItem> items = castTo(Array.class, coseMac0).getDataItems();
        if (items.size() < 4) {
            throw new IllegalArgumentException("coseMac0 have less than 4 elements");
        }
        DataItem tagItem = items.get(3);
        return castTo(ByteString.class, tagItem).getBytes();
    }

    /**
     * Brute-force but good enough since users will only pass relatively small amounts of data.
     */
    static boolean hasSubByteArray(  byte[] haystack,   byte[] needle) {
        int n = 0;
        while (needle.length + n <= haystack.length) {
            boolean found = true;
            for (int m = 0; m < needle.length; m++) {
                if (needle[m] != haystack[n + m]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return true;
            }
            n++;
        }
        return false;
    }

    static  
    byte[] stripLeadingZeroes(  byte[] value) {
        int n = 0;
        while (n < value.length && value[n] == 0) {
            n++;
        }
        int newLen = value.length - n;
        byte[] ret = new byte[newLen];
        int m = 0;
        while (n < value.length) {
            ret[m++] = value[n++];
        }
        return ret;
    }

    /**
     * Returns #6.24(bstr) of the given already encoded CBOR
     */
    public static  
    DataItem cborBuildTaggedByteString(  byte[] encodedCbor) {
        DataItem item = new ByteString(encodedCbor);
        item.setTag(CBOR_SEMANTIC_TAG_ENCODED_CBOR);
        return item;
    }

    /**
     * For a #6.24(bstr), extracts the bytes.
     */
    public static  
    byte[] cborExtractTaggedCbor(  byte[] encodedTaggedBytestring) {
        DataItem item = cborDecode(encodedTaggedBytestring);
        ByteString itemByteString = castTo(ByteString.class, item);
        if (!item.hasTag() || item.getTag().getValue() != CBOR_SEMANTIC_TAG_ENCODED_CBOR) {
            throw new IllegalArgumentException("ByteString is not tagged with tag 24");
        }
        return itemByteString.getBytes();
    }

    /**
     * For a #6.24(bstr), extracts the bytes and decodes it and returns
     * the decoded CBOR as a DataItem.
     */
    public static  
    DataItem cborExtractTaggedAndEncodedCbor(  DataItem item) {
        ByteString itemByteString = castTo(ByteString.class, item);
        if (!item.hasTag() || item.getTag().getValue() != CBOR_SEMANTIC_TAG_ENCODED_CBOR) {
            throw new IllegalArgumentException("ByteString is not tagged with tag 24");
        }
        byte[] encodedCbor = itemByteString.getBytes();
        DataItem embeddedItem = cborDecode(encodedCbor);
        return embeddedItem;
    }

    /**
     * Returns the empty byte-array if no data is included in the structure.
     */
    static  
    byte[] coseSign1GetData(  DataItem coseSign1) {
        if (coseSign1.getMajorType() != MajorType.ARRAY) {
            throw new IllegalArgumentException("Data item is not an array");
        }
        List<DataItem> items = castTo(co.nstant.in.cbor.model.Array.class, coseSign1).getDataItems();
        if (items.size() < 4) {
            throw new IllegalArgumentException("Expected at least four items in COSE_Sign1 array");
        }
        byte[] payload = new byte[0];
        if (items.get(2).getMajorType() == MajorType.SPECIAL) {
            if (((co.nstant.in.cbor.model.Special) items.get(2)).getSpecialType()
                    != SpecialType.SIMPLE_VALUE) {
                throw new IllegalArgumentException(
                        "Item 2 (payload) is a special but not a simple value");
            }
            SimpleValue simple = castTo(co.nstant.in.cbor.model.SimpleValue.class, items.get(2));
            if (simple.getSimpleValueType() != SimpleValueType.NULL) {
                throw new IllegalArgumentException(
                        "Item 2 (payload) is a simple but not the value null");
            }
        } else if (items.get(2).getMajorType() == MajorType.BYTE_STRING) {
            payload = castTo(co.nstant.in.cbor.model.ByteString.class, items.get(2)).getBytes();
        } else {
            throw new IllegalArgumentException("Item 2 (payload) is not nil or byte-string");
        }
        return payload;
    }

    /**
     * Returns the empty collection if no x5chain is included in the structure.
     *
     * Throws exception if the given bytes aren't valid COSE_Sign1.
     */
    static  
    List<X509Certificate> coseSign1GetX5Chain(
              DataItem coseSign1) {
        ArrayList<X509Certificate> ret = new ArrayList<>();
        if (coseSign1.getMajorType() != MajorType.ARRAY) {
            throw new IllegalArgumentException("Data item is not an array");
        }
        List<DataItem> items = castTo(co.nstant.in.cbor.model.Array.class, coseSign1).getDataItems();
        if (items.size() < 4) {
            throw new IllegalArgumentException("Expected at least four items in COSE_Sign1 array");
        }
        if (items.get(1).getMajorType() != MajorType.MAP) {
            throw new IllegalArgumentException("Item 1 (unprotected headers) is not a map");
        }
        co.nstant.in.cbor.model.Map map = (co.nstant.in.cbor.model.Map) items.get(1);
        DataItem x5chainItem = map.get(new UnsignedInteger(COSE_LABEL_X5CHAIN));
        if (x5chainItem != null) {
            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                if (x5chainItem instanceof ByteString) {
                    ByteArrayInputStream certBais = new ByteArrayInputStream(
                            castTo(ByteString.class, x5chainItem).getBytes());
                    ret.add((X509Certificate) factory.generateCertificate(certBais));
                } else if (x5chainItem instanceof Array) {
                    for (DataItem certItem : castTo(Array.class, x5chainItem).getDataItems()) {
                        ByteArrayInputStream certBais = new ByteArrayInputStream(
                                castTo(ByteString.class, certItem).getBytes());
                        ret.add((X509Certificate) factory.generateCertificate(certBais));
                    }
                } else {
                    throw new IllegalArgumentException("Unexpected type for x5chain value");
                }
            } catch (CertificateException e) {
                throw new IllegalArgumentException("Unexpected error", e);
            }
        }
        return ret;
    }

    /* Encodes an integer according to Section 2.3.5 Field-Element-to-Octet-String Conversion
     * of SEC 1: Elliptic Curve Cryptography (https://www.secg.org/sec1-v2.pdf).
     */
    static byte[] sec1EncodeFieldElementAsOctetString(int octetStringSize, BigInteger fieldValue) {
        return BigIntegers.asUnsignedByteArray(octetStringSize, fieldValue);
    }

    public static  
    DataItem cborBuildCoseKey(PublicKey key) {
        ECPublicKey ecKey = (ECPublicKey) key;
        ECPoint w = ecKey.getW();
        byte[] x = sec1EncodeFieldElementAsOctetString(32, w.getAffineX());
        byte[] y = sec1EncodeFieldElementAsOctetString(32, w.getAffineY());
        DataItem item = new CborBuilder()
                .addMap()
                .put(COSE_KEY_KTY, COSE_KEY_TYPE_EC2)
                .put(COSE_KEY_EC2_CRV, COSE_KEY_EC2_CRV_P256)
                .put(COSE_KEY_EC2_X, x)
                .put(COSE_KEY_EC2_Y, y)
                .end()
                .build().get(0);
        return item;
    }

    static boolean cborMapHasKey(  DataItem map,   String key) {
        DataItem item = castTo(Map.class, map).get(new UnicodeString(key));
        return item != null;
    }

    static boolean cborMapHasKey(  DataItem map, long key) {
        DataItem keyDataItem = key >= 0 ? new UnsignedInteger(key) : new NegativeInteger(key);
        DataItem item = castTo(Map.class, map).get(keyDataItem);
        return item != null;
    }

    static long cborMapExtractNumber(  DataItem map, long key) {
        DataItem keyDataItem = key >= 0 ? new UnsignedInteger(key) : new NegativeInteger(key);
        DataItem item = castTo(Map.class, map).get(keyDataItem);
        return checkedLongValue(item);
    }

    static long cborMapExtractNumber(  DataItem map,   String key) {
        DataItem item = castTo(Map.class, map).get(new UnicodeString(key));
        return checkedLongValue(item);
    }

    static  
    String cborMapExtractString(  DataItem map,
              String key) {
        DataItem item = castTo(Map.class, map).get(new UnicodeString(key));
        return checkedStringValue(item);
    }

    static  
    String cborMapExtractString(  DataItem map, long key) {
        DataItem keyDataItem = key >= 0 ? new UnsignedInteger(key) : new NegativeInteger(key);
        DataItem item = castTo(Map.class, map).get(keyDataItem);
        return checkedStringValue(item);
    }

    static  
    List<DataItem> cborMapExtractArray(  DataItem map,
              String key) {
        DataItem item = castTo(Map.class, map).get(new UnicodeString(key));
        return castTo(Array.class, item).getDataItems();
    }

    static  
    List<DataItem> cborMapExtractArray(  DataItem map, long key) {
        DataItem keyDataItem = key >= 0 ? new UnsignedInteger(key) : new NegativeInteger(key);
        DataItem item = castTo(Map.class, map).get(keyDataItem);
        return castTo(Array.class, item).getDataItems();
    }

    static  
    DataItem cborMapExtractMap(  DataItem map,
              String key) {
        DataItem item = castTo(Map.class, map).get(new UnicodeString(key));
        return castTo(Map.class, item);
    }

    static  
    Collection<String> cborMapExtractMapStringKeys(  DataItem map) {
        List<String> ret = new ArrayList<>();
        for (DataItem item : castTo(Map.class, map).getKeys()) {
            ret.add(checkedStringValue(item));
        }
        return ret;
    }

    static  
    Collection<Long> cborMapExtractMapNumberKeys(  DataItem map) {
        List<Long> ret = new ArrayList<>();
        for (DataItem item : castTo(Map.class, map).getKeys()) {
            ret.add(checkedLongValue(item));
        }
        return ret;
    }

    static  
    byte[] cborMapExtractByteString(  DataItem map,
            long key) {
        DataItem keyDataItem = key >= 0 ? new UnsignedInteger(key) : new NegativeInteger(key);
        DataItem item = castTo(Map.class, map).get(keyDataItem);
        return castTo(ByteString.class, item).getBytes();
    }

    public static  
    byte[] cborMapExtractByteString(  DataItem map,
              String key) {
        DataItem item = castTo(Map.class, map).get(new UnicodeString(key));
        return castTo(ByteString.class, item).getBytes();
    }

    static boolean cborMapExtractBoolean(  DataItem map,   String key) {
        DataItem item = castTo(Map.class, map).get(new UnicodeString(key));
        return castTo(SimpleValue.class, item).getSimpleValueType() == SimpleValueType.TRUE;
    }

    static boolean cborMapExtractBoolean(  DataItem map, long key) {
        DataItem keyDataItem = key >= 0 ? new UnsignedInteger(key) : new NegativeInteger(key);
        DataItem item = castTo(Map.class, map).get(keyDataItem);
        return castTo(SimpleValue.class, item).getSimpleValueType() == SimpleValueType.TRUE;
    }

    static Timestamp cborMapExtractDateTime(  DataItem map, String key) {
        DataItem item = castTo(Map.class, map).get(new UnicodeString(key));
        UnicodeString unicodeString = castTo(UnicodeString.class, item);
        return cborDecodeDateTime(unicodeString);
    }

    static  
    DataItem cborMapExtract(  DataItem map,   String key) {
        DataItem item = castTo(Map.class, map).get(new UnicodeString(key));
        if (item == null) {
            throw new IllegalArgumentException("Expected item");
        }
        return item;
    }

    public static  
    PublicKey coseKeyDecode(  DataItem coseKey) {
        long kty = cborMapExtractNumber(coseKey, COSE_KEY_KTY);
        if (kty != COSE_KEY_TYPE_EC2) {
            throw new IllegalArgumentException("Expected COSE_KEY_TYPE_EC2, got " + kty);
        }
        long crv = cborMapExtractNumber(coseKey, COSE_KEY_EC2_CRV);
        if (crv != COSE_KEY_EC2_CRV_P256) {
            throw new IllegalArgumentException("Expected COSE_KEY_EC2_CRV_P256, got " + crv);
        }
        byte[] encodedX = cborMapExtractByteString(coseKey, COSE_KEY_EC2_X);
        byte[] encodedY = cborMapExtractByteString(coseKey, COSE_KEY_EC2_Y);

        BigInteger x = new BigInteger(1, encodedX);
        BigInteger y = new BigInteger(1, encodedY);

        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ecParameters = params.getParameterSpec(ECParameterSpec.class);

            ECPoint ecPoint = new ECPoint(x, y);
            ECPublicKeySpec keySpec = new ECPublicKeySpec(ecPoint, ecParameters);
            KeyFactory kf = KeyFactory.getInstance("EC");
            ECPublicKey ecPublicKey = (ECPublicKey) kf.generatePublic(keySpec);
            return ecPublicKey;

        } catch (NoSuchAlgorithmException
                | InvalidParameterSpecException
                | InvalidKeySpecException e) {
            throw new IllegalStateException("Unexpected error", e);
        }
    }

    static  
    SecretKey calcEMacKeyForReader(
              PublicKey authenticationPublicKey,
              PrivateKey ephemeralReaderPrivateKey,
              byte[] encodedSessionTranscript) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(ephemeralReaderPrivateKey);
            ka.doPhase(authenticationPublicKey, true);
            byte[] sharedSecret = ka.generateSecret();

            byte[] sessionTranscriptBytes =
                    Util.cborEncode(Util.cborBuildTaggedByteString(encodedSessionTranscript));

            byte[] salt = MessageDigest.getInstance("SHA-256").digest(sessionTranscriptBytes);
            byte[] info = new byte[]{'E', 'M', 'a', 'c', 'K', 'e', 'y'};
            byte[] derivedKey = computeHkdf("HmacSha256", sharedSecret, salt, info, 32);

            SecretKey secretKey = new SecretKeySpec(derivedKey, "");
            return secretKey;
        } catch (InvalidKeyException
                | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error performing key agreement", e);
        }
    }

    static  
    String cborPrettyPrint(  DataItem dataItem) {
        StringBuilder sb = new StringBuilder();
        cborPrettyPrintDataItem(sb, 0, dataItem);
        return sb.toString();
    }

    static  
    String cborPrettyPrint(  byte[] encodedBytes) {
        StringBuilder sb = new StringBuilder();

        ByteArrayInputStream bais = new ByteArrayInputStream(encodedBytes);
        List<DataItem> dataItems = null;
        try {
            dataItems = new CborDecoder(bais).decode();
        } catch (CborException e) {
            throw new IllegalStateException(e);
        }
        int count = 0;
        for (DataItem dataItem : dataItems) {
            if (count > 0) {
                sb.append(",\n");
            }
            cborPrettyPrintDataItem(sb, 0, dataItem);
            count++;
        }

        return sb.toString();
    }

    // Returns true iff all elements in |items| are not compound (e.g. an array or a map).
    private static boolean cborAreAllDataItemsNonCompound(  List<DataItem> items) {
        for (DataItem item : items) {
            switch (item.getMajorType()) {
                case ARRAY:
                case MAP:
                    return false;
                default:
                    // Do nothing
                    break;
            }
        }
        return true;
    }

    private static void cborPrettyPrintDataItem(  StringBuilder sb, int indent,
              DataItem dataItem) {
        StringBuilder indentBuilder = new StringBuilder();
        for (int n = 0; n < indent; n++) {
            indentBuilder.append(' ');
        }
        String indentString = indentBuilder.toString();

        if (dataItem.hasTag()) {
            sb.append(String.format(Locale.US, "tag %d ", dataItem.getTag().getValue()));
        }

        switch (dataItem.getMajorType()) {
            case INVALID:
                // TODO: throw
                sb.append("<invalid>");
                break;
            case UNSIGNED_INTEGER: {
                // Major type 0: an unsigned integer.
                BigInteger value = ((UnsignedInteger) dataItem).getValue();
                sb.append(value);
            }
            break;
            case NEGATIVE_INTEGER: {
                // Major type 1: a negative integer.
                BigInteger value = ((NegativeInteger) dataItem).getValue();
                sb.append(value);
            }
            break;
            case BYTE_STRING: {
                // Major type 2: a byte string.
                byte[] value = ((ByteString) dataItem).getBytes();
                sb.append("[");
                int count = 0;
                for (byte b : value) {
                    if (count > 0) {
                        sb.append(", ");
                    }
                    sb.append(String.format("0x%02x", b));
                    count++;
                }
                sb.append("]");
            }
            break;
            case UNICODE_STRING: {
                // Major type 3: string of Unicode characters that is encoded as UTF-8 [RFC3629].
                String value = checkedStringValue(dataItem);
                // TODO: escape ' in |value|
                sb.append("'" + value + "'");
            }
            break;
            case ARRAY: {
                // Major type 4: an array of data items.
                List<DataItem> items = ((co.nstant.in.cbor.model.Array) dataItem).getDataItems();
                if (items.size() == 0) {
                    sb.append("[]");
                } else if (cborAreAllDataItemsNonCompound(items)) {
                    // The case where everything fits on one line.
                    sb.append("[");
                    int count = 0;
                    for (DataItem item : items) {
                        cborPrettyPrintDataItem(sb, indent, item);
                        if (++count < items.size()) {
                            sb.append(", ");
                        }
                    }
                    sb.append("]");
                } else {
                    sb.append("[\n" + indentString);
                    int count = 0;
                    for (DataItem item : items) {
                        sb.append("  ");
                        cborPrettyPrintDataItem(sb, indent + 2, item);
                        if (++count < items.size()) {
                            sb.append(",");
                        }
                        sb.append("\n" + indentString);
                    }
                    sb.append("]");
                }
            }
            break;
            case MAP: {
                // Major type 5: a map of pairs of data items.
                Collection<DataItem> keys = ((co.nstant.in.cbor.model.Map) dataItem).getKeys();
                if (keys.size() == 0) {
                    sb.append("{}");
                } else {
                    sb.append("{\n" + indentString);
                    int count = 0;
                    for (DataItem key : keys) {
                        sb.append("  ");
                        DataItem value = ((co.nstant.in.cbor.model.Map) dataItem).get(key);
                        cborPrettyPrintDataItem(sb, indent + 2, key);
                        sb.append(" : ");
                        cborPrettyPrintDataItem(sb, indent + 2, value);
                        if (++count < keys.size()) {
                            sb.append(",");
                        }
                        sb.append("\n" + indentString);
                    }
                    sb.append("}");
                }
            }
            break;
            case TAG:
                // Major type 6: optional semantic tagging of other major types
                //
                // We never encounter this one since it's automatically handled via the
                // DataItem that is tagged.
                throw new IllegalStateException("Semantic tag data item not expected");

            case SPECIAL:
                // Major type 7: floating point numbers and simple data types that need no
                // content, as well as the "break" stop code.
                if (dataItem instanceof SimpleValue) {
                    switch (((SimpleValue) dataItem).getSimpleValueType()) {
                        case FALSE:
                            sb.append("false");
                            break;
                        case TRUE:
                            sb.append("true");
                            break;
                        case NULL:
                            sb.append("null");
                            break;
                        case UNDEFINED:
                            sb.append("undefined");
                            break;
                        case RESERVED:
                            sb.append("reserved");
                            break;
                        case UNALLOCATED:
                            sb.append("unallocated");
                            break;
                    }
                } else if (dataItem instanceof DoublePrecisionFloat) {
                    DecimalFormat df = new DecimalFormat("0",
                            DecimalFormatSymbols.getInstance(Locale.ENGLISH));
                    df.setMaximumFractionDigits(340);
                    sb.append(df.format(((DoublePrecisionFloat) dataItem).getValue()));
                } else if (dataItem instanceof AbstractFloat) {
                    DecimalFormat df = new DecimalFormat("0",
                            DecimalFormatSymbols.getInstance(Locale.ENGLISH));
                    df.setMaximumFractionDigits(340);
                    sb.append(df.format(((AbstractFloat) dataItem).getValue()));
                } else {
                    sb.append("break");
                }
                break;
        }
    }

    static  
    byte[] canonicalizeCbor(  byte[] encodedCbor) {
        return cborEncode(cborDecode(encodedCbor));
    }

    static  
    String replaceLine(  String text, int lineNumber,
              String replacementLine) {
        @SuppressWarnings("StringSplitter")
        String[] lines = text.split("\n");
        int numLines = lines.length;
        if (lineNumber < 0) {
            lineNumber = numLines - -lineNumber;
        }
        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < numLines; n++) {
            if (n == lineNumber) {
                sb.append(replacementLine);
            } else {
                sb.append(lines[n]);
            }
            // Only add terminating newline if passed-in string ends in a newline.
            if (n == numLines - 1) {
                if (text.endsWith("\n")) {
                    sb.append('\n');
                }
            } else {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Helper function to create a CBOR data for requesting data items. The IntentToRetain
     * value will be set to false for all elements.
     *
     * <p>The returned CBOR data conforms to the following CDDL schema:</p>
     *
     * <pre>
     *   ItemsRequest = {
     *     ? "docType" : DocType,
     *     "nameSpaces" : NameSpaces,
     *     ? "RequestInfo" : {* tstr => any} ; Additional info the reader wants to provide
     *   }
     *
     *   NameSpaces = {
     *     + NameSpace => DataElements     ; Requested data elements for each NameSpace
     *   }
     *
     *   DataElements = {
     *     + DataElement => IntentToRetain
     *   }
     *
     *   DocType = tstr
     *
     *   DataElement = tstr
     *   IntentToRetain = bool
     *   NameSpace = tstr
     * </pre>
     *
     * @param entriesToRequest The entries to request, organized as a map of namespace
     *                         names with each value being a collection of data elements
     *                         in the given namespace.
     * @param docType          The document type or {@code null} if there is no document
     *                         type.
     * @return CBOR data conforming to the CDDL mentioned above.
     *
     * TODO: docType is no longer optional so change docType to be NonNull and update all callers.
     */
    static  
    byte[] createItemsRequest(
              java.util.Map<String, Collection<String>> entriesToRequest, String docType) {
        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> mapBuilder = builder.addMap();
        if (docType != null) {
            mapBuilder.put("docType", docType);
        }

        MapBuilder<MapBuilder<CborBuilder>> nsMapBuilder = mapBuilder.putMap("nameSpaces");
        for (String namespaceName : entriesToRequest.keySet()) {
            Collection<String> entryNames = entriesToRequest.get(namespaceName);
            MapBuilder<MapBuilder<MapBuilder<CborBuilder>>> entryNameMapBuilder =
                    nsMapBuilder.putMap(namespaceName);
            for (String entryName : entryNames) {
                entryNameMapBuilder.put(entryName, false);
            }
        }
        return cborEncode(builder.build().get(0));
    }

    static
    byte[] getPopSha256FromAuthKeyCert(  X509Certificate cert) {
        byte[] octetString = cert.getExtensionValue("1.3.6.1.4.1.11129.2.1.26");
        if (octetString == null) {
            return null;
        }
        try {
            ASN1InputStream asn1InputStream = new ASN1InputStream(octetString);
            byte[] cborBytes = ((ASN1OctetString) asn1InputStream.readObject()).getOctets();

            ByteArrayInputStream bais = new ByteArrayInputStream(cborBytes);
            List<DataItem> dataItems = new CborDecoder(bais).decode();
            if (dataItems.size() != 1) {
                throw new IllegalArgumentException("Expected 1 item, found " + dataItems.size());
            }
            Array array = castTo(Array.class, dataItems.get(0));
            List<DataItem> items = array.getDataItems();
            if (items.size() < 2) {
                throw new IllegalArgumentException(
                        "Expected at least 2 array items, found " + items.size());
            }
            String id = checkedStringValue(items.get(0));
            if (!id.equals("ProofOfBinding")) {
                throw new IllegalArgumentException("Expected ProofOfBinding, got " + id);
            }
            byte[] popSha256 = castTo(ByteString.class, items.get(1)).getBytes();
            if (popSha256.length != 32) {
                throw new IllegalArgumentException(
                        "Expected bstr to be 32 bytes, it is " + popSha256.length);
            }
            return popSha256;
        } catch (IOException e) {
            throw new IllegalArgumentException("Error decoding extension data", e);
        } catch (CborException e) {
            throw new IllegalArgumentException("Error decoding data", e);
        }
    }

    /**
     * Clears elementValue in IssuerSignedItemBytes CBOR.
     *
     * @param encodedIssuerSignedItem encoded CBOR conforming to IssuerSignedItem.
     * @return Same as given CBOR but with elementValue set to NULL.
     */
    static  
    byte[] issuerSignedItemClearValue(  byte[] encodedIssuerSignedItem) {
        byte[] encodedNullValue = Util.cborEncode(SimpleValue.NULL);
        return issuerSignedItemSetValue(encodedIssuerSignedItem, encodedNullValue);
    }

    /**
     * Sets elementValue in IssuerSignedItem CBOR.
     *
     * Throws if the given encodedIssuerSignedItemBytes isn't IssuersignedItemBytes.
     *
     * @param encodedIssuerSignedItem      encoded CBOR conforming to IssuerSignedItem.
     * @param encodedElementValue          the value to set elementValue to.
     * @return Same as given CBOR but with elementValue set to given value.
     */
    static  
    byte[] issuerSignedItemSetValue(
              byte[] encodedIssuerSignedItem,
              byte[] encodedElementValue) {
        DataItem issuerSignedItemElem = Util.cborDecode(encodedIssuerSignedItem);
        Map issuerSignedItem = castTo(Map.class, issuerSignedItemElem);
        DataItem elementValue = Util.cborDecode(encodedElementValue);
        issuerSignedItem.put(new UnicodeString("elementValue"), elementValue);

        // By using the non-canonical encoder the order is preserved.
        return Util.cborEncodeWithoutCanonicalizing(issuerSignedItem);
    }

    public static  
    PrivateKey getPrivateKeyFromInteger(  BigInteger s) {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ecParameters = params.getParameterSpec(ECParameterSpec.class);

            ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(s, ecParameters);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePrivate(privateKeySpec);

        } catch (NoSuchAlgorithmException
                | InvalidParameterSpecException
                | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    public static  
    PublicKey getPublicKeyFromIntegers(  BigInteger x,
              BigInteger y) {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ecParameters = params.getParameterSpec(ECParameterSpec.class);

            ECPoint ecPoint = new ECPoint(x, y);
            ECPublicKeySpec keySpec = new ECPublicKeySpec(ecPoint, ecParameters);
            KeyFactory kf = KeyFactory.getInstance("EC");
            ECPublicKey ecPublicKey = (ECPublicKey) kf.generatePublic(keySpec);
            return ecPublicKey;
        } catch (NoSuchAlgorithmException
                | InvalidParameterSpecException
                | InvalidKeySpecException e) {
            throw new IllegalStateException("Unexpected error", e);
        }
    }

    // Returns null on End Of Stream.
    //
    static
    ByteBuffer readBytes(  InputStream inputStream, int numBytes)
            throws IOException {
        ByteBuffer data = ByteBuffer.allocate(numBytes);
        int offset = 0;
        int numBytesRemaining = numBytes;
        while (numBytesRemaining > 0) {
            int numRead = inputStream.read(data.array(), offset, numBytesRemaining);
            if (numRead == -1) {
                return null;
            }
            if (numRead == 0) {
                throw new IllegalStateException("read() returned zero bytes");
            }
            numBytesRemaining -= numRead;
            offset += numRead;
        }
        return data;
    }

    // TODO: Maybe return List<DataItem> instead of reencoding.
    //
    static  
    List<byte[]> extractDeviceRetrievalMethods(
              byte[] encodedDeviceEngagement) {
        List<byte[]> ret = new ArrayList<>();
        DataItem deviceEngagement = Util.cborDecode(encodedDeviceEngagement);
        List<DataItem> methods = Util.cborMapExtractArray(deviceEngagement, 2);
        for (DataItem method : methods) {
            ret.add(Util.cborEncode(method));
        }
        return ret;
    }

    static long getDeviceRetrievalMethodType(  byte[] encodeDeviceRetrievalMethod) {
        List<DataItem> di = ((Array) Util.cborDecode(encodeDeviceRetrievalMethod)).getDataItems();
        return checkedLongValue(di.get(0));
    }

    static   KeyPair createEphemeralKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
            kpg.initialize(ecSpec);
            KeyPair keyPair = kpg.generateKeyPair();
            return keyPair;
        } catch (NoSuchAlgorithmException
                | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Error generating ephemeral key-pair", e);
        }
    }

    static  
    X509Certificate signPublicKeyWithPrivateKey(  String keyToSignAlias,
              String keyToSignWithAlias) {
        KeyStore ks = null;
        try {
            ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);

            /* First note that KeyStore.getCertificate() returns a self-signed X.509 certificate
             * for the key in question. As per RFC 5280, section 4.1 an X.509 certificate has the
             * following structure:
             *
             *   Certificate  ::=  SEQUENCE  {
             *        tbsCertificate       TBSCertificate,
             *        signatureAlgorithm   AlgorithmIdentifier,
             *        signatureValue       BIT STRING  }
             *
             * Conveniently, the X509Certificate class has a getTBSCertificate() method which
             * returns the tbsCertificate blob. So all we need to do is just sign that and build
             * signatureAlgorithm and signatureValue and combine it with tbsCertificate. We don't
             * need a full-blown ASN.1/DER encoder to do this.
             */
            X509Certificate selfSignedCert = (X509Certificate) ks.getCertificate(keyToSignAlias);
            byte[] tbsCertificate = selfSignedCert.getTBSCertificate();

            KeyStore.Entry keyToSignWithEntry = ks.getEntry(keyToSignWithAlias, null);
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(((KeyStore.PrivateKeyEntry) keyToSignWithEntry).getPrivateKey());
            s.update(tbsCertificate);
            byte[] signatureValue = s.sign();

            /* The DER encoding for a SEQUENCE of length 128-65536 - the length is updated below.
             *
             * We assume - and test for below - that the final length is always going to be in
             * this range. This is a sound assumption given we're using 256-bit EC keys.
             */
            byte[] sequence = new byte[]{
                    0x30, (byte) 0x82, 0x00, 0x00
            };

            /* The DER encoding for the ECDSA with SHA-256 signature algorithm:
             *
             *   SEQUENCE (1 elem)
             *      OBJECT IDENTIFIER 1.2.840.10045.4.3.2 ecdsaWithSHA256 (ANSI X9.62 ECDSA
             *      algorithm with SHA256)
             */
            byte[] signatureAlgorithm = new byte[]{
                    0x30, 0x0a, 0x06, 0x08, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x04, 0x03,
                    0x02
            };

            /* The DER encoding for a BIT STRING with one element - the length is updated below.
             *
             * We assume the length of signatureValue is always going to be less than 128. This
             * assumption works since we know ecdsaWithSHA256 signatures are always 69, 70, or
             * 71 bytes long when DER encoded.
             */
            byte[] bitStringForSignature = new byte[]{0x03, 0x00, 0x00};

            // Calculate sequence length and set it in |sequence|.
            int sequenceLength = tbsCertificate.length
                    + signatureAlgorithm.length
                    + bitStringForSignature.length
                    + signatureValue.length;
            if (sequenceLength < 128 || sequenceLength > 65535) {
                throw new IllegalStateException("Unexpected sequenceLength " + sequenceLength);
            }
            sequence[2] = (byte) (sequenceLength >> 8);
            sequence[3] = (byte) (sequenceLength & 0xff);

            // Calculate signatureValue length and set it in |bitStringForSignature|.
            int signatureValueLength = signatureValue.length + 1;
            if (signatureValueLength >= 128) {
                throw new IllegalStateException("Unexpected signatureValueLength "
                        + signatureValueLength);
            }
            bitStringForSignature[1] = (byte) signatureValueLength;

            // Finally concatenate everything together.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(sequence);
            baos.write(tbsCertificate);
            baos.write(signatureAlgorithm);
            baos.write(bitStringForSignature);
            baos.write(signatureValue);
            byte[] resultingCertBytes = baos.toByteArray();

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream bais = new ByteArrayInputStream(resultingCertBytes);
            X509Certificate result = (X509Certificate) cf.generateCertificate(bais);
            return result;
        } catch (IOException
                | InvalidKeyException
                | KeyStoreException
                | NoSuchAlgorithmException
                | SignatureException
                | UnrecoverableEntryException
                | CertificateException e) {
            throw new IllegalStateException("Error signing key with private key", e);
        }
    }

    // This returns a SessionTranscript which satisfy the requirement
    // that the uncompressed X and Y coordinates of the key for the
    // mDL's ephemeral key-pair appear somewhere in the encoded
    // DeviceEngagement.
    //
    // TODO: rename to buildFakeSessionTranscript().
    //
    static   byte[] buildSessionTranscript(  KeyPair ephemeralKeyPair) {
        // Make the coordinates appear in an already encoded bstr - this
        // mimics how the mDL COSE_Key appear as encoded data inside the
        // encoded DeviceEngagement
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ECPoint w = ((ECPublicKey) ephemeralKeyPair.getPublic()).getW();
            // X and Y are always positive so for interop we remove any leading zeroes
            // inserted by the BigInteger encoder.
            byte[] x = stripLeadingZeroes(w.getAffineX().toByteArray());
            byte[] y = stripLeadingZeroes(w.getAffineY().toByteArray());
            baos.write(new byte[]{42});
            baos.write(x);
            baos.write(y);
            baos.write(new byte[]{43, 44});
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        byte[] blobWithCoords = baos.toByteArray();

        DataItem encodedDeviceEngagementItem = cborBuildTaggedByteString(
                cborEncode(new CborBuilder()
                        .addArray()
                        .add(blobWithCoords)
                        .end()
                        .build().get(0)));
        DataItem encodedEReaderKeyItem =
                cborBuildTaggedByteString(cborEncodeString("doesn't matter"));

        baos = new ByteArrayOutputStream();
        try {
            byte[] handoverSelectBytes = new byte[]{0x01, 0x02, 0x03};
            DataItem handover = new CborBuilder()
                    .addArray()
                    .add(handoverSelectBytes)
                    .add(SimpleValue.NULL)
                    .end()
                    .build().get(0);
            new CborEncoder(baos).encode(new CborBuilder()
                    .addArray()
                    .add(encodedDeviceEngagementItem)
                    .add(encodedEReaderKeyItem)
                    .add(handover)
                    .end()
                    .build());
        } catch (CborException e) {
            e.printStackTrace();
            return null;
        }
        return baos.toByteArray();
    }

    /**
     * Helper to determine the length of a single encoded CBOR data item.
     *
     * <p>This is used for handling 18013-5:2021 L2CAP data where messages are not separated
     * by any framing.
     *
     * @param data data with a single encoded CBOR data item and possibly more
     * @return -1 if no single encoded CBOR data item could be found, otherwise the length of the
     *    CBOR data that was decoded.
     */
    static int cborGetLength(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataItem dataItem = null;
        try {
            dataItem = new CborDecoder(bais).decodeNext();
        } catch (CborException e) {
            return -1;
        }
        if (dataItem == null) {
            return -1;
        }
        return cborEncodeWithoutCanonicalizing(dataItem).length;
    }

    /**
     * Extracts the first CBOR data item from a stream of bytes.
     *
     * <p>If a data item was found, returns the bytes and removes it from the given output stream.
     *
     * @param pendingDataBaos A {@link ByteArrayOutputStream} with incoming bytes which must all
     *                        be valid CBOR.
     * @return the bytes of the first CBOR data item or {@code null} if not enough bytes have
     *   been received.
     */
    static byte[] cborExtractFirstDataItem(  ByteArrayOutputStream pendingDataBaos) {
        byte[] pendingData = pendingDataBaos.toByteArray();
        int dataItemLength = Util.cborGetLength(pendingData);
        if (dataItemLength == -1) {
            return null;
        }
        byte[] dataItemBytes = new byte[dataItemLength];
        System.arraycopy(pendingDataBaos.toByteArray(),0, dataItemBytes, 0, dataItemLength);
        pendingDataBaos.reset();
        pendingDataBaos.write(pendingData, dataItemLength, pendingData.length - dataItemLength);
        return dataItemBytes;
    }

    static
    DataItem coseSign1Sign(Signature s, byte[] data, byte[] detachedContent, Collection<X509Certificate> certificateChain) {

        int dataLen = (data != null ? data.length : 0);
        int detachedContentLen = (detachedContent != null ? detachedContent.length : 0);
        if (dataLen > 0 && detachedContentLen > 0) {
            throw new IllegalArgumentException("data and detachedContent cannot both be non-empty");
        }

        int keySize;
        long alg;
        if (s.getAlgorithm().equals("SHA256withECDSA")) {
            keySize = 32;
            alg = COSE_ALG_ECDSA_256;
        } else if (s.getAlgorithm().equals("SHA384withECDSA")) {
            keySize = 48;
            alg = COSE_ALG_ECDSA_384;
        } else if (s.getAlgorithm().equals("SHA512withECDSA")) {
            keySize = 64;
            alg = COSE_ALG_ECDSA_512;
        } else {
            throw new IllegalArgumentException("Unsupported algorithm " + s.getAlgorithm());
        }

        CborBuilder protectedHeaders = new CborBuilder();
        MapBuilder<CborBuilder> protectedHeadersMap = protectedHeaders.addMap();
        protectedHeadersMap.put(COSE_LABEL_ALG, alg);
        byte[] protectedHeadersBytes = cborEncode(protectedHeaders.build().get(0));

        byte[] toBeSigned = coseBuildToBeSigned(protectedHeadersBytes, data, detachedContent);

        byte[] coseSignature = null;
        try {
            s.update(toBeSigned);
            byte[] derSignature = s.sign();
            coseSignature = signatureDerToCose(derSignature, keySize);
        } catch (SignatureException e) {
            throw new IllegalStateException("Error signing data", e);
        }

        CborBuilder builder = new CborBuilder();
        ArrayBuilder<CborBuilder> array = builder.addArray();
        array.add(protectedHeadersBytes);
        MapBuilder<ArrayBuilder<CborBuilder>> unprotectedHeaders = array.addMap();
        try {
            if (certificateChain != null && certificateChain.size() > 0) {
                if (certificateChain.size() == 1) {
                    X509Certificate cert = certificateChain.iterator().next();
                    unprotectedHeaders.put(COSE_LABEL_X5CHAIN, cert.getEncoded());
                } else {
                    ArrayBuilder<MapBuilder<ArrayBuilder<CborBuilder>>> x5chainsArray =
                            unprotectedHeaders.putArray(COSE_LABEL_X5CHAIN);
                    for (X509Certificate cert : certificateChain) {
                        x5chainsArray.add(cert.getEncoded());
                    }
                }
            }
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("Error encoding certificate", e);
        }
        if (data == null || data.length == 0) {
            array.add(new SimpleValue(SimpleValueType.NULL));
        } else {
            array.add(data);
        }
        array.add(coseSignature);

        return builder.build().get(0);
    }

    /**
     * Note: this uses the default JCA provider which may not support a lot of curves, for
     * example it doesn't support Brainpool curves. If you need to use such curves, use
     * {@link #coseSign1Sign(Signature, byte[], byte[], Collection)} instead with a
     * Signature created using a provider that does have support.
     *
     * Currently only ECDSA signatures are supported.
     *
     * TODO: add support and tests for Ed25519 and Ed448.
     */
    static
    DataItem coseSign1Sign(PrivateKey key, String algorithm, byte[] data, byte[] additionalData, Collection<X509Certificate> certificateChain) {
        try {
            Signature s = Signature.getInstance(algorithm);
            s.initSign(key);
            return coseSign1Sign(s, data, additionalData, certificateChain);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Caught exception", e);
        }
    }

    private static byte[] signatureDerToCose(byte[] signature, int keySize) {
        ASN1Primitive asn1;
        try {
            asn1 = new ASN1InputStream(new ByteArrayInputStream(signature)).readObject();
        } catch (IOException e) {
            throw new IllegalArgumentException("Error decoding DER signature", e);
        }
        ASN1Encodable[] asn1Encodables = castTo(ASN1Sequence.class, asn1).toArray();
        if (asn1Encodables.length != 2) {
            throw new IllegalArgumentException("Expected two items in sequence");
        }
        BigInteger r = castTo(ASN1Integer.class, asn1Encodables[0].toASN1Primitive()).getValue();
        BigInteger s = castTo(ASN1Integer.class, asn1Encodables[1].toASN1Primitive()).getValue();

        byte[] rBytes = stripLeadingZeroes(r.toByteArray());
        byte[] sBytes = stripLeadingZeroes(s.toByteArray());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            for (int n = 0; n < keySize - rBytes.length; n++) {
                baos.write(0x00);
            }
            baos.write(rBytes);
            for (int n = 0; n < keySize - sBytes.length; n++) {
                baos.write(0x00);
            }
            baos.write(sBytes);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return baos.toByteArray();
    }
}