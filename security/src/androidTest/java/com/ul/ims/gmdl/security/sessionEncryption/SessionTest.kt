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

package com.ul.ims.gmdl.security.sessionEncryption

import androidx.security.identity.IdentityCredentialStore
import androidx.test.InstrumentationRegistry
import com.ul.ims.gmdl.security.TestUtils
import com.ul.ims.gmdl.security.sessionEncryption.holder.HolderSessionTest
import com.ul.ims.gmdl.security.sessionencryption.holder.HolderSession
import com.ul.ims.gmdl.security.sessionencryption.verifier.VerifierSession
import org.junit.Assert
import org.junit.Test

class SessionTest {

    @Test
    fun encryptDecryptTest() {
        val appContext = InstrumentationRegistry.getTargetContext()
        val store = IdentityCredentialStore.getInstance(appContext)
        val holderMessage = "hello from holder".toByteArray()
        val readerMessage = "hello from reader".toByteArray()

        // Create a Credential
        store?.let {
            it.deleteCredentialByName(HolderSessionTest.CREDENTIAL_NAME)
            TestUtils.createCredential(it, HolderSessionTest.CREDENTIAL_NAME)
        }

            val holderSession = HolderSession(appContext, HolderSessionTest.CREDENTIAL_NAME)
            val pk = holderSession.getEphemeralPublicKey()

            pk?.let {pKey ->
                val verifierSession = VerifierSession(pKey)

                // Holder Encrypt a msg and verifier must decrypt it
                holderSession.setVerifierEphemeralPublicKey(verifierSession.getReaderPublicKey())

                // Verifier Encrypt a msg and Holder must decrypt it
                var encryptedMsg = verifierSession.encryptMessageToHolder(readerMessage)
                Assert.assertNotNull(encryptedMsg)
                encryptedMsg?.let {
                    val decryptedMsg = holderSession.decryptMessageFromReader(it)
                    Assert.assertNotNull(decryptedMsg)
                    Assert.assertArrayEquals(readerMessage, decryptedMsg)
                }

                encryptedMsg = holderSession.encryptMessageToReader(holderMessage)
                Assert.assertNotNull(encryptedMsg)

                encryptedMsg?.let {
                    val decryptedMsg = verifierSession.decryptMessageFromHolder(it)
                    Assert.assertNotNull(decryptedMsg)
                    Assert.assertArrayEquals(holderMessage, decryptedMsg)
                }
            }
    }
}