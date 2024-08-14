package com.android.identity.documenttype

/**
 * A class representing a request for a particular set of namespaces and data elements
 * for a particular document type.
 *
 * @param docType the mdoc doctype.
 * @param namespacesToRequest the namespaces to request.
 */
data class MdocRequest(
    val docType: String,
    val namespacesToRequest: List<MdocNamespaceRequest>
)