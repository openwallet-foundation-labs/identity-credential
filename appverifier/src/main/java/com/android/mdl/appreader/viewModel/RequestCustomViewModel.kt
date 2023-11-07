package com.android.mdl.appreader.viewModel

import androidx.lifecycle.ViewModel
import com.android.identity.android.mdoc.document.DataElement
import com.android.mdl.appreader.document.RequestDocument

class RequestCustomViewModel : ViewModel() {
    private var selectedDataItems = mutableListOf<DataElement>()
    private lateinit var requestDocument: RequestDocument
    private var isInitiated = false

    fun init(requestDocument: RequestDocument) {
        if (!isInitiated) {
            this.requestDocument = requestDocument
            requestDocument.elements.forEach { selectedDataItems.add(it) }
            isInitiated = true
        }
    }

    fun isSelectedDataItem(dataElement: DataElement) = selectedDataItems.any { it == dataElement }

    fun dataItemSelected(dataElement: DataElement) {
        if (isSelectedDataItem(dataElement)) {
            selectedDataItems.remove(dataElement)
        } else {
            selectedDataItems.add(dataElement)
        }
    }

    fun getSelectedDataItems(): List<DataElement> {
        if (!isInitiated) {
            throw IllegalStateException("Needed to be initiated with a request document")
        }
        return selectedDataItems
    }

}