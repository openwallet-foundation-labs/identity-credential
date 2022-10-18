package com.android.mdl.app.request

import java.io.Serializable


abstract class RequestDocument : Serializable {
    abstract val docType: String
    abstract val nameSpace: String
    abstract val dataItems: List<RequestDataItem>

    fun getFullItemsToRequest(): MutableMap<String, MutableCollection<String>> {
        val list = mutableListOf<String>()
        dataItems.forEach {
            list.add(it.identifier)
        }
        return mutableMapOf(Pair(nameSpace, list))
    }

    interface RequestDataItem {
        val identifier: String
        val stringResourceId: Int
    }
}