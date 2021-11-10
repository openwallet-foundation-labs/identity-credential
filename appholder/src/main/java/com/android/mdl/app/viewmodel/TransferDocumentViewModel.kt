package com.android.mdl.app.viewmodel

import android.app.Application
import android.util.Log
import android.view.View
import androidx.biometric.BiometricPrompt.CryptoObject
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.security.identity.Constants.DEVICE_RESPONSE_STATUS_OK
import androidx.security.identity.DeviceRequestParser
import androidx.security.identity.DeviceResponseGenerator
import androidx.security.identity.InvalidRequestMessageException
import com.android.mdl.app.R
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.TransferStatus
import java.util.NoSuchElementException

class TransferDocumentViewModel(val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "TransferDocumentViewModel"
    }

    var inProgress = ObservableInt(View.GONE)
    var documentsSent = ObservableField<String>()
    private var documentsCount = 0

    private val transferManager = TransferManager.getInstance(app.applicationContext)
    private val documentManager = DocumentManager.getInstance(app.applicationContext)

    fun getTransferStatus(): LiveData<TransferStatus> = transferManager.getTransferStatus()

    fun getRequestedDocuments(): Collection<DeviceRequestParser.DocumentRequest> =
        transferManager.getDeviceRequest().docRequests

    fun getDocuments() = documentManager.getDocuments()

    fun getEntryNames(): Map<Document, List<String>> {
        val documents = mutableMapOf<Document, List<String>>()
        val docRequests =
            transferManager.getDeviceRequest().docRequests

        docRequests.forEach { doc ->
            try {
                val entryNames = mutableListOf<String>()
                doc.namespaces.forEach { ns ->
                    entryNames.addAll(doc.getEntryNames(ns))
                }
                documents[documentManager.getDocuments().first { it.docType == doc.docType }] =
                    entryNames
            } catch (e : NoSuchElementException) {
                Log.w(LOG_TAG, "No document for docType " + doc.docType)
            }
        }
        return documents
    }

    fun getCryptoObject() = transferManager.getCryptoObject()

    @Throws(InvalidRequestMessageException::class)
    fun sendResponse(): CryptoObject? {
        inProgress.set(View.VISIBLE)
        // Currently we don't care about the request, for now we just send the mdoc we have
        // without any regard to what the reader actually requested...
        //
        val documents = getDocuments()
        val requestedDocuments = getRequestedDocuments()
        val response = DeviceResponseGenerator(DEVICE_RESPONSE_STATUS_OK)
        requestedDocuments.forEach { reqDoc ->
            try {
                val doc = documents.first { it.docType == reqDoc.docType }
                val issuerSignedEntriesToRequest = mutableMapOf<String, Collection<String>>()
                reqDoc.namespaces.forEach { ns ->
                    issuerSignedEntriesToRequest[ns] = reqDoc.getEntryNames(ns)
                }
                val authNeeded = transferManager.addDocumentToResponse(
                    doc.identityCredentialName,
                    doc.docType,
                    issuerSignedEntriesToRequest,
                    response,
                    reqDoc.readerAuth,
                    reqDoc.itemsRequest
                )
                if (authNeeded) {
                    inProgress.set(View.GONE)
                    inProgress.notifyChange()
                    return transferManager.getCryptoObject()
                }
            } catch (e : NoSuchElementException) {
                Log.w(LOG_TAG, "No document for docType " + reqDoc.docType)
            }
        }

        transferManager.sendResponse(response.generate())
        documentsCount++
        documentsSent.set(app.getString(R.string.txt_documents_sent, documentsCount))
        inProgress.set(View.GONE)
        return null
    }

    fun cancelPresentation(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) =
        transferManager.stopPresentation(
            sendSessionTerminationMessage,
            useTransportSpecificSessionTermination
        )
}