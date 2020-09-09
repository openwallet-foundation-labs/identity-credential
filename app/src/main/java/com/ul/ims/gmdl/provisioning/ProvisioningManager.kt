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

package com.ul.ims.gmdl.provisioning

import android.content.Context
import android.util.Log
import androidx.security.identity.*
import androidx.security.identity.IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import com.ul.ims.gmdl.issuerauthority.IIssuerAuthority
import java.security.cert.X509Certificate
import java.util.*

/**
 * Class Responsible to Provision Credential data using Identity Credential Framework
 *
 * **/
object ProvisioningManager {

    private const val TAG = "ProvisioningManager"


    fun getIdentityCredential(context: Context, credentialName : String) : IdentityCredential? {
        val store = IdentityCredentialStore.getInstance(context)

        return store.getCredentialByName(
            credentialName,
            CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
        )
    }

    @Throws (IdentityCredentialException::class)
    fun createCredential(context: Context, credentialName : String,
                         issuerAuthority: IIssuerAuthority, authRequired : Boolean) :
            Collection<X509Certificate>? {
        val userCredential = issuerAuthority.getCredentials()
        val store = IdentityCredentialStore.getInstance(context)
        store.deleteCredentialByName(credentialName)

        val wc: WritableIdentityCredential?
        var certificateChain: Collection<X509Certificate>? = null

        try {
            wc = store.createCredential(
                credentialName,
                MdlDoctype.docType
            )
        } catch (ex: CipherSuiteNotSupportedException) {
            Log.e(TAG, ex.message, ex)
            throw IdentityCredentialException("CipherSuite Not Supported", ex)
        }

        wc.let {
            try {
                certificateChain =
                    wc.getCredentialKeyCertificateChain(issuerAuthority.getProvisionChallenge())

                val personalizationBuilder = PersonalizationData.Builder()

                if (authRequired) {
                    // Profile 1 (user auth on every reader session)
                    // Connected with getCryptoObject() call
                    personalizationBuilder.addAccessControlProfile(
                        AccessControlProfile.Builder(AccessControlProfileId(1))
                            .setUserAuthenticationRequired(true)
                            .build()
                    )
                } else {
                    // Profile 1 no auth.
                    personalizationBuilder.addAccessControlProfile(
                        AccessControlProfile.Builder(AccessControlProfileId(1))
                            .setUserAuthenticationRequired(false)
                            .build()
                    )
                }

                val idsNoAuth = ArrayList<AccessControlProfileId>()
                idsNoAuth.add(AccessControlProfileId(1))

                userCredential?.let {
                    userCredential.getCredentialsForProvisioning(
                        idsNoAuth,
                        personalizationBuilder
                    )
                }

                val proofOfProvisioningCbor = wc.personalize(personalizationBuilder.build())
                Log.i(TAG, "Provisioned Credential CBOR ")
                com.ul.ims.gmdl.offlinetransfer.utils.Log.d(
                    TAG,
                    CborUtils.cborPrettyPrint(proofOfProvisioningCbor)
                )

            } catch (ex: Exception) {
                Log.e(TAG, ex.message, ex)
                throw IdentityCredentialException(ex.message ?: ex.javaClass.simpleName, ex)
            }
        }

            return certificateChain
    }
}