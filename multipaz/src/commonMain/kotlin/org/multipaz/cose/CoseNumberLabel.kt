package org.multipaz.cose

import org.multipaz.cbor.DataItem
import org.multipaz.cbor.toDataItem

/**
 * A COSE Label for a number.
 *
 * @param number the number.
 */
data class CoseNumberLabel(val number: Long) : CoseLabel() {
    override fun toDataItem(): DataItem = number.toDataItem()
}

/**
 * Gets a [CoseLabel] from a number.
 */
val Long.toCoseLabel
    get() = CoseNumberLabel(this)
