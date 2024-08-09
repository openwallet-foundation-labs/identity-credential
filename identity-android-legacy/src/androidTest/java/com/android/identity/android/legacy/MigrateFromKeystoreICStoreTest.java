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

import android.content.Context;

import com.android.identity.android.securearea.AndroidKeystoreKeyInfo;
import com.android.identity.android.securearea.AndroidKeystoreSecureArea;
import com.android.identity.android.storage.AndroidStorageEngine;
import com.android.identity.cbor.Cbor;
import com.android.identity.cbor.DiagnosticOption;
import com.android.identity.crypto.Crypto;
import com.android.identity.crypto.EcPublicKey;
import com.android.identity.crypto.EcPublicKeyJvmKt;
import com.android.identity.crypto.EcSignature;
import com.android.identity.document.NameSpacedData;
import com.android.identity.crypto.Algorithm;
import com.android.identity.crypto.EcCurve;
import com.android.identity.securearea.KeyAttestation;
import com.android.identity.securearea.KeyPurpose;
import com.android.identity.storage.StorageEngine;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.UnicodeString;
import kotlinx.io.files.Path;

@SuppressWarnings("deprecation")
public class MigrateFromKeystoreICStoreTest {
    private static final String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    private static final String MDL_NAMESPACE = "org.iso.18013.5.1";
    private static final String AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva";
    private static final String TEST_NAMESPACE = "org.example.test";

