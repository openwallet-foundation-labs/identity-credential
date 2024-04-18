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
package com.android.identity.android.document

import androidx.test.InstrumentationRegistry
import com.android.identity.AndroidAttestationExtensionParser
import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.CredentialFactory
import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.storage.StorageEngine
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File

// See DocumentStoreTest in non-Android tests for main tests for DocumentStore. These
// tests are just for the Android-specific bits including attestation.
//
class AndroidKeystoreSecureAreaDocumentStoreTest {
    companion object {
        private const val CREDENTIAL_DOMAIN = "domain"
    }

    private lateinit var storageEngine: StorageEngine
    private lateinit var secureArea: SecureArea
    private lateinit var secureAreaRepository: SecureAreaRepository
    private lateinit var credentialFactory: CredentialFactory

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getTargetContext()
        val storageDir = File(context.dataDir, "ic-testing")
        storageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
        secureAreaRepository = SecureAreaRepository()
        secureArea = AndroidKeystoreSecureArea(context, storageEngine)
        secureAreaRepository.addImplementation(secureArea)
        credentialFactory = CredentialFactory()
        credentialFactory.addCredentialImplementation(SecureAreaBoundCredential::class)
    }

    @Test
    fun testBasic() {
        val documentStore = DocumentStore(storageEngine, secureAreaRepository, credentialFactory)
        var document: Document? = documentStore.createDocument(
            "testDocument"
        )
        documentStore.addDocument(document!!)
        Assert.assertEquals("testDocument", document!!.name)

        // Create pending credential and check its attestation
        val authKeyChallenge = byteArrayOf(20, 21, 22)
        val pendingCredential = SecureAreaBoundCredential(
            null,
            CREDENTIAL_DOMAIN,
            secureArea,
            AndroidKeystoreCreateKeySettings.Builder(authKeyChallenge)
                .setUserAuthenticationRequired(
                    true, (30 * 1000).toLong(),
                    setOf(UserAuthenticationType.LSKF, UserAuthenticationType.BIOMETRIC)
                )
                .build(),
        )
        document.addCredential(pendingCredential)
        Assert.assertFalse(pendingCredential.isCertified)
        val parser =
            AndroidAttestationExtensionParser(pendingCredential.attestation.certificates[0].javaX509Certificate)
        Assert.assertArrayEquals(
            authKeyChallenge,
            parser.attestationChallenge
        )
        Assert.assertEquals(
            AndroidAttestationExtensionParser.SecurityLevel.TRUSTED_ENVIRONMENT,
            parser.keymasterSecurityLevel
        )

        // Check we can load the document...
        document = documentStore.lookupDocument("testDocument")
        Assert.assertNotNull(document)
        Assert.assertEquals("testDocument", document!!.name)
        Assert.assertNull(documentStore.lookupDocument("nonExistingDocument"))

        // Check creating a document with an existing name overwrites the existing one
        document = documentStore.createDocument(
            "testDocument"
        )
        documentStore.addDocument(document)
        Assert.assertEquals("testDocument", document.name)
        document = documentStore.lookupDocument("testDocument")
        Assert.assertNotNull(document)
        Assert.assertEquals("testDocument", document!!.name)
        documentStore.deleteDocument("testDocument")
        Assert.assertNull(documentStore.lookupDocument("testDocument"))
    }
}
