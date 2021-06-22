package com.android.mdl.app.viewmodel

import android.app.Application
import android.view.View
import androidx.databinding.ObservableField
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.android.mdl.app.document.Document
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.TransferStatus

class ShareDocumentViewModel(val app: Application) :
    AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "ShareDocumentViewModel"
    }

    private val transferManager = TransferManager.getInstance(app.applicationContext)
    var deviceEngagementQr = ObservableField<View>()
    var message = ObservableField<String>()
    private var hasStarted = false

    fun getTransferStatus(): LiveData<TransferStatus> = transferManager.getTransferStatus()

    fun startPresentation(document: Document) {
        // No need to call more than once
        if (!hasStarted) {
            transferManager.startPresentation(document)
            hasStarted = true
        }
    }

    fun cancelPresentation() {
        transferManager.stopPresentation()
        hasStarted = false
        message.set("Presentation canceled")
    }

    fun setDeviceEngagement() {
        deviceEngagementQr.set(transferManager.getDeviceEngagementQrCode())
    }

}

