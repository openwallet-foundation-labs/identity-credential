package com.android.mdl.app.viewmodel

import android.app.Application
import android.util.Log
import android.view.View
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.identity.Constants.DEVICE_RESPONSE_STATUS_OK
import com.android.identity.DeviceRequestParser
import com.android.identity.DeviceResponseGenerator
import com.android.mdl.app.R
import com.android.mdl.app.authconfirmation.RequestedDocumentData
import com.android.mdl.app.authconfirmation.SignedPropertiesCollection
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.TransferStatus

class TransferDocumentViewModel(val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "TransferDocumentViewModel"
    }

    private val transferManager = TransferManager.getInstance(app.applicationContext)
    private val documentManager = DocumentManager.getInstance(app.applicationContext)
    private val signedProperties = SignedPropertiesCollection()
    private val requestedProperties = mutableListOf<RequestedDocumentData>()
    private val closeConnectionMutableLiveData = MutableLiveData<Boolean>()
    private val selectedDocuments = mutableListOf<Document>()

    var inProgress = ObservableInt(View.GONE)
    var documentsSent = ObservableField<String>()
    val connectionClosedLiveData: LiveData<Boolean> = closeConnectionMutableLiveData

    fun getTransferStatus(): LiveData<TransferStatus> =
        transferManager.getTransferStatus()

    fun getRequestedDocuments(): Collection<DeviceRequestParser.DocumentRequest> =
        transferManager.documentRequests()

    fun getDocuments() = documentManager.getDocuments()

    fun getSelectedDocuments() = selectedDocuments

    fun getCryptoObject() = transferManager.getCryptoObject()

    fun requestedProperties() = requestedProperties

    fun closeConnection() {
        cleanUp()
        closeConnectionMutableLiveData.value = true
    }

    fun addDocumentForSigning(document: RequestedDocumentData) {
        signedProperties.addNamespace(document)
    }

    fun toggleSignedProperty(namespace: String, property: String) {
        signedProperties.toggleProperty(namespace, property)
    }

    fun createSelectedItemsList() {
        val ownDocuments = getSelectedDocuments()
        val requestedDocuments = getRequestedDocuments()
        val result = mutableListOf<RequestedDocumentData>()
        requestedDocuments.forEach { requestedDocument ->
            try {
                val ownDocument = ownDocuments.first { it.docType == requestedDocument.docType }
                val issuerSignedEntriesToRequest = requestedPropertiesFrom(requestedDocument)
                result.addAll(issuerSignedEntriesToRequest.map {
                    RequestedDocumentData(
                        ownDocument.userVisibleName,
                        it.key,
                        ownDocument.identityCredentialName,
                        false,
                        it.value,
                        requestedDocument
                    )
                })
            } catch (e: NoSuchElementException) {
                Log.w(LOG_TAG, "No document for docType " + requestedDocument.docType)
            }
        }
        requestedProperties.addAll(result)
    }

    fun sendResponseForSelection(): Boolean {
        val propertiesToSend = signedProperties.collect()
        val response = DeviceResponseGenerator(DEVICE_RESPONSE_STATUS_OK)
        propertiesToSend.forEach { signedDocument ->
            try {
                val issuerSignedEntries = with(signedDocument) {
                    mutableMapOf(namespace to signedProperties)
                }
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
            } catch (e: NoSuchElementException) {
                Log.w(LOG_TAG, "No requestedDocument for " + signedDocument.documentType)
            }
        }
        transferManager.sendResponse(response.generate())
        transferManager.setResponseServed()
        val documentsCount = propertiesToSend.count()
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

    private fun requestedPropertiesFrom(
        requestedDocument: DeviceRequestParser.DocumentRequest
    ): MutableMap<String, Collection<String>> {
        val result = mutableMapOf<String, Collection<String>>()
        requestedDocument.namespaces.forEach { namespace ->
            val list = result.getOrDefault(namespace, ArrayList())
            (list as ArrayList).addAll(requestedDocument.getEntryNames(namespace))
            result[namespace] = list
        }
        return result
    }

    private fun cleanUp() {
        requestedProperties.clear()
        signedProperties.clear()
        selectedDocuments.clear()
    }
}