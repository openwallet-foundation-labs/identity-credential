package com.ul.ims.gmdl.appholder.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.security.identity.IdentityCredentialPresentation
import androidx.security.identity.InvalidRequestMessageException
import com.ul.ims.gmdl.appholder.transfer.TransferManager
import com.ul.ims.gmdl.appholder.util.DocumentData.AAMVA_NAMESPACE
import com.ul.ims.gmdl.appholder.util.DocumentData.DUMMY_CREDENTIAL_NAME
import com.ul.ims.gmdl.appholder.util.DocumentData.MDL_DOCTYPE
import com.ul.ims.gmdl.appholder.util.DocumentData.MDL_NAMESPACE
import com.ul.ims.gmdl.appholder.util.TransferStatus

class TransferDocumentViewModel(val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "TransferDocumentViewModel"
    }

    private val transferManager = TransferManager.getInstance(app.applicationContext)

    fun getTransferStatus(): LiveData<TransferStatus> = transferManager.getTransferStatus()

    @Throws(InvalidRequestMessageException::class)
    fun sendResponse() {
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
        transferManager.sendCredential(
            docRequest,
            DUMMY_CREDENTIAL_NAME,
            issuerSignedEntriesToRequest
        )

    }

    fun cancelPresentation() {
        transferManager.stopPresentation()
    }
}