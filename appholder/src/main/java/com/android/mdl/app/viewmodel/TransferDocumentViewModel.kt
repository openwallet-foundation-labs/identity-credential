package com.android.mdl.app.viewmodel

import android.app.Application
import android.util.Log
import android.view.View
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.android.identity.Constants.DEVICE_RESPONSE_STATUS_OK
import com.android.identity.DeviceRequestParser
import com.android.identity.DeviceResponseGenerator
import com.android.mdl.app.R
import com.android.mdl.app.authconfirmation.RequestedDocumentData
import com.android.mdl.app.authconfirmation.SignedDocumentData
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.TransferStatus

class TransferDocumentViewModel(val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "TransferDocumentViewModel"
    }

    var inProgress = ObservableInt(View.GONE)
    var documentsSent = ObservableField<String>()

    private val transferManager = TransferManager.getInstance(app.applicationContext)
    private val documentManager = DocumentManager.getInstance(app.applicationContext)

    fun getTransferStatus(): LiveData<TransferStatus> = transferManager.getTransferStatus()

    fun getRequestedDocuments(): Collection<DeviceRequestParser.DocumentRequest> =
        transferManager.getDeviceRequest().documentRequests

    fun getDocuments() = documentManager.getDocuments()

    fun getCryptoObject() = transferManager.getCryptoObject()

//    fun getEntryNames(): Map<Document, List<String>> {
//        val documents = mutableMapOf<Document, List<String>>()
//        val docRequests =
//            transferManager.getDeviceRequest().documentRequests
//
//        docRequests.forEach { doc ->
//            try {
//                val entryNames = mutableListOf<String>()
//                doc.namespaces.forEach { ns ->
//                    entryNames.addAll(doc.getEntryNames(ns))
//                }
//                documents[documentManager.getDocuments().first { it.docType == doc.docType }] =
//                    entryNames
//            } catch (e: NoSuchElementException) {
//                Log.w(LOG_TAG, "No document for docType " + doc.docType)
//            }
//        }
//        return documents
//    }
//
//    @Throws(InvalidRequestMessageException::class)
//    fun sendResponse(): Boolean {
//        inProgress.set(View.VISIBLE)
//        // Currently we don't care about the request, for now we just send the mdoc we have
//        // without any regard to what the reader actually requested...
//        //
//        val documents = getDocuments()
//        val requestedDocuments = getRequestedDocuments()
//        val response = DeviceResponseGenerator(DEVICE_RESPONSE_STATUS_OK)
//        requestedDocuments.forEach { reqDoc ->
//            try {
//                val doc = documents.first { it.docType == reqDoc.docType }
//                val issuerSignedEntriesToRequest = mutableMapOf<String, Collection<String>>()
//                reqDoc.namespaces.forEach { ns ->
//                    issuerSignedEntriesToRequest[ns] = reqDoc.getEntryNames(ns)
//                }
//                val authNeeded = transferManager.addDocumentToResponse(
//                    doc.identityCredentialName,
//                    doc.docType,
//                    issuerSignedEntriesToRequest,
//                    response,
//                    reqDoc.readerAuth,
//                    reqDoc.itemsRequest
//                )
//                if (authNeeded) {
//                    inProgress.set(View.GONE)
//                    inProgress.notifyChange()
//                    return true
//                }
//            } catch (e: NoSuchElementException) {
//                Log.w(LOG_TAG, "No document for docType " + reqDoc.docType)
//            }
//        }
//
//        transferManager.sendResponse(response.generate())
//        documentsCount++
//        documentsSent.set(app.getString(R.string.txt_documents_sent, documentsCount))
//        inProgress.set(View.GONE)
//        return false
//    }

    val requested = mutableListOf<RequestedDocumentData>()

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
            requested.addAll(result)
            return result
        }
        transferManager.sendResponse(response.generate())
        val documentsCount = result.count()
        documentsSent.set(app.getString(R.string.txt_documents_sent, documentsCount))
        return emptyList()
    }

    fun sendResponseForSelection(signedDocuments: List<SignedDocumentData>) {
        val response = DeviceResponseGenerator(DEVICE_RESPONSE_STATUS_OK)
        signedDocuments.forEach { signedDocument ->
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
        val documentsCount = signedDocuments.count()
        documentsSent.set(app.getString(R.string.txt_documents_sent, documentsCount))
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

    fun cancelPresentation(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) = transferManager.stopPresentation(
        sendSessionTerminationMessage,
        useTransportSpecificSessionTermination
    )
}