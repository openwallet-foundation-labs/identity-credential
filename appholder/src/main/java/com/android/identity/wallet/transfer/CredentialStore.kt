package com.android.identity.wallet.transfer

import android.content.Context
import com.android.identity.android.legacy.IdentityCredentialStore
import com.android.identity.wallet.util.PreferencesHelper

class CredentialStore(
    private val context: Context
) {

    fun createIdentityCredentialStore(): IdentityCredentialStore {
        return if (PreferencesHelper.isHardwareBacked())
            IdentityCredentialStore.getHardwareInstance(context)
                ?: createKeystoreBackedStore() else createKeystoreBackedStore()
    }

    private fun createKeystoreBackedStore(): IdentityCredentialStore {
        val keystoreBackedStorageLocation = PreferencesHelper
            .getKeystoreBackedStorageLocation(context)
        return IdentityCredentialStore
            .getKeystoreInstance(context, keystoreBackedStorageLocation)
    }
}