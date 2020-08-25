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

package com.ul.ims.gmdl.security.sessionencryption.verifier

import android.util.Log
import androidx.security.identity.IdentityCredentialException
import com.ul.ims.gmdl.cbordata.cryptoUtils.CryptoUtils
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.sessionEncryption.SessionEstablishment
import java.security.PrivateKey
import java.security.interfaces.ECPublicKey

class VerifierSessionManager constructor(private val holderCoseKey: CoseKey,
                                         val deviceEngagement: DeviceEngagement) {

    companion object {
        const val LOG_TAG = "VerifierSessionManager"
    }

    //TODO: Add support for potentially new CipherSuites
    private var readerSession = VerifierSession(
        holderCoseKey.getPublicKey(),
        deviceEngagement
    )

    fun createSessionEstablishment(bytes : ByteArray) : SessionEstablishment {
        return SessionEstablishment.Builder()
            .setEncryptedData(bytes)
            .setReaderKey(getReaderCoseKey())
            .build()
    }

    fun getHolderPkHash() : ByteArray? {
        return holderCoseKey.calculatePublickeyHash()
    }

    fun getVerifierPrivateKey() : PrivateKey {
        val pk = readerSession.getPrivateKey()
            if (pk != null) {
                return pk
            } else {
             throw RuntimeException("Private Key Not Found")
            }
    }

    fun getVerifierPublicKey() : ECPublicKey? {
        return readerSession.getReaderPublicKey()
    }

    fun getReaderCoseKey() : CoseKey? {
        val pk = readerSession.getReaderPublicKey()

        var readerCoseKey : CoseKey? = null

        pk?.let {
            val holderKeyBuilder = CoseKey.Builder()
            // TODO: Add support for other curves (CoseKey.P256.value.toInt()).
            val curveId = 1

            val xco = CryptoUtils.toByteArrayUnsigned(it.w.affineX)
            val yco = CryptoUtils.toByteArrayUnsigned(it.w.affineY)
            holderKeyBuilder.setKeyType(2)
            holderKeyBuilder.setCurve(curveId, xco, yco, null)
            readerCoseKey = holderKeyBuilder.build()
        }
        return readerCoseKey
    }

    fun encryptData(data : ByteArray) : ByteArray? {
        return try {
            readerSession.encryptMessageToHolder(data)
        } catch (ex : IdentityCredentialException) {
            Log.e(LOG_TAG, ex.message, ex)
            null
        }
    }

    fun decryptData(data: ByteArray) : ByteArray? {
        return try {
            readerSession.decryptMessageFromHolder(data)
        } catch (ex : IdentityCredentialException) {
            Log.e(LOG_TAG, ex.message, ex)
            null
        }
    }
}