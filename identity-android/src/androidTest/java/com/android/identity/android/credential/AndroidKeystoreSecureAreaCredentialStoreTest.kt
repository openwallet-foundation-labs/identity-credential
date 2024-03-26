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
package com.android.identity.android.credential

import androidx.test.InstrumentationRegistry
import com.android.identity.AndroidAttestationExtensionParser
import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.Credential
import com.android.identity.credential.CredentialStore
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.storage.StorageEngine
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File

// See CredentialStoreTest in non-Android tests for main tests for CredentialStore. These
// tests are just for the Android-specific bits including attestation.
//
class AndroidKeystoreSecureAreaCredentialStoreTest {
    companion object {
        private const val AUTH_KEY_DOMAIN = "domain"
    }

    private lateinit var storageEngine: StorageEngine
    private lateinit var secureArea: SecureArea
    private lateinit var secureAreaRepository: SecureAreaRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getTargetContext()
        val storageDir = File(context.dataDir, "ic-testing")
        storageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
        secureAreaRepository = SecureAreaRepository()
        secureArea = AndroidKeystoreSecureArea(context, storageEngine)
        secureAreaRepository.addImplementation(secureArea)
    }

    @Test
    fun testBasic() {
        val credentialStore = CredentialStore(storageEngine, secureAreaRepository)
        var credential: Credential? = credentialStore.createCredential(
            "testCredential"
        )
        credentialStore.addCredential(credential!!)
        Assert.assertEquals("testCredential", credential!!.name)

        // Create pending authentication key and check its attestation
        val authKeyChallenge = byteArrayOf(20, 21, 22)
        val pendingAuthenticationKey = credential.createAuthenticationKey(
            AUTH_KEY_DOMAIN,
            secureArea,
            AndroidKeystoreCreateKeySettings.Builder(authKeyChallenge)
                .setUserAuthenticationRequired(
                    true, (30 * 1000).toLong(),
                    setOf(UserAuthenticationType.LSKF, UserAuthenticationType.BIOMETRIC)
                )
                .build(),
            null
        )
        Assert.assertFalse(pendingAuthenticationKey.isCertified)
        val parser =
            AndroidAttestationExtensionParser(pendingAuthenticationKey.attestation.certificates[0].javaX509Certificate)
        Assert.assertArrayEquals(
            authKeyChallenge,
            parser.attestationChallenge
        )
        Assert.assertEquals(
            AndroidAttestationExtensionParser.SecurityLevel.TRUSTED_ENVIRONMENT,
            parser.keymasterSecurityLevel
        )

        // Check we can load the credential...
        credential = credentialStore.lookupCredential("testCredential")
        Assert.assertNotNull(credential)
        Assert.assertEquals("testCredential", credential!!.name)
        Assert.assertNull(credentialStore.lookupCredential("nonExistingCredential"))

        // Check creating a credential with an existing name overwrites the existing one
        credential = credentialStore.createCredential(
            "testCredential"
        )
        credentialStore.addCredential(credential)
        Assert.assertEquals("testCredential", credential.name)
        credential = credentialStore.lookupCredential("testCredential")
        Assert.assertNotNull(credential)
        Assert.assertEquals("testCredential", credential!!.name)
        credentialStore.deleteCredential("testCredential")
        Assert.assertNull(credentialStore.lookupCredential("testCredential"))
    }
}
