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

    var inProgress = ObservableInt(View.GONE)
    var documentsSent = ObservableField<String>()
    val connectionClosedLiveData: LiveData<Boolean> = closeConnectionMutableLiveData

    fun getTransferStatus(): LiveData<TransferStatus> = transferManager.getTransferStatus()

    fun getRequestedDocuments(): Collection<DeviceRequestParser.DocumentRequest> =
        transferManager.getDeviceRequest().documentRequests

    fun getDocuments() = documentManager.getDocuments()

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

    fun sendResponseForRequestedDocument(): List<RequestedDocumentData> {
        val ownDocuments = getDocuments()
        val requestedDocuments = getRequestedDocuments()
        val result = mutableListOf<RequestedDocumentData>()
        val response = DeviceResponseGenerator(DEVICE_RESPONSE_STATUS_OK)
        requestedDocuments.forEach { requestedDocument ->
            try {
                val ownDocument = ownDocuments.first { it.docType == requestedDocument.docType }
                val userReadableName = ownDocument.userVisibleName
                val identityCredentialName = ownDocument.identityCredentialName
                val issuerSignedEntriesToRequest = requestedPropertiesFrom(requestedDocument)
                val authNeeded = transferManager.addDocumentToResponse(
                    identityCredentialName,
                    requestedDocument.docType,
                    issuerSignedEntriesToRequest,
                    response,
                    requestedDocument.readerAuth,
                    requestedDocument.itemsRequest
                )
                result.addAll(issuerSignedEntriesToRequest.map {
                    RequestedDocumentData(
                        userReadableName,
                        it.key,
                        identityCredentialName,
                        authNeeded,
                        it.value,
                        requestedDocument
                    )
                })
            } catch (e: NoSuchElementException) {
                Log.w(LOG_TAG, "No requestedDocument for docType " + requestedDocument.docType)
            }
        }
        if (result.any { it.needsAuth }) {
            requestedProperties.addAll(result)
            return result
        }
        transferManager.sendResponse(response.generate())
        val documentsCount = result.count()
        documentsSent.set(app.getString(R.string.txt_documents_sent, documentsCount))
        return emptyList()
    }

    fun sendResponseForSelection() {
        val propertiesToSend = signedProperties.collect()
        val response = DeviceResponseGenerator(DEVICE_RESPONSE_STATUS_OK)
        propertiesToSend.forEach { signedDocument ->
            try {
                val issuerSignedEntries = with(signedDocument) {
                    mutableMapOf(namespace to signedProperties)
                }
                transferManager.addDocumentToResponse(
                    signedDocument.identityCredentialName,
                    signedDocument.documentType,
                    issuerSignedEntries,
                    response,
                    signedDocument.readerAuth,
                    signedDocument.itemsRequest
                )
            } catch (e: NoSuchElementException) {
                Log.w(LOG_TAG, "No requestedDocument for " + signedDocument.documentType)
            }
        }
        transferManager.sendResponse(response.generate())
        val documentsCount = propertiesToSend.count()
        documentsSent.set(app.getString(R.string.txt_documents_sent, documentsCount))
        cleanUp()
    }

    fun cancelPresentation(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) = transferManager.stopPresentation(
        sendSessionTerminationMessage,
        useTransportSpecificSessionTermination
    )

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
    }
}