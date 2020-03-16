/*
 * Copyright (C) 2019 Google LLC
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

package com.ul.ims.gmdl.security.sessionEncryption.holder

import android.content.Context
import androidx.security.identity.IdentityCredentialStore
import androidx.test.core.app.ApplicationProvider
import com.ul.ims.gmdl.security.TestUtils
import com.ul.ims.gmdl.security.sessionencryption.holder.HolderSession
import org.junit.Assert
import org.junit.Test

class HolderSessionTest {

    companion object {
        const val CREDENTIAL_NAME = "mycredentialtest"
    }

    @Test
    fun getEphemeralPublicKeyTest() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val store = IdentityCredentialStore.getInstance(appContext)
        Assert.assertNotNull(store)

        store?.let {
            TestUtils.createCredential(store, CREDENTIAL_NAME)
            val credential = store.getCredentialByName(CREDENTIAL_NAME,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256)
            credential?.let {
                val eKeyPair = credential.createEphemeralKeyPair()
            }
        }

        val session = HolderSession(appContext, CREDENTIAL_NAME)
        Assert.assertNotNull(session)

        val ephemeralPublicKey = session.getEphemeralPublicKey()
        Assert.assertNotNull(ephemeralPublicKey)
    }
}