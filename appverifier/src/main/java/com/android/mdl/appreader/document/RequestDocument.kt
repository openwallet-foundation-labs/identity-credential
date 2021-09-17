package com.android.mdl.appreader.document

import androidx.security.identity.IdentityCredentialVerification
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

    /**
     * Builds a DocumentRequest based in the select data items. This methods is a helper to create
     * an instance of IdentityCredentialVerification.DocumentRequest
     */
    fun getDocumentRequest(): IdentityCredentialVerification.DocumentRequest {
        mapSelectedDataItem?.let {
            val documentRequestBuilder =
                IdentityCredentialVerification.DocumentRequest.Builder(docType)
            documentRequestBuilder.addRequestNamespace(nameSpace, it)
            return documentRequestBuilder.build()
        } ?: throw IllegalStateException("Need to setSelectedDataItems first")
    }

    interface RequestDataItem {
        val identifier: String
        val stringResourceId: Int
    }
}