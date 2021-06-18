package com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods

import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.Map
import com.ul.ims.gmdl.cbordata.generic.AbstractCborStructure
import java.io.ByteArrayOutputStream

abstract class RetrievalOptions : AbstractCborStructure() {
    abstract val options: kotlin.collections.Map<Int, Any>
    abstract override fun equals(other: Any?): Boolean

    // Convert the map structure to the Cbor map
    fun toDataItem(): Map {
        var map = Map()
        options.forEach { (key, value) ->
            map = map.put(toDataItem(key), toDataItem(value))
        }
        return map
    }

    // Encode Cbor map structure to a byte array
    override fun encode(): ByteArray {
        val dataItem = toDataItem()

        val outputStream = ByteArrayOutputStream()
        CborEncoder(outputStream).encode(dataItem)

        return outputStream.toByteArray()
    }
}
