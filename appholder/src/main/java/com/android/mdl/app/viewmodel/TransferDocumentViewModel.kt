package com.android.mdl.app.viewmodel

import android.app.Application
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.identity.Constants.DEVICE_RESPONSE_STATUS_OK
import com.android.identity.CredentialInvalidatedException
import com.android.identity.DeviceRequestParser
import com.android.identity.DeviceResponseGenerator
import com.android.mdl.app.R
import com.android.mdl.app.authconfirmation.RequestedDocumentData
import com.android.mdl.app.authconfirmation.RequestedElement
import com.android.mdl.app.authconfirmation.SignedElementsCollection
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.TransferStatus
import com.android.mdl.app.util.logWarning

class TransferDocumentViewModel(val app: Application) : AndroidViewModel(app) {

    private val transferManager = TransferManager.getInstance(app.applicationContext)
    private val documentManager = DocumentManager.getInstance(app.applicationContext)
    private val signedElements = SignedElementsCollection()
    private val requestedElements = mutableListOf<RequestedDocumentData>()
    private val closeConnectionMutableLiveData = MutableLiveData<Boolean>()
    private val selectedDocuments = mutableListOf<Document>()

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

    fun getRequestedDocuments(): Collection<DeviceRequestParser.DocumentRequest> =
        transferManager.documentRequests()

    fun getDocuments() = documentManager.getDocuments()

    fun getSelectedDocuments() = selectedDocuments

    fun getCryptoObject() = transferManager.getCryptoObject()

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
                        ownDocument.userVisibleName,
                        ownDocument.identityCredentialName,
                        false,
                        issuerSignedEntriesToRequest,
                        requestedDocument
                    )
                )
            } catch (e: NoSuchElementException) {
                logWarning("No document for docType " + requestedDocument.docType)
            }
        }
        requestedElements.addAll(result)
    }

    fun sendResponseForSelection(): Boolean {
        val elementsToSend = signedElements.collect()
        val response = DeviceResponseGenerator(DEVICE_RESPONSE_STATUS_OK)
        elementsToSend.forEach { signedDocument ->
            try {
                val issuerSignedEntries = signedDocument.issuerSignedEntries()
                val authNeeded = transferManager.addDocumentToResponse(
                    signedDocument.identityCredentialName,
                    signedDocument.documentType,
                    issuerSignedEntries,
                    response,
                    signedDocument.readerAuth,
                    signedDocument.itemsRequest
                )
                if (authNeeded) {
                    return false
                }
            } catch (e: CredentialInvalidatedException) {
                logWarning("Credential '${signedDocument.identityCredentialName}' is invalid. Deleting.")
                documentManager.deleteCredentialByName(signedDocument.identityCredentialName)
                Toast.makeText(app.applicationContext, "Deleting invalid credential "
                    + signedDocument.identityCredentialName,
                    Toast.LENGTH_SHORT).show()
            } catch (e: NoSuchElementException) {
                logWarning("No requestedDocument for " + signedDocument.documentType)
            }
        }
        transferManager.sendResponse(response.generate())
        transferManager.setResponseServed()
        val documentsCount = elementsToSend.count()
        documentsSent.set(app.getString(R.string.txt_documents_sent, documentsCount))
        cleanUp()
        return true
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
        requestedDocument: DeviceRequestParser.DocumentRequest
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