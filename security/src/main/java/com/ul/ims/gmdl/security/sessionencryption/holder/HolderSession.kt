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

package com.ul.ims.gmdl.security.sessionencryption.holder

import android.content.Context
import android.util.Log
import androidx.security.identity.IdentityCredential
import androidx.security.identity.IdentityCredentialException
import androidx.security.identity.IdentityCredentialStore
import androidx.security.identity.ResultData
import com.ul.ims.gmdl.cbordata.cryptoUtils.CryptoUtils
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.issuerauthority.IIssuerAuthority
import java.security.KeyPair
import java.security.PublicKey
import java.security.interfaces.ECPublicKey

class HolderSession
@Throws(IdentityCredentialException::class)
constructor(context : Context, credentialName : String) {

    private var credential : IdentityCredential? = null
    private var ephemeralKeyPair : KeyPair? = null

    companion object {
        const val LOG_TAG = "HolderSession"
        private const val PRIVATE_KEYS_COUNT = 1
        private const val MAX_USES_PER_KEY = 1
    }

    init {
        val store = IdentityCredentialStore.getInstance(context)
        credential = store.getCredentialByName(
            credentialName,
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
        )

        renewEphemeralKeys()
    }

    private fun renewEphemeralKeys() {
        ephemeralKeyPair = credential?.createEphemeralKeyPair()
    }

    /**
     * This routine will generate and sign 20 MSO Objects that can be used 5 times each.
     * It should be executed from time to time, in order to provision new keys and generate new MSOs
     * **/
    suspend fun setAuthenticationData(issuerAuthority: IIssuerAuthority) {
        credential?.setAvailableAuthenticationKeys(PRIVATE_KEYS_COUNT, MAX_USES_PER_KEY)

        checkDeviceKeysNeedingCertification(issuerAuthority)
    }

    suspend fun checkDeviceKeysNeedingCertification(issuerAuthority: IIssuerAuthority) {
        val dynAuthKeyCerts = credential?.
            authKeysNeedingCertification

        if (dynAuthKeyCerts?.isNotEmpty() == true) {
            Log.d(LOG_TAG, "Device Keys needing certification ${dynAuthKeyCerts.size}")
            dynAuthKeyCerts.forEach { cert ->
                // returns the Cose_Sign1 Obj with the MSO in the payload
                val issuerAuth = issuerAuthority.getIssuerSignedData(cert.publicKey)
                issuerAuth?.let {
                    credential?.storeStaticAuthenticationData(
                        cert, it)

                    Log.d(LOG_TAG, "Provisioned Isser Auth ${encodeToString(issuerAuth)} " +
                                "for Device Key ${encodeToString(cert.publicKey.encoded)}")
                }
            }
        } else {
            Log.d(LOG_TAG, "No Device Keys Needing Certification for now")
        }
    }

    // Helper function to display a cbor structure in HEX
    private fun encodeToString(encoded: ByteArray): String {

        val sb = StringBuilder(encoded.size * 2)

        for (b in encoded) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    fun getEphemeralPublicKey() : PublicKey? {
        return ephemeralKeyPair?.public
    }

    fun setVerifierEphemeralPublicKey(readerKey: CoseKey) {
        setVerifierEphemeralPublicKey(extractPublicKey(readerKey))
    }

    fun setVerifierEphemeralPublicKey(pk: ECPublicKey?) {
        try {
            pk?.let {
                credential?.setReaderEphemeralPublicKey(it)
            }
        } catch (ex: IdentityCredentialException) {
            Log.e(LOG_TAG, ex.message, ex.cause)
        }
    }

    fun setSessionTranscript(sessionTranscript: ByteArray) {
        credential?.setSessionTranscript(sessionTranscript)
    }

    fun getEntries(entriesToRequest: Map<String, Collection<String>>):
            ResultData? {
        Log.d(LOG_TAG, "calling getEntries() with " + entriesToRequest.size)
        return credential?.getEntries(
            null,  // TODO: need to set requestMessage
            entriesToRequest,
            null
        )
    }

    fun getIdentityCredential(): IdentityCredential? {
        return credential
    }

    private fun extractPublicKey(readerKey: CoseKey): ECPublicKey? {
        val xco = readerKey.curve?.xCoordinate
        xco?.let {
            val yco = readerKey.curve?.yCoordinate
            yco?.let {
                val pk = CryptoUtils.toUncompressedPoint(xco, yco)
                return CryptoUtils.decodeUncompressedPoint(pk)
            }
        }
        return null
    }

    fun encryptMessageToReader(message : ByteArray) : ByteArray? {
        var encryptedMessage : ByteArray? = null

        try {
            encryptedMessage = credential?.encryptMessageToReader(message)
        } catch (ex : IdentityCredentialException) {
            Log.e(LOG_TAG, ex.message, ex)
        }

        return encryptedMessage
    }

    fun decryptMessageFromReader(message: ByteArray) : ByteArray? {
        var  decryptedMessage : ByteArray? = null

        try {
            decryptedMessage = credential?.decryptMessageFromReader(message)
        } catch (ex : IdentityCredentialException) {
            Log.e(LOG_TAG, ex.message, ex)
        }

        return decryptedMessage
    }
}