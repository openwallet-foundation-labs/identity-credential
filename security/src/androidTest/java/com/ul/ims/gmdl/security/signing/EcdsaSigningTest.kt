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

package com.ul.ims.gmdl.security.signing

import android.content.Context
import android.util.Log
import androidx.security.identity.*
import androidx.security.identity.IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
import androidx.test.core.app.ApplicationProvider
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.deviceEngagement.security.Security
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype.docType
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.cbordata.response.DeviceAuth
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.DeviceNameSpaces
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.SessionTranscript
import com.ul.ims.gmdl.security.TestUtils
import com.ul.ims.gmdl.security.sessionencryption.holder.HolderSessionManager
import com.ul.ims.gmdl.security.sessionencryption.verifier.VerifierSessionManager
import org.junit.Assert
import org.junit.Before
import java.security.cert.X509Certificate
import java.util.*
import kotlin.test.assertNotNull

class EcdsaSigningTest {

    companion object {
        const val CREDENTIAL_NAME = "EcdsaSigningTest"
    }

    private lateinit var context: Context
    private var icStore: IdentityCredentialStore? = null
    val sessionTranscript = byteArrayOf(
        0x82.toByte(), 0x58.toByte(), 0x59.toByte(), 0x84.toByte(), 0x01.toByte(),
        0x82.toByte(), 0xa4.toByte(), 0x01.toByte(), 0x02.toByte(), 0x20.toByte(),
        0x01.toByte(), 0x21.toByte(), 0x58.toByte(), 0x20.toByte(), 0x6c.toByte(),
        0xfe.toByte(), 0x09.toByte(), 0x49.toByte(), 0x8a.toByte(), 0xb4.toByte(),
        0xc3.toByte(), 0xb7.toByte(), 0x4d.toByte(), 0x4c.toByte(), 0x05.toByte(),
        0xd4.toByte(), 0x92.toByte(), 0x90.toByte(), 0xbe.toByte(), 0xb8.toByte(),
        0x21.toByte(), 0x9e.toByte(), 0x54.toByte(), 0xbe.toByte(), 0x01.toByte(),
        0xc0.toByte(), 0x17.toByte(), 0x34.toByte(), 0x85.toByte(), 0xc3.toByte(),
        0x89.toByte(), 0x7c.toByte(), 0x25.toByte(), 0x03.toByte(), 0x03.toByte(),
        0xe7.toByte(), 0x22.toByte(), 0x58.toByte(), 0x20.toByte(), 0x50.toByte(),
        0xad.toByte(), 0x23.toByte(), 0xcc.toByte(), 0x34.toByte(), 0x85.toByte(),
        0xaf.toByte(), 0xc3.toByte(), 0x93.toByte(), 0x06.toByte(), 0xd9.toByte(),
        0x92.toByte(), 0x8d.toByte(), 0xd9.toByte(), 0x6f.toByte(), 0x6b.toByte(),
        0x6b.toByte(), 0x56.toByte(), 0x29.toByte(), 0xa1.toByte(), 0x00.toByte(),
        0x9b.toByte(), 0x1c.toByte(), 0x0b.toByte(), 0x58.toByte(), 0x7e.toByte(),
        0x1a.toByte(), 0x2f.toByte(), 0x47.toByte(), 0xec.toByte(), 0x2d.toByte(),
        0x9d.toByte(), 0x01.toByte(), 0x81.toByte(), 0x83.toByte(), 0x02.toByte(),
        0x01.toByte(), 0xa2.toByte(), 0x00.toByte(), 0xf5.toByte(), 0x01.toByte(),
        0xf5.toByte(), 0xa0.toByte(), 0x58.toByte(), 0x4b.toByte(), 0xa4.toByte(),
        0x01.toByte(), 0x02.toByte(), 0x20.toByte(), 0x01.toByte(), 0x21.toByte(),
        0x58.toByte(), 0x20.toByte(), 0x08.toByte(), 0x4c.toByte(), 0x8f.toByte(),
        0x86.toByte(), 0x4e.toByte(), 0xa0.toByte(), 0x98.toByte(), 0x1b.toByte(),
        0x4c.toByte(), 0xaa.toByte(), 0xd3.toByte(), 0xa8.toByte(), 0xab.toByte(),
        0xe6.toByte(), 0xfe.toByte(), 0x0b.toByte(), 0x18.toByte(), 0x31.toByte(),
        0x9c.toByte(), 0xee.toByte(), 0xf9.toByte(), 0x6a.toByte(), 0xdf.toByte(),
        0x84.toByte(), 0x91.toByte(), 0x0c.toByte(), 0xe4.toByte(), 0xe5.toByte(),
        0x2e.toByte(), 0x15.toByte(), 0x66.toByte(), 0x76.toByte(), 0x22.toByte(),
        0x58.toByte(), 0x20.toByte(), 0xc6.toByte(), 0xe4.toByte(), 0xed.toByte(),
        0xea.toByte(), 0x03.toByte(), 0x37.toByte(), 0x7a.toByte(), 0x6a.toByte(),
        0xad.toByte(), 0xc8.toByte(), 0x76.toByte(), 0xa5.toByte(), 0x7e.toByte(),
        0xd9.toByte(), 0xfb.toByte(), 0x6c.toByte(), 0x4d.toByte(), 0x53.toByte(),
        0xe2.toByte(), 0xcb.toByte(), 0x13.toByte(), 0x02.toByte(), 0xc0.toByte(),
        0xbc.toByte(), 0x26.toByte(), 0x7c.toByte(), 0xc7.toByte(), 0x60.toByte(),
        0x0b.toByte(), 0x1a.toByte(), 0x97.toByte(), 0x83.toByte()
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        icStore = IdentityCredentialStore.getInstance(context)
    }

