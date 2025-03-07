package org.multipaz.cose

import org.multipaz.cbor.DataItem
import org.multipaz.cbor.toDataItem

/**
 * A COSE Label for a string.
 *
 * @param text the string.
 */
data class CoseTextLabel(val text: String) : CoseLabel() {
    override fun toDataItem(): DataItem = text.toDataItem()
}

/**
 * Gets a [CoseLabel] from a string.
 */
fun String.toCoseLabel() = CoseTextLabel(this)
