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

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.util.Map;
import java.util.Set;

import co.nstant.in.cbor.model.DataItem;

public class MobileSecurityObjectParserTest {

    @Test
    @SmallTest
    public void testMSOParserWithVectors() throws CertificateEncodingException {
        DataItem deviceResponse = Util.cborDecode(Util.fromHex(
                TestVectors.ISO_18013_5_ANNEX_D_DEVICE_RESPONSE));
        DataItem documentDataItem = Util.cborMapExtractArray(deviceResponse,
                "documents").get(0);

        DataItem issuerSigned = Util.cborMapExtractMap(documentDataItem, "issuerSigned");
        DataItem issuerAuthDataItem = Util.cborMapExtract(issuerSigned, "issuerAuth");

        DataItem mobileSecurityObjectBytes = Util.cborDecode(
                Util.coseSign1GetData(issuerAuthDataItem));
        DataItem mobileSecurityObject = Util.cborExtractTaggedAndEncodedCbor(
                mobileSecurityObjectBytes);
        byte[] encodedMobileSecurityObject = Util.cborEncode(mobileSecurityObject);

        // the response above and all the following constants are from ISO 18013-5 D.4.1.2 mdoc
        // response - the goal is to check that the parser returns the expected values
        MobileSecurityObjectParser.MobileSecurityObject mso = new MobileSecurityObjectParser()
                .setMobileSecurityObject(encodedMobileSecurityObject).parse();
        Assert.assertEquals("1.0", mso.getVersion());
        Assert.assertEquals("SHA-256", mso.getDigestAlgorithm());
        Assert.assertEquals("org.iso.18013.5.1.mDL", mso.getDocType());

        Assert.assertEquals(Set.of("org.iso.18013.5.1", "org.iso.18013.5.1.US"),
                mso.getValueDigestNamespaces());
        Assert.assertNull(mso.getDigestIDs("abc"));
        Map<Long, byte[]> isoDigestIDs = mso.getDigestIDs("org.iso.18013.5.1");
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

        Map<Long, byte[]> isoUSDigestIDs = mso.getDigestIDs("org.iso.18013.5.1.US");
        Assert.assertEquals(Set.of(0L, 1L, 2L, 3L), isoUSDigestIDs.keySet());
        Assert.assertEquals("D80B83D25173C484C5640610FF1A31C949C1D934BF4CF7F18D5223B15DD4F21C"
                .toLowerCase(), Util.toHex(isoUSDigestIDs.get(0L)));
        Assert.assertEquals("4D80E1E2E4FB246D97895427CE7000BB59BB24C8CD003ECF94BF35BBD2917E34"
                .toLowerCase(), Util.toHex(isoUSDigestIDs.get(1L)));
        Assert.assertEquals("8B331F3B685BCA372E85351A25C9484AB7AFCDF0D2233105511F778D98C2F544"
                .toLowerCase(), Util.toHex(isoUSDigestIDs.get(2L)));
        Assert.assertEquals("C343AF1BD1690715439161ABA73702C474ABF992B20C9FB55C36A336EBE01A87"
                .toLowerCase(), Util.toHex(isoUSDigestIDs.get(3L)));

        PublicKey deviceKeyFromVector = Util.getPublicKeyFromIntegers(
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X, 16),
                new BigInteger(TestVectors.ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y, 16));
        Assert.assertEquals(deviceKeyFromVector, mso.getDeviceKey());
        Assert.assertNull(mso.getDeviceKeyAuthorizedNameSpaces());
        Assert.assertNull(mso.getDeviceKeyAuthorizedDataElements());
        Assert.assertNull(mso.getDeviceKeyInfo());

        Assert.assertEquals(Timestamp.ofEpochMilli(1601559002000L), mso.getSigned());
        Assert.assertEquals(Timestamp.ofEpochMilli(1601559002000L), mso.getValidFrom());
        Assert.assertEquals(Timestamp.ofEpochMilli(1633095002000L), mso.getValidUntil());
        Assert.assertNull(mso.getExpectedUpdate());
    }
}
