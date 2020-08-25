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
import androidx.security.identity.ResultData
import com.ul.ims.gmdl.cbordata.cryptoUtils.CryptoUtils
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.sessionEncryption.SessionData
import com.ul.ims.gmdl.cbordata.security.sessionEncryption.SessionEstablishment
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import com.ul.ims.gmdl.issuerauthority.IIssuerAuthority
import java.security.interfaces.ECPublicKey

class HolderSessionManager private constructor(
    val context: Context,
    private val credentialName: String
) {

    companion object {
        @Volatile
        private var INSTANCE: HolderSessionManager? = null

        fun getInstance(
            context: Context,
            credentialName: String
        ): HolderSessionManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE
                    ?: HolderSessionManager(
                        context,
                        credentialName
                    ).also { INSTANCE = it }
            }
        const val LOG_TAG = "HolderSessionManager"
    }

    private var holderSession: HolderSession? = null

    private fun getHolderSession(): HolderSession {
        holderSession?.let { session ->
            return session
        }
        throw IdentityCredentialException("Holder session has not been initialised")
    }

    fun initializeHolderSession() {
        holderSession = HolderSession(context, credentialName)
    }

    suspend fun setAuthenticationData(issuerAuthority: IIssuerAuthority) {
        holderSession?.setAuthenticationData(issuerAuthority)
    }

    suspend fun checkDeviceKeysNeedingCertification(issuerAuthority: IIssuerAuthority) {
        holderSession?.checkDeviceKeysNeedingCertification(issuerAuthority)
    }

    fun generateHolderCoseKey() : CoseKey? {
        val holderPKey = getHolderSession().getEphemeralPublicKey() as? ECPublicKey

        var holderCoseKey : CoseKey? = null

        holderPKey?.let {
            val holderKeyBuilder = CoseKey.Builder()
            //TODO: Add support for other curves (CoseKey.P256.value.toInt()).
            val curveId = 1

            val xco = CryptoUtils.toByteArrayUnsigned(it.w.affineX)
            val yco = CryptoUtils.toByteArrayUnsigned(it.w.affineY)
            holderKeyBuilder.setKeyType(2)
            holderKeyBuilder.setCurve(curveId, xco, yco, null)
            holderCoseKey = holderKeyBuilder.build()
        }
        return holderCoseKey
    }

    fun getHolderPkHash() : ByteArray? {
        return generateHolderCoseKey()?.calculatePublickeyHash()
    }

    fun decryptSessionEstablishment(sessionEstablishment: SessionEstablishment) : ByteArray? {
        val readerKey = sessionEstablishment.readerKey
        readerKey?.let {
            setVerifierEphemeralPublicKey(it)

            val encryptedData = sessionEstablishment.encryptedData
            encryptedData?.let {
                return getHolderSession().decryptMessageFromReader(encryptedData)
            }
        }
        return null
    }

    fun setSessionTranscript(sessionTranscript: ByteArray) {
        holderSession?.setSessionTranscript(sessionTranscript)
    }

    fun getEntries(entriesToRequest: Map<String, Collection<String>>) :
            ResultData? {
        return try {
            holderSession?.getEntries(entriesToRequest)
        } catch (ex: NullPointerException) {
            null
        }
    }

    fun getIdentityCredential(): IdentityCredential? {
        return holderSession?.getIdentityCredential()
    }

    fun setVerifierEphemeralPublicKey(readerKey: CoseKey) {
        getHolderSession().setVerifierEphemeralPublicKey(readerKey)
    }

    private fun encryptData(byteArray: ByteArray): ByteArray? {
        return getHolderSession().encryptMessageToReader(byteArray)
    }

    fun decryptData(byteArray: ByteArray) : ByteArray? {
        return getHolderSession().decryptMessageFromReader(byteArray)
    }

    fun generateResponse(byteArray: ByteArray?) : ByteArray {
        val response: ByteArray
        val builder = SessionData.Builder()

        if (byteArray?.isNotEmpty() == true) {
            val encryptedResponse = encryptData(byteArray)
            encryptedResponse?.let { encryptedRes ->
                builder.setEncryptedData(encryptedRes)
            } ?: run {
                Log.d(LOG_TAG, "Unable to Encrypt the Response")
            }
        } else {
            // Generic error if we're not able to decrypt the request
            builder.setErrorCode(10)
        }

        val sessionData = builder.build()

        response = sessionData.encode()
        Log.d(LOG_TAG, "Session Data Obj")
        Log.d(LOG_TAG, CborUtils.cborPrettyPrint(response))
        return response
    }
}