package com.android.mdl.app.viewmodel

import android.app.Application
import android.view.View
import androidx.databinding.ObservableField
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.android.mdl.app.document.DocumentManager
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.TransferStatus


class ShareDocumentViewModel(val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "ShareDocumentViewModel"
    }

    private val transferManager = TransferManager.getInstance(app.applicationContext)
    private val documentManager = DocumentManager.getInstance(app.applicationContext)
    var deviceEngagementQr = ObservableField<View>()
    var message = ObservableField<String>()

    fun getTransferStatus(): LiveData<TransferStatus> = transferManager.getTransferStatus()

    fun startPresentation() {
        transferManager.startPresentation(documentManager.store)
    }

    fun cancelPresentation() {
        transferManager.stopPresentation()
        message.set("Presentation canceled")
    }

    fun setDeviceEngagement() {
        deviceEngagementQr.set(transferManager.getDeviceEngagementQrCode())
    }

}

