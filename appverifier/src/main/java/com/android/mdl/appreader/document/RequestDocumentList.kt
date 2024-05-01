package com.android.mdl.appreader.document

import java.io.Serializable

class RequestDocumentList : Serializable {
    private val list = mutableListOf<RequestDocument>()

    fun addRequestDocument(requestDocument: RequestDocument) {
        list.add(requestDocument)
    }

    fun getAll(): List<RequestDocument> {
        return list
    }
}
