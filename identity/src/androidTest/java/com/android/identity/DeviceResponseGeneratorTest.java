/*
 * Copyright 2022 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.security.keystore.KeyProperties;
import androidx.core.util.Pair;

import androidx.test.filters.SmallTest;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.DataItem;

// TODO: Move to non-Android tests - can't do that right now b/c it's used IdentityCredentialStore
//   to generate DeviceNameSpaces and DeviceAuth.
public class DeviceResponseGeneratorTest {

    private static final String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    private static final String MDL_NAMESPACE = "org.iso.18013.5.1";
    private static final String AAMVA_NAMESPACE = "org.aamva.18013.5.1";  // TODO: verify

    private KeyPair generateIssuingAuthorityKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        return kpg.generateKeyPair();
    }


    private X509Certificate getSelfSignedIssuerAuthorityCertificate(
            KeyPair issuerAuthorityKeyPair) throws Exception {
        X500Name issuer = new X500Name("CN=State Of Utopia");
        X500Name subject = new X500Name("CN=State Of Utopia Issuing Authority Signing Key");

        // Valid from now to five years from now.
        Date now = new Date();
        final long kMilliSecsInOneYear = 365L * 24 * 60 * 60 * 1000;
        Date expirationDate = new Date(now.getTime() + 5 * kMilliSecsInOneYear);
        BigInteger serial = new BigInteger("42");
        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(issuer,
                        serial,
                        now,
                        expirationDate,
                        subject,
                        issuerAuthorityKeyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .build(issuerAuthorityKeyPair.getPrivate());

        X509CertificateHolder certHolder = builder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

    @Test
    @SmallTest
    public void testDeviceResponseGenerator() throws Exception {
        Context appContext = androidx.test.InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = Util.getIdentityCredentialStore(appContext);
        assumeTrue(store.getFeatureVersion() >= IdentityCredentialStore.FEATURE_VERSION_202201);

        store.deleteCredentialByName("test");
        WritableIdentityCredential wc = store.createCredential("test", MDL_DOCTYPE);
        Collection<X509Certificate> certificateChain =
                wc.getCredentialKeyCertificateChain("myChallenge".getBytes(UTF_8));

        // Profile 0 (no authentication)
        AccessControlProfile noAuthProfile =
                new AccessControlProfile.Builder(new AccessControlProfileId(0))
                        .setUserAuthenticationRequired(false)
                        .build();
        final Collection<AccessControlProfileId> idsNoAuth =
                Arrays.asList(new AccessControlProfileId(0));

        // Also put in some complicated CBOR to check that we don't accidentally
        // canonicalize this, leading to digest mismatches on the reader side.
        byte[] rawCbor1 = Util.cborEncode(
                new CborBuilder()
                        .addMap()
                        .put("a", "foo")
                        .put("b", "bar")
                        .put("c", "baz")
                        .end()
                        .build().get(0));
        Assert.assertEquals(
                "{\n" +
                "  'a' : 'foo',\n" +
                "  'b' : 'bar',\n" +
                "  'c' : 'baz'\n" +
                "}", Util.cborPrettyPrint(rawCbor1));

        byte[] rawCbor2 = Util.cborEncode(
                new CborBuilder()
                        .addMap()
                        .put("c", "baz")
                        .put("b", "bar")
                        .put("a", "foo")
                        .end()
                        .build().get(0));
        Assert.assertEquals(
                "{\n" +
                        "  'c' : 'baz',\n" +
                        "  'b' : 'bar',\n" +
                        "  'a' : 'foo'\n" +
                        "}", Util.cborPrettyPrint(rawCbor2));

        PersonalizationData personalizationData =
                new PersonalizationData.Builder()
                        .addAccessControlProfile(noAuthProfile)
                        .putEntryString(MDL_NAMESPACE, "given_name", idsNoAuth, "Erika")
                        .putEntryString(MDL_NAMESPACE, "family_name", idsNoAuth, "Mustermann")
                        .putEntryInteger(MDL_NAMESPACE, "some_number", idsNoAuth, 42)
                        .putEntry(MDL_NAMESPACE, "raw_cbor_1", idsNoAuth, rawCbor1)
                        .putEntry(MDL_NAMESPACE, "raw_cbor_2", idsNoAuth, rawCbor2)
                        .putEntryBoolean(AAMVA_NAMESPACE, "real_id", idsNoAuth, true)
                        .build();

        // Generate Issuing Authority keypair and X509 certificate.
        KeyPair issuerAuthorityKeyPair = generateIssuingAuthorityKeyPair();
        X509Certificate issuerAuthorityCertificate =
                getSelfSignedIssuerAuthorityCertificate(issuerAuthorityKeyPair);

        byte[] signedPop = Utility.provisionSelfSignedCredential(store,
                "test",
                issuerAuthorityKeyPair.getPrivate(),
                issuerAuthorityCertificate,
                MDL_DOCTYPE,
                personalizationData,
                1,
                2);

        // Check that the Proof Of Provisioning contains raw_cbor_1 and raw_cbor_2 in
        // without any reordering...
        byte[] proofOfProvisioning = Util.coseSign1GetData(Util.cborDecode(signedPop));
        Assert.assertEquals(
                "[\n" +
                        "  'ProofOfProvisioning',\n" +
                        "  'org.iso.18013.5.1.mDL',\n" +
                        "  [\n" +
                        "    {\n" +
                        "      'id' : 0\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  {\n" +
                        "    'org.iso.18013.5.1' : [\n" +
                        "      {\n" +
                        "        'name' : 'given_name',\n" +
                        "        'value' : 'Erika',\n" +
                        "        'accessControlProfiles' : [0]\n" +
                        "      },\n" +
                        "      {\n" +
                        "        'name' : 'family_name',\n" +
                        "        'value' : 'Mustermann',\n" +
                        "        'accessControlProfiles' : [0]\n" +
                        "      },\n" +
                        "      {\n" +
                        "        'name' : 'some_number',\n" +
                        "        'value' : 42,\n" +
                        "        'accessControlProfiles' : [0]\n" +
                        "      },\n" +
                        "      {\n" +
                        "        'name' : 'raw_cbor_1',\n" +
                        "        'value' : {\n" +
                        "          'a' : 'foo',\n" +
                        "          'b' : 'bar',\n" +
                        "          'c' : 'baz'\n" +
                        "        },\n" +
                        "        'accessControlProfiles' : [0]\n" +
                        "      },\n" +
                        "      {\n" +
                        "        'name' : 'raw_cbor_2',\n" +
                        "        'value' : {\n" +
                        "          'c' : 'baz',\n" +
                        "          'b' : 'bar',\n" +
                        "          'a' : 'foo'\n" +
                        "        },\n" +
                        "        'accessControlProfiles' : [0]\n" +
                        "      }\n" +
                        "    ],\n" +
                        "    'org.aamva.18013.5.1' : [\n" +
                        "      {\n" +
                        "        'name' : 'real_id',\n" +
                        "        'value' : true,\n" +
                        "        'accessControlProfiles' : [0]\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  },\n" +
                        "  false\n" +
                        "]",
                Util.cborPrettyPrint(proofOfProvisioning));

        Map<String, Collection<String>> issuerSignedEntriesToRequest = new HashMap<>();
        issuerSignedEntriesToRequest.put(MDL_NAMESPACE,
                Arrays.asList("given_name", "family_name", "some_number", "raw_cbor_1", "raw_cbor_2"));
        issuerSignedEntriesToRequest.put(AAMVA_NAMESPACE, Arrays.asList("real_id"));

        KeyPair readerEphemeralKeyPair = Util.createEphemeralKeyPair();

        PresentationSession session = store.createPresentationSession(
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        KeyPair eDeviceKeyPair = session.getEphemeralKeyPair();
        session.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());

        byte[] encodedSessionTranscript = Util.buildSessionTranscript(eDeviceKeyPair);
        session.setSessionTranscript(encodedSessionTranscript);
        CredentialDataResult result = session.getCredentialData("test",
                new CredentialDataRequest.Builder()
                        .setIssuerSignedEntriesToRequest(issuerSignedEntriesToRequest)
                        // TODO: also request some deviceSigned values and check below...
                        .build());

        // Check that credential storage didn't accidentally canonicalize data element values.
        Assert.assertArrayEquals(rawCbor1, result.getIssuerSignedEntries().getEntry(MDL_NAMESPACE, "raw_cbor_1"));
        Assert.assertArrayEquals(rawCbor2, result.getIssuerSignedEntries().getEntry(MDL_NAMESPACE, "raw_cbor_2"));

        byte[] encodedDeviceNamespaces = result.getDeviceNameSpaces();
        byte[] encodedDeviceSignedSignature = result.getDeviceSignature();
        byte[] encodedDeviceSignedMac = result.getDeviceMac();

        byte[] staticAuthData = result.getStaticAuthenticationData();
        Pair<Map<String, List<byte[]>>, byte[]>
                decodedStaticAuthData = Utility.decodeStaticAuthData(staticAuthData);

        Map<String, List<byte[]>> issuerSignedDataItems = decodedStaticAuthData.first;
        byte[] encodedIssuerAuth = decodedStaticAuthData.second;

        Map<String, List<byte[]>> issuerSignedDataItemsWithValues =
                Utility.mergeIssuerSigned(issuerSignedDataItems, result.getIssuerSignedEntries());

        Map<String, Long> mdlNsErrors = new HashMap<>();
        mdlNsErrors.put("element_with_error", 0L);
        mdlNsErrors.put("another_element_with_error", (long) -42);
        Map<String, Long> aamvaNsErrors = new HashMap<>();
        aamvaNsErrors.put("yet_another_element_with_error", 1L);
        Map<String, Map<String, Long>> errors = new HashMap<>();
        errors.put(MDL_NAMESPACE, mdlNsErrors);
        errors.put(AAMVA_NAMESPACE, aamvaNsErrors);

        // Generate DeviceResponse
        byte[] encodedDeviceResponse =
                new DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
                        .addDocument(MDL_DOCTYPE,
                                encodedDeviceNamespaces,
                                encodedDeviceSignedSignature,
                                encodedDeviceSignedMac,
                                issuerSignedDataItemsWithValues,
                                errors,
                                encodedIssuerAuth)
                        .generate();

        // Check that errors is set correctly.
        DataItem drItem = Util.cborDecode(encodedDeviceResponse);
        List<DataItem> docsItem = Util.cborMapExtractArray(drItem, "documents");
        DataItem errorsItem = Util.cborMapExtract(docsItem.get(0), "errors");
        Assert.assertEquals("{\n" +
                        "  \"org.aamva.18013.5.1\": {\n" +
                        "    \"yet_another_element_with_error\": 1\n" +
                        "  },\n" +
                        "  \"org.iso.18013.5.1\": {\n" +
                        "    \"element_with_error\": 0,\n" +
                        "    \"another_element_with_error\": -42\n" +
                        "  }\n" +
                        "}",
                CborUtil.toDiagnostics(errorsItem, CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT));

        // Check that the parser picked up all the fields we were setting above...
        //
        DeviceResponseParser.DeviceResponse deviceResponse = new DeviceResponseParser()
                .setEphemeralReaderKey(readerEphemeralKeyPair.getPrivate())
                .setDeviceResponse(encodedDeviceResponse)
                .setSessionTranscript(encodedSessionTranscript)
                .parse();
        Assert.assertEquals(Constants.DEVICE_RESPONSE_STATUS_OK,
                deviceResponse.getStatus());
        Assert.assertEquals(1, deviceResponse.getDocuments().size());
        DeviceResponseParser.Document d = deviceResponse.getDocuments().get(0);

        Assert.assertEquals(MDL_DOCTYPE, d.getDocType());
        Assert.assertEquals(0, d.getDeviceNamespaces().size());
        Assert.assertEquals(2, d.getIssuerNamespaces().size());
        Assert.assertEquals(5, d.getIssuerEntryNames(MDL_NAMESPACE).size());
        Assert.assertEquals("Erika", d.getIssuerEntryString(MDL_NAMESPACE, "given_name"));
        Assert.assertEquals("Mustermann", d.getIssuerEntryString(MDL_NAMESPACE, "family_name"));
        Assert.assertEquals(42, d.getIssuerEntryNumber(MDL_NAMESPACE, "some_number"));
        Assert.assertEquals(1, d.getIssuerEntryNames(AAMVA_NAMESPACE).size());
        Assert.assertEquals(true, d.getIssuerEntryBoolean(AAMVA_NAMESPACE, "real_id"));
        // Check that response encoding/decoding didn't accidentally canonicalize data element values.
        Assert.assertArrayEquals(rawCbor1, d.getIssuerEntryData(MDL_NAMESPACE, "raw_cbor_1"));
        Assert.assertArrayEquals(rawCbor2, d.getIssuerEntryData(MDL_NAMESPACE, "raw_cbor_2"));

        // TODO: also check |errors| when/if we get DeviceResponseParser API to read it.
    }

}
