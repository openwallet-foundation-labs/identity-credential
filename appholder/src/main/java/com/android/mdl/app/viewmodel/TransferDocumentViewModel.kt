package com.android.mdl.app.viewmodel

import android.app.Application
import androidx.biometric.BiometricPrompt.CryptoObject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.security.identity.Constants.DEVICE_RESPONSE_STATUS_OK
import androidx.security.identity.DeviceResponseGenerator
import androidx.security.identity.InvalidRequestMessageException
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.TransferStatus

class TransferDocumentViewModel(val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "TransferDocumentViewModel"
    }

    private val transferManager = TransferManager.getInstance(app.applicationContext)

    fun getTransferStatus(): LiveData<TransferStatus> = transferManager.getTransferStatus()

    @Throws(InvalidRequestMessageException::class)
    fun sendResponse(): CryptoObject? {
        // Currently we don't care about the request, for now we just send the mdoc we have
        // without any regard to what the reader actually requested...
        //
        val docRequests =
            transferManager.getDeviceRequest().docRequests

        val response = DeviceResponseGenerator(DEVICE_RESPONSE_STATUS_OK)
        docRequests.forEach { doc ->
            val issuerSignedEntriesToRequest = mutableMapOf<String, Collection<String>>()
            doc.namespaces.forEach { ns ->
                issuerSignedEntriesToRequest[ns] = doc.getEntryNames(ns)
            }
            val authNeeded = transferManager.addDocumentToResponse(
                doc.docType,
                issuerSignedEntriesToRequest,
                response
            )
            if (authNeeded) {
                return transferManager.getCryptoObject()
            }
        }

        transferManager.sendResponse(response.generate())
        return null
    }

    fun cancelPresentation() {
        transferManager.stopPresentation()
    }
}