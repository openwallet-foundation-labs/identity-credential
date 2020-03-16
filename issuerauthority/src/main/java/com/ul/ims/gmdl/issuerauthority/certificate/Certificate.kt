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

package com.ul.ims.gmdl.issuerauthority.certificate

import java.security.*
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*

abstract class Certificate {
    var signerPublicKey: PublicKey? = null
    var certificate: X509Certificate? = null

    fun getPublicKey() : PublicKey? {
        return this.certificate?.publicKey
    }

    fun version() : Int {
        return certificate?.version ?: throw IssuerDataAuthenticationException("Invalid version")
    }

    fun isNotExpired(date: Date): Boolean {
        return date.after(this.certificate?.notBefore) && date.before(this.certificate?.notAfter)
    }
    fun isSelfSigned() : Boolean {
        return try {
            this.certificate?.verify(getPublicKey())
            true
        } catch (e: CertificateException) {
            false
        } catch (e: NoSuchAlgorithmException) {
            false
        } catch (e: InvalidKeyException) {
            false
        } catch (e: NoSuchProviderException) {
            false
        } catch (e: SignatureException) {
            false
        }
    }

}