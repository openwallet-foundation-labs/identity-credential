package com.android.mdl.app.viewmodel

import android.app.Application
import android.view.View
import androidx.biometric.BiometricPrompt.CryptoObject
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.security.identity.Constants.DEVICE_RESPONSE_STATUS_OK
import androidx.security.identity.DeviceResponseGenerator
import androidx.security.identity.InvalidRequestMessageException
import com.android.mdl.app.R
import com.android.mdl.app.document.Document
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.TransferStatus

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

    fun getRequestedDocuments(): List<Document> {
        val requestedDocuments = mutableListOf<Document>()

        val docRequests =
            transferManager.getDeviceRequest().docRequests
        docRequests.forEach { doc ->
            requestedDocuments.add(
                documentManager.getDocuments().first { it.docType == doc.docType })
        }
        return requestedDocuments
    }

    fun getEntryNames(): List<String> {
        val docRequests =
            transferManager.getDeviceRequest().docRequests

        val entryNames = mutableListOf<String>()
        docRequests.forEach { doc ->
            doc.namespaces.forEach { ns ->
                entryNames.addAll(doc.getEntryNames(ns))
            }
        }
        return entryNames
    }

    fun getCryptoObject() = transferManager.getCryptoObject()

    @Throws(InvalidRequestMessageException::class)
    fun sendResponse(): CryptoObject? {
        inProgress.set(View.VISIBLE)
        // Currently we don't care about the request, for now we just send the mdoc we have
        // without any regard to what the reader actually requested...
        //
        val docRequests =
            transferManager.getDeviceRequest().docRequests

        val requestedDocuments = getRequestedDocuments()
        val response = DeviceResponseGenerator(DEVICE_RESPONSE_STATUS_OK)
        docRequests.forEach { doc ->
            val requestedDocument = requestedDocuments.first { it.docType == doc.docType }
            val issuerSignedEntriesToRequest = mutableMapOf<String, Collection<String>>()
            doc.namespaces.forEach { ns ->
                issuerSignedEntriesToRequest[ns] = doc.getEntryNames(ns)
            }
            val authNeeded = transferManager.addDocumentToResponse(
                requestedDocument.identityCredentialName,
                doc.docType,
                issuerSignedEntriesToRequest,
                response
            )
            if (authNeeded) {
                inProgress.set(View.GONE)
                inProgress.notifyChange()
                return transferManager.getCryptoObject()
            }
        }

        transferManager.sendResponse(response.generate())
        documentsCount++
        documentsSent.set(app.getString(R.string.txt_documents_sent, documentsCount))
        inProgress.set(View.GONE)
        return null
    }

    fun cancelPresentation() = transferManager.stopPresentation()
}