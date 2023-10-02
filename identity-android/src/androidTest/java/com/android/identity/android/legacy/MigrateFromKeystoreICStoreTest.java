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

package com.android.identity.android.legacy;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import com.android.identity.AndroidAttestationExtensionParser;
import com.android.identity.android.securearea.AndroidKeystoreSecureArea;
import com.android.identity.android.storage.AndroidStorageEngine;
import com.android.identity.credential.Credential;
import com.android.identity.credential.CredentialStore;
import com.android.identity.credential.NameSpacedData;
import com.android.identity.internal.Util;
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator;
import com.android.identity.mdoc.mso.StaticAuthDataGenerator;
import com.android.identity.mdoc.util.MdocUtil;
import com.android.identity.securearea.SecureAreaRepository;
import com.android.identity.storage.StorageEngine;
import com.android.identity.util.Timestamp;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;

@SuppressWarnings("deprecation")
public class MigrateFromKeystoreICStoreTest {
    private static final String TAG = "";
    final String DOC_TYPE = "com.example.credential_xyz";
    IdentityCredentialStore mICStore;
    StorageEngine mStorageEngine;

    AndroidKeystoreSecureArea mKeystoreEngine;

    SecureAreaRepository mKeystoreEngineRepository;

    @Before
    public void setup() {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        mStorageEngine = new AndroidStorageEngine.Builder(context, storageDir).build();

        mKeystoreEngineRepository = new SecureAreaRepository();
        mKeystoreEngine = new AndroidKeystoreSecureArea(context, mStorageEngine);
        mKeystoreEngineRepository.addImplementation(mKeystoreEngine);

        mICStore = Utility.getIdentityCredentialStore(context);
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

    private static byte[] getExampleDrivingPrivilegesCbor() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new CborEncoder(baos).encode(new CborBuilder()
                    .addArray()
                    .addMap()
                    .put(new UnicodeString("vehicle_category_code"), new UnicodeString("TODO"))
                    .put(new UnicodeString("value"), new UnsignedInteger(42))
                    .end()
                    .end()
                    .build());
        } catch (CborException e) {
            fail();
        }
        return baos.toByteArray();
    }

    static void createCredentialWithChallenge(IdentityCredentialStore store,
                                              String credentialName,
                                              byte[] challenge,
                                              Map<String, Map<String, byte[]>> namespacedData)
            throws IdentityCredentialException {
        WritableIdentityCredential wc = store.createCredential(credentialName,
                "org.iso.18013-5.2019.mdl");

        Collection<X509Certificate> certificateChain =
                wc.getCredentialKeyCertificateChain(challenge);

        // Profile 0 (no authentication)
        AccessControlProfile noAuthProfile =
                new AccessControlProfile.Builder(new AccessControlProfileId(0))
                        .setUserAuthenticationRequired(false)
                        .build();

        byte[] drivingPrivileges = getExampleDrivingPrivilegesCbor();

        Collection<AccessControlProfileId> idsNoAuth = new ArrayList<AccessControlProfileId>();
        idsNoAuth.add(new AccessControlProfileId(0));
        String mdlNs = "org.iso.18013-5.2019";
        PersonalizationData.Builder personalizationDataBuilder =
                new PersonalizationData.Builder()
                        .addAccessControlProfile(noAuthProfile);

        for (String namespace : namespacedData.keySet()) {
            for (String dataElemId : namespacedData.get(namespace).keySet()) {
                personalizationDataBuilder.putEntry(namespace, dataElemId, idsNoAuth, namespacedData.get(namespace).get(dataElemId));
            }
        }

        byte[] proofOfProvisioningSignature = wc.personalize(personalizationDataBuilder.build());
        byte[] proofOfProvisioning =
                Util.coseSign1GetData(Util.cborDecode(proofOfProvisioningSignature));

        assertTrue(Util.coseSign1CheckSignature(
                Util.cborDecode(proofOfProvisioningSignature),
                new byte[0], // Additional data
                certificateChain.iterator().next().getPublicKey()));

    }

    static Collection<X509Certificate> createCredentialWithPersonalizationData(
            IdentityCredentialStore store,
            String credentialName,
            byte[] challenge,
            PersonalizationData personalizationData) throws IdentityCredentialException {
        WritableIdentityCredential wc = null;
        wc = store.createCredential(credentialName, "org.iso.18013-5.2019.mdl");

        Collection<X509Certificate> certificateChain =
                wc.getCredentialKeyCertificateChain(challenge);

        byte[] proofOfProvisioningSignature = wc.personalize(personalizationData);
        byte[] proofOfProvisioning =
                Util.coseSign1GetData(Util.cborDecode(proofOfProvisioningSignature));

        assertTrue(Util.coseSign1CheckSignature(
                Util.cborDecode(proofOfProvisioningSignature),
                new byte[0], // Additional data
                certificateChain.iterator().next().getPublicKey()));

        return certificateChain;
    }

    private static KeyPair generateIssuingAuthorityKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        kpg.initialize(ecSpec);
        return kpg.generateKeyPair();
    }

    private static X509Certificate getSelfSignedIssuerAuthorityCertificate(
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

    private void migrateAndCheckResults(String credName,
                                        byte[] challenge,
                                        Map<String, Map<String, byte[]>> expectedNsData) throws Exception {
        // get KeystoreIdentityCredential
        KeystoreIdentityCredential keystoreCred = (KeystoreIdentityCredential) mICStore.getCredentialByName(
                credName, IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(keystoreCred);

        // migrate
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mKeystoreEngineRepository);
        Credential migratedCred = keystoreCred.migrateToCredentialStore(credentialStore);

        // check deletion
        assertNull(mICStore.getCredentialByName(
                credName, IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256));

        // ensure all namespace data is as expected
        NameSpacedData nsData = migratedCred.getNameSpacedData();
        List<String> resultNamespaces = nsData.getNameSpaceNames();
        assertEquals(resultNamespaces.size(), expectedNsData.keySet().size());
        assertTrue(resultNamespaces.containsAll(expectedNsData.keySet()));
        for (String namespace : expectedNsData.keySet()) {
            for (String dataElemId : expectedNsData.get(namespace).keySet()) {
                assertArrayEquals(expectedNsData.get(namespace).get(dataElemId), nsData.getDataElement(namespace, dataElemId));
            }
        }

        // check key alias + attestation
        String aliasForOldCredKey = CredentialData.getAliasFromCredentialName(credName); // "identity_credential_credkey_givenCredName"
        assertEquals(aliasForOldCredKey, migratedCred.getCredentialKeyAlias());

        KeyStore ks;
        List<X509Certificate> certChain = new ArrayList<>();
        Certificate[] certificateChain;
        try {
            ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            certificateChain = ks.getCertificateChain(aliasForOldCredKey);
            for (Certificate cert : certificateChain) {
                certChain.add((X509Certificate) cert);
            }
        } catch (CertificateException
                 | KeyStoreException
                 | IOException
                 | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error generate certificate chain", e);
        }
        assertEquals(certChain, migratedCred.getAttestation());

        // Check the attestation extension
        AndroidAttestationExtensionParser parser = new AndroidAttestationExtensionParser(certChain.get(0));
        Assert.assertArrayEquals("SomeChallenge".getBytes(), parser.getAttestationChallenge());
        AndroidAttestationExtensionParser.SecurityLevel securityLevel = parser.getKeymasterSecurityLevel();
        Assert.assertEquals(AndroidAttestationExtensionParser.SecurityLevel.TRUSTED_ENVIRONMENT, securityLevel);

        // Check we can load the credential...
        migratedCred = credentialStore.lookupCredential(credName);
        Assert.assertNotNull(migratedCred);
        Assert.assertEquals(credName, migratedCred.getName());
        List<X509Certificate> certChain2 = migratedCred.getAttestation();
        Assert.assertEquals(certChain.size(), certChain2.size());
        for (int n = 0; n < certChain.size(); n++) {
            Assert.assertEquals(certChain.get(n), certChain2.get(n));
        }

        // Create pending authentication key and check its attestation
        assertEquals(0, migratedCred.getAuthenticationKeys().size());
        assertEquals(0, migratedCred.getPendingAuthenticationKeys().size());
        assertNull(migratedCred.findAuthenticationKey(Timestamp.ofEpochMilli(100)));
        byte[] authKeyChallenge = new byte[] {20, 21, 22};
        Credential.PendingAuthenticationKey pendingAuthenticationKey =
                migratedCred.createPendingAuthenticationKey(new AndroidKeystoreSecureArea.CreateKeySettings.Builder(authKeyChallenge)
                                .setUserAuthenticationRequired(true, 30*1000,
                                        AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_LSKF)
                                .build(),
                        null);
        parser = new AndroidAttestationExtensionParser(pendingAuthenticationKey.getAttestation().get(0));
        Assert.assertArrayEquals(authKeyChallenge,
                parser.getAttestationChallenge());
        Assert.assertEquals(AndroidAttestationExtensionParser.SecurityLevel.TRUSTED_ENVIRONMENT,
                parser.getKeymasterSecurityLevel());


        // Generate an MSO and issuer-signed data for this authentication key.
        Timestamp timeBeforeValidity = Timestamp.ofEpochMilli(40);
        Timestamp timeValidityBegin = Timestamp.ofEpochMilli(50);
        Timestamp timeDuringValidity = Timestamp.ofEpochMilli(100);
        Timestamp timeValidityEnd = Timestamp.ofEpochMilli(150);
        Timestamp timeAfterValidity = Timestamp.ofEpochMilli(200);
        MobileSecurityObjectGenerator msoGenerator = new MobileSecurityObjectGenerator(
                "SHA-256",
                DOC_TYPE,
                pendingAuthenticationKey.getAttestation().get(0).getPublicKey());
        msoGenerator.setValidityInfo(timeBeforeValidity, timeValidityBegin, timeValidityEnd, null);

        Random deterministicRandomProvider = new Random(42);
        Map<String, List<byte[]>> issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                nsData,
                deterministicRandomProvider,
                16);

        for (String nameSpaceName : issuerNameSpaces.keySet()) {
            Map<Long, byte[]> digests = MdocUtil.calculateDigestsForNameSpace(
                    nameSpaceName,
                    issuerNameSpaces,
                    "SHA-256");
            msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests);
        }

        KeyPair issuerKeyPair = generateIssuingAuthorityKeyPair();
        X509Certificate issuerCert = getSelfSignedIssuerAuthorityCertificate(issuerKeyPair);

        byte[] mso = msoGenerator.generate();
        byte[] taggedEncodedMso = Util.cborEncode(Util.cborBuildTaggedByteString(mso));

        // IssuerAuth is a COSE_Sign1 where payload is MobileSecurityObjectBytes
        //
        // MobileSecurityObjectBytes = #6.24(bstr .cbor MobileSecurityObject)
        //
        ArrayList<X509Certificate> issuerCertChain = new ArrayList<>();
        issuerCertChain.add(issuerCert);
        byte[] encodedIssuerAuth = Util.cborEncode(Util.coseSign1Sign(issuerKeyPair.getPrivate(),
                "SHA256withECDSA", taggedEncodedMso,
                null,
                issuerCertChain));

        byte[] issuerProvidedAuthenticationData = new StaticAuthDataGenerator(
                MdocUtil.stripIssuerNameSpaces(issuerNameSpaces),
                encodedIssuerAuth).generate();

        // Now that we have issuer-provided authentication data we certify the authentication key.
        Credential.AuthenticationKey authKey = pendingAuthenticationKey.certify(
                issuerProvidedAuthenticationData,
                timeValidityBegin,
                timeValidityEnd);

        Assert.assertEquals(1, migratedCred.getAuthenticationKeys().size());
        Assert.assertEquals(0, migratedCred.getPendingAuthenticationKeys().size());

        // If at a time before anything is valid, should not be able to present
        Assert.assertNull(migratedCred.findAuthenticationKey(timeBeforeValidity));

        // Ditto for right after
        Assert.assertNull(migratedCred.findAuthenticationKey(timeAfterValidity));

        // Check we're able to present at a time when the auth keys are valid
        authKey = migratedCred.findAuthenticationKey(timeDuringValidity);
        Assert.assertNotNull(authKey);

        Assert.assertEquals(0, authKey.getUsageCount());
        authKey.increaseUsageCount();
        Assert.assertEquals(1, authKey.getUsageCount());

    }

    @Test
    public void singleNamespaceMultipleACP() throws Exception {
        Map<String, Map<String, byte[]>> namespacedData = new HashMap<>();
        Map<String, byte[]> dataForNs = new HashMap<>();

        // Profile 0 (no authentication)
        AccessControlProfile noAuthProfile =
                new AccessControlProfile.Builder(new AccessControlProfileId(0))
                        .setUserAuthenticationRequired(false)
                        .build();

        byte[] drivingPrivileges = getExampleDrivingPrivilegesCbor();

        Collection<AccessControlProfileId> idsNoAuth = new ArrayList<AccessControlProfileId>();
        idsNoAuth.add(new AccessControlProfileId(0));
        Collection<AccessControlProfileId> idsNoAcp = new ArrayList<AccessControlProfileId>();
        String mdlNs = "org.iso.18013-5.2019";
        PersonalizationData personalizationData =
                new PersonalizationData.Builder()
                        .addAccessControlProfile(noAuthProfile)
                        .putEntry(mdlNs, "First name", idsNoAuth, Util.cborEncodeString("Alan"))
                        .putEntry(mdlNs, "Last name", idsNoAuth, Util.cborEncodeString("Turing"))
                        .putEntry(mdlNs, "Home address", idsNoAuth,
                                Util.cborEncodeString("Maida Vale, London, England"))
                        .putEntry(mdlNs, "Birth date", idsNoAuth,
                                Util.cborEncodeString("19120623"))
                        .putEntry(mdlNs, "Cryptanalyst", idsNoAuth, Util.cborEncodeBoolean(true))
                        .putEntry(mdlNs, "Portrait image", idsNoAuth, Util.cborEncodeBytestring(
                                new byte[]{0x01, 0x02}))
                        .putEntry(mdlNs, "Height", idsNoAuth, Util.cborEncodeNumber(180))
                        .putEntry(mdlNs, "Neg Item", idsNoAuth, Util.cborEncodeNumber(-42))
                        .putEntry(mdlNs, "Int Two Bytes", idsNoAuth, Util.cborEncodeNumber(0x101))
                        .putEntry(mdlNs, "Int Four Bytes", idsNoAuth,
                                Util.cborEncodeNumber(0x10001))
                        .putEntry(mdlNs, "Int Eight Bytes", idsNoAuth,
                                Util.cborEncodeNumber(0x100000001L))
                        .putEntry(mdlNs, "driving_privileges", idsNoAuth, drivingPrivileges)
                        .putEntry(mdlNs, "No Access", idsNoAcp,
                                Util.cborEncodeString("Cannot be retrieved"))
                        .build();
        dataForNs.put("First name", Util.cborEncodeString("Alan"));
        dataForNs.put("Last name", Util.cborEncodeString("Turing"));
        dataForNs.put("Home address", Util.cborEncodeString("Maida Vale, London, England"));
        dataForNs.put("Birth date", Util.cborEncodeString("19120623"));
        dataForNs.put("Cryptanalyst", Util.cborEncodeBoolean(true));
        dataForNs.put("Portrait image", Util.cborEncodeBytestring(new byte[]{0x01, 0x02}));
        dataForNs.put("Height", Util.cborEncodeNumber(180));
        dataForNs.put("Neg Item", Util.cborEncodeNumber(-42));
        dataForNs.put("Int Two Bytes", Util.cborEncodeNumber(0x101));
        dataForNs.put("Int Four Bytes", Util.cborEncodeNumber(0x10001));
        dataForNs.put("Int Eight Bytes", Util.cborEncodeNumber(0x100000001L));
        dataForNs.put("driving_privileges", drivingPrivileges);
        dataForNs.put("No Access", Util.cborEncodeString("Cannot be retrieved"));
        namespacedData.put(mdlNs, dataForNs);

        String credName = "test1";
        byte[] challenge = "SomeChallenge".getBytes();
        mICStore.deleteCredentialByName(credName);
        createCredentialWithPersonalizationData(mICStore, credName, challenge, personalizationData);
        KeystoreIdentityCredential keystoreCred = (KeystoreIdentityCredential) mICStore.getCredentialByName(
                credName, IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(keystoreCred);

        migrateAndCheckResults(credName, challenge, namespacedData);
    }

    @Test
    public void multipleNamespaceSameData() throws Exception {
        Map<String, Map<String, byte[]>> namespacedData = new HashMap<>();
        Map<String, byte[]> dataForNs = new HashMap<>();

        byte[] drivingPrivileges = getExampleDrivingPrivilegesCbor();

        dataForNs.put("First name", Util.cborEncodeString("Alan"));
        dataForNs.put("Last name", Util.cborEncodeString("Turing"));
        dataForNs.put("Home address", Util.cborEncodeString("Maida Vale, London, England"));
        dataForNs.put("Birth date", Util.cborEncodeString("19120623"));
        dataForNs.put("Cryptanalyst", Util.cborEncodeBoolean(true));
        dataForNs.put("Portrait image", Util.cborEncodeBytestring(new byte[]{0x01, 0x02}));
        dataForNs.put("Height", Util.cborEncodeNumber(180));
        dataForNs.put("Neg Item", Util.cborEncodeNumber(-42));
        dataForNs.put("Int Two Bytes", Util.cborEncodeNumber(0x101));
        dataForNs.put("Int Four Bytes", Util.cborEncodeNumber(0x10001));
        dataForNs.put("Int Eight Bytes", Util.cborEncodeNumber(0x100000001L));
        dataForNs.put("driving_privileges", drivingPrivileges);
        dataForNs.put("No Access", Util.cborEncodeString("Cannot be retrieved"));

        namespacedData.put("org.iso.18013-5.2019", dataForNs);
        namespacedData.put("test.org.iso.18013-5.2019", dataForNs);

        String credName = "test2";
        byte[] challenge = "SomeChallenge".getBytes();
        mICStore.deleteCredentialByName(credName);
        createCredentialWithChallenge(mICStore, credName, challenge, namespacedData);
        KeystoreIdentityCredential keystoreCred = (KeystoreIdentityCredential) mICStore.getCredentialByName(
                credName, IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(keystoreCred);

        migrateAndCheckResults(credName, challenge, namespacedData);
    }

    @Test
    public void multipleNamespaceOneEmpty() throws Exception {
        Map<String, Map<String, byte[]>> namespacedData = new HashMap<>();
        Map<String, byte[]> dataForNs1 = new HashMap<>();
        Map<String, byte[]> dataForNs2 = new HashMap<>();
        Map<String, byte[]> dataForNs3 = new HashMap<>();

        byte[] drivingPrivileges = getExampleDrivingPrivilegesCbor();

        dataForNs1.put("First name", Util.cborEncodeString("Alan"));
        dataForNs1.put("Last name", Util.cborEncodeString("Turing"));
        dataForNs1.put("Home address", Util.cborEncodeString("Maida Vale, London, England"));
        dataForNs1.put("Birth date", Util.cborEncodeString("19120623"));
        dataForNs2.put("Cryptanalyst", Util.cborEncodeBoolean(true));
        dataForNs2.put("Portrait image", Util.cborEncodeBytestring(new byte[]{0x01, 0x02}));
        dataForNs2.put("Height", Util.cborEncodeNumber(180));
        dataForNs2.put("Neg Item", Util.cborEncodeNumber(-42));
        dataForNs2.put("Int Two Bytes", Util.cborEncodeNumber(0x101));
        dataForNs2.put("Int Four Bytes", Util.cborEncodeNumber(0x10001));
        dataForNs2.put("Int Eight Bytes", Util.cborEncodeNumber(0x100000001L));
        dataForNs2.put("driving_privileges", drivingPrivileges);
        dataForNs2.put("No Access", Util.cborEncodeString("Cannot be retrieved"));
        dataForNs3.put("Random name", Util.cborEncodeBytestring(new byte[]{0x01, 0x02}));

        namespacedData.put("namespace 1", dataForNs1);
        namespacedData.put("namespace 2", dataForNs2);
        namespacedData.put("", dataForNs3);

        String credName = "test3";
        byte[] challenge = "SomeChallenge".getBytes();
        mICStore.deleteCredentialByName(credName);
        createCredentialWithChallenge(mICStore, credName, challenge, namespacedData);
        KeystoreIdentityCredential keystoreCred = (KeystoreIdentityCredential) mICStore.getCredentialByName(
                credName, IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(keystoreCred);

        migrateAndCheckResults(credName, challenge, namespacedData);
    }

    @Test
    public void deleteBeforeMigrationTest() throws Exception {
        Map<String, Map<String, byte[]>> namespacedData = new HashMap<>();
        Map<String, byte[]> dataForNs1 = new HashMap<>();

        dataForNs1.put("First name", Util.cborEncodeString("Alan"));
        namespacedData.put("namespace 1", dataForNs1);

        String credName = "testFailure";
        byte[] challenge = "SomeChallenge".getBytes();
        mICStore.deleteCredentialByName(credName);
        createCredentialWithChallenge(mICStore, credName, challenge, namespacedData);
        KeystoreIdentityCredential keystoreCred = (KeystoreIdentityCredential) mICStore.getCredentialByName(
                credName, IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(keystoreCred);

        // delete and try to migrate
        mICStore.deleteCredentialByName(credName);
        CredentialStore credentialStore = new CredentialStore(
                mStorageEngine,
                mKeystoreEngineRepository);
        assertNull(mICStore.getCredentialByName(credName, IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256));
        assertThrows(IllegalStateException.class, () -> keystoreCred.migrateToCredentialStore(credentialStore));
    }
}