    // The two methods that can be used to migrate a credential from KeystoreIdentityCredentialStore
    // to CredentialStore are getNamedSpacedData() and getCredentialKey(). This test checks that
    // they work as expected..
    //
    @Test
    public void testMigrateToCredentialStore() throws Exception {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        Path storageFile = new Path(new File(context.getDataDir(), "testdata.bin"));
        StorageEngine storageEngine = new AndroidStorageEngine.Builder(context, storageFile).build();
        AndroidKeystoreSecureArea aksSecureArea = new AndroidKeystoreSecureArea(context, storageEngine);
        IdentityCredentialStore icStore = Utility.getIdentityCredentialStore(context);

        AccessControlProfile noAuthProfile =
                new AccessControlProfile.Builder(new AccessControlProfileId(0))
                        .setUserAuthenticationRequired(false)
                        .build();
        Collection<AccessControlProfileId> ids = new ArrayList<AccessControlProfileId>();
        ids.add(new AccessControlProfileId(0));

        byte[] encodedDrivingPrivileges = Util.cborEncode(
                new CborBuilder()
                        .addArray()
                        .addMap()
                        .put(new UnicodeString("vehicle_category_code"), new UnicodeString("A"))
                        .end()
                        .end()
                        .build().get(0));

        PersonalizationData personalizationData =
                new PersonalizationData.Builder()
                        .addAccessControlProfile(noAuthProfile)
                        .putEntry(MDL_NAMESPACE, "given_name", ids, Util.cborEncodeString("Erika"))
                        .putEntry(MDL_NAMESPACE, "family_name", ids, Util.cborEncodeString("Mustermann"))
                        .putEntry(MDL_NAMESPACE, "resident_address", ids, Util.cborEncodeString("Germany"))
                        .putEntry(MDL_NAMESPACE, "portrait", ids, Util.cborEncodeBytestring(new byte[]{0x01, 0x02}))
                        .putEntry(MDL_NAMESPACE, "height", ids, Util.cborEncodeNumber(180))
                        .putEntry(MDL_NAMESPACE, "driving_privileges", ids, encodedDrivingPrivileges)
                        .putEntry(AAMVA_NAMESPACE, "weight_range", ids, Util.cborEncodeNumber(5))
                        .putEntry(TEST_NAMESPACE, "neg_int", ids, Util.cborEncodeNumber(-42))
                        .putEntry(TEST_NAMESPACE, "int_16", ids, Util.cborEncodeNumber(0x101))
                        .putEntry(TEST_NAMESPACE, "int_32", ids, Util.cborEncodeNumber(0x10001))
                        .putEntry(TEST_NAMESPACE, "int_64", ids, Util.cborEncodeNumber(0x100000001L))
                        .build();
        String credName = "test";
        icStore.deleteCredentialByName(credName);
        WritableIdentityCredential wc = icStore.createCredential(credName, MDL_DOCTYPE);
        Collection<X509Certificate> wcCertChain = wc.getCredentialKeyCertificateChain("".getBytes(StandardCharsets.UTF_8));
        PublicKey credentialKeyPublic = wcCertChain.iterator().next().getPublicKey();
        wc.personalize(personalizationData);

        KeystoreIdentityCredential cred = (KeystoreIdentityCredential) icStore.getCredentialByName(
                credName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        Assert.assertNotNull(cred);

        // Get and check NameSpacedData
        NameSpacedData nsd = cred.getNameSpacedData();
        Assert.assertEquals(
                "{\n" +
                        "  \"org.iso.18013.5.1\": {\n" +
                        "    \"given_name\": 24(<< \"Erika\" >>),\n" +
                        "    \"family_name\": 24(<< \"Mustermann\" >>),\n" +
                        "    \"resident_address\": 24(<< \"Germany\" >>),\n" +
                        "    \"portrait\": 24(<< h'0102' >>),\n" +
                        "    \"height\": 24(<< 180 >>),\n" +
                        "    \"driving_privileges\": 24(<< [\n" +
                        "      {\n" +
                        "        \"vehicle_category_code\": \"A\"\n" +
                        "      }\n" +
                        "    ] >>)\n" +
                        "  },\n" +
                        "  \"org.iso.18013.5.1.aamva\": {\n" +
                        "    \"weight_range\": 24(<< 5 >>)\n" +
                        "  },\n" +
                        "  \"org.example.test\": {\n" +
                        "    \"neg_int\": 24(<< -42 >>),\n" +
                        "    \"int_16\": 24(<< 257 >>),\n" +
                        "    \"int_32\": 24(<< 65537 >>),\n" +
                        "    \"int_64\": 24(<< 4294967297 >>)\n" +
                        "  }\n" +
                        "}",
                Cbor.INSTANCE.toDiagnostics(
                        nsd.encodeAsCbor(),
                        Set.of(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)));

        String credentialKeyAlias = cred.getCredentialKeyAlias();
        aksSecureArea.createKeyForExistingAlias(credentialKeyAlias);

        // Check that CrendentialKey's KeyInfo is correct
        AndroidKeystoreKeyInfo keyInfo = aksSecureArea.getKeyInfo(credentialKeyAlias);
        Assert.assertNotNull(keyInfo);
        KeyAttestation attestation = keyInfo.getAttestation();
        Assert.assertTrue(attestation.getCertChain().getCertificates().size() >= 1);
        Assert.assertEquals(Set.of(KeyPurpose.SIGN), keyInfo.getKeyPurposes());
        Assert.assertEquals(EcCurve.P256, keyInfo.getPublicKey().getCurve());
        Assert.assertFalse(keyInfo.isStrongBoxBacked());
        Assert.assertFalse(keyInfo.isUserAuthenticationRequired());
        Assert.assertEquals(0, keyInfo.getUserAuthenticationTimeoutMillis());
        Assert.assertEquals(Set.of(), keyInfo.getUserAuthenticationTypes());
        Assert.assertNull(keyInfo.getAttestKeyAlias());
        Assert.assertNull(keyInfo.getValidFrom());
        Assert.assertNull(keyInfo.getValidUntil());

        // Check that we can use CredentialKey via AndroidKeystoreSecureArea...
        byte[] dataToSign = new byte[]{1, 2, 3};
        EcSignature ecSignature;
        ecSignature = aksSecureArea.sign(
                credentialKeyAlias,
                Algorithm.ES256,
                dataToSign,
                null);
        EcPublicKey ecCredentialKeyPublic = EcPublicKeyJvmKt.toEcPublicKey(credentialKeyPublic, EcCurve.P256);
        Assert.assertTrue(Crypto.INSTANCE.checkSignature(
                ecCredentialKeyPublic,
                dataToSign,
                Algorithm.ES256,
                ecSignature));
    }
}
