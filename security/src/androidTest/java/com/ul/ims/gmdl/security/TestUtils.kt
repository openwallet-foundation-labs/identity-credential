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

package com.ul.ims.gmdl.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.identity.*
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype.docType
import com.ul.ims.gmdl.cbordata.security.CoseKey
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.*

object TestUtils {
    const val DE_VERSION = "1.0"
    const val CHIPER_SUITE_IDENT = 1

    // Generate an Ephemeral KeyPair
    fun genEphemeralKeyPair(): KeyPair? {
        var mEphemeralKeyPair: KeyPair? = null

        //Create a Public Key
        try {
            val kpg: KeyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )
            val parameterSpec: KeyGenParameterSpec = KeyGenParameterSpec.Builder(
                "key1",
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).run {
                setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                build()
            }

            kpg.initialize(parameterSpec)

            mEphemeralKeyPair = kpg.generateKeyPair()

        } catch (e: NoSuchAlgorithmException) {
            throw IdentityCredentialException("Error generating ephemeral key", e)
        } catch (e: InvalidAlgorithmParameterException) {
            throw IdentityCredentialException("Error generating ephemeral key", e)
        } finally {
            return mEphemeralKeyPair
        }
    }

    // Create a CoseKey Obj
    fun genCoseKey(): CoseKey {
        val mEphemeralKeyPair = genEphemeralKeyPair()

        val holderPKey = mEphemeralKeyPair?.public as? ECPublicKey

        var holderCoseKey: CoseKey? = null

        holderPKey?.let {
            val holderKeyBuilder = CoseKey.Builder()
            val curveId = 1

            val xco = it.w.affineX.toByteArray()
            val yco = it.w.affineY.toByteArray()
            holderKeyBuilder.setKeyType(2)
            holderKeyBuilder.setCurve(curveId, xco, yco, null)
            holderCoseKey = holderKeyBuilder.build()
        }
        return holderCoseKey
            ?: throw IdentityCredentialException("Error generating Holder CoseKey")
    }

    // Create a Credential
    @Throws(IdentityCredentialException::class)
    fun createCredential(
        store: IdentityCredentialStore,
        credentialName: String
    ): Collection<X509Certificate>? {

        store.deleteCredentialByName(credentialName)

        var wc: WritableIdentityCredential? = null
        try {
            wc = store.createCredential(
                credentialName,
                "org.iso.18013-5.2019.mdl"
            )
        } catch (e: CipherSuiteNotSupportedException) {
            e.printStackTrace()
        }

        var certificateChain: Collection<X509Certificate>? = null
        try {
            certificateChain = wc?.getCredentialKeyCertificateChain("SomeChallenge".toByteArray())
        } catch (e: IdentityCredentialException) {
            e.printStackTrace()
        }

        val personalizationBuilder = PersonalizationData.Builder()

        // Profile 1 no auth.
        personalizationBuilder.addAccessControlProfile(
            AccessControlProfile.Builder(AccessControlProfileId(1))
                .setUserAuthenticationRequired(false)
                .build()
        )

        val idsNoAuth = ArrayList<AccessControlProfileId>()
        idsNoAuth.add(AccessControlProfileId(1))

        personalizationBuilder.let {
            it.putEntryString(docType, "First name", idsNoAuth, "Alan")
            it.putEntryString(docType, "Last name", idsNoAuth, "Turing")
            it.putEntryString(docType, "Home address", idsNoAuth, "Maida Vale, London, England")
            it.putEntryString(docType, "Birth date", idsNoAuth, "19120623")
            it.putEntryBoolean(docType, "Cryptanalyst", idsNoAuth, true)
            it.putEntryBytestring(docType, "Portrait image", idsNoAuth, byteArrayOf(0x01, 0x02))
        }

        wc?.personalize(personalizationBuilder.build())

        return certificateChain
    }
}