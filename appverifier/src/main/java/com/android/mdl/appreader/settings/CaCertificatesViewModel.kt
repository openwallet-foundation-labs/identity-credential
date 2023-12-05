package com.android.mdl.appreader.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.mdl.appreader.VerifierApp
import com.android.mdl.appreader.trustmanagement.getSubjectKeyIdentifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CaCertificatesViewModel() : ViewModel() {

    private val _screenState = MutableStateFlow(CaCertificatesScreenState())
    val screenState: StateFlow<CaCertificatesScreenState> = _screenState.asStateFlow()

    private val _currentCertificateItem = MutableStateFlow<CertificateItem?>(null)
    val currentCertificateItem = _currentCertificateItem.asStateFlow()
    fun loadCertificates() {
        val certificates =
            VerifierApp.trustManagerInstance.getAllCertificates().map { it.toCertificateItem() }
        _screenState.update { it.copy(certificates = certificates) }
    }

    fun setCurrentCertificateItem(certificateItem: CertificateItem) {
        _currentCertificateItem.update { certificateItem }
    }

    fun deleteCertificate() {
        _currentCertificateItem.value?.certificate?.let {
            VerifierApp.trustManagerInstance.removeCertificate(it)
            VerifierApp.certificateStorageEngineInstance.delete(it.getSubjectKeyIdentifier())
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer { CaCertificatesViewModel() }
            }
        }
    }
}