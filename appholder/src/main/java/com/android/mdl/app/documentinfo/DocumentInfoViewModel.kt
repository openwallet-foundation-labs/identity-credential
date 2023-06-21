package com.android.mdl.app.documentinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.mdl.app.composables.toCardArt
import com.android.mdl.app.document.DocumentInformation
import com.android.mdl.app.document.DocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocumentInfoViewModel(
    private val documentManager: DocumentManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentInfoScreenState())
    val screenState: StateFlow<DocumentInfoScreenState> = _state.asStateFlow()

    fun loadDocument(documentName: String) {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val documentInfo = withContext(Dispatchers.IO) {
                documentManager.getDocumentInformation(documentName)
            }
            onDocumentInfoLoaded(documentInfo)
        }
    }

    fun promptDocumentDelete() {
        _state.update { it.copy(isDeletingPromptShown = true) }
    }

    fun confirmDocumentDelete(documentName: String) {
        documentManager.deleteCredentialByName(documentName)
        _state.update { it.copy(isDeleted = true, isDeletingPromptShown = false) }
    }

    fun cancelDocumentDelete() {
        _state.update { it.copy(isDeletingPromptShown = false) }
    }

    fun refreshAuthKeys(documentName: String) {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                documentManager.refreshAuthKeys(documentName)
            }
            onAuthKeysRefreshed()
        }
    }

    private fun onDocumentInfoLoaded(documentInformation: DocumentInformation?) {
        documentInformation?.let {
            _state.update {
                it.copy(
                    isLoading = false,
                    documentName = documentInformation.userVisibleName,
                    documentType = documentInformation.docType,
                    documentColor = documentInformation.documentColor.toCardArt(),
                    provisioningDate = documentInformation.dateProvisioned,
                    isSelfSigned = documentInformation.selfSigned,
                    authKeys = documentInformation.authKeys.asScreenStateKeys()
                )
            }
        }
    }

    private fun List<DocumentInformation.KeyData>.asScreenStateKeys(): List<DocumentInfoScreenState.KeyInformation> {
        return map { keyData ->
            DocumentInfoScreenState.KeyInformation(
                alias = keyData.alias,
                validFrom = keyData.validFrom,
                validUntil = keyData.validUntil,
                issuerDataBytesCount = keyData.issuerDataBytesCount,
                usagesCount = keyData.usagesCount
            )
        }
    }

    private fun onAuthKeysRefreshed() {
        _state.update { it.copy(isLoading = false) }
    }

    companion object {
        fun Factory(documentManager: DocumentManager): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DocumentInfoViewModel(documentManager)
                }
            }
    }
}