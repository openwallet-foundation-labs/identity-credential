package org.multipaz.records.data

import org.multipaz.documenttype.DocumentAttribute

/**
 * A class that describes type of a particular record or field.
 */
class RecordType(
    val attribute: DocumentAttribute,
    val subAttributes: Map<String, RecordType> = mapOf()
)