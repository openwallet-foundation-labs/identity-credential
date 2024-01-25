package com.android.identity_credential.wallet

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.identity.credential.Credential
import com.android.identity.issuance.CredentialExtensions.housekeeping
import com.android.identity.issuance.CredentialExtensions.refreshState
import com.android.identity.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CredentialInformationViewModel  : ViewModel() {

    companion object {
        private const val TAG = "CredentialInformationViewModel"
    }

    var lastHousekeepingAt = mutableStateOf(0L)

    fun housekeeping(application: WalletApplication,
                     credential: Credential) {
        viewModelScope.launch(Dispatchers.IO) {
            credential.housekeeping(
                application.issuingAuthorityRepository,
                true,
                3,
                30*24*3600,
                application.androidKeystoreSecureArea,
                "mdoc/MSO")
            lastHousekeepingAt.value = System.currentTimeMillis()
        }
    }
}
