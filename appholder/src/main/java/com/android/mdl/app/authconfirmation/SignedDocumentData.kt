package com.android.mdl.app.authconfirmation

class SignedDocumentData(
    private val signedElements: List<RequestedElement>,
    val identityCredentialName: String,
    val documentType: String,
    val readerAuth: ByteArray?,
    val itemsRequest: ByteArray
) {

    fun issuerSignedEntries(): MutableMap<String, Collection<String>> {
        val byNamespace = signedElements.groupBy { it.namespace }
        val result = mutableMapOf<String, Collection<String>>()
        byNamespace.forEach { (namespace, elements) ->
            result[namespace] = elements.map { it.value }
        }
        return result
    }
}