package com.android.mdl.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.android.mdl.app.transfer.TransferManager
import com.android.mdl.app.util.TransferStatus

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