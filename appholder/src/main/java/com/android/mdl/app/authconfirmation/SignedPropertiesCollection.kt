package com.android.mdl.app.authconfirmation

class SignedPropertiesCollection {

    private val requestedDocuments = mutableMapOf<String, RequestedDocumentData>()
    private val signedProperties = mutableMapOf<String, ArrayList<String>>()

    fun addNamespace(requestedData: RequestedDocumentData) {
        this.requestedDocuments[requestedData.namespace] = requestedData
        signedProperties[requestedData.namespace] = ArrayList()
    }

    fun toggleProperty(namespace: String, property: String) {
        val propertiesForNamespace = signedProperties.getOrDefault(namespace, ArrayList())
        if (!propertiesForNamespace.remove(property)) {
            propertiesForNamespace.add(property)
        }
        signedProperties[namespace] = propertiesForNamespace
    }

    fun collect(): List<SignedDocumentData> {
        return requestedDocuments.keys.map { namespace ->
            val document = requestedDocuments.getValue(namespace)
            SignedDocumentData(
                namespace,
                signedProperties[namespace] as Collection<String>,
                document.identityCredentialName,
                document.requestedDocument.docType,
                document.requestedDocument.readerAuth,
                document.requestedDocument.itemsRequest
            )
        }
    }

    fun clear() {
        requestedDocuments.clear()
        signedProperties.clear()
    }
}