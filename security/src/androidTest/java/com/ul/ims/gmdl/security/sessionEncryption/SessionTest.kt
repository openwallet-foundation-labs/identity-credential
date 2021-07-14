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

import androidx.security.identity.IdentityCredentialException
import androidx.security.identity.IdentityCredentialStore
import androidx.test.InstrumentationRegistry
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.deviceEngagement.security.Security
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.Handover
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.SessionTranscript
import com.ul.ims.gmdl.cbordata.security.sessionEncryption.SessionData
import com.ul.ims.gmdl.cbordata.security.sessionEncryption.SessionEstablishment
import com.ul.ims.gmdl.security.TestUtils
import com.ul.ims.gmdl.security.TestUtils.CHIPER_SUITE_IDENT
import com.ul.ims.gmdl.security.TestUtils.DE_VERSION
import com.ul.ims.gmdl.security.sessionEncryption.holder.HolderSessionTest
import com.ul.ims.gmdl.security.sessionencryption.holder.HolderSessionManager
import com.ul.ims.gmdl.security.sessionencryption.verifier.VerifierSessionManager
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
        store.deleteCredentialByName(HolderSessionTest.CREDENTIAL_NAME)
        TestUtils.createCredential(store, HolderSessionTest.CREDENTIAL_NAME)

        // Session Manager is used to Encrypt/Decrypt Messages
        val sessionManager =
            HolderSessionManager.getInstance(appContext, HolderSessionTest.CREDENTIAL_NAME)

        // Set up a new holder session so the Device Engagement COSE_Key is ephemeral to this engagement
        sessionManager.initializeHolderSession()

        // Generate a CoseKey with an Ephemeral Key
        val coseKey = sessionManager.generateHolderCoseKey()
            ?: throw IdentityCredentialException("Error generating Holder CoseKey")

        val security = Security.Builder()
            .setCoseKey(coseKey)
            .setCipherSuiteIdent(CHIPER_SUITE_IDENT)
            .build()

        // Device engagement
        val deviceEngagement = DeviceEngagement.Builder()
            .version(DE_VERSION)
            .security(security)
            .build()

        val handover = Handover.Builder().build()

        // Verifier Session Manager is used to Encrypt/Decrypt Messages
        val verifierSessionManager = VerifierSessionManager(coseKey, deviceEngagement, handover)

        val verifierCoseKey = verifierSessionManager.getReaderCoseKey()
            ?: throw IdentityCredentialException("Error generating Verifier CoseKey")

        // Set up holder with reader cose key
        sessionManager.setVerifierEphemeralPublicKey(verifierCoseKey)

        // Generate a encrypted message from verifier to holder
        var encryptedMsg = verifierSessionManager.encryptData(readerMessage)
        Assert.assertNotNull(encryptedMsg)
        encryptedMsg?.let {
            // First message is sent inside the session establishment
            val sessionEstablishment = SessionEstablishment.Builder()
                .decode(verifierSessionManager.createSessionEstablishment(it).encode())
                .build()

            // Set session transcript in the holder side
            sessionEstablishment.readerKey?.let { rKey ->
                val sessionTranscript = SessionTranscript.Builder()
                    .setReaderKey(rKey.encode())
                    .setDeviceEngagement(deviceEngagement.encode())
                    .setHandover(handover)
                    .build()

                sessionManager.setSessionTranscript(sessionTranscript.encode())
            }

            val decryptedMsg = sessionManager.decryptData(it)

            Assert.assertNotNull(decryptedMsg)
            Assert.assertArrayEquals(readerMessage, decryptedMsg)
        }

        // Generate response encrypts data inside a Cbor structure
        encryptedMsg = sessionManager.generateResponse(holderMessage)
        Assert.assertNotNull(encryptedMsg)

        // Decode Cbor structure to get the data
        val sessionData = SessionData.Builder()
            .decode(encryptedMsg)
            .build()
        Assert.assertNotNull(sessionData.encryptedData)

        sessionData.encryptedData?.let { encryptedRes ->
            // Decrypt the message sent from holder to verifier
            val decryptedMsg = verifierSessionManager.decryptData(encryptedRes)
            Assert.assertNotNull(decryptedMsg)
            Assert.assertArrayEquals(holderMessage, decryptedMsg)
        }
    }
}