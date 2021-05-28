package com.ul.ims.gmdl.appholder.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.ul.ims.gmdl.appholder.transfer.TransferManager
import com.ul.ims.gmdl.appholder.util.TransferStatus

class UserConsentViewModel(val app: Application) : AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "UserConsentViewModel"
    }

    private val transferManager = TransferManager.getInstance(app.applicationContext)

    fun getTransferStatus(): LiveData<TransferStatus> = transferManager.getTransferStatus()

    fun cancelPresentation() {
        transferManager.stopPresentation()
    }
}