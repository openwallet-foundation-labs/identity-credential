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
import com.ul.ims.gmdl.security.R
import com.ul.ims.gmdl.security.issuerdataauthentication.IACACertificate
import org.junit.Assert
import org.junit.Test
import java.time.Instant
import java.util.*

class IACACertificateTests {

    @Test
    fun testIACA_ULCertificate() {
        val appContext : Context = ApplicationProvider.getApplicationContext()
        val inputStream = appContext.resources.openRawResource(R.raw.iaca_ulims)
        val iaca = IACACertificate(inputStream)

        Assert.assertTrue(iaca.isValid(Date.from(Instant.now())))
    }

    @Test
    fun testIACA_FAST() {
        val appContext : Context = ApplicationProvider.getApplicationContext()
        val inputStream = appContext.resources.openRawResource(R.raw.fast)
        val iaca = IACACertificate(inputStream)

        Assert.assertTrue(iaca.isValid(Date.from(Instant.now())))
    }
//TODO: Nava check here.
//    @Test
////    fun testIACA_CRTFile() {
////        val appContext : Context = ApplicationProvider.getApplicationContext()
////        val inputStream = appContext.resources.openRawResource(R.raw.iaca_veridos)
////        val iaca = IACACertificate(inputStream)
////
////        Assert.assertTrue(iaca.isValid(Date.from(Instant.now())))
////    }

    @Test
    fun testIACA_thales() {
        val appContext : Context = ApplicationProvider.getApplicationContext()
        val inputStream = appContext.resources.openRawResource(R.raw.thales)
        val iaca = IACACertificate(inputStream)

        Assert.assertTrue(iaca.isValid(Date.from(Instant.now())))
    }

    @Test
    fun testIACA_bundesdruckerei() {
        val appContext : Context = ApplicationProvider.getApplicationContext()
        val inputStream = appContext.resources.openRawResource(R.raw.bundesdruckerei)
        val iaca = IACACertificate(inputStream)

        Assert.assertTrue(iaca.isValid(Date.from(Instant.now())))
    }
}