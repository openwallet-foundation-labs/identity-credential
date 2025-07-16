package org.multipaz.documenttype

/**
 * A class representing a request for a particular set of namespaces and data elements for a particular document type.
 *
 * @param docType the ISO mdoc doctype.
 * @param useZkp `true` if the canned request should indicate a preference for use of Zero-Knowledge Proofs.
 * @param namespacesToRequest the namespaces to request.
 */
data class MdocCannedRequest(
    val docType: String,
    val useZkp: Boolean,
    val namespacesToRequest: List<MdocNamespaceRequest>
)