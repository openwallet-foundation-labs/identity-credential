package com.android.identity.wallet.viewmodel

import android.app.Application
import android.view.View
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.util.Constants.DEVICE_RESPONSE_STATUS_OK
import com.android.identity.wallet.R
import com.android.identity.wallet.authconfirmation.RequestedDocumentData
import com.android.identity.wallet.authconfirmation.RequestedElement
import com.android.identity.wallet.authconfirmation.SignedElementsCollection
import com.android.identity.wallet.document.DocumentInformation
import com.android.identity.wallet.document.DocumentManager
import com.android.identity.wallet.transfer.AddDocumentToResponseResult
import com.android.identity.wallet.transfer.TransferManager
import com.android.identity.wallet.util.PreferencesHelper
import com.android.identity.wallet.util.TransferStatus
import com.android.identity.wallet.util.logWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransferDocumentViewModel(val app: Application) : AndroidViewModel(app) {

    private val transferManager = TransferManager.getInstance(app.applicationContext)
    private val documentManager = DocumentManager.getInstance(app.applicationContext)
    private val signedElements = SignedElementsCollection()
    private val requestedElements = mutableListOf<RequestedDocumentData>()
    private val closeConnectionMutableLiveData = MutableLiveData<Boolean>()
    private val selectedDocuments = mutableListOf<DocumentInformation>()

    var inProgress = ObservableInt(View.GONE)
    var documentsSent = ObservableField<String>()
    val connectionClosedLiveData: LiveData<Boolean> = closeConnectionMutableLiveData

    private val mutableConfirmationState = MutableLiveData<Boolean?>()
    val authConfirmationState: LiveData<Boolean?> = mutableConfirmationState

    fun onAuthenticationCancelled() {
        mutableConfirmationState.value = true
    }

    fun onAuthenticationCancellationConsumed() {
        mutableConfirmationState.value = null
    }

    fun getTransferStatus(): LiveData<TransferStatus> =
        transferManager.getTransferStatus()

    fun getRequestedDocuments(): Collection<DeviceRequestParser.DocRequest> =
        transferManager.documentRequests()

    fun getDocuments() = documentManager.getDocuments()

    fun getSelectedDocuments() = selectedDocuments

    fun requestedElements() = requestedElements

    fun closeConnection() {
        cleanUp()
        closeConnectionMutableLiveData.value = true
    }

    fun addDocumentForSigning(document: RequestedDocumentData) {
        signedElements.addNamespace(document)
    }

    fun toggleSignedElement(element: RequestedElement) {
        signedElements.toggleProperty(element)
    }

    fun createSelectedItemsList() {
        val ownDocuments = getSelectedDocuments()
        val requestedDocuments = getRequestedDocuments()
        val result = mutableListOf<RequestedDocumentData>()
        requestedDocuments.forEach { requestedDocument ->
            try {
                val ownDocument = ownDocuments.first { it.docType == requestedDocument.docType }
                val issuerSignedEntriesToRequest = requestedElementsFrom(requestedDocument)
                result.add(
                    RequestedDocumentData(
                        userReadableName = ownDocument.userVisibleName,
                        identityCredentialName = ownDocument.docName,
                        requestedElements = issuerSignedEntriesToRequest,
                        requestedDocument = requestedDocument
                    )
                )
            } catch (e: NoSuchElementException) {
                logWarning("No document for docType " + requestedDocument.docType)
            }
        }
        requestedElements.addAll(result)
    }

    fun sendResponseForSelection(
        onResultReady: (result: AddDocumentToResponseResult) -> Unit,
        credential: MdocCredential? = null,
        authKeyUnlockData: KeyUnlockData? = null
    ) {
        val elementsToSend = signedElements.collect()
        val responseGenerator = DeviceResponseGenerator(DEVICE_RESPONSE_STATUS_OK)
        viewModelScope.launch {
            elementsToSend.forEach { signedDocument ->
                try {
                    val issuerSignedEntries = signedDocument.issuerSignedEntries()
                    val result = withContext(Dispatchers.IO) { //<- Offload from UI thread
                        transferManager.addDocumentToResponse(
                            signedDocument.identityCredentialName,
                            signedDocument.documentType,
                            issuerSignedEntries,
                            responseGenerator,
                            credential,
                            authKeyUnlockData,
                        )
                    }
                    if (result !is AddDocumentToResponseResult.DocumentAdded) {
                        onResultReady(result)
                        return@forEach
                    }
                    transferManager.sendResponse(
                        responseGenerator.generate(),
                        PreferencesHelper.isConnectionAutoCloseEnabled()
                    )
                    transferManager.setResponseServed()
                    val documentsCount = elementsToSend.count()
                    documentsSent.set(app.getString(R.string.txt_documents_sent, documentsCount as Int))
                    cleanUp()
                    onResultReady(result)
                    /*
                } catch (e: CredentialInvalidatedException) {
                    logWarning("Credential '${signedDocument.identityCredentialName}' is invalid. Deleting.")
                    documentManager.deleteCredentialByName(signedDocument.identityCredentialName)
                    Toast.makeText(
                        app.applicationContext, "Deleting invalid document "
                                + signedDocument.identityCredentialName,
                        Toast.LENGTH_SHORT
                    ).show()
                     */
                } catch (e: NoSuchElementException) {
                    logWarning("No requestedDocument for " + signedDocument.documentType)
                }
            }
        }
    }

    fun cancelPresentation(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) {
        transferManager.stopPresentation(
            sendSessionTerminationMessage,
            useTransportSpecificSessionTermination
        )
    }

    private fun requestedElementsFrom(
        requestedDocument: DeviceRequestParser.DocRequest
    ): ArrayList<RequestedElement> {
        val result = arrayListOf<RequestedElement>()
        requestedDocument.namespaces.forEach { namespace ->
            val elements = requestedDocument.getEntryNames(namespace).map { element ->
                RequestedElement(namespace, element)
            }
            result.addAll(elements)
        }
        return result
    }

    private fun cleanUp() {
        requestedElements.clear()
        signedElements.clear()
        selectedDocuments.clear()
    }
}