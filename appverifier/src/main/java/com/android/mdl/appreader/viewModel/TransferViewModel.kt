package com.android.mdl.appreader.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.android.mdl.appreader.document.RequestDocumentList
import com.android.mdl.appreader.transfer.TransferManager
import com.android.mdl.appreader.util.TransferStatus

class TransferViewModel(val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "TransferViewModel"
    }

    private val transferManager = TransferManager.getInstance(app.applicationContext)

    fun getTransferStatus(): LiveData<TransferStatus> = transferManager.getTransferStatus()

    fun isUsingReverseEngagement(): Boolean = transferManager.usingReverseEngagement

    fun connect() {
        transferManager.connect()
    }

    fun sendRequest(requestDocumentList: RequestDocumentList) {
        transferManager.sendRequest(requestDocumentList)
    }

    fun sendNewRequest(requestDocumentList: RequestDocumentList) {
        transferManager.sendNewRequest(requestDocumentList)
    }
}