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
package com.android.identity.document

import com.android.identity.claim.Claim
import com.android.identity.credential.CredentialLoader
import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.crypto.EcCurve
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.storage.Storage
import com.android.identity.storage.ephemeral.EphemeralStorage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant;
import kotlinx.io.bytestring.ByteString
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentUtilTest {
    private lateinit var storage: Storage
    private lateinit var secureAreaRepository: SecureAreaRepository
    private lateinit var credentialLoader: CredentialLoader

    @BeforeTest
    fun setup() {
        storage = EphemeralStorage()
        secureAreaRepository = SecureAreaRepository.build {
            add(SoftwareSecureArea.create(storage))
        }
        credentialLoader = CredentialLoader()
        credentialLoader.addCredentialImplementation(
            TestSecureAreaBoundCredential::class
        ) { document -> TestSecureAreaBoundCredential(document) }
    }

    @Test
    fun managedCredentialHelper() = runTest {
        val documentStore = DocumentStore(
            storage = storage,
            secureAreaRepository = secureAreaRepository,
            credentialLoader = credentialLoader,
            documentMetadataFactory = SimpleDocumentMetadata::create
        )
        val secureArea: SecureArea =
            secureAreaRepository.getImplementation(SoftwareSecureArea.IDENTIFIER)!!
        val document = documentStore.createDocument()
        assertEquals(0, document.getCertifiedCredentials().size.toLong())
        assertEquals(0, document.getPendingCredentials().size.toLong())
        val authKeySettings = CreateKeySettings(setOf(KeyPurpose.SIGN), EcCurve.P256)
        val numCreds = 10
        val maxUsesPerCred = 5
        val minValidTimeMillis = 10L
        var numCredsCreated: Int
        val managedCredDomain = "managedCredentials"

        // Start the process at time 100 and certify all those credentials so they're
        // valid until time 200.
        numCredsCreated = DocumentUtil.managedCredentialHelper(
            document,
            managedCredDomain,
            createCredential = { credentialToReplace ->
                TestSecureAreaBoundCredential.create(
                    document,
                    credentialToReplace,
                    managedCredDomain,
                    secureArea,
                    authKeySettings
                )
            },
            Instant.fromEpochMilliseconds(100),
            numCreds,
            maxUsesPerCred,
            minValidTimeMillis,
            false
        )
        assertEquals(numCreds.toLong(), numCredsCreated.toLong())
        assertEquals(
            numCreds.toLong(),
            document.getPendingCredentials().size.toLong()
        )
        var count = 0
        for (pak in document.getPendingCredentials()) {
            pak.certify(
                ByteString(byteArrayOf(0, count++.toByte())),
                Instant.fromEpochMilliseconds(100),
                Instant.fromEpochMilliseconds(200)
            )
        }
        // We should now have |numCreds| certified credentials and none pending
        assertEquals(0, document.getPendingCredentials().size.toLong())
        assertEquals(numCreds.toLong(), document.getCertifiedCredentials().size.toLong())

        // Certifying again at this point should not make a difference.
        numCredsCreated = DocumentUtil.managedCredentialHelper(
            document,
            managedCredDomain,
            createCredential = { credentialToReplace ->
                TestSecureAreaBoundCredential.create(
                    document,
                    credentialToReplace,
                    managedCredDomain,
                    secureArea,
                    authKeySettings
                )
            },
            Instant.fromEpochMilliseconds(100),
            numCreds,
            maxUsesPerCred,
            minValidTimeMillis,
            false
        )
        assertEquals(0, numCredsCreated.toLong())
        assertEquals(0, document.getPendingCredentials().size.toLong())

        // Use up until just before the limit, and check it doesn't make a difference
        for (ak in document.getCertifiedCredentials()) {
            for (n in 0 until maxUsesPerCred - 1) {
                ak.increaseUsageCount()
            }
        }
        numCredsCreated = DocumentUtil.managedCredentialHelper(
            document,
            managedCredDomain,
            createCredential = { credentialToReplace ->
                TestSecureAreaBoundCredential.create(
                    document,
                    credentialToReplace,
                    managedCredDomain,
                    secureArea,
                    authKeySettings
                )
            },
            Instant.fromEpochMilliseconds(100),
            numCreds,
            maxUsesPerCred,
            minValidTimeMillis,
            false
        )
        assertEquals(0, numCredsCreated.toLong())
        assertEquals(0, document.getPendingCredentials().size.toLong())

        // For the first 5, use one more time and check replacements are generated for those
        // Let the replacements expire just a tad later
        count = 0
        for (ak in document.getCertifiedCredentials()) {
            ak.increaseUsageCount()
            if (++count >= 5) {
                break
            }
        }
        numCredsCreated = DocumentUtil.managedCredentialHelper(
            document,
            managedCredDomain,
            createCredential = { credentialToReplace ->
                TestSecureAreaBoundCredential.create(
                    document,
                    credentialToReplace,
                    managedCredDomain,
                    secureArea,
                    authKeySettings
                )
            },
            Instant.fromEpochMilliseconds(100),
            numCreds,
            maxUsesPerCred,
            minValidTimeMillis,
            false
        )
        assertEquals(5, numCredsCreated.toLong())
        assertEquals(5, document.getPendingCredentials().size.toLong())
        count = 0
        for (pak in document.getPendingCredentials()) {
            assertEquals(managedCredDomain, pak.domain)
            pak.certify(
                ByteString(byteArrayOf(1, count++.toByte())),
                Instant.fromEpochMilliseconds(100),
                Instant.fromEpochMilliseconds(210)
            )
        }
        // We should now have |numCreds| certified credentials and none pending
        assertEquals(0, document.getPendingCredentials().size.toLong())
        assertEquals(numCreds.toLong(), document.getCertifiedCredentials().size.toLong())
        // Check that the _right_ ones were removed by inspecting issuer-provided data.
        // We rely on some implementation details on how ordering works... also cross-reference
        // with data passed into certify() functions above.
        count = 0
        val expectedData1 = arrayOf(
            ByteString(byteArrayOf(0, 5)),
            ByteString(byteArrayOf(0, 6)),
            ByteString(byteArrayOf(0, 7)),
            ByteString(byteArrayOf(0, 8)),
            ByteString(byteArrayOf(0, 9)),
            ByteString(byteArrayOf(1, 0)),
            ByteString(byteArrayOf(1, 1)),
            ByteString(byteArrayOf(1, 2)),
            ByteString(byteArrayOf(1, 3)),
            ByteString(byteArrayOf(1, 4))
        )
        for (cred in document.getCertifiedCredentials()) {
            assertEquals(expectedData1[count++], cred.issuerProvidedData)
        }

        // Now move close to the expiration date of the original five credentials.
        // This should trigger just them for replacement
        numCredsCreated = DocumentUtil.managedCredentialHelper(
            document,
            managedCredDomain,
            createCredential = { credentialToReplace ->
                val credential = TestSecureAreaBoundCredential.create(
                    document,
                    credentialToReplace,
                    managedCredDomain,
                    secureArea,
                    authKeySettings
                )
                credential
            },
            Instant.fromEpochMilliseconds(195),
            numCreds,
            maxUsesPerCred,
            minValidTimeMillis,
            false
        )
        assertEquals(5, numCredsCreated.toLong())
        assertEquals(5, document.getPendingCredentials().size.toLong())
        count = 0
        for (pak in document.getPendingCredentials()) {
            assertEquals(managedCredDomain, pak.domain)
            pak.certify(
                ByteString(byteArrayOf(2, count++.toByte())),
                Instant.fromEpochMilliseconds(100),
                Instant.fromEpochMilliseconds(210)
            )
        }
        // We should now have |numCreds| certified credentials and none pending
        assertEquals(0, document.getPendingCredentials().size.toLong())
        assertEquals(numCreds.toLong(), document.getCertifiedCredentials().size.toLong())
        // Check that the _right_ ones were removed by inspecting issuer-provided data.
        // We rely on some implementation details on how ordering works... also cross-reference
        // with data passed into certify() functions above.
        val expectedData2 = arrayOf(
            ByteString(byteArrayOf(1, 0)),
            ByteString(byteArrayOf(1, 1)),
            ByteString(byteArrayOf(1, 2)),
            ByteString(byteArrayOf(1, 3)),
            ByteString(byteArrayOf(1, 4)),
            ByteString(byteArrayOf(2, 0)),
            ByteString(byteArrayOf(2, 1)),
            ByteString(byteArrayOf(2, 2)),
            ByteString(byteArrayOf(2, 3)),
            ByteString(byteArrayOf(2, 4))
        )
        count = 0
        for (credential in document.getCertifiedCredentials()) {
            assertEquals(expectedData2[count++], credential.issuerProvidedData)
        }
    }

    class TestSecureAreaBoundCredential : SecureAreaBoundCredential {
        companion object {
            suspend fun create(
                document: Document,
                asReplacementForIdentifier: String?,
                domain: String,
                secureArea: SecureArea,
                createKeySettings: CreateKeySettings
            ): TestSecureAreaBoundCredential {
                return TestSecureAreaBoundCredential(
                    document,
                    asReplacementForIdentifier,
                    domain,
                    secureArea,
                ).apply {
                    generateKey(createKeySettings)
                }
            }
        }

        private constructor(
            document: Document,
            asReplacementForIdentifier: String?,
            domain: String,
            secureArea: SecureArea,
        ) : super(document, asReplacementForIdentifier, domain, secureArea) {
        }

        constructor(
            document: Document
        ) : super(document) {}

        override fun getClaims(documentTypeRepository: DocumentTypeRepository?): List<Claim> {
            throw NotImplementedError()
        }
    }

}
