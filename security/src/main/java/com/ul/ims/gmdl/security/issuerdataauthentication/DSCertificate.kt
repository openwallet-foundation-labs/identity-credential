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
import java.io.ByteArrayInputStream
import java.security.*
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*

class DSCertificate(bytes: ByteArray,
                    private val rootCertificatesAndPublicKeys: Map<IACACertificate, PublicKey>,
                    private val issuingCountry : String?) : Certificate() {

    var rootCertificate: IACACertificate? = null
    companion object {
        const val LOG_TAG = "DSCertificate"
        private val KEY_USAGE = booleanArrayOf(
            true, false, false, false, false, false, false, false, false
        )
        private const val EXTENDED_KEY_USAGE = "1.0.18013.5.1.2"
        private const val ISSUING_COUNTRY_IDENTIFIER = "C="
    }

    init {
        validateCertificate(bytes)
        val certificateFactory = CertificateFactory.getInstance("X509")
        this.certificate = certificateFactory
            .generateCertificate(ByteArrayInputStream(bytes)) as? X509Certificate
        val issuerDN = certificate?.issuerDN
        setSignerAndPublicKey(issuerDN)
    }

    private fun validateCertificate(certificateBytes: ByteArray?) {
        if (certificateBytes == null) {
            throw IssuerDataAuthenticationException("Invalid Certificate")
        }
    }

    fun isValid(date: Date): Boolean {
        val cert = this.certificate ?: throw IssuerDataAuthenticationException("X509 DS certificate is null")
        if (cert.version != 3) {
            throw IssuerDataAuthenticationException("Invalid certificate version")
        }
        if (isSelfSigned()) {
            throw IssuerDataAuthenticationException("DS Certificate is self signed")
        }
        if (!isNotExpired(date)) {
            throw IssuerDataAuthenticationException("DS Certificate has expired")
        }
        if (!keyUsageIsValid()) {
            throw IssuerDataAuthenticationException("Invalid keyUsage")
        }
        if (!extendedKeyUsageIsValid()) {
            throw IssuerDataAuthenticationException("Invalid extendedKeyUsage")
        }
        if (!issuingCountryIsValid()) {
            throw IssuerDataAuthenticationException("issuing_country is wrong")
        }
        try {
            isSigned()
        } catch (ex: IssuerDataAuthenticationException) {
            throw IssuerDataAuthenticationException("Invalid certificate chain.")
        }
        Log.i(LOG_TAG, "DS certificate validated.")
        return true
    }

    private fun issuingCountryIsValid(): Boolean {
        val iCountry = decodeIssuingCountry(this.certificate?.subjectDN?.name)
        return if (issuingCountry != null) {
            iCountry == issuingCountry
        } else
            true
    }

    private fun decodeIssuingCountry(subjectDnName: String?): String? {
        subjectDnName?.let {
            if (ISSUING_COUNTRY_IDENTIFIER in subjectDnName) {
                val index = subjectDnName.indexOf(ISSUING_COUNTRY_IDENTIFIER)
                return subjectDnName.substring(index+2, index+4)
            }
        }
        return null
    }

    private fun keyUsageIsValid(): Boolean {
        val keyUsage = this.certificate?.keyUsage ?: return false
        if (!keyUsage.contentEquals(KEY_USAGE)) {
            return false
        }
        Log.i(LOG_TAG, "keyUsage validated.")
        return true
    }

    private fun extendedKeyUsageIsValid(): Boolean {
        val extendedKeyUsage = this.certificate?.extendedKeyUsage ?: return false
        if (!extendedKeyUsage.contains(EXTENDED_KEY_USAGE)) {
            return false
        }
        Log.i(LOG_TAG, "extendedKeyUsage validated.")
        return true
    }

    private fun setSignerAndPublicKey(issuerDN: Principal?) {
        if (issuerDN == null) {
            throw IssuerDataAuthenticationException("issuerDN is null")
        }
        val rootCertificates = rootCertificatesAndPublicKeys.keys
        for (rootCert in rootCertificates) {
            val c = rootCert.certificate
            c?.let {
                if (c.subjectDN == issuerDN) {
                    this.rootCertificate = rootCert
                    this.signerPublicKey = rootCert.getPublicKey()
                    return
                }
            }
        }
        if (this.signerPublicKey == null) {
            throw IssuerDataAuthenticationException("IACA PublicKey not found.")
        }
    }

    private fun isSigned(): Boolean {
        try {
            this.certificate?.verify(this.signerPublicKey)
            return true
        } catch (ex: CertificateException) {
            val message = ex.message
            message?.let {
                throw IssuerDataAuthenticationException(message + "${ex}")
            }
            throw IssuerDataAuthenticationException("Invalid certificate chain: ${ex}")
        } catch (ex: NoSuchAlgorithmException) {
            val message = ex.message
            message?.let {
                throw IssuerDataAuthenticationException(message + "${ex}")
            }
            throw IssuerDataAuthenticationException("Invalid certificate chain: ${ex}")
        } catch (ex: InvalidKeyException) {
            val message = ex.message
            message?.let {
                throw IssuerDataAuthenticationException(message + "${ex}")
            }
            throw IssuerDataAuthenticationException("Invalid certificate chain: ${ex}")
        } catch (ex: NoSuchProviderException) {
            val message = ex.message
            message?.let {
                throw IssuerDataAuthenticationException(message + "${ex}")
            }
            throw IssuerDataAuthenticationException("Invalid certificate chain: ${ex}")
        } catch (ex: SignatureException) {
            val message = ex.message
            message?.let {
                throw IssuerDataAuthenticationException(message + "${ex}")
            }
            throw IssuerDataAuthenticationException("Invalid certificate chain: ${ex}")
        }
    }
}