    fun signTest() {
        assertNotNull(icStore)
        icStore?.let {
            it.deleteCredentialByName(CREDENTIAL_NAME)
            createCredential(it)
        }

        val holder = HolderSessionManager.getInstance(context, CREDENTIAL_NAME)

        val coseKey = holder.generateHolderCoseKey()
        Assert.assertNotNull(coseKey)

        coseKey?.let { cKey ->
            val security = Security.Builder()
                .setCoseKey(cKey)
                .setCipherSuiteIdent(TestUtils.CHIPER_SUITE_IDENT)
                .build()

            // Device engagement for QR Code
            val deBuilder = DeviceEngagement.Builder()

            deBuilder.version(TestUtils.DE_VERSION)
            deBuilder.security(security)

            val deviceEngagement = deBuilder.build()
            val verifier = VerifierSessionManager(cKey, deviceEngagement)
            val vCoseKey = verifier.getReaderCoseKey()
            Assert.assertNotNull(vCoseKey)

            vCoseKey?.let { vck ->
                holder.setVerifierEphemeralPublicKey(vck)

                val sessionTranscript = SessionTranscript
                    .Builder()
                    .setReaderKey(vck.encode())
                    .build()

                // Empty Cbor Map
                val devNs = DeviceNameSpaces.Builder()
                    .build()

//                // Create DeviceAuthentication obj
//                val deviceAuthentication = DeviceAuthentication.Builder()
//                    .setDeviceNameSpaces(devNs)
//                    .setDocType(MdlNamespace.namespace)
//                    .setSessionTranscript(sessionTranscript)
//                    .build()

                val credential = icStore?.getCredentialByName(CREDENTIAL_NAME,
                    CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256)

                credential?.let { cred ->
                    cred.createEphemeralKeyPair()

                    cred.setAvailableAuthenticationKeys(1, 10)
                    val dynAuthKeyCerts = cred.authKeysNeedingCertification

                    dynAuthKeyCerts.forEach {
                        println("mDL Public Key")
                        println(encodeToString(it.publicKey.encoded))
                    }
                    cred.storeStaticAuthenticationData(
                        dynAuthKeyCerts.iterator().next(),
                        ByteArray(0)
                    )

                    val requestEntryNamespaces = getIdentityCredentialRequest()

                    val entryResult = credential.getEntries(
                        null,
                        requestEntryNamespaces,
                        sessionTranscript.encode()
                    )

                    println("entryResult.authenticatedData")
                    println(encodeToString(entryResult.authenticatedData))

                    println("entryResult.staticAuthenticationData")
                    println(encodeToString(entryResult.staticAuthenticationData))

                    entryResult.ecdsaSignature?.let {
                        Log.d("entryResult.ecdsaSignature",
                            encodeToString(it))

                        val coseSign1 = CoseSign1.Builder()
                            .setSignature(it)
                            .build()
                        println("entryResult.cosesign1")
                        println(coseSign1.encodeToString())

                        val deviceAuth = DeviceAuth.Builder()
                            .setCoseSign1(coseSign1)
                            .build()
                    }

                    entryResult.messageAuthenticationCode?.let {
                        println("entryResult.messageAuthenticationCode")
                        println(encodeToString(it))
                    }
                }
            }
        }
    }

    private fun createCredential(store: IdentityCredentialStore):
            Collection<X509Certificate>? {

        store.deleteCredentialByName(CREDENTIAL_NAME)

        val wc: WritableIdentityCredential?
        val certificateChain: Collection<X509Certificate>?

        try {
            wc = store.createCredential(CREDENTIAL_NAME, docType)
        } catch (ex: CipherSuiteNotSupportedException) {
            Log.e(CREDENTIAL_NAME, ex.message, ex)
            throw IdentityCredentialException("CipherSuite Not Supported", ex)
        }

        try {
            certificateChain = wc.getCredentialKeyCertificateChain("challenge".toByteArray())

            val personalizationBuilder = PersonalizationData.Builder()

            // Profile 1 no auth.
            personalizationBuilder.addAccessControlProfile(
                AccessControlProfile.Builder(AccessControlProfileId(1))
                    .setUserAuthenticationRequired(false)
                    .build()
            )

            val idsNoAuth = ArrayList<AccessControlProfileId>()
            idsNoAuth.add(AccessControlProfileId(1))
            getCredentialsForProvisioning(idsNoAuth, personalizationBuilder)

            wc.personalize(personalizationBuilder.build())

        } catch (ex: IdentityCredentialException) {
            Log.e(CREDENTIAL_NAME, ex.message, ex)
            throw IdentityCredentialException(ex.message ?: ex.javaClass.simpleName, ex)
        }

        return certificateChain
    }

    private fun getCredentialsForProvisioning(
        accessControlProfileIds: ArrayList<AccessControlProfileId>,
        personalizationBuilder: PersonalizationData.Builder
    ) {
        personalizationBuilder.let {
            it.putEntryString(docType, "family_name", accessControlProfileIds, "Do Nascimento")
            it.putEntryString(docType, "given_name", accessControlProfileIds, "Edson Arantes")
            it.putEntryString(docType, "issuing_country", accessControlProfileIds, "US")
            it.putEntryString(docType, "issuing_authority", accessControlProfileIds, "Google")
            it.putEntryString(docType, "license_number", accessControlProfileIds, "NDL12345JKL")
        }
    }

    private fun encodeToString(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }

        return sb.toString()
    }

    private fun getIdentityCredentialRequest(): HashMap<String, Collection<String>> {
        val entriesToRequestIssuerSigned = HashMap<String, Collection<String>>()
        entriesToRequestIssuerSigned[MdlNamespace.namespace] = MdlNamespace.items.keys

        return entriesToRequestIssuerSigned
    }
}