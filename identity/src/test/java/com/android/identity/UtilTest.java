/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.identity;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.security.keystore.KeyProperties;

import androidx.test.filters.MediumTest;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.Random;
import java.util.TimeZone;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.DoublePrecisionFloat;
import co.nstant.in.cbor.model.HalfPrecisionFloat;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.SimpleValueType;
import co.nstant.in.cbor.model.SinglePrecisionFloat;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

@MediumTest
@SuppressWarnings("deprecation")
public class UtilTest {

    @Before
    public void setUp() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @After
    public void tearDown() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
    }

    @Test
    public void prettyPrintMultipleCompleteTypes() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .add("text")                // add string
                .add(1234)                  // add integer
                .add(new byte[]{0x10})   // add byte array
                .addArray()                 // add array
                .add(1)
                .add("text")
                .end()
                .build());
        assertEquals("'text',\n"
                + "1234,\n"
                + "[0x10],\n"
                + "[1, 'text']", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintString() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new UnicodeString("foobar"));
        assertEquals("'foobar'", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintBytestring() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new ByteString(new byte[]{1, 2, 33, (byte) 254}));
        assertEquals("[0x01, 0x02, 0x21, 0xfe]", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintUnsignedInteger() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new UnsignedInteger(42));
        assertEquals("42", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintNegativeInteger() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new NegativeInteger(-42));
        assertEquals("-42", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintDouble() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new DoublePrecisionFloat(1.1));
        assertEquals("1.1", Util.cborPrettyPrint(baos.toByteArray()));

        baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new DoublePrecisionFloat(-42.0000000001));
        assertEquals("-42.0000000001", Util.cborPrettyPrint(baos.toByteArray()));

        baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new DoublePrecisionFloat(-5));
        assertEquals("-5", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintFloat() throws CborException {
        ByteArrayOutputStream baos;

        // TODO: These two tests yield different results on different devices, disable for now
        /*
        baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new SinglePrecisionFloat(1.1f));
        assertEquals("1.100000023841858", SUtil.cborPrettyPrint(baos.toByteArray()));

        baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new SinglePrecisionFloat(-42.0001f));
        assertEquals("-42.000099182128906", SUtil.cborPrettyPrint(baos.toByteArray()));
        */

        baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new SinglePrecisionFloat(-5f));
        assertEquals("-5", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintHalfFloat() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new HalfPrecisionFloat(1.1f));
        assertEquals("1.099609375", Util.cborPrettyPrint(baos.toByteArray()));

        baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new HalfPrecisionFloat(-42.0001f));
        assertEquals("-42", Util.cborPrettyPrint(baos.toByteArray()));

        baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new HalfPrecisionFloat(-5f));
        assertEquals("-5", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintFalse() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new SimpleValue(SimpleValueType.FALSE));
        assertEquals("false", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintTrue() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new SimpleValue(SimpleValueType.TRUE));
        assertEquals("true", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintNull() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new SimpleValue(SimpleValueType.NULL));
        assertEquals("null", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintUndefined() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new SimpleValue(SimpleValueType.UNDEFINED));
        assertEquals("undefined", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintTag() throws CborException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addTag(0)
                .add("ABC")
                .build());
        byte[] data = baos.toByteArray();
        assertEquals("tag 0 'ABC'", Util.cborPrettyPrint(data));
    }

    @Test
    public void prettyPrintArrayNoCompounds() throws CborException {
        // If an array has no compound elements, no newlines are used.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addArray()                 // add array
                .add(1)
                .add("text")
                .add(new ByteString(new byte[]{1, 2, 3}))
                .end()
                .build());
        assertEquals("[1, 'text', [0x01, 0x02, 0x03]]", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintArray() throws CborException {
        // This array contains a compound value so will use newlines
        CborBuilder array = new CborBuilder();
        ArrayBuilder<CborBuilder> arrayBuilder = array.addArray();
        arrayBuilder.add(2);
        arrayBuilder.add(3);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addArray()                 // add array
                .add(1)
                .add("text")
                .add(new ByteString(new byte[]{1, 2, 3}))
                .add(array.build().get(0))
                .end()
                .build());
        assertEquals("[\n"
                + "  1,\n"
                + "  'text',\n"
                + "  [0x01, 0x02, 0x03],\n"
                + "  [2, 3]\n"
                + "]", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void prettyPrintMap() throws CborException {
        // If an array has no compound elements, no newlines are used.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addMap()
                .put("Foo", 42)
                .put("Bar", "baz")
                .put(43, 44)
                .put(new UnicodeString("bstr"), new ByteString(new byte[]{1, 2, 3}))
                .put(new ByteString(new byte[]{1, 2, 3}), new UnicodeString("other way"))
                .end()
                .build());
        assertEquals("{\n"
                + "  43 : 44,\n"
                + "  [0x01, 0x02, 0x03] : 'other way',\n"
                + "  'Bar' : 'baz',\n"
                + "  'Foo' : 42,\n"
                + "  'bstr' : [0x01, 0x02, 0x03]\n"
                + "}", Util.cborPrettyPrint(baos.toByteArray()));
    }

    @Test
    public void cborEncodeDecode() {
        // TODO: add better coverage and check specific encoding etc.
        assertEquals(42, Util.cborDecodeLong(Util.cborEncodeNumber(42)));
        assertEquals(123456, Util.cborDecodeLong(Util.cborEncodeNumber(123456)));
        assertFalse(Util.cborDecodeBoolean(Util.cborEncodeBoolean(false)));
        assertTrue(Util.cborDecodeBoolean(Util.cborEncodeBoolean(true)));
    }

    @Test
    public void cborEncodeDecodeCalendar() throws CborException {
        GregorianCalendar c;
        byte[] data;

        c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(2019, Calendar.JULY, 8, 11, 51, 42);
        data = Util.cborEncodeDateTime(Timestamp.ofEpochMilli(c.getTimeInMillis()));
        assertEquals("tag 0 '2019-07-08T11:51:42Z'", Util.cborPrettyPrint(data));
        assertEquals("tag 0 '2019-07-08T11:51:42Z'",
                Util.cborPrettyPrint(Util.cborEncodeDateTime(Util.cborDecodeDateTime(data))));
        assertEquals(c.getTimeInMillis(), Util.cborDecodeDateTime(data).toEpochMilli());

        c = new GregorianCalendar(TimeZone.getTimeZone("GMT-04:00"));
        c.clear();
        c.set(2019, Calendar.JULY, 8, 11, 51, 42);
        data = Util.cborEncodeDateTime(Timestamp.ofEpochMilli(c.getTimeInMillis()));
        assertEquals("tag 0 '2019-07-08T15:51:42Z'", Util.cborPrettyPrint(data));
        assertEquals("tag 0 '2019-07-08T15:51:42Z'",
                Util.cborPrettyPrint(Util.cborEncodeDateTime(Util.cborDecodeDateTime(data))));
        assertEquals(c.getTimeInMillis(), Util.cborDecodeDateTime(data).toEpochMilli());

        c = new GregorianCalendar(TimeZone.getTimeZone("GMT-08:00"));
        c.clear();
        c.set(2019, Calendar.JULY, 8, 11, 51, 42);
        data = Util.cborEncodeDateTime(Timestamp.ofEpochMilli(c.getTimeInMillis()));
        assertEquals("tag 0 '2019-07-08T19:51:42Z'", Util.cborPrettyPrint(data));
        assertEquals("tag 0 '2019-07-08T19:51:42Z'",
                Util.cborPrettyPrint(Util.cborEncodeDateTime(Util.cborDecodeDateTime(data))));
        assertEquals(c.getTimeInMillis(), Util.cborDecodeDateTime(data).toEpochMilli());

        c = new GregorianCalendar(TimeZone.getTimeZone("GMT+04:30"));
        c.clear();
        c.set(2019, Calendar.JULY, 8, 11, 51, 42);
        data = Util.cborEncodeDateTime(Timestamp.ofEpochMilli(c.getTimeInMillis()));
        assertEquals("tag 0 '2019-07-08T07:21:42Z'", Util.cborPrettyPrint(data));
        assertEquals("tag 0 '2019-07-08T07:21:42Z'",
                Util.cborPrettyPrint(Util.cborEncodeDateTime(Util.cborDecodeDateTime(data))));
        assertEquals(c.getTimeInMillis(), Util.cborDecodeDateTime(data).toEpochMilli());
    }

    @Test
    public void cborCalendarMilliseconds() throws CborException {
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(2019, Calendar.JULY, 8, 11, 51, 42);
        c.set(Calendar.MILLISECOND, 123);
        // Even if we see a time with fractional seconds, we ignore them
        byte[] data = Util.cborEncodeDateTime(Timestamp.ofEpochMilli(c.getTimeInMillis()));
        assertEquals("tag 0 '2019-07-08T11:51:42Z'", Util.cborPrettyPrint(data));
        assertEquals("tag 0 '2019-07-08T11:51:42Z'",
                Util.cborPrettyPrint(Util.cborEncodeDateTime(Util.cborDecodeDateTime(data))));
        assertEquals(c.getTimeInMillis() - 123, Util.cborDecodeDateTime(data).toEpochMilli());
    }

    @Test
    public void cborCalendarForeign() throws CborException {
        ByteArrayOutputStream baos;
        byte[] data;

        // milliseconds, non-standard format
        baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addTag(0)
                .add("2019-07-08T11:51:42.25Z")
                .build());
        data = baos.toByteArray();
        assertEquals("tag 0 '2019-07-08T11:51:42Z'",
                Util.cborPrettyPrint(Util.cborEncodeDateTime(Util.cborDecodeDateTime(data))));

        // milliseconds set to 0
        baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addTag(0)
                .add("2019-07-08T11:51:42.0Z")
                .build());
        data = baos.toByteArray();
        assertEquals("tag 0 '2019-07-08T11:51:42Z'",
                Util.cborPrettyPrint(Util.cborEncodeDateTime(Util.cborDecodeDateTime(data))));

        // we only support millisecond-precision
        baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addTag(0)
                .add("2019-07-08T11:51:42.9876Z")
                .build());
        data = baos.toByteArray();
        assertEquals("tag 0 '2019-07-08T11:51:42Z'",
                Util.cborPrettyPrint(Util.cborEncodeDateTime(Util.cborDecodeDateTime(data))));

        // milliseconds and timezone
        baos = new ByteArrayOutputStream();
        new CborEncoder(baos).encode(new CborBuilder()
                .addTag(0)
                .add("2019-07-08T11:51:42.26-11:30")
                .build());
        data = baos.toByteArray();
        assertEquals("tag 0 '2019-07-08T23:21:42Z'",
                Util.cborPrettyPrint(Util.cborEncodeDateTime(Util.cborDecodeDateTime(data))));
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(new ECGenParameterSpec("secp256k1"));
        return kpg.generateKeyPair();
    }

    @Test
    public void coseSignAndVerify_P256() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                new BouncyCastleProvider());
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        KeyPair keyPair = kpg.generateKeyPair();

        byte[] data = new byte[]{0x10, 0x11, 0x12, 0x13};
        byte[] detachedContent = new byte[]{};
        DataItem sig = Util.coseSign1Sign(keyPair.getPrivate(), "SHA256withECDSA", data,
                detachedContent, null);
        assertTrue(Util.coseSign1CheckSignature(sig, detachedContent, keyPair.getPublic()));
        assertArrayEquals(data, Util.coseSign1GetData(sig));
        assertEquals(0, Util.coseSign1GetX5Chain(sig).size());
    }

    @Test
    public void coseSignAndVerify_P384() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                new BouncyCastleProvider());
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("secp384r1");
        kpg.initialize(ecSpec);
        KeyPair keyPair = kpg.generateKeyPair();

        byte[] data = new byte[]{0x10, 0x11, 0x12, 0x13};
        byte[] detachedContent = new byte[]{};
        DataItem sig = Util.coseSign1Sign(keyPair.getPrivate(), "SHA384withECDSA", data,
                detachedContent, null);
        assertTrue(Util.coseSign1CheckSignature(sig, detachedContent, keyPair.getPublic()));
        assertArrayEquals(data, Util.coseSign1GetData(sig));
        assertEquals(0, Util.coseSign1GetX5Chain(sig).size());
    }

    /* TODO: investigate why this test is flaky */
    @Ignore
    @Test
    public void coseSignAndVerify_P521() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                new BouncyCastleProvider());
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("secp521r1");
        kpg.initialize(ecSpec);
        KeyPair keyPair = kpg.generateKeyPair();

        byte[] data = new byte[]{0x10, 0x11, 0x12, 0x13};
        byte[] detachedContent = new byte[]{};
        DataItem sig = Util.coseSign1Sign(keyPair.getPrivate(), "SHA512withECDSA", data,
                detachedContent, null);
        assertTrue(Util.coseSign1CheckSignature(sig, detachedContent, keyPair.getPublic()));
        assertArrayEquals(data, Util.coseSign1GetData(sig));
        assertEquals(0, Util.coseSign1GetX5Chain(sig).size());
    }

    @Test
    public void coseSignAndVerify_brainpoolP256r1() throws Exception {
        BouncyCastleProvider bcProvider = new BouncyCastleProvider();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                bcProvider);
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("brainpoolP256r1");
        kpg.initialize(ecSpec);
        KeyPair keyPair = kpg.generateKeyPair();

        byte[] data = new byte[]{0x10, 0x11, 0x12, 0x13};
        byte[] detachedContent = new byte[]{};

        Signature s = Signature.getInstance("SHA256withECDSA", bcProvider);
        s.initSign(keyPair.getPrivate());

        DataItem sig = Util.coseSign1Sign(s, data, detachedContent, null);
        assertTrue(Util.coseSign1CheckSignature(sig, detachedContent, keyPair.getPublic()));
        assertArrayEquals(data, Util.coseSign1GetData(sig));
        assertEquals(0, Util.coseSign1GetX5Chain(sig).size());
    }

    @Test
    public void coseSignAndVerify_brainpoolP384r1() throws Exception {
        BouncyCastleProvider bcProvider = new BouncyCastleProvider();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                bcProvider);
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("brainpoolP384r1");
        kpg.initialize(ecSpec);
        KeyPair keyPair = kpg.generateKeyPair();

        byte[] data = new byte[]{0x10, 0x11, 0x12, 0x13};
        byte[] detachedContent = new byte[]{};

        Signature s = Signature.getInstance("SHA384withECDSA", bcProvider);
        s.initSign(keyPair.getPrivate());

        DataItem sig = Util.coseSign1Sign(s, data, detachedContent, null);
        assertTrue(Util.coseSign1CheckSignature(sig, detachedContent, keyPair.getPublic()));
        assertArrayEquals(data, Util.coseSign1GetData(sig));
        assertEquals(0, Util.coseSign1GetX5Chain(sig).size());
    }

    @Test
    public void coseSignAndVerify_brainpoolP512r1() throws Exception {
        BouncyCastleProvider bcProvider = new BouncyCastleProvider();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                bcProvider);
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("brainpoolP512r1");
        kpg.initialize(ecSpec);
        KeyPair keyPair = kpg.generateKeyPair();

        byte[] data = new byte[]{0x10, 0x11, 0x12, 0x13};
        byte[] detachedContent = new byte[]{};

        Signature s = Signature.getInstance("SHA512withECDSA", bcProvider);
        s.initSign(keyPair.getPrivate());

        DataItem sig = Util.coseSign1Sign(s, data, detachedContent, null);
        assertTrue(Util.coseSign1CheckSignature(sig, detachedContent, keyPair.getPublic()));
        assertArrayEquals(data, Util.coseSign1GetData(sig));
        assertEquals(0, Util.coseSign1GetX5Chain(sig).size());
    }

    // TODO: add tests for Curve25519 and Curve448 curves.

    @Test
    public void coseSignAndVerifyDetachedContent() throws Exception {
        KeyPair keyPair = generateKeyPair();
        byte[] data = new byte[]{};
        byte[] detachedContent = new byte[]{0x20, 0x21, 0x22, 0x23, 0x24};
        DataItem sig = Util.coseSign1Sign(keyPair.getPrivate(), "SHA256withECDSA", data,
                detachedContent, null);
        assertTrue(Util.coseSign1CheckSignature(sig, detachedContent, keyPair.getPublic()));
        assertArrayEquals(data, Util.coseSign1GetData(sig));
        assertEquals(0, Util.coseSign1GetX5Chain(sig).size());
    }

    @Test
    public void coseSignAndVerifySingleCertificate() throws Exception {
        KeyPair keyPair = generateKeyPair();
        byte[] data = new byte[]{};
        byte[] detachedContent = new byte[]{0x20, 0x21, 0x22, 0x23, 0x24};
        LinkedList<X509Certificate> certs = new LinkedList<X509Certificate>();
        certs.add(TestUtilities.generateSelfSignedCert(keyPair));
        DataItem sig = Util.coseSign1Sign(keyPair.getPrivate(), "SHA256withECDSA", data,
                detachedContent, certs);
        assertTrue(Util.coseSign1CheckSignature(sig, detachedContent, keyPair.getPublic()));
        assertArrayEquals(data, Util.coseSign1GetData(sig));
        assertEquals(certs, Util.coseSign1GetX5Chain(sig));
    }

    @Test
    public void coseSignAndVerifyMultipleCertificates() throws Exception {
        KeyPair keyPair = generateKeyPair();
        byte[] data = new byte[]{};
        byte[] detachedContent = new byte[]{0x20, 0x21, 0x22, 0x23, 0x24};
        LinkedList<X509Certificate> certs = new LinkedList<X509Certificate>();
        certs.add(TestUtilities.generateSelfSignedCert(keyPair));
        certs.add(TestUtilities.generateSelfSignedCert(keyPair));
        certs.add(TestUtilities.generateSelfSignedCert(keyPair));
        DataItem sig = Util.coseSign1Sign(keyPair.getPrivate(), "SHA256withECDSA", data,
                detachedContent, certs);
        assertTrue(Util.coseSign1CheckSignature(sig, detachedContent, keyPair.getPublic()));
        assertArrayEquals(data, Util.coseSign1GetData(sig));
        assertEquals(certs, Util.coseSign1GetX5Chain(sig));
    }

    @Test
    public void coseMac0() throws Exception {
        SecretKey secretKey = new SecretKeySpec(new byte[32], "");
        byte[] data = new byte[]{0x10, 0x11, 0x12, 0x13};
        byte[] detachedContent = new byte[]{};
        DataItem mac = Util.coseMac0(secretKey, data, detachedContent);
        assertEquals("[\n"
                + "  [0xa1, 0x01, 0x05],\n"
                + "  {},\n"
                + "  [0x10, 0x11, 0x12, 0x13],\n"
                + "  [0x6c, 0xec, 0xb5, 0x6a, 0xc9, 0x5c, 0xae, 0x3b, 0x41, 0x13, 0xde, 0xa4, "
                + "0xd8, 0x86, 0x5c, 0x28, 0x2c, 0xd5, 0xa5, 0x13, 0xff, 0x3b, 0xd1, 0xde, 0x70, "
                + "0x5e, 0xbb, 0xe2, 0x2d, 0x42, 0xbe, 0x53]\n"
                + "]", Util.cborPrettyPrint(mac));
    }

    @Test
    public void coseMac0DetachedContent() throws Exception {
        SecretKey secretKey = new SecretKeySpec(new byte[32], "");
        byte[] data = new byte[]{};
        byte[] detachedContent = new byte[]{0x10, 0x11, 0x12, 0x13};
        DataItem mac = Util.coseMac0(secretKey, data, detachedContent);
        // Same HMAC as in coseMac0 test, only difference is that payload is null.
        assertEquals("[\n"
                + "  [0xa1, 0x01, 0x05],\n"
                + "  {},\n"
                + "  null,\n"
                + "  [0x6c, 0xec, 0xb5, 0x6a, 0xc9, 0x5c, 0xae, 0x3b, 0x41, 0x13, 0xde, 0xa4, "
                + "0xd8, 0x86, 0x5c, 0x28, 0x2c, 0xd5, 0xa5, 0x13, 0xff, 0x3b, 0xd1, 0xde, 0x70, "
                + "0x5e, 0xbb, 0xe2, 0x2d, 0x42, 0xbe, 0x53]\n"
                + "]", Util.cborPrettyPrint(mac));
    }

    @Test
    public void coseKeyEncoding() throws Exception {
        // This checks the encoding of X and Y are encoded as specified in
        // Section 2.3.5 Field-Element-to-Octet-String Conversion of
        // SEC 1: Elliptic Curve Cryptography (https://www.secg.org/sec1-v2.pdf).
        assertEquals("{\n" +
                        "  1 : 2,\n" +
                        "  -1 : 1,\n" +
                        "  -2 : [0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, " +
                                "0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, " +
                                "0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, " +
                                "0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01],\n" +
                        "  -3 : [0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, " +
                                "0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, " +
                                "0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, " +
                                "0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02]\n" +
                        "}",
                Util.cborPrettyPrint(Util.cborBuildCoseKey(
                        Util.getPublicKeyFromIntegers(
                                BigInteger.valueOf(1),
                                BigInteger.valueOf(2)))));
    }

    @Test
    public void replaceLineTest() {
        assertEquals("foo",
                Util.replaceLine("Hello World", 0, "foo"));
        assertEquals("foo\n",
                Util.replaceLine("Hello World\n", 0, "foo"));
        assertEquals("Hello World",
                Util.replaceLine("Hello World", 1, "foo"));
        assertEquals("Hello World\n",
                Util.replaceLine("Hello World\n", 1, "foo"));
        assertEquals("foo\ntwo\nthree",
                Util.replaceLine("one\ntwo\nthree", 0, "foo"));
        assertEquals("one\nfoo\nthree",
                Util.replaceLine("one\ntwo\nthree", 1, "foo"));
        assertEquals("one\ntwo\nfoo",
                Util.replaceLine("one\ntwo\nthree", 2, "foo"));
        assertEquals("one\ntwo\nfoo",
                Util.replaceLine("one\ntwo\nthree", -1, "foo"));
        assertEquals("one\ntwo\nthree\nfoo",
                Util.replaceLine("one\ntwo\nthree\nfour", -1, "foo"));
        assertEquals("one\ntwo\nfoo\nfour",
                Util.replaceLine("one\ntwo\nthree\nfour", -2, "foo"));
    }

    // This test makes sure that Util.issuerSignedItemBytesSetValue() preserves the map order.
    //
    @Test
    public void testIssuerSignedItemSetValue() {
        DataItem di;
        byte[] encoded;
        byte[] encodedWithValue;
        byte[] encodedDataElement = Util.cborEncodeString("A String");

        // Just try two different orders, canonical and non-canonical.

        // Canonical:
        di = new CborBuilder()
                .addMap()
                .put("random", new byte[]{1, 2, 3})
                .put("digestID", 42)
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .put("elementIdentifier", "foo")
                .end()
                .build().get(0);
        encoded = Util.cborEncode(di);
        assertEquals("{\n" +
                "  'random' : [0x01, 0x02, 0x03],\n" +
                "  'digestID' : 42,\n" +
                "  'elementValue' : null,\n" +
                "  'elementIdentifier' : 'foo'\n" +
                "}", Util.cborPrettyPrint(encoded));
        encodedWithValue = Util.issuerSignedItemSetValue(encoded, encodedDataElement);
        assertEquals("{\n" +
                "  'random' : [0x01, 0x02, 0x03],\n" +
                "  'digestID' : 42,\n" +
                "  'elementValue' : 'A String',\n" +
                "  'elementIdentifier' : 'foo'\n" +
                "}", Util.cborPrettyPrint(encodedWithValue));

        // Non-canonical:
        di = new CborBuilder()
                .addMap()
                .put("digestID", 42)
                .put("random", new byte[]{1, 2, 3})
                .put("elementIdentifier", "foo")
                .put(new UnicodeString("elementValue"), SimpleValue.NULL)
                .end()
                .build().get(0);
        encoded = Util.cborEncode(di);
        assertEquals("{\n" +
                "  'digestID' : 42,\n" +
                "  'random' : [0x01, 0x02, 0x03],\n" +
                "  'elementIdentifier' : 'foo',\n" +
                "  'elementValue' : null\n" +
                "}", Util.cborPrettyPrint(encoded));
        encodedWithValue = Util.issuerSignedItemSetValue(encoded, encodedDataElement);
        assertEquals("{\n" +
                "  'digestID' : 42,\n" +
                "  'random' : [0x01, 0x02, 0x03],\n" +
                "  'elementIdentifier' : 'foo',\n" +
                "  'elementValue' : 'A String'\n" +
                "}", Util.cborPrettyPrint(encodedWithValue));
    }

    @Test
    public void toHexThrows() {
        assertThrows(NullPointerException.class, () -> Util.toHex((byte[]) null));
        assertThrows(NullPointerException.class, () -> Util.toHex((byte[]) null, 0, 0));
        // Either NullPointerException or IllegalArgumentException would be reasonable, this
        // test ensures our exception behaviour doesn't accidentally change.
        assertThrows(NullPointerException.class, () -> Util.toHex((byte[]) null, 1, 0));

        byte[] twoBytes = {0x17, 0x18};
        assertThrows(IllegalArgumentException.class, () -> Util.toHex(twoBytes, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> Util.toHex(twoBytes, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> Util.toHex(twoBytes, 2, 1));
    }

    @Test
    public void toHex() {
        assertEquals("", Util.toHex(new byte[0]));
        assertEquals("00ff13ab0b",
                Util.toHex(new byte[]{0x00, (byte) 0xFF, 0x13, (byte) 0xAB, 0x0B}));
    }

    @Test
    public void toHexRange() {
        // arbitrary values in arbitrary large array
        byte[] manyBytes = new byte[50000];
        new Random().nextBytes(manyBytes);
        manyBytes[31337] = 0x13;
        manyBytes[31338] = (byte) 0xAB;
        assertEquals("13ab", Util.toHex(manyBytes, 31337, 31339));
        assertEquals("1718", Util.toHex(new byte[]{0x17, 0x18}, 0, 2));
    }

    @Test
    public void fromHexThrows() {
        assertThrows(NullPointerException.class, () -> Util.fromHex((String) null));

        assertThrows(IllegalArgumentException.class, () -> Util.fromHex("0"));
        assertThrows(IllegalArgumentException.class, () -> Util.fromHex("XX"));
    }

    @Test
    public void checkedStringValue() {
        final DataItem dataItem = new co.nstant.in.cbor.model.UnicodeString("hello");
        assertEquals("hello", Util.checkedStringValue(dataItem));
    }

    @Test
    public void checkedStringValueThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> Util.checkedStringValue(co.nstant.in.cbor.model.SimpleValue.TRUE));
        assertThrows(IllegalArgumentException.class,
            () -> Util.checkedStringValue(new co.nstant.in.cbor.model.UnsignedInteger(42)));
        assertThrows(IllegalArgumentException.class,
            () -> Util.checkedStringValue(
                new co.nstant.in.cbor.model.ByteString(new byte[]{1, 2, 3})));
    }

    @Test
    public void checkedLongValue() {
        final DataItem dataItem = new co.nstant.in.cbor.model.UnsignedInteger(8675309);
        assertEquals(8675309, Util.checkedLongValue(dataItem));
    }

    @Test
    public void checkedLongValueThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> Util.checkedLongValue(co.nstant.in.cbor.model.SimpleValue.TRUE));
        assertThrows(IllegalArgumentException.class,
            () -> Util.checkedLongValue(new co.nstant.in.cbor.model.UnicodeString("not a number")));
        assertThrows(IllegalArgumentException.class,
            () -> Util.checkedLongValue(
                new co.nstant.in.cbor.model.ByteString(new byte[]{1, 2, 3})));
    }

    @Test
    public void checkedLongValueEdgeCases() {
        final BigInteger upperLimit = BigInteger.valueOf(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE,
            Util.checkedLongValue(new co.nstant.in.cbor.model.UnsignedInteger(upperLimit)));

        final BigInteger lowerLimit = BigInteger.valueOf(Long.MIN_VALUE);
        assertEquals(Long.MIN_VALUE,
            Util.checkedLongValue(new co.nstant.in.cbor.model.NegativeInteger(lowerLimit)));

        final BigInteger tooBig = upperLimit.add(BigInteger.ONE);
        assertThrows(ArithmeticException.class,
            () -> Util.checkedLongValue(new co.nstant.in.cbor.model.UnsignedInteger(tooBig)));

        final BigInteger tooSmall = lowerLimit.subtract(BigInteger.ONE);
        assertThrows(ArithmeticException.class,
            () -> Util.checkedLongValue(new co.nstant.in.cbor.model.NegativeInteger(tooSmall)));
    }

    // TODO: Replace with Assert.assertThrows() once we use a recent enough version of JUnit.
    /** Asserts that the given {@code runnable} throws the given exception class, or a subclass. */
    private static void assertThrows(
            Class<? extends RuntimeException> expected, Runnable runnable) {
        try {
            runnable.run();
            fail("Expected " + expected + " was not thrown");
        } catch (RuntimeException e) {
            Class actual = e.getClass();
            assertTrue("Unexpected Exception class: " + actual,
                    expected.isAssignableFrom(actual));
        }
    }

    @Test
    public void fromHex() {
        assertArrayEquals(new byte[0], Util.fromHex(""));
        assertArrayEquals(new byte[]{0x00, (byte) 0xFF, 0x13, (byte) 0xAB, 0x0B},
                Util.fromHex("00ff13ab0b"));
        assertArrayEquals(new byte[]{0x00, (byte) 0xFF, 0x13, (byte) 0xAB, 0x0B},
                Util.fromHex("00FF13AB0B"));
    }

    @Test
    public void toHexFromHexRoundTrip() {
        Random random = new Random(31337); // deterministic but arbitrary
        for (int numBytes : new int[]{0, 1, 2, 10, 50000}) {
            byte[] bytes = new byte[numBytes];
            random.nextBytes(bytes);
            assertArrayEquals(bytes, Util.fromHex(Util.toHex(bytes)));
        }
    }

    @Test
    public void base16() {
        assertThrows(NullPointerException.class, () -> Util.base16(null));
        assertEquals("", Util.base16(new byte[0]));
        assertEquals("00FF13AB0B",
                Util.base16(new byte[]{0x00, (byte) 0xFF, 0x13, (byte) 0xAB, 0x0B}));
    }

    @Test
    public void testCborGetLengthBasic() {
        byte[] data = Util.cborEncode(new CborBuilder().add("text").build().get(0));
        assertEquals(data.length, Util.cborGetLength(data));
    }

    @Test
    public void testCborGetLengthNonCbor() {
        byte[] data = new byte[] {0x70, 0x71};
        assertEquals(-1, Util.cborGetLength(data));
    }

    @Test
    public void testCborGetLengthIncompleteCbor() {
        byte[] data = Util.cborEncode(new CborBuilder().add("text").build().get(0));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(data, 0, data.length - 1);
        byte[] incompleteData = baos.toByteArray();
        assertEquals(-1, Util.cborGetLength(incompleteData));
    }

    @Test
    public void testCborGetLengthComplicated() {
        byte[] data = Util.cborEncode(
                new CborBuilder()
                        .addArray()
                        .add("text")
                        .add(42)
                        .addMap()
                        .put("foo", "bar")
                        .put("fizz", "buzz")
                        .end()
                        .end()
                        .build().get(0));
        assertEquals(data.length, Util.cborGetLength(data));
    }

    @Test
    public void testCborGetLengthMultipleDataItems() throws IOException {
        byte[] data1 = Util.cborEncode(new CborBuilder().add("text1").build().get(0));
        byte[] data2 = Util.cborEncode(new CborBuilder().add("text2").build().get(0));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(data1);
        baos.write(data2);
        byte[] multipleDataItems = baos.toByteArray();
        assertEquals(data1.length, Util.cborGetLength(multipleDataItems));
    }

    @Test
    public void testCborGetLengthBasicWithTrailingInvalidData() throws IOException {
        byte[] data = Util.cborEncode(new CborBuilder().add("text").build().get(0));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(data);
        baos.write(0x70);
        baos.write(0x71);
        byte[] withTrailer = baos.toByteArray();
        assertEquals(data.length, Util.cborGetLength(withTrailer));
    }

    @Test
    public void testCborExtractFirstDataItemSingle() throws IOException {
        byte[] data1 = Util.cborEncode(new CborBuilder().add("text1").build().get(0));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(data1);

        byte[] firstDataItemBytes = Util.cborExtractFirstDataItem(baos);
        assertArrayEquals(data1, firstDataItemBytes);
        assertEquals(0, baos.toByteArray().length);
    }

    @Test
    public void testCborExtractFirstDataItemIncomplete() {
        byte[] data = Util.cborEncode(new CborBuilder().add("text").build().get(0));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(data, 0, data.length - 1);
        byte[] incompleteData = baos.toByteArray();

        byte[] firstDataItemBytes = Util.cborExtractFirstDataItem(baos);
        assertNull(firstDataItemBytes);
        assertArrayEquals(incompleteData, baos.toByteArray());
    }

    @Test
    public void testCborExtractFirstDataItemMultiple() throws IOException {
        byte[] data1 = Util.cborEncode(new CborBuilder().add("text1").build().get(0));
        byte[] data2 = Util.cborEncode(new CborBuilder().add("text2").build().get(0));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(data1);
        baos.write(data2);

        byte[] firstDataItemBytes = Util.cborExtractFirstDataItem(baos);
        assertArrayEquals(data1, firstDataItemBytes);
        assertArrayEquals(data2, baos.toByteArray());
        firstDataItemBytes = Util.cborExtractFirstDataItem(baos);
        assertArrayEquals(data2, firstDataItemBytes);
        assertEquals(0, baos.toByteArray().length);
    }

    @Test
    public void testCborExtractFirstDataItemJunkTrailing() throws IOException {
        byte[] data1 = Util.cborEncode(new CborBuilder().add("text1").build().get(0));
        byte[] data2 = Util.cborEncode(new CborBuilder().add("text2").build().get(0));
        byte[] dataNonCbor = new byte[]{0x70, 0x71};
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(data1);
        baos.write(data2);
        baos.write(dataNonCbor);

        byte[] firstDataItemBytes = Util.cborExtractFirstDataItem(baos);
        assertArrayEquals(data1, firstDataItemBytes);
        firstDataItemBytes = Util.cborExtractFirstDataItem(baos);
        assertArrayEquals(data2, firstDataItemBytes);
        assertArrayEquals(dataNonCbor, baos.toByteArray());
    }

    @Test
    public void testCborExtractFirstDataItemIncompleteTrailing() throws IOException {
        byte[] data1 = Util.cborEncode(new CborBuilder().add("text1").build().get(0));
        byte[] data2 = Util.cborEncode(new CborBuilder().add("text2").build().get(0));
        byte[] data3 = Util.cborEncode(new CborBuilder().add("text3").build().get(0));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(data1);
        baos.write(data2);
        byte[] incompleteCbor = Arrays.copyOfRange(data3, 0, data3.length - 3);
        baos.write(incompleteCbor);

        byte[] firstDataItemBytes = Util.cborExtractFirstDataItem(baos);
        assertArrayEquals(data1, firstDataItemBytes);
        firstDataItemBytes = Util.cborExtractFirstDataItem(baos);
        assertArrayEquals(data2, firstDataItemBytes);
        assertArrayEquals(incompleteCbor, baos.toByteArray());
    }

    @Test
    public void testMdocVersionCompare() {
        assertTrue(Util.mdocVersionCompare("1.0", "1.0") == 0);
        assertTrue(Util.mdocVersionCompare("1.0", "1.1") < 0);
        assertTrue(Util.mdocVersionCompare("1.1", "1.0") > 0);
    }
}
