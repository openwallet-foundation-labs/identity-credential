package com.android.mdl.appreader.document

import java.io.Serializable


abstract class RequestDocument : Serializable {
    abstract val docType: String
    abstract val nameSpace: String
    abstract val dataItems: List<RequestDataItem>

    private var mapSelectedDataItem: Map<String, Boolean>? = null

    /**
     * Set data items selected by the user
     *
     * @param mapSelectedDataItem Map with the <code>String</code> identifier of the data item and
     *                             intentToRetain <code>Boolean</code>
     */
    fun setSelectedDataItems(mapSelectedDataItem: Map<String, Boolean>) {
        this.mapSelectedDataItem = mapSelectedDataItem
    }

    fun getItemsToRequest(): Map<String, Map<String, Boolean>> {
        mapSelectedDataItem?.let {

            return mapOf(Pair(nameSpace, it))
        } ?: throw IllegalStateException("No data items selected for this request")
    }

    interface RequestDataItem {
        val identifier: String
        val stringResourceId: Int
    }
}