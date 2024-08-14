package com.android.identity.documenttype


/**
 * Class representing a well-known document request.
 *
 * @param displayName a short string with the name of the request, short enough to be used
 * for a button. For example "Age Over 21 and Portrait" or "Full mDL".
 */
data class DocumentWellKnownRequest(
    val displayName: String,
    val mdocRequest: MdocRequest?,
)