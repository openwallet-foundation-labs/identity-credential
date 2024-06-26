package com.android.identity.cose

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.toDataItem

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
