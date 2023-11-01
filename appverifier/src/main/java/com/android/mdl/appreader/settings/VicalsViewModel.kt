package com.android.mdl.appreader.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.mdl.appreader.VerifierApp
import com.android.mdl.appreader.issuerauth.vical.Vical
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.StringBuilder

class VicalsViewModel(val context: Context): ViewModel() {
    private val _screenState = MutableStateFlow(VicalsScreenState())
    val screenState: StateFlow<VicalsScreenState> = _screenState.asStateFlow()

    private val _currentVicalItem = MutableStateFlow<VicalItem?>(null)
    val currentVicalItem = _currentVicalItem.asStateFlow()
    private val _currentCertificateItem = MutableStateFlow<CertificateItem?>(null)
    val currentCertificateItem = _currentCertificateItem.asStateFlow()

    fun loadVicals() {
        val vicals = VerifierApp.vicalStoreInstance.getAll().map { it.toVicalItem() }
        _screenState.update { it.copy(vicals = vicals) }
    }

    fun setCurrentVicalItem(vicalItem: VicalItem) {
        _currentVicalItem.update { vicalItem }
    }
    fun setCurrentCertificateItem(certificateItem: CertificateItem) {
        _currentCertificateItem.update { certificateItem }
    }
    fun deleteVical() {
        _currentVicalItem.value?.vical?.let { VerifierApp.vicalStoreInstance.delete(it) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer { VicalsViewModel(context) }
            }
        }
    }
}