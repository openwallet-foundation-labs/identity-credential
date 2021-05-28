package com.ul.ims.gmdl.appholder.viewmodel

import android.app.Application
import android.view.View
import androidx.databinding.ObservableField
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.ul.ims.gmdl.appholder.document.DocumentManager
import com.ul.ims.gmdl.appholder.transfer.TransferManager
import com.ul.ims.gmdl.appholder.util.TransferStatus


class ShareDocumentViewModel(val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "ShareDocumentViewModel"
    }

    private val transferManager = TransferManager.getInstance(app.applicationContext)
    private val documentManager = DocumentManager.getInstance(app.applicationContext)
    var deviceEngagementQr = ObservableField<View>()
    var message = ObservableField<String>()
    private var hasStarted = false

    fun getTransferStatus(): LiveData<TransferStatus> = transferManager.getTransferStatus()

    fun startPresentation() {
        if (!hasStarted)
            transferManager.startPresentation(documentManager.store)
        hasStarted = true
    }

    fun cancelPresentation() {
        transferManager.stopPresentation()
        hasStarted = false
        message.set("Presentation canceled")
    }

    fun setDeviceEngagementQrCode() {
        deviceEngagementQr.set(transferManager.getDeviceEngagementQrCode())
    }

}

