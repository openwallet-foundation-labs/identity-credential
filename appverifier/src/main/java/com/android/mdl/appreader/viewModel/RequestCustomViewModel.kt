package com.android.mdl.appreader.viewModel

import androidx.lifecycle.ViewModel
import com.android.mdl.appreader.document.RequestDocument

class RequestCustomViewModel : ViewModel() {
    private var selectedDataItems = mutableListOf<String>()
    private lateinit var requestDocument: RequestDocument
    private var isInitiated = false

    fun init(requestDocument: RequestDocument) {
        if (!isInitiated) {
            this.requestDocument = requestDocument
            requestDocument.dataItems.forEach { dataItem ->
                selectedDataItems.add(dataItem.identifier)
            }
            isInitiated = true
        }
    }

    fun isSelectedDataItem(identifier: String) = selectedDataItems.any { it == identifier }

    fun dataItemSelected(identifier: String) {
        if (isSelectedDataItem(identifier)) {
            selectedDataItems.remove(identifier)
        } else {
            selectedDataItems.add(identifier)
        }
    }

    fun getSelectedDataItems(intentToRetain: Boolean): Map<String, Boolean> {
        if (!isInitiated) {
            throw IllegalStateException("Needed to be initiated with a request document")
        }

        val map = mutableMapOf<String, Boolean>()
        selectedDataItems.forEach {
            map[it] = intentToRetain
        }

        return map
    }

}