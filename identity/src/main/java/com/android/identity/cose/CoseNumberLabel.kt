package com.android.identity.cose

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.dataItem

/**
 * A COSE Label for a number.
 *
 * @param number the number.
 */
data class CoseNumberLabel(val number: Long) : CoseLabel() {
    override val dataItem: DataItem
        get() = number.dataItem
}

/**
 * Gets a [CoseLabel] from a number.
 */
val Long.coseLabel
    get() = CoseNumberLabel(this)
