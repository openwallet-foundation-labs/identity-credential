package org.multipaz.documenttype


/**
 * Class representing a well-known document request.
 *
 * @param id an identifier for the well-known document request (unique only for the document type).
 * @param displayName a short string with the name of the request, short enough to be used
 *   for a button. For example "Age Over 21 and Portrait" or "Full mDL".
 * @param mdocRequest the request for a ISO mdoc credential, if defined.
 * @param jsonRequest the request for a JSON-based credential, if defined.
 */
data class DocumentCannedRequest(
    val id: String,
    val displayName: String,
    val mdocRequest: MdocCannedRequest?,
    val jsonRequest: JsonCannedRequest?
)