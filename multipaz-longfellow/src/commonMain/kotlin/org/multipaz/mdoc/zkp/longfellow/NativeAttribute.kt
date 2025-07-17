package org.multipaz.mdoc.zkp.longfellow

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem

/** Statements for the zk proof. */
internal data class NativeAttribute(
    val key: String,
    val value: ByteArray
) {
    companion object {
        fun fromDataItem(dataItem: DataItem): NativeAttribute {
            val decodedAttr = Cbor.decode(dataItem.asTagged.asBstr)
            return NativeAttribute(
                key = decodedAttr["elementIdentifier"].asTstr,
                value = Cbor.encode(decodedAttr["elementValue"])
            )
        }
    }
}
