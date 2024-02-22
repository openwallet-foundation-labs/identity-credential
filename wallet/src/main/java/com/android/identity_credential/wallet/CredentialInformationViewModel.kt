package com.android.identity_credential.wallet

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.credential.Credential
import com.android.identity.issuance.CredentialExtensions.housekeeping
import com.android.identity.issuance.IssuingAuthorityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CredentialInformationViewModel : ViewModel() {

    companion object {
        private const val TAG = "CredentialInformationViewModel"
    }

    var lastHousekeepingAt = mutableStateOf(0L)

    fun housekeeping(
        issuingAuthorityRepository: IssuingAuthorityRepository,
        androidKeystoreSecureArea: AndroidKeystoreSecureArea,
        credential: Credential
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            credential.housekeeping(
                issuingAuthorityRepository,
                true,
                3,
                30 * 24 * 3600,
                androidKeystoreSecureArea,
                WalletApplication.AUTH_KEY_DOMAIN
            )
            lastHousekeepingAt.value = System.currentTimeMillis()
        }
    }
}
