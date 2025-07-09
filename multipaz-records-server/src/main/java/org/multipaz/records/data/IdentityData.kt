package org.multipaz.records.data

import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple
import org.multipaz.cbor.annotation.CborSerializable

/**
 * Class that holds data for a person.
 *
 * Note: this design is generic enough to handle some interesting and illustrative cases, but it
 * is simplistic and would not scale for may real-life scenarios!
 *
 * Data is split in two parts: [core] data and [records]. Core data contains information that is
 * shared across most credentials and is common for all people: name, date of birth, etc.
 * On the other hand, records are specialized pieces of data, each of which contains information
 * about a particular event in person's life (e.g. graduation) or some kind of grant (i.e.
 * driving privileges).
 *
 * Core data maps core field name (such as "family_name") to a particular piece of data.
 *
 * Records map record type to a map that holds all of the person's records of a given type indexed
 * by unique record ids. Even though for certain record types (e.g. diving privileges) multiple
 * records of that type do not make much sense, for other record types (e.g. graduations) they
 * are necessary and the structure is the same in all cases. While [DataItem] that holds record
 * data can be of any cbor type, in most cases it is a map.
 *
 * Note that records in general do not correspond to credentials 1:1. For instance, one could
 * have a credential that certifies that a person does not have any criminal records on file.
 */
@CborSerializable
class IdentityData(
    val core: Map<String, DataItem>,
    val records: Map<String, Map<String, DataItem>>
) {
    /**
     * Merges updated data in.
     *
     * For core data merging granularity is field: fields listed in [core] are updated with
     * new values, except that fields that are given `null` as a value are deleted; existing
     * fields that are not listed in [core] are not modified.
     *
     * Records are merged with record data granularity: the whole record is updated (or removed
     * if the new value is `null`).
     */
    fun merge(
        core: Map<String, DataItem>,
        records: Map<String, Map<String, DataItem>>
    ): IdentityData {
        val mergedCommon = this.core.toMutableMap()
        mergeMap(mergedCommon, core)
        val mergedExtra = this.records.toMutableMap()
        for ((key, value) in records) {
            if (value.isEmpty()) {
                mergedExtra.remove(key)
            } else {
                val merged = mergedExtra[key]?.toMutableMap() ?: mutableMapOf()
                mergeMap(merged, value)
                mergedExtra[key] = merged.toMap()
            }
        }
        return IdentityData(mergedCommon.toMap(), mergedExtra.toMap())
    }

    private fun mergeMap(target: MutableMap<String, DataItem>, source: Map<String, DataItem>) {
        for ((field, value) in source) {
            if (value == Simple.NULL) {
                target.remove(field)
            } else {
                target[field] = value
            }
        }
    }

    companion object
}