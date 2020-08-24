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

package com.ul.ims.gmdl.security.issuerdataauthentication

import android.content.Context
import android.util.Log
import java.lang.reflect.Field
import java.security.PublicKey
import java.util.*

class RootCertificateInitialiser @Throws(IssuerDataAuthenticationException::class)
constructor(context: Context) {

    companion object {
        val rawFiles: Array<Field> = com.ul.ims.gmdl.security.R.raw::class.java.fields
        const val LOG_TAG = "RootCertificateInitialiser"
    }

    var rootCertificatesAndPublicKeys: IdentityHashMap<IACACertificate, PublicKey> = IdentityHashMap()

    init {
        try {
            this.rootCertificatesAndPublicKeys = getRootCertificates(context)
        } catch (ex: IssuerDataAuthenticationException) {
            val message = ex.message
            message?.let {
                throw IssuerDataAuthenticationException(message + "$ex")
            }
            throw IssuerDataAuthenticationException("Unable to initialise root certificates: $ex")
        }
    }

    @Throws(IssuerDataAuthenticationException::class)
    private fun getRootCertificates(context: Context): IdentityHashMap<IACACertificate, PublicKey> {
        val fileIds : MutableList<Int> = mutableListOf()
        for (count in rawFiles.indices) {
            fileIds.add(rawFiles[count].getInt(rawFiles[count]))
        }
        if (fileIds.size == 0) {
            throw IssuerDataAuthenticationException("No root certificates found")
        }

        for (i in fileIds) {
            val inputStream = context.resources.openRawResource(i)
            val filename = context.resources.getResourceName(i)
            try {
                val certificate = IACACertificate(inputStream)
                this.rootCertificatesAndPublicKeys[certificate] = certificate.getPublicKey()
            } catch (ex: IssuerDataAuthenticationException) {
                if (ex.message == "Could not initialise IACA certificate.") {
                    Log.e(LOG_TAG, "${ex.message} with res/raw filename: $filename")
                }
            }
        }
        return rootCertificatesAndPublicKeys
    }
}
