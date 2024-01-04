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
        ProvisioningUtil.getInstance(app.applicationContext).logStore.historyLogStore


    private val _logEntries =
        MutableStateFlow(listOf(PresentationLogEntry.Builder(101010101).build()))
    val logEntries: StateFlow<List<PresentationLogEntry>>
        get() = _logEntries.asStateFlow()


    fun getPresentationLogHistory(): StateFlow<List<PresentationLogEntry>> {
        val entries = presentationHistoryStore.fetchAllLogEntries()
        viewModelScope.launch {
            _logEntries.value = entries
        }
        return logEntries
    }

    fun deleteSelectedEntries(entryIds: List<Int>) {
        viewModelScope.launch {
            entryIds.forEach { entryId ->
                presentationHistoryStore.deleteLogEntry(entryId)
            }
            getPresentationLogHistory()
        }
    }

    fun deleteAllEntries() {
        viewModelScope.launch {
            presentationHistoryStore.deleteAllLogs()
            getPresentationLogHistory()
        }
    }
}