/*
 * Copyright 2023 The Android Open Source Project
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

import android.security.keystore.KeyProperties;

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MobileSecurityObjectGeneratorTest {
    private static final String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    private static final String MDL_NAMESPACE = "org.iso.18013.5.1";
    private static final String AAMVA_NAMESPACE = "org.aamva.18013.5.1";

    private static final String MVR_DOCTYPE = "org.iso.18013.7.1.mVR";
    private static final String MVR_NAMESPACE = "org.iso.18013.7.1";

    private KeyPair generateReaderKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        return kpg.generateKeyPair();
    }

    private Map<Long, byte[]> generateISODigest() {
        // the following constants are from ISO 18013-5 D.4.1.2 mdoc response
        Map<Long, byte[]> isoDigestIDs = new HashMap<>();
        isoDigestIDs.put(0L,
                Util.fromHex("75167333B47B6C2BFB86ECCC1F438CF57AF055371AC55E1E359E20F254ADCEBF"));
        isoDigestIDs.put(1L,
                Util.fromHex("67E539D6139EBD131AEF441B445645DD831B2B375B390CA5EF6279B205ED4571"));
        isoDigestIDs.put(2L,
                Util.fromHex("3394372DDB78053F36D5D869780E61EDA313D44A392092AD8E0527A2FBFE55AE"));
        isoDigestIDs.put(3L,
                Util.fromHex("2E35AD3C4E514BB67B1A9DB51CE74E4CB9B7146E41AC52DAC9CE86B8613DB555"));
        isoDigestIDs.put(4L,
                Util.fromHex("EA5C3304BB7C4A8DCB51C4C13B65264F845541341342093CCA786E058FAC2D59"));
        isoDigestIDs.put(5L,
                Util.fromHex("FAE487F68B7A0E87A749774E56E9E1DC3A8EC7B77E490D21F0E1D3475661AA1D"));
        isoDigestIDs.put(6L,
                Util.fromHex("7D83E507AE77DB815DE4D803B88555D0511D894C897439F5774056416A1C7533"));
        isoDigestIDs.put(7L,
                Util.fromHex("F0549A145F1CF75CBEEFFA881D4857DD438D627CF32174B1731C4C38E12CA936"));
        isoDigestIDs.put(8L,
                Util.fromHex("B68C8AFCB2AAF7C581411D2877DEF155BE2EB121A42BC9BA5B7312377E068F66"));
        isoDigestIDs.put(9L,
                Util.fromHex("0B3587D1DD0C2A07A35BFB120D99A0ABFB5DF56865BB7FA15CC8B56A66DF6E0C"));
        isoDigestIDs.put(10L,
                Util.fromHex("C98A170CF36E11ABB724E98A75A5343DFA2B6ED3DF2ECFBB8EF2EE55DD41C881"));
        isoDigestIDs.put(11L,
                Util.fromHex("B57DD036782F7B14C6A30FAAAAE6CCD5054CE88BDFA51A016BA75EDA1EDEA948"));
        isoDigestIDs.put(12L,
                Util.fromHex("651F8736B18480FE252A03224EA087B5D10CA5485146C67C74AC4EC3112D4C3A"));
        return isoDigestIDs;
    }

    private Map<Long, byte[]> generateISOUSDigest() {
        // the following constants are from ISO 18013-5 D.4.1.2 mdoc response
        Map<Long, byte[]> isoUSDigestIDs = new HashMap<>();
        isoUSDigestIDs.put(0L,
                Util.fromHex("D80B83D25173C484C5640610FF1A31C949C1D934BF4CF7F18D5223B15DD4F21C"));
        isoUSDigestIDs.put(1L,
                Util.fromHex("4D80E1E2E4FB246D97895427CE7000BB59BB24C8CD003ECF94BF35BBD2917E34"));
        isoUSDigestIDs.put(2L,
                Util.fromHex("8B331F3B685BCA372E85351A25C9484AB7AFCDF0D2233105511F778D98C2F544"));
        isoUSDigestIDs.put(3L,
                Util.fromHex("C343AF1BD1690715439161ABA73702C474ABF992B20C9FB55C36A336EBE01A87"));
        return isoUSDigestIDs;
    }

    private void checkISODigest(Map<Long, byte[]> isoDigestIDs) {
        Assert.assertEquals(Set.of(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L),
                isoDigestIDs.keySet());
        Assert.assertEquals("75167333B47B6C2BFB86ECCC1F438CF57AF055371AC55E1E359E20F254ADCEBF"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(0L)));
        Assert.assertEquals("67E539D6139EBD131AEF441B445645DD831B2B375B390CA5EF6279B205ED4571"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(1L)));
        Assert.assertEquals("3394372DDB78053F36D5D869780E61EDA313D44A392092AD8E0527A2FBFE55AE"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(2L)));
        Assert.assertEquals("2E35AD3C4E514BB67B1A9DB51CE74E4CB9B7146E41AC52DAC9CE86B8613DB555"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(3L)));
        Assert.assertEquals("EA5C3304BB7C4A8DCB51C4C13B65264F845541341342093CCA786E058FAC2D59"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(4L)));
        Assert.assertEquals("FAE487F68B7A0E87A749774E56E9E1DC3A8EC7B77E490D21F0E1D3475661AA1D"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(5L)));
        Assert.assertEquals("7D83E507AE77DB815DE4D803B88555D0511D894C897439F5774056416A1C7533"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(6L)));
        Assert.assertEquals("F0549A145F1CF75CBEEFFA881D4857DD438D627CF32174B1731C4C38E12CA936"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(7L)));
        Assert.assertEquals("B68C8AFCB2AAF7C581411D2877DEF155BE2EB121A42BC9BA5B7312377E068F66"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(8L)));
        Assert.assertEquals("0B3587D1DD0C2A07A35BFB120D99A0ABFB5DF56865BB7FA15CC8B56A66DF6E0C"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(9L)));
        Assert.assertEquals("C98A170CF36E11ABB724E98A75A5343DFA2B6ED3DF2ECFBB8EF2EE55DD41C881"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(10L)));
        Assert.assertEquals("B57DD036782F7B14C6A30FAAAAE6CCD5054CE88BDFA51A016BA75EDA1EDEA948"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(11L)));
        Assert.assertEquals("651F8736B18480FE252A03224EA087B5D10CA5485146C67C74AC4EC3112D4C3A"
                .toLowerCase(), Util.toHex(isoDigestIDs.get(12L)));
    }

    private void checkISOUSDigest(Map<Long, byte[]> isoUSDigestIDs) {
        Assert.assertEquals(Set.of(0L, 1L, 2L, 3L), isoUSDigestIDs.keySet());
        Assert.assertEquals("D80B83D25173C484C5640610FF1A31C949C1D934BF4CF7F18D5223B15DD4F21C"
                .toLowerCase(), Util.toHex(isoUSDigestIDs.get(0L)));
        Assert.assertEquals("4D80E1E2E4FB246D97895427CE7000BB59BB24C8CD003ECF94BF35BBD2917E34"
                .toLowerCase(), Util.toHex(isoUSDigestIDs.get(1L)));
        Assert.assertEquals("8B331F3B685BCA372E85351A25C9484AB7AFCDF0D2233105511F778D98C2F544"
                .toLowerCase(), Util.toHex(isoUSDigestIDs.get(2L)));
        Assert.assertEquals("C343AF1BD1690715439161ABA73702C474ABF992B20C9FB55C36A336EBE01A87"
                .toLowerCase(), Util.toHex(isoUSDigestIDs.get(3L)));
    }

    public void testFullMSO(String digestAlgorithm) {
        PublicKey deviceKeyFromVector = Util.getPublicKeyFromIntegers(
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X, 16),
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y, 16));
        final Timestamp signedTimestamp = Timestamp.ofEpochMilli(1601559002000L);
        final Timestamp validFromTimestamp = Timestamp.ofEpochMilli(1601559002000L);
        final Timestamp validUntilTimestamp = Timestamp.ofEpochMilli(1633095002000L);
        final Timestamp expectedTimestamp = Timestamp.ofEpochMilli(1611093002000L);

        Map<String, List<String>> deviceKeyAuthorizedDataElements = new HashMap<>();
        deviceKeyAuthorizedDataElements.put("a", List.of("1", "2", "f"));
        deviceKeyAuthorizedDataElements.put("b", List.of("4", "5", "k"));

        Map<Integer, byte[]> keyInfo = new HashMap<>();
        keyInfo.put(10, Util.fromHex("C985"));

        byte[] encodedMSO = new MobileSecurityObjectGenerator(digestAlgorithm,
                "org.iso.18013.5.1.mDL", deviceKeyFromVector)
                .digestIdToDigestMap("org.iso.18013.5.1", generateISODigest())
                .digestIdToDigestMap("org.iso.18013.5.1.US", generateISOUSDigest())
                .setDeviceKeyAuthorizedNameSpaces(List.of("abc", "bcd"))
                .setDeviceKeyAuthorizedDataElements(deviceKeyAuthorizedDataElements)
                .setDeviceKeyInfo(keyInfo)
                .setValidityInfo(
                        signedTimestamp,
                        validFromTimestamp,
                        validUntilTimestamp,
                        expectedTimestamp)
                .generate();

        MobileSecurityObjectParser.MobileSecurityObject mso = new MobileSecurityObjectParser()
                .setMobileSecurityObject(encodedMSO).parse();

        Assert.assertEquals("1.0", mso.getVersion());
        Assert.assertEquals(digestAlgorithm, mso.getDigestAlgorithm());
        Assert.assertEquals("org.iso.18013.5.1.mDL", mso.getDocType());

        Assert.assertEquals(Set.of("org.iso.18013.5.1", "org.iso.18013.5.1.US"),
                mso.getValueDigestNamespaces());
        Assert.assertNull(mso.getDigestIDs("abc"));
        checkISODigest(mso.getDigestIDs("org.iso.18013.5.1"));
        checkISOUSDigest(mso.getDigestIDs("org.iso.18013.5.1.US"));

        Assert.assertEquals(deviceKeyFromVector, mso.getDeviceKey());
        Assert.assertEquals(List.of("abc", "bcd"), mso.getDeviceKeyAuthorizedNameSpaces());
        Assert.assertEquals(deviceKeyAuthorizedDataElements, mso.getDeviceKeyAuthorizedDataElements());
        Assert.assertEquals(keyInfo.keySet(), mso.getDeviceKeyInfo().keySet());
        Assert.assertEquals(Util.toHex(keyInfo.get(10)), Util.toHex(mso.getDeviceKeyInfo().get(10)));

        Assert.assertEquals(signedTimestamp, mso.getSigned());
        Assert.assertEquals(validFromTimestamp, mso.getValidFrom());
        Assert.assertEquals(validUntilTimestamp, mso.getValidUntil());
        Assert.assertEquals(expectedTimestamp, mso.getExpectedUpdate());
    }

    @Test
    @SmallTest
    public void testBasicMSO() throws Exception {
        PublicKey deviceKeyFromVector = Util.getPublicKeyFromIntegers(
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X, 16),
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y, 16));
        final Timestamp signedTimestamp = Timestamp.ofEpochMilli(1601559002000L);
        final Timestamp validFromTimestamp = Timestamp.ofEpochMilli(1601559002000L);
        final Timestamp validUntilTimestamp = Timestamp.ofEpochMilli(1633095002000L);

        byte[] encodedMSO = new MobileSecurityObjectGenerator("SHA-256",
                "org.iso.18013.5.1.mDL", deviceKeyFromVector)
                .digestIdToDigestMap("org.iso.18013.5.1", generateISODigest())
                .digestIdToDigestMap("org.iso.18013.5.1.US", generateISOUSDigest())
                .setValidityInfo(signedTimestamp, validFromTimestamp, validUntilTimestamp, null)
                .generate();

        MobileSecurityObjectParser.MobileSecurityObject mso = new MobileSecurityObjectParser()
                .setMobileSecurityObject(encodedMSO).parse();

        Assert.assertEquals("1.0", mso.getVersion());
        Assert.assertEquals("SHA-256", mso.getDigestAlgorithm());
        Assert.assertEquals("org.iso.18013.5.1.mDL", mso.getDocType());

        Assert.assertEquals(Set.of("org.iso.18013.5.1", "org.iso.18013.5.1.US"),
                mso.getValueDigestNamespaces());
        Assert.assertNull(mso.getDigestIDs("abc"));
        checkISODigest(mso.getDigestIDs("org.iso.18013.5.1"));
        checkISOUSDigest(mso.getDigestIDs("org.iso.18013.5.1.US"));

        Assert.assertEquals(deviceKeyFromVector, mso.getDeviceKey());
        Assert.assertNull(mso.getDeviceKeyAuthorizedNameSpaces());
        Assert.assertNull(mso.getDeviceKeyAuthorizedDataElements());
        Assert.assertNull(mso.getDeviceKeyInfo());

        Assert.assertEquals(signedTimestamp, mso.getSigned());
        Assert.assertEquals(validFromTimestamp, mso.getValidFrom());
        Assert.assertEquals(validUntilTimestamp, mso.getValidUntil());
        Assert.assertNull(mso.getExpectedUpdate());
    }

    @Test
    @SmallTest
    public void testFullMSO_Sha256() {
        testFullMSO("SHA-256");
    }

    @Test
    @SmallTest
    public void testFullMSO_Sha384() {
        testFullMSO("SHA-384");
    }

    @Test
    @SmallTest
    public void testFullMSO_Sha512() {
        testFullMSO("SHA-512");
    }

    @Test
    @SmallTest
    public void testMSOExceptions() {
        PublicKey deviceKeyFromVector = Util.getPublicKeyFromIntegers(
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X, 16),
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y, 16));

        Assert.assertThrows("expect exception for illegal digestAlgorithm",
                IllegalArgumentException.class,
                () -> new MobileSecurityObjectGenerator("SHA-257",
                        "org.iso.18013.5.1.mDL", deviceKeyFromVector));

        MobileSecurityObjectGenerator msoGenerator = new MobileSecurityObjectGenerator("SHA-256",
                "org.iso.18013.5.1.mDL", deviceKeyFromVector);

        Assert.assertThrows("expect exception for empty digestIDs",
                IllegalArgumentException.class,
                () -> msoGenerator.digestIdToDigestMap("org.iso.18013.5.1", new HashMap<>()));

        Assert.assertThrows("expect exception for validFrom < signed",
                IllegalArgumentException.class,
                () -> msoGenerator.setValidityInfo(
                        Timestamp.ofEpochMilli(1601559002000L),
                        Timestamp.ofEpochMilli(1601559001000L),
                        Timestamp.ofEpochMilli(1633095002000L),
                        Timestamp.ofEpochMilli(1611093002000L)));

        Assert.assertThrows("expect exception for validUntil <= validFrom",
                IllegalArgumentException.class,
                () -> msoGenerator.setValidityInfo(
                        Timestamp.ofEpochMilli(1601559002000L),
                        Timestamp.ofEpochMilli(1601559002000L),
                        Timestamp.ofEpochMilli(1601559002000L),
                        Timestamp.ofEpochMilli(1611093002000L)));

        Map<String, List<String>> deviceKeyAuthorizedDataElements = new HashMap<>();
        deviceKeyAuthorizedDataElements.put("a", List.of("1", "2", "f"));
        deviceKeyAuthorizedDataElements.put("b", List.of("4", "5", "k"));
        Assert.assertThrows("expect exception for deviceKeyAuthorizedDataElements including " +
                        "namespace in deviceKeyAuthorizedNameSpaces",
                IllegalArgumentException.class,
                () -> msoGenerator.setDeviceKeyAuthorizedNameSpaces(List.of("a", "bcd"))
                        .setDeviceKeyAuthorizedDataElements(deviceKeyAuthorizedDataElements));
        Assert.assertThrows("expect exception for deviceKeyAuthorizedNameSpaces including " +
                        "namespace in deviceKeyAuthorizedDataElements",
                IllegalArgumentException.class,
                () -> msoGenerator.setDeviceKeyAuthorizedDataElements(deviceKeyAuthorizedDataElements)
                        .setDeviceKeyAuthorizedNameSpaces(List.of("a", "bcd")));

        Assert.assertThrows("expect exception for msoGenerator which has not had " +
                        "digestIdToDigestMap and setValidityInfo called before generating",
                IllegalStateException.class,
                msoGenerator::generate);


        Assert.assertThrows("expect exception for msoGenerator which has not had " +
                        "digestIdToDigestMap called before generating",
                IllegalStateException.class,
                () -> {new MobileSecurityObjectGenerator("SHA-256",
                        "org.iso.18013.5.1.mDL", deviceKeyFromVector)
                        .setValidityInfo(
                                Timestamp.ofEpochMilli(1601559002000L),
                                Timestamp.ofEpochMilli(1601559002000L),
                                Timestamp.ofEpochMilli(1633095002000L),
                                Timestamp.ofEpochMilli(1611093002000L))
                        .generate();});

        Assert.assertThrows("expect exception for msoGenerator which has not had " +
                        "setValidityInfo called before generating",
                IllegalStateException.class,
                () -> {new MobileSecurityObjectGenerator("SHA-256",
                        "org.iso.18013.5.1.mDL", deviceKeyFromVector)
                        .digestIdToDigestMap("org.iso.18013.5.1", generateISODigest())
                        .digestIdToDigestMap("org.iso.18013.5.1.US", generateISOUSDigest())
                        .generate();});

    }
}
