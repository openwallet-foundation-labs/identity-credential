package com.android.identity.wallet.presentationlog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.identity.wallet.util.ProvisioningUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PresentationHistoryViewModel(val app: Application) : AndroidViewModel(app) {

    val presentationHistoryStore: PresentationLogStore.PresentationHistoryStore =
        ProvisioningUtil.getInstance(app.applicationContext).logStore.presentationHistoryStore

    private val _logEntries = MutableStateFlow(listOf<PresentationLogEntry>())
    val logEntries: StateFlow<List<PresentationLogEntry>>
        get() = _logEntries.asStateFlow()


    fun fetchPresentationLogHistory(): StateFlow<List<PresentationLogEntry>> {
        val entries = presentationHistoryStore.fetchAllLogEntries()
        viewModelScope.launch {
            _logEntries.value = entries
        }
        return logEntries
    }

    fun deleteSelectedEntries(entryIds: List<Long>) {
        viewModelScope.launch {
            entryIds.forEach { entryId ->
                presentationHistoryStore.deleteLogEntry(entryId)
            }
            fetchPresentationLogHistory()
        }
    }

    fun deleteAllEntries() {
        viewModelScope.launch {
            presentationHistoryStore.deleteAllLogs()
            fetchPresentationLogHistory()
        }
    }
}