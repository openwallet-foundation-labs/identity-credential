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

import android.util.Log
import java.io.BufferedInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*

class IACACertificate(inputStream: InputStream): Certificate() {

    init {
        setCertificate(fetchFile(inputStream))
    }

    companion object {
        const val LOG_TAG = "IACACertificate"
    }

    private fun fetchFile(inputStream: InputStream): BufferedInputStream {
        try {
            return BufferedInputStream(inputStream)
        } catch (e: FileNotFoundException) {
            throw IssuerDataAuthenticationException("FileNotFound: " + e.message + "${e}")
        }
    }

    private fun setCertificate(bis : BufferedInputStream?) {
        val factory: CertificateFactory
        try {
            factory = CertificateFactory.getInstance("X509")
            this.certificate = factory.generateCertificate(bis) as X509Certificate
            val cert = certificate
            cert?.let {
                this.signerPublicKey = cert.publicKey
                Log.i(LOG_TAG,"IACA certificate and publicKey have been set.")
            }
        } catch (e: CertificateException) {
            throw IssuerDataAuthenticationException("Could not initialise IACA certificate: ${e}.")
        }
    }

    fun isValid(date: Date) :Boolean {
        if (!isNotExpired(date)) {
            throw IssuerDataAuthenticationException("IACA Certificate has expired on: ${this.certificate?.notAfter}")
        }
        if (!isSelfSigned()) {
            throw IssuerDataAuthenticationException("IACA Certificate is not self signed")
        }
        Log.i(LOG_TAG, "IACA certificate validated.")
        return true
    }
}
