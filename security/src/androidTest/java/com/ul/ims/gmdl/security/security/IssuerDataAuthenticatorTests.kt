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

package com.ul.ims.gmdl.security.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ul.ims.gmdl.security.issuerdataauthentication.IACACertificate
import com.ul.ims.gmdl.security.issuerdataauthentication.RootCertificateInitialiser
import org.junit.Before
import java.security.PublicKey

class IssuerDataAuthenticatorTests {
    private lateinit var rootCertificatesAndPublicKeys: Map<IACACertificate, PublicKey>

    @Before
    fun setUp() {
        val context : Context = ApplicationProvider.getApplicationContext()

        val rootCertificateInitialiser = RootCertificateInitialiser(context)
        rootCertificatesAndPublicKeys = rootCertificateInitialiser.rootCertificatesAndPublicKeys
    }

// TODO: Tests need to be fixed, test data is not valid after structure changes

//    @Test
//    fun testInvalidX509chainIdentifier_ResultsInNullDsCertificate() {
//        val coseSign1 = CoseSign1.Builder().decode(TestData.coseSign1ForInvalidX509Id).build()
//        val issuerNameSpaces = IssuerNameSpaces.Builder().decode(TestData.issuerNameSpacesWithInvalidX509Id).build()
//        val issuerDataAuthenticator = IssuerDataAuthenticator(
//            rootCertificatesAndPublicKeys,
//            coseSign1,
//            issuerNameSpaces,
//            MdlNamespace.namespace,
//            null)
//        val exception = assertFailsWith<IssuerDataAuthenticationException> {
//            issuerDataAuthenticator.isDataAuthentic(Date.from(Instant.now()))
//        }
//
//        Assert.assertTrue(exception.message?.contains("DSCertificate is null") ?: false)
//    }

//    @Test
//    fun testInvalidX509chain() {
//        val coseSign1 = CoseSign1.Builder().decode(TestData.cS1ForInvalidX509Chain).build()
//        val issuerNameSpaces = IssuerNameSpaces.Builder().decode(TestData.insForInvalidX509Chain).build()
//        val issuerDataAuthenticator = IssuerDataAuthenticator(
//            rootCertificatesAndPublicKeys,
//            coseSign1,
//            issuerNameSpaces,
//            MdlDoctype.docType,
//            null)
//        val exception = assertFailsWith<IssuerDataAuthenticationException> {
//            issuerDataAuthenticator.isDataAuthentic(Date.from(Instant.now()))
//        }
//
//        Assert.assertTrue(exception.message?.contains("Invalid certificate chain.") ?: false)
//    }

//    @Test
//    fun testSignature() {
//        val coseSign1 = CoseSign1.Builder()
//            .decode(TestData.coseSign1ValidData)
//            .build()
//
//        val issuerNameSpaces = IssuerNameSpaces.Builder()
//            .decode(TestData.issuerNameSpacesValidData).build()
//
//        val iDA = IssuerDataAuthenticator(
//            rootCertificatesAndPublicKeys,
//            coseSign1,
//            issuerNameSpaces,
//            MdlNamespace.namespace,
//            null
//        )
//
//        Assert.assertTrue(iDA.isDataAuthentic(Date.from(Instant.now())))
//    }
}

