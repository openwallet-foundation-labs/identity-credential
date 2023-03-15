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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private Map<Long, byte[]> generateISODigest(String digestAlgorithm) throws NoSuchAlgorithmException {
        MessageDigest digester = MessageDigest.getInstance(digestAlgorithm);
        Map<Long, byte[]> isoDigestIDs = new HashMap<>();
        isoDigestIDs.put(0L, digester.digest("aardvark".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(1L, digester.digest("alligator".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(2L, digester.digest("baboon".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(3L, digester.digest("butterfly".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(4L, digester.digest("cat".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(5L, digester.digest("cricket".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(6L, digester.digest("dog".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(7L, digester.digest("elephant".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(8L, digester.digest("firefly".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(9L, digester.digest("frog".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(10L, digester.digest("gecko".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(11L, digester.digest("hippo".getBytes(StandardCharsets.UTF_8)));
        isoDigestIDs.put(12L, digester.digest("iguana".getBytes(StandardCharsets.UTF_8)));
        return isoDigestIDs;
    }

    private Map<Long, byte[]> generateISOUSDigest(String digestAlgorithm) throws NoSuchAlgorithmException {
        MessageDigest digester = MessageDigest.getInstance(digestAlgorithm);
        Map<Long, byte[]> isoUSDigestIDs = new HashMap<>();
        isoUSDigestIDs.put(0L, digester.digest("jaguar".getBytes(StandardCharsets.UTF_8)));
        isoUSDigestIDs.put(1L, digester.digest("jellyfish".getBytes(StandardCharsets.UTF_8)));
        isoUSDigestIDs.put(2L, digester.digest("koala".getBytes(StandardCharsets.UTF_8)));
        isoUSDigestIDs.put(3L, digester.digest("lemur".getBytes(StandardCharsets.UTF_8)));
        return isoUSDigestIDs;
    }

    private void checkISODigest(Map<Long, byte[]> isoDigestIDs, String digestAlgorithm)
            throws NoSuchAlgorithmException {
        MessageDigest digester = MessageDigest.getInstance(digestAlgorithm);
        Assert.assertEquals(Set.of(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L),
                isoDigestIDs.keySet());
        Assert.assertArrayEquals(digester.digest("aardvark".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(0L));
        Assert.assertArrayEquals(digester.digest("alligator".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(1L));
        Assert.assertArrayEquals(digester.digest("baboon".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(2L));
        Assert.assertArrayEquals(digester.digest("butterfly".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(3L));
        Assert.assertArrayEquals(digester.digest("cat".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(4L));
        Assert.assertArrayEquals(digester.digest("cricket".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(5L));
        Assert.assertArrayEquals(digester.digest("dog".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(6L));
        Assert.assertArrayEquals(digester.digest("elephant".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(7L));
        Assert.assertArrayEquals(digester.digest("firefly".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(8L));
        Assert.assertArrayEquals(digester.digest("frog".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(9L));
        Assert.assertArrayEquals(digester.digest("gecko".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(10L));
        Assert.assertArrayEquals(digester.digest("hippo".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(11L));
        Assert.assertArrayEquals(digester.digest("iguana".getBytes(StandardCharsets.UTF_8)),
                isoDigestIDs.get(12L));
    }

    private void checkISOUSDigest(Map<Long, byte[]> isoUSDigestIDs, String digestAlgorithm)
            throws NoSuchAlgorithmException {
        MessageDigest digester = MessageDigest.getInstance(digestAlgorithm);
        Assert.assertEquals(Set.of(0L, 1L, 2L, 3L), isoUSDigestIDs.keySet());
        Assert.assertArrayEquals(digester.digest("jaguar".getBytes(StandardCharsets.UTF_8)),
                isoUSDigestIDs.get(0L));
        Assert.assertArrayEquals(digester.digest("jellyfish".getBytes(StandardCharsets.UTF_8)),
                isoUSDigestIDs.get(1L));
        Assert.assertArrayEquals(digester.digest("koala".getBytes(StandardCharsets.UTF_8)),
                isoUSDigestIDs.get(2L));
        Assert.assertArrayEquals(digester.digest("lemur".getBytes(StandardCharsets.UTF_8)),
                isoUSDigestIDs.get(3L));
    }

    public void testFullMSO(String digestAlgorithm) throws NoSuchAlgorithmException {
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

        Map<Long, byte[]> keyInfo = new HashMap<>();
        keyInfo.put(10L, Util.fromHex("C985"));

        byte[] encodedMSO = new MobileSecurityObjectGenerator(digestAlgorithm,
                "org.iso.18013.5.1.mDL", deviceKeyFromVector)
                .addDigestIdsForNamespace("org.iso.18013.5.1", generateISODigest(digestAlgorithm))
                .addDigestIdsForNamespace("org.iso.18013.5.1.US", generateISOUSDigest(digestAlgorithm))
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
        checkISODigest(mso.getDigestIDs("org.iso.18013.5.1"), digestAlgorithm);
        checkISOUSDigest(mso.getDigestIDs("org.iso.18013.5.1.US"), digestAlgorithm);

        Assert.assertEquals(deviceKeyFromVector, mso.getDeviceKey());
        Assert.assertEquals(List.of("abc", "bcd"), mso.getDeviceKeyAuthorizedNameSpaces());
        Assert.assertEquals(deviceKeyAuthorizedDataElements, mso.getDeviceKeyAuthorizedDataElements());
        Assert.assertEquals(keyInfo.keySet(), mso.getDeviceKeyInfo().keySet());
        Assert.assertEquals(Util.toHex(keyInfo.get(10L)), Util.toHex(mso.getDeviceKeyInfo().get(10L)));

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
        final String digestAlgorithm = "SHA-256";

        byte[] encodedMSO = new MobileSecurityObjectGenerator(digestAlgorithm,
                "org.iso.18013.5.1.mDL", deviceKeyFromVector)
                .addDigestIdsForNamespace("org.iso.18013.5.1", generateISODigest(digestAlgorithm))
                .addDigestIdsForNamespace("org.iso.18013.5.1.US", generateISOUSDigest(digestAlgorithm))
                .setValidityInfo(signedTimestamp, validFromTimestamp, validUntilTimestamp, null)
                .generate();

        MobileSecurityObjectParser.MobileSecurityObject mso = new MobileSecurityObjectParser()
                .setMobileSecurityObject(encodedMSO).parse();

        Assert.assertEquals("1.0", mso.getVersion());
        Assert.assertEquals(digestAlgorithm, mso.getDigestAlgorithm());
        Assert.assertEquals("org.iso.18013.5.1.mDL", mso.getDocType());

        Assert.assertEquals(Set.of("org.iso.18013.5.1", "org.iso.18013.5.1.US"),
                mso.getValueDigestNamespaces());
        Assert.assertNull(mso.getDigestIDs("abc"));
        checkISODigest(mso.getDigestIDs("org.iso.18013.5.1"), digestAlgorithm);
        checkISOUSDigest(mso.getDigestIDs("org.iso.18013.5.1.US"), digestAlgorithm);

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
    public void testFullMSO_Sha256() throws NoSuchAlgorithmException {
        testFullMSO("SHA-256");
    }

    @Test
    @SmallTest
    public void testFullMSO_Sha384() throws NoSuchAlgorithmException {
        testFullMSO("SHA-384");
    }

    @Test
    @SmallTest
    public void testFullMSO_Sha512() throws NoSuchAlgorithmException {
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

        final String digestAlgorithm = "SHA-256";
        MobileSecurityObjectGenerator msoGenerator = new MobileSecurityObjectGenerator(digestAlgorithm,
                "org.iso.18013.5.1.mDL", deviceKeyFromVector);

        Assert.assertThrows("expect exception for empty digestIDs",
                IllegalArgumentException.class,
                () -> msoGenerator.addDigestIdsForNamespace("org.iso.18013.5.1", new HashMap<>()));

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
                        "addDigestIdsForNamespace and setValidityInfo called before generating",
                IllegalStateException.class,
                msoGenerator::generate);


        Assert.assertThrows("expect exception for msoGenerator which has not had " +
                        "addDigestIdsForNamespace called before generating",
                IllegalStateException.class,
                () -> {new MobileSecurityObjectGenerator(digestAlgorithm,
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
                () -> {new MobileSecurityObjectGenerator(digestAlgorithm,
                        "org.iso.18013.5.1.mDL", deviceKeyFromVector)
                        .addDigestIdsForNamespace("org.iso.18013.5.1", generateISODigest(digestAlgorithm))
                        .addDigestIdsForNamespace("org.iso.18013.5.1.US", generateISOUSDigest(digestAlgorithm))
                        .generate();});

    }
}
