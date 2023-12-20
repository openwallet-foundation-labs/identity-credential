package com.android.mdl.appreader.viewModel

import androidx.lifecycle.ViewModel
import com.android.mdl.appreader.document.RequestDocument

class RequestCustomViewModel : ViewModel() {
    private var selectedDataItems = mutableMapOf<String, MutableList<String>>()
    private lateinit var requestDocument: RequestDocument
    private var isInitiated = false

    fun init(requestDocument: RequestDocument) {
        if (!isInitiated) {
            this.requestDocument = requestDocument
            requestDocument.itemsToRequest.forEach { ns ->
                selectedDataItems[ns.key] = ns.value.keys.toMutableList()
            }
            isInitiated = true
        }
    }

    fun isSelectedDataItem(namespace: String, identifier: String) =
        selectedDataItems[namespace]?.any { it == identifier } ?: false

    fun dataItemSelected(namespace: String, identifier: String) {
        if (isSelectedDataItem(namespace, identifier)) {
            selectedDataItems[namespace]?.remove(identifier)
        } else {
            selectedDataItems[namespace]?.add(identifier)
        }
    }

    fun getSelectedDataItems(intentToRetain: Boolean): Map<String, Map<String, Boolean>> {
        if (!isInitiated) {
            throw IllegalStateException("Needed to be initiated with a request document")
        }
        return selectedDataItems.map { ns ->
            Pair(ns.key, ns.value.map { el -> Pair(el, intentToRetain) }.toMap())
        }.toMap()
    }

}