package com.android.mdl.app.viewmodel

import android.app.Application
import androidx.biometric.BiometricPrompt.CryptoObject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.security.identity.IdentityCredentialPresentation
import androidx.security.identity.InvalidRequestMessageException
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.DocumentData.AAMVA_NAMESPACE
import com.android.mdl.app.util.DocumentData.MDL_DOCTYPE
import com.android.mdl.app.util.DocumentData.MDL_NAMESPACE
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
        val docRequest: IdentityCredentialPresentation.DocumentRequest =
            transferManager.request?.documentRequests?.first { it.docType == MDL_DOCTYPE }
                ?: throw InvalidRequestMessageException("No DocRequest from reader")

        val issuerSignedEntriesToRequest: MutableMap<String, Collection<String>> = mutableMapOf(
            Pair(MDL_NAMESPACE, arrayListOf("given_name", "family_name", "portrait")),
            Pair(AAMVA_NAMESPACE, arrayListOf("real_id"))
        )
        return transferManager.sendCredential(
            docRequest,
            issuerSignedEntriesToRequest
        )
    }

    fun cancelPresentation() {
        transferManager.stopPresentation()
    }
}