package com.android.identity.documenttype

/**
 * A class representing a request for data elements in a namespace.
 *
 * @param namespace the namespace.
 * @param dataElementsToRequest the data elements to request.
 */
data class MdocNamespaceRequest(
    val namespace: String,
    val dataElementsToRequest: List<MdocDataElement>
)
