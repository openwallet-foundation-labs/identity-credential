package org.multipaz.documenttype

/**
 * A class representing a request for data elements in a namespace.
 *
 * @param namespace the namespace.
 * @param dataElementsToRequest the data elements to request, with intent to retain.
 */
data class MdocNamespaceRequest(
    val namespace: String,
    val dataElementsToRequest: Map<MdocDataElement, Boolean>
)
