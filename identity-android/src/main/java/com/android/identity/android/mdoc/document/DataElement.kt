package com.android.identity.android.mdoc.document

/**
 * Interface for the enums that describe the data elements
 */
interface DataElement {
    val elementName: String
    val stringResourceId: Int
    val nameSpace: Namespace
}