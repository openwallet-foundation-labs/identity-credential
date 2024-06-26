package com.android.identity.cose

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Nint
import com.android.identity.cbor.Tstr
import com.android.identity.cbor.Uint

/**
 * Abstract base class for COSE Labels.
 */
sealed class CoseLabel {

    /**
     * The CBOR encoding for a COSE Label.
     */
    abstract fun toDataItem(): DataItem

    companion object {
        /**
         * Gets a [CoseLabel] from a CBOR data item.
         *
         * @param dataItem the CBOR data item.
         * @return the [CoseLabel].
         */
        fun fromDataItem(dataItem: DataItem): CoseLabel =
            when (dataItem) {
                is Uint -> CoseNumberLabel(dataItem.asNumber)
                is Nint -> CoseNumberLabel(dataItem.asNumber)
                is Tstr -> CoseTextLabel(dataItem.asTstr)
                else -> throw IllegalStateException("Unexpected item")
            }
    }
}