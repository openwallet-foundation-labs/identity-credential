package org.multipaz.documenttype

/**
 * A class representing a request for a particular set of namespaces and data elements for a particular document type.
 *
 * @param docType the ISO mdoc doctype.
 * @param namespacesToRequest the namespaces to request.
 */
data class MdocCannedRequest(
    val docType: String,
    val namespacesToRequest: List<MdocNamespaceRequest>
)