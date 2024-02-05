package com.android.identity.cose

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.dataItem

/**
 * A COSE Label for a string.
 *
 * @param text the string.
 */
data class CoseTextLabel(val text: String) : CoseLabel() {
    override val dataItem: DataItem
        get() = text.dataItem
}

/**
 * Gets a [CoseLabel] from a string.
 */
val String.coseLabel
    get() = CoseTextLabel(this)
