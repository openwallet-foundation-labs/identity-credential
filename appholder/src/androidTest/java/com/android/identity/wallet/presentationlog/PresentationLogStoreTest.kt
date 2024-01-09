package com.android.identity.wallet.presentationlog

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.storage.StorageEngine
import com.android.identity.wallet.util.PreferencesHelper
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresentationLogStoreTest {

    private lateinit var storageEngine: StorageEngine

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storageDir = PreferencesHelper.getKeystoreBackedStorageLocation(context)
        storageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
    }

    fun oneEntryOneComponent(){

    }

    fun oneEntryMultiComponents(){

    }

    fun multipleEntriesOneComponent(){

    }

    fun multipleEntriesMultipleComponents(){

    }

    fun deleteOneEntry(){

    }

    fun deleteMultipleEntries(){

    }

    fun deleteAllEntries(){

    }
}