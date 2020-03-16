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

package com.ul.ims.gmdl.issuerauthority.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ul.ims.gmdl.issuerauthority.IIssuerAuthority
import com.ul.ims.gmdl.issuerauthority.MockIssuerAuthority
import com.ul.ims.gmdl.issuerauthority.util.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class IssuerAuthorityDatabaseTest {

    lateinit var issuerAuthority: IIssuerAuthority
    lateinit var appContext: Context
    lateinit var db: IssuerAuthorityDatabase
    private lateinit var database: IssuerAuthorityDatabase
    private lateinit var credentialDao: CredentialDao

    @Before
    fun createDb() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database =
            Room.inMemoryDatabaseBuilder(context, IssuerAuthorityDatabase::class.java).build()
        credentialDao = database.credentialDao()

        database.credentialDao().insert(testCredential)
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext<Context>()
        issuerAuthority = MockIssuerAuthority.getInstance(appContext)
        db = Room.databaseBuilder(
            appContext,
            IssuerAuthorityDatabase::class.java, "issuer-authority-database-test"
        ).build()
    }

    @Test
    fun getCredentialsTest() = runBlocking {
        val credentialRetrieved = credentialDao.loadById(credentialId)
        Log.d("IssuerAuthorityDatabaseTest", "Byte Array length: ${credentialRetrieved.size}")
        debugPrint("IssuerAuthorityDatabaseTest", "encodeToHex: ${hexEncode(credentialRetrieved)}")
        assertTrue(credentialRetrieved.contentEquals(credentialValue))
    }
}