package com.android.mdl.app.authconfirmation

class SignedElementsCollection {

    private val requestedDocuments = mutableMapOf<String, RequestedDocumentData>()
    private val signedElements = mutableListOf<RequestedElement>()

    fun addNamespace(requestedData: RequestedDocumentData) {
        this.requestedDocuments[requestedData.identityCredentialName] = requestedData
    }

    fun toggleProperty(element: RequestedElement) {
        if (!signedElements.remove(element)) {
            signedElements.add(element)
        }
    }

    fun collect(): List<SignedDocumentData> {
        return requestedDocuments.keys.map { namespace ->
            val document = requestedDocuments.getValue(namespace)
            SignedDocumentData(
                signedElements,
                document.identityCredentialName,
                document.requestedDocument.docType,
                document.requestedDocument.readerAuth,
                document.requestedDocument.itemsRequest
            )
        }
    }

    fun clear() {
        requestedDocuments.clear()
        signedElements.clear()
    }
}