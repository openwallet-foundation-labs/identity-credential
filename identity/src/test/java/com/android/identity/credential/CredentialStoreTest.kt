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
package com.android.identity.credential

import com.android.identity.crypto.EcCurve
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Timestamp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.Security

class CredentialStoreTest {
    private lateinit var storageEngine: StorageEngine
    private lateinit var secureArea: SecureArea
    private lateinit var secureAreaRepository: SecureAreaRepository

    // This isn't really used, we only use a single domain.
    private val AUTH_KEY_DOMAIN = "domain"

    @Before
    fun setup() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        storageEngine = EphemeralStorageEngine()
        secureAreaRepository = SecureAreaRepository()
        secureArea = SoftwareSecureArea(storageEngine)
        secureAreaRepository.addImplementation(secureArea)
    }

    @Test
    fun testListCredentials() {
        storageEngine.deleteAll()
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )
        Assert.assertEquals(0, credentialStore.listCredentials().size.toLong())
        for (n in 0..9) {
            credentialStore.addCredential(credentialStore.createCredential("testCred$n"))
        }
        Assert.assertEquals(10, credentialStore.listCredentials().size.toLong())
        credentialStore.deleteCredential("testCred1")
        Assert.assertEquals(9, credentialStore.listCredentials().size.toLong())
        for (n in 0..9) {
            if (n == 1) {
                Assert.assertFalse(credentialStore.listCredentials().contains("testCred$n"))
            } else {
                Assert.assertTrue(credentialStore.listCredentials().contains("testCred$n"))
            }
        }
    }

    @Test
    fun testObserver() {
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )

        val sb = StringBuilder()
        val observer = object : CredentialStore.Observer {
            override fun onCredentialAdded(credential: Credential) {
                sb.append("Added ${credential.name}")
            }

            override fun onCredentialDeleted(credential: Credential) {
                sb.append("Deleted ${credential.name}")
            }

            override fun onCredentialChanged(credential: Credential) {
                sb.append("Changed ${credential.name}")
            }
        }
        credentialStore.startObserving(observer)

        val cred0 = credentialStore.createCredential("cred0")
        Assert.assertEquals("", sb.toString())
        cred0.applicationData.setString("foo", "should not be notified")
        Assert.assertEquals("", sb.toString())
        credentialStore.addCredential(cred0)
        Assert.assertEquals("Added cred0", sb.toString())
        sb.clear()
        val cred1 = credentialStore.createCredential("cred1")
        Assert.assertEquals("", sb.toString())
        val cred2 = credentialStore.createCredential("cred2")
        Assert.assertEquals("", sb.toString())
        credentialStore.addCredential(cred1)
        Assert.assertEquals("Added cred1", sb.toString())
        credentialStore.addCredential(cred2)
        Assert.assertEquals("Added cred1" + "Added cred2", sb.toString())
        sb.clear()
        cred2.applicationData.setString("foo", "bar")
        cred1.applicationData.setString("foo", "bar")
        Assert.assertEquals("Changed cred2" + "Changed cred1", sb.toString())
        sb.clear()
        credentialStore.deleteCredential("cred0")
        credentialStore.deleteCredential("cred2")
        credentialStore.deleteCredential("cred1")
        Assert.assertEquals("Deleted cred0" + "Deleted cred2" + "Deleted cred1", sb.toString())
    }

    @Test
    fun testCreationDeletion() {
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )

        val credential = credentialStore.createCredential(
            "testCredential"
        )
        credentialStore.addCredential(credential)
        Assert.assertEquals("testCredential", credential.name)

        val credential2 = credentialStore.lookupCredential("testCredential")
        Assert.assertNotNull(credential2)
        Assert.assertEquals("testCredential", credential2!!.name)

        Assert.assertNull(credentialStore.lookupCredential("nonExistingCredential"))

        credentialStore.deleteCredential("testCredential")
        Assert.assertNull(credentialStore.lookupCredential("testCredential"))
    }

    /* Validates that the same instance is returned for the same credential name. This
     * relies on Credential.equals() not being overridden.
     */
    @Test
    fun testCaching() {
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )
        val a = credentialStore.createCredential("a")
        credentialStore.addCredential(a)
        val b = credentialStore.createCredential("b")
        credentialStore.addCredential(b)
        Assert.assertEquals(a, credentialStore.lookupCredential("a"))
        Assert.assertEquals(a, credentialStore.lookupCredential("a"))
        Assert.assertEquals(b, credentialStore.lookupCredential("b"))
        Assert.assertEquals(b, credentialStore.lookupCredential("b"))
        credentialStore.deleteCredential("a")
        Assert.assertNull(credentialStore.lookupCredential("a"))
        val a_prime = credentialStore.createCredential("a")
        credentialStore.addCredential(a_prime)
        Assert.assertEquals(a_prime, credentialStore.lookupCredential("a"))
        Assert.assertEquals(a_prime, credentialStore.lookupCredential("a"))
        Assert.assertNotEquals(a_prime, a)
        Assert.assertEquals(b, credentialStore.lookupCredential("b"))
    }

    @Test
    fun testNameSpacedData() {
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )
        val credential = credentialStore.createCredential("testCredential")
        credentialStore.addCredential(credential)
        val nameSpacedData = NameSpacedData.Builder()
            .putEntryString("ns1", "foo1", "bar1")
            .putEntryString("ns1", "foo2", "bar2")
            .putEntryString("ns1", "foo3", "bar3")
            .putEntryString("ns2", "bar1", "foo1")
            .putEntryString("ns2", "bar2", "foo2")
            .build()
        credential.applicationData.setNameSpacedData("credentialData", nameSpacedData)
        val loadedCredential = credentialStore.lookupCredential("testCredential")
        Assert.assertNotNull(loadedCredential)
        Assert.assertEquals("testCredential", loadedCredential!!.name)

        // We check that NameSpacedData is preserved across loads by simply comparing the
        // encoded data.
        Assert.assertEquals(
            credential.applicationData.getNameSpacedData("credentialData").toCbor(),
            loadedCredential.applicationData.getNameSpacedData("credentialData").toCbor()
        )
    }

    @Test
    fun testAuthenticationKeyUsage() {
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )
        val credential = credentialStore.createCredential("testCredential")
        credentialStore.addCredential(credential)
        val timeBeforeValidity = Timestamp.ofEpochMilli(40)
        val timeValidityBegin = Timestamp.ofEpochMilli(50)
        val timeDuringValidity = Timestamp.ofEpochMilli(100)
        val timeValidityEnd = Timestamp.ofEpochMilli(150)
        val timeAfterValidity = Timestamp.ofEpochMilli(200)

        // By default, we don't have any auth keys nor any pending auth keys.
        Assert.assertEquals(0, credential.certifiedAuthenticationKeys.size.toLong())
        Assert.assertEquals(0, credential.pendingAuthenticationKeys.size.toLong())
        Assert.assertEquals(0, credential.authenticationKeyCounter)

        // Since none are certified or even pending yet, we can't present anything.
        Assert.assertNull(credential.findAuthenticationKey(AUTH_KEY_DOMAIN, timeDuringValidity))

        // Create ten authentication keys...
        for (n in 0..9) {
            credential.createAuthenticationKey(
                AUTH_KEY_DOMAIN,
                secureArea,
                CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256),
                null
            )
        }
        Assert.assertEquals(0, credential.certifiedAuthenticationKeys.size.toLong())
        Assert.assertEquals(10, credential.pendingAuthenticationKeys.size.toLong())
        Assert.assertEquals(10, credential.authenticationKeyCounter)

        // ... and certify all of them
        var n = 0
        for (pendingAuthenticationKey in credential.pendingAuthenticationKeys) {
            val issuerProvidedAuthenticationData = byteArrayOf(1, 2, n++.toByte())
            pendingAuthenticationKey.certify(
                issuerProvidedAuthenticationData,
                timeValidityBegin,
                timeValidityEnd
            )
            Assert.assertEquals(n.toLong(), pendingAuthenticationKey.authenticationKeyCounter)
        }
        Assert.assertEquals(10, credential.certifiedAuthenticationKeys.size.toLong())
        Assert.assertEquals(0, credential.pendingAuthenticationKeys.size.toLong())

        // If at a time before anything is valid, should not be able to present
        Assert.assertNull(credential.findAuthenticationKey(AUTH_KEY_DOMAIN, timeBeforeValidity))

        // Ditto for right after
        Assert.assertNull(credential.findAuthenticationKey(AUTH_KEY_DOMAIN, timeAfterValidity))

        // Check we're able to present at a time when the auth keys are valid
        var authKey = credential.findAuthenticationKey(AUTH_KEY_DOMAIN, timeDuringValidity)
        Assert.assertNotNull(authKey)
        Assert.assertEquals(0, authKey!!.usageCount.toLong())

        // B/c of how findAuthenticationKey(AUTH_KEY_DOMAIN) we know we get the first key. Match
        // up with expected issuer signed data as per above.
        Assert.assertEquals(0.toByte().toLong(), authKey.issuerProvidedData[2].toLong())
        Assert.assertEquals(0, authKey.usageCount.toLong())
        authKey.increaseUsageCount()
        Assert.assertEquals(1, authKey.usageCount.toLong())

        // Simulate nine more presentations, all of them should now be used up
        n = 0
        while (n < 9) {
            authKey = credential.findAuthenticationKey(AUTH_KEY_DOMAIN, timeDuringValidity)
            Assert.assertNotNull(authKey)

            // B/c of how findAuthenticationKey(AUTH_KEY_DOMAIN) we know we get the keys after
            // the first one in order. Match up with expected issuer signed data as per above.
            Assert.assertEquals(
                (n + 1).toByte().toLong(), authKey!!.issuerProvidedData[2].toLong()
            )
            authKey.increaseUsageCount()
            n++
        }

        // All ten auth keys should now have a use count of 1.
        for (authenticationKey in credential.certifiedAuthenticationKeys) {
            Assert.assertEquals(1, authenticationKey.usageCount.toLong())
        }

        // Simulate ten more presentations
        n = 0
        while (n < 10) {
            authKey = credential.findAuthenticationKey(AUTH_KEY_DOMAIN, timeDuringValidity)
            Assert.assertNotNull(authKey)
            authKey!!.increaseUsageCount()
            n++
        }

        // All ten auth keys should now have a use count of 2.
        for (authenticationKey in credential.certifiedAuthenticationKeys) {
            Assert.assertEquals(2, authenticationKey.usageCount.toLong())
        }

        // Create and certify five replacements
        n = 0
        while (n < 5) {
            credential.createAuthenticationKey(
                AUTH_KEY_DOMAIN,
                secureArea,
                CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256),
                null
            )
            n++
        }
        Assert.assertEquals(10, credential.certifiedAuthenticationKeys.size.toLong())
        Assert.assertEquals(5, credential.pendingAuthenticationKeys.size.toLong())
        Assert.assertEquals(15, credential.authenticationKeyCounter)
        n = 11
        for (pendingAuthenticationKey in credential.pendingAuthenticationKeys) {
            pendingAuthenticationKey.certify(
                ByteArray(0),
                timeValidityBegin,
                timeValidityEnd
            )
            Assert.assertEquals(n.toLong(), pendingAuthenticationKey.authenticationKeyCounter)
            n++
        }
        Assert.assertEquals(15, credential.certifiedAuthenticationKeys.size.toLong())
        Assert.assertEquals(0, credential.pendingAuthenticationKeys.size.toLong())

        // Simulate ten presentations and check we get the newly created ones
        n = 0
        while (n < 10) {
            authKey = credential.findAuthenticationKey(AUTH_KEY_DOMAIN, timeDuringValidity)
            Assert.assertNotNull(authKey)
            Assert.assertEquals(0, authKey!!.issuerProvidedData.size.toLong())
            authKey.increaseUsageCount()
            n++
        }

        // All fifteen auth keys should now have a use count of 2.
        for (authenticationKey in credential.certifiedAuthenticationKeys) {
            Assert.assertEquals(2, authenticationKey.usageCount.toLong())
        }

        // Simulate 15 more presentations
        n = 0
        while (n < 15) {
            authKey = credential.findAuthenticationKey(AUTH_KEY_DOMAIN, timeDuringValidity)
            Assert.assertNotNull(authKey)
            authKey!!.increaseUsageCount()
            n++
        }

        // All fifteen auth keys should now have a use count of 3. This shows that
        // we're hitting the auth keys evenly (both old and new).
        for (authenticationKey in credential.certifiedAuthenticationKeys) {
            Assert.assertEquals(3, authenticationKey.usageCount.toLong())
        }
    }

    @Test
    fun testAuthenticationKeyPersistence() {
        var n: Int
        val timeValidityBegin = Timestamp.ofEpochMilli(50)
        val timeValidityEnd = Timestamp.ofEpochMilli(150)
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )
        val credential = credentialStore.createCredential("testCredential")
        credentialStore.addCredential(credential)
        Assert.assertEquals(0, credential.certifiedAuthenticationKeys.size.toLong())
        Assert.assertEquals(0, credential.pendingAuthenticationKeys.size.toLong())

        // Create ten pending auth keys and certify four of them
        n = 0
        while (n < 4) {
            credential.createAuthenticationKey(
                AUTH_KEY_DOMAIN,
                secureArea,
                CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256),
                null
            )
            n++
        }
        Assert.assertEquals(0, credential.certifiedAuthenticationKeys.size.toLong())
        Assert.assertEquals(4, credential.pendingAuthenticationKeys.size.toLong())
        n = 0
        for (authenticationKey in credential.pendingAuthenticationKeys) {
            // Because we check that we serialize things correctly below, make sure
            // the data and validity times vary for each key...
            authenticationKey.certify(
                byteArrayOf(1, 2, n.toByte()),
                Timestamp.ofEpochMilli(timeValidityBegin.toEpochMilli() + n),
                Timestamp.ofEpochMilli(timeValidityEnd.toEpochMilli() + 2 * n)
            )
            for (m in 0 until n) {
                authenticationKey.increaseUsageCount()
            }
            Assert.assertEquals(n.toLong(), authenticationKey.usageCount.toLong())
            n++
        }
        Assert.assertEquals(4, credential.certifiedAuthenticationKeys.size.toLong())
        Assert.assertEquals(0, credential.pendingAuthenticationKeys.size.toLong())
        n = 0
        while (n < 6) {
            credential.createAuthenticationKey(
                AUTH_KEY_DOMAIN,
                secureArea,
                CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256),
                null
            )
            n++
        }
        Assert.assertEquals(4, credential.certifiedAuthenticationKeys.size.toLong())
        Assert.assertEquals(6, credential.pendingAuthenticationKeys.size.toLong())
        val credential2 = credentialStore.lookupCredential("testCredential")
        Assert.assertNotNull(credential2)
        Assert.assertEquals(4, credential2!!.certifiedAuthenticationKeys.size.toLong())
        Assert.assertEquals(6, credential2.pendingAuthenticationKeys.size.toLong())

        // Now check that what we loaded matches what we created in-memory just above. We
        // use the fact that the order of the keys are preserved across save/load.
        val it1 = credential.certifiedAuthenticationKeys.iterator()
        val it2 = credential2.certifiedAuthenticationKeys.iterator()
        n = 0
        while (n < 4) {
            val key1 = it1.next()
            val key2 = it2.next()
            Assert.assertEquals(key1.alias, key2.alias)
            Assert.assertEquals(key1.validFrom, key2.validFrom)
            Assert.assertEquals(key1.validUntil, key2.validUntil)
            Assert.assertEquals(key1.usageCount.toLong(), key2.usageCount.toLong())
            Assert.assertArrayEquals(key1.issuerProvidedData, key2.issuerProvidedData)
            Assert.assertEquals(key1.attestation, key2.attestation)
            n++
        }
        val itp1 = credential.pendingAuthenticationKeys.iterator()
        val itp2 = credential2.pendingAuthenticationKeys.iterator()
        n = 0
        while (n < 6) {
            val key1 = itp1.next()
            val key2 = itp2.next()
            Assert.assertEquals(key1.alias, key2.alias)
            Assert.assertEquals(key1.attestation, key2.attestation)
            n++
        }
    }

    @Test
    fun testAuthenticationKeyValidity() {
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )
        val credential = credentialStore.createCredential("testCredential")
        credentialStore.addCredential(credential)

        // We want to check the behavior for when the holder has a birthday and the issuer
        // carefully sends half the MSOs to be used before the birthday (with age_in_years set to
        // 17) and half the MSOs for after the birthday (with age_in_years set to 18).
        //
        // The validity periods are carefully set so the MSOs for 17 are have validUntil set to
        // to the holders birthday and the MSOs for 18 are set so validFrom starts at the birthday.
        //
        val timeValidityBegin = Timestamp.ofEpochMilli(50)
        val timeOfUseBeforeBirthday = Timestamp.ofEpochMilli(80)
        val timeOfBirthday = Timestamp.ofEpochMilli(100)
        val timeOfUseAfterBirthday = Timestamp.ofEpochMilli(120)
        val timeValidityEnd = Timestamp.ofEpochMilli(150)

        // Create and certify ten auth keys. Put age_in_years as the issuer provided data so we can
        // check it below.
        var n = 0
        while (n < 10) {
            credential.createAuthenticationKey(
                AUTH_KEY_DOMAIN,
                secureArea,
                CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256),
                null
            )
            n++
        }
        Assert.assertEquals(10, credential.pendingAuthenticationKeys.size.toLong())
        n = 0
        for (pendingAuthenticationKey in credential.pendingAuthenticationKeys) {
            if (n < 5) {
                pendingAuthenticationKey.certify(byteArrayOf(17), timeValidityBegin, timeOfBirthday)
            } else {
                pendingAuthenticationKey.certify(byteArrayOf(18), timeOfBirthday, timeValidityEnd)
            }
            n++
        }

        // Simulate ten presentations before the birthday
        n = 0
        while (n < 10) {
            val authenticationKey =
                credential.findAuthenticationKey(AUTH_KEY_DOMAIN, timeOfUseBeforeBirthday)
            Assert.assertNotNull(authenticationKey)
            // Check we got a key with age 17.
            Assert.assertEquals(
                17.toByte().toLong(), authenticationKey!!.issuerProvidedData[0].toLong()
            )
            authenticationKey.increaseUsageCount()
            n++
        }

        // Simulate twenty presentations after the birthday
        n = 0
        while (n < 20) {
            val authenticationKey =
                credential.findAuthenticationKey(AUTH_KEY_DOMAIN, timeOfUseAfterBirthday)
            Assert.assertNotNull(authenticationKey)
            // Check we got a key with age 18.
            Assert.assertEquals(
                18.toByte().toLong(), authenticationKey!!.issuerProvidedData[0].toLong()
            )
            authenticationKey.increaseUsageCount()
            n++
        }

        // Examine the authentication keys. The first five should have use count 2, the
        // latter five use count 4.
        n = 0
        for (authenticationKey in credential.certifiedAuthenticationKeys) {
            if (n++ < 5) {
                Assert.assertEquals(2, authenticationKey.usageCount.toLong())
            } else {
                Assert.assertEquals(4, authenticationKey.usageCount.toLong())
            }
        }
    }

    @Test
    fun testApplicationData() {
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )
        val credential = credentialStore.createCredential("testCredential")
        credentialStore.addCredential(credential)
        val appData = credential.applicationData
        Assert.assertFalse(appData.keyExists("key1"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getData("key1") }
        Assert.assertFalse(appData.keyExists("key2"))
        Assert.assertThrows(IllegalArgumentException::class.java) { appData.getData("key2") }
        appData.setString("key1", "value1")
        Assert.assertEquals("value1", credential.applicationData.getString("key1"))
        appData.setString("key2", "value2")
        Assert.assertEquals("value2", credential.applicationData.getString("key2"))
        appData.setData("key3", byteArrayOf(1, 2, 3, 4))
        Assert.assertArrayEquals(byteArrayOf(1, 2, 3, 4), credential.applicationData.getData("key3"))
        appData.setData("key2", null as ByteArray?)
        Assert.assertFalse(credential.applicationData.keyExists("key2"))
        Assert.assertThrows(IllegalArgumentException::class.java) {
            credential.applicationData.getData(
                "key2"
            )
        }

        // Load the credential again and check that data is still there
        val loadedCredential = credentialStore.lookupCredential("testCredential")
        Assert.assertNotNull(loadedCredential)
        Assert.assertEquals("testCredential", loadedCredential!!.name)
        Assert.assertEquals(
            "value1", loadedCredential.applicationData
                .getString("key1")
        )
        Assert.assertFalse(loadedCredential.applicationData.keyExists("key2"))
        Assert.assertThrows(IllegalArgumentException::class.java) {
            loadedCredential.applicationData.getData(
                "key2"
            )
        }
        Assert.assertArrayEquals(
            byteArrayOf(1, 2, 3, 4), loadedCredential.applicationData
                .getData("key3")
        )
    }

    @Test
    fun testAuthKeyApplicationData() {
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )
        var credential: Credential? = credentialStore.createCredential("testCredential")
        credentialStore.addCredential(credential!!)
        for (n in 0..9) {
            val pendingAuthKey = credential.createAuthenticationKey(
                AUTH_KEY_DOMAIN,
                secureArea,
                CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256),
                null
            )
            val value = String.format("bar%02d", n)
            val pendingAppData = pendingAuthKey.applicationData
            pendingAppData.setString("foo", value)
            pendingAppData.setData("bar", ByteArray(0))
            Assert.assertEquals(value, pendingAppData.getString("foo"))
            Assert.assertEquals(0, pendingAppData.getData("bar").size.toLong())
            Assert.assertFalse(pendingAppData.keyExists("non-existent"))
            Assert.assertThrows(IllegalArgumentException::class.java) { pendingAppData.getString("non-existent") }
        }
        Assert.assertEquals(10, credential.pendingAuthenticationKeys.size.toLong())
        Assert.assertEquals(0, credential.certifiedAuthenticationKeys.size.toLong())

        // Check that it's persisted to disk.
        try {
            Thread.sleep(1)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        credential = credentialStore.lookupCredential("testCredential")
        Assert.assertEquals(10, credential!!.pendingAuthenticationKeys.size.toLong())
        var n = 0
        for (pendingAuthKey in credential.pendingAuthenticationKeys) {
            val value = String.format("bar%02d", n++)
            val pendingAppData = pendingAuthKey.applicationData
            Assert.assertEquals(value, pendingAppData.getString("foo"))
            Assert.assertEquals(0, pendingAppData.getData("bar").size.toLong())
            Assert.assertFalse(pendingAppData.keyExists("non-existent"))
            Assert.assertThrows(IllegalArgumentException::class.java) { pendingAppData.getString("non-existent") }
        }

        // Certify and check that data carries over from pending AuthenticationKey
        // to AuthenticationKey
        n = 0
        for (authKey in credential.pendingAuthenticationKeys) {
            val value = String.format("bar%02d", n++)
            val pendingAppData = authKey.applicationData
            Assert.assertEquals(value, pendingAppData.getString("foo"))
            Assert.assertEquals(0, pendingAppData.getData("bar").size.toLong())
            Assert.assertFalse(pendingAppData.keyExists("non-existent"))
            Assert.assertThrows(IllegalArgumentException::class.java) { pendingAppData.getString("non-existent") }
            authKey.certify(
                byteArrayOf(0, n.toByte()),
                Timestamp.ofEpochMilli(100),
                Timestamp.ofEpochMilli(200)
            )
            val appData = authKey.applicationData
            Assert.assertEquals(value, appData.getString("foo"))
            Assert.assertEquals(0, appData.getData("bar").size.toLong())
            Assert.assertFalse(appData.keyExists("non-existent"))
            Assert.assertThrows(IllegalArgumentException::class.java) { appData.getString("non-existent") }
        }

        // Check it's persisted to disk.
        n = 0
        for (authKey in credential.certifiedAuthenticationKeys) {
            val value = String.format("bar%02d", n++)
            val appData = authKey.applicationData
            Assert.assertEquals(value, appData.getString("foo"))
            Assert.assertEquals(0, appData.getData("bar").size.toLong())
            Assert.assertFalse(appData.keyExists("non-existent"))
            Assert.assertThrows(IllegalArgumentException::class.java) { appData.getString("non-existent") }
        }
    }

    @Test
    fun testAuthKeyReplacement() {
        val credentialStore = CredentialStore(
            storageEngine,
            secureAreaRepository
        )
        val credential = credentialStore.createCredential("testCredential")
        credentialStore.addCredential(credential)
        Assert.assertEquals(0, credential.certifiedAuthenticationKeys.size.toLong())
        Assert.assertEquals(0, credential.pendingAuthenticationKeys.size.toLong())
        for (n in 0..9) {
            val pendingAuthKey = credential.createAuthenticationKey(
                AUTH_KEY_DOMAIN,
                secureArea,
                CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256),
                null
            )
            pendingAuthKey.certify(
                byteArrayOf(0, n.toByte()),
                Timestamp.ofEpochMilli(100),
                Timestamp.ofEpochMilli(200)
            )
        }
        Assert.assertEquals(0, credential.pendingAuthenticationKeys.size.toLong())
        Assert.assertEquals(10, credential.certifiedAuthenticationKeys.size.toLong())

        // Now replace the fifth authentication key
        val keyToReplace = credential.certifiedAuthenticationKeys[5]
        Assert.assertArrayEquals(byteArrayOf(0, 5), keyToReplace.issuerProvidedData)
        val pendingAuthKey = credential.createAuthenticationKey(
            AUTH_KEY_DOMAIN,
            secureArea,
            CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256),
            keyToReplace
        )
        // ... it's not replaced until certify() is called
        Assert.assertEquals(1, credential.pendingAuthenticationKeys.size.toLong())
        Assert.assertEquals(10, credential.certifiedAuthenticationKeys.size.toLong())
        pendingAuthKey.certify(
            byteArrayOf(1, 0),
            Timestamp.ofEpochMilli(100),
            Timestamp.ofEpochMilli(200)
        )
        // ... now it should be gone.
        Assert.assertEquals(0, credential.pendingAuthenticationKeys.size.toLong())
        Assert.assertEquals(10, credential.certifiedAuthenticationKeys.size.toLong())

        // Check that it was indeed the fifth key that was replaced inspecting issuer-provided data.
        // We rely on some implementation details on how ordering works... also cross-reference
        // with data passed into certify() functions above.
        var count = 0
        for (authKey in credential.certifiedAuthenticationKeys) {
            val expectedData = arrayOf(
                byteArrayOf(0, 0),
                byteArrayOf(0, 1),
                byteArrayOf(0, 2),
                byteArrayOf(0, 3),
                byteArrayOf(0, 4),
                byteArrayOf(0, 6),
                byteArrayOf(0, 7),
                byteArrayOf(0, 8),
                byteArrayOf(0, 9),
                byteArrayOf(1, 0)
            )
            Assert.assertArrayEquals(expectedData[count++], authKey.issuerProvidedData)
        }

        // Test the case where the replacement key is prematurely deleted. The key
        // being replaced should no longer reference it has a replacement...
        val toBeReplaced = credential.certifiedAuthenticationKeys[0]
        var replacement = credential.createAuthenticationKey(
            AUTH_KEY_DOMAIN,
            secureArea,
            CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256),
            toBeReplaced
        )
        Assert.assertEquals(toBeReplaced, replacement.replacementFor)
        Assert.assertEquals(replacement, toBeReplaced.replacement)
        replacement.delete()
        Assert.assertNull(toBeReplaced.replacement)

        // Similarly, test the case where the key to be replaced is prematurely deleted.
        // The replacement key should no longer indicate it's a replacement key.
        replacement = credential.createAuthenticationKey(
            AUTH_KEY_DOMAIN,
            secureArea,
            CreateKeySettings(ByteArray(0), setOf(KeyPurpose.SIGN), EcCurve.P256),
            toBeReplaced
        )
        Assert.assertEquals(toBeReplaced, replacement.replacementFor)
        Assert.assertEquals(replacement, toBeReplaced.replacement)
        toBeReplaced.delete()
        Assert.assertNull(replacement.replacementFor)
    }
}
