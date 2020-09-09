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

package com.ul.ims.gmdl.model

import android.content.Context
import androidx.security.identity.AccessControlProfile
import androidx.security.identity.AccessControlProfileId
import androidx.security.identity.PersonalizationData
import androidx.test.core.app.ApplicationProvider
import com.ul.ims.gmdl.cbordata.model.UserCredential
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class UserCredentialTest {
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun useStaticDataTest() {
        val builder = UserCredential.Builder()

        Assert.assertNotNull(builder)

        val credential = UserCredential.Builder()
            .useStaticData(appContext.resources)
            .build()

        Assert.assertNotNull(credential)
    }

    @Test
    fun getCredentialsForProvisioningTest() {
        val credential = UserCredential.Builder()
            .useStaticData(appContext.resources)
            .build()

        Assert.assertNotNull(credential)
        val personalizationBuilder = PersonalizationData.Builder()

        // Profile 1 no auth.
        personalizationBuilder.addAccessControlProfile(
            AccessControlProfile.Builder(AccessControlProfileId(1))
                .setUserAuthenticationRequired(false)
                .build()
        )

        // No Auth Required
        val idsNoAuth = ArrayList<AccessControlProfileId>()
        idsNoAuth.add(AccessControlProfileId(1))

        val entryNamespace =
            credential.getCredentialsForProvisioning(idsNoAuth, personalizationBuilder)

        Assert.assertNotNull(entryNamespace)
    }
}