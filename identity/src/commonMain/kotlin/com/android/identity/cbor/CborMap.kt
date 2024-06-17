package com.android.identity.cbor

import kotlinx.io.bytestring.ByteStringBuilder

/**
 * Map (major type 5).
 *
 * @param items a map with the key/value pairs in the map.
 * @param indefiniteLength whether the map is to be encoded or was encoded using indefinite length.
 */
class CborMap(
    val items: MutableMap<DataItem, DataItem>,
    val indefiniteLength: Boolean = false
) : DataItem(MajorType.MAP) {
    override fun encode(builder: ByteStringBuilder) {
        if (indefiniteLength) {
            val majorTypeShifted = (majorType.type shl 5)
            builder.append((majorTypeShifted + 31).toByte())
            for ((keyItem, valueItem) in items) {
                keyItem.encode(builder)
                valueItem.encode(builder)
            }
            builder.append(0xff.toByte())
        } else {
            Cbor.encodeLength(builder, majorType, items.size)
            for ((keyItem, valueItem) in items) {
                keyItem.encode(builder)
                valueItem.encode(builder)
            }
        }
    }

    companion object {
        /**
         * Creates a new builder.
         *
         * @return a [MapBuilder], call [MapBuilder.end] when done adding items to get a
         * [CborBuilder].
         */
        fun builder(): MapBuilder<CborBuilder> {
            val dataItem = CborMap(mutableMapOf())
            return MapBuilder(CborBuilder(dataItem), dataItem)
        }

        internal fun decode(encodedCbor: ByteArray, offset: Int): Pair<Int, CborMap> {
            val lowBits = encodedCbor[offset].toInt().and(0x1f)
            if (lowBits == 31) {
                // indefinite length
                var cursor = offset + 1
                val items = mutableMapOf<DataItem, DataItem>()
                while (true) {
                    if (encodedCbor[cursor].toInt().and(0xff) == 0xff) {
                        // BREAK code, we're done
                        cursor += 1
                        break
                    }
                    val (nextItemOffset, keyItem) = Cbor.decode(encodedCbor, cursor)
                    val (nextItemOffset2, valueItem) = Cbor.decode(encodedCbor, nextItemOffset)
                    items.put(keyItem, valueItem)
                    check(nextItemOffset2 > cursor)
                    cursor = nextItemOffset2
                }
                return Pair(cursor, CborMap(items, true))
            } else {
                var (cursor, numItems) = Cbor.decodeLength(encodedCbor, offset)
                val items = mutableMapOf<DataItem, DataItem>()
                if (numItems == 0UL) {
                    return Pair(cursor, CborMap(mutableMapOf()))
                }
                for (n in IntRange(0, numItems.toInt() - 1)) {
                    val (nextItemOffset, keyItem) = Cbor.decode(encodedCbor, cursor)
                    val (nextItemOffset2, valueItem) = Cbor.decode(encodedCbor, nextItemOffset)
                    items.put(keyItem, valueItem)
                    check(nextItemOffset2 > cursor)
                    cursor = nextItemOffset2
                }
                return Pair(cursor, CborMap(items))
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (!(other is CborMap)) {
            return false
        }
        if (other.items.size != items.size) {
            return false
        }
        for ((key, value) in items) {
            val otherValue = other.items[key]
            if (!value.equals(otherValue)) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 0
        for (item in items) {
            result = 31*result + item.hashCode()
        }
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder("CborMap(")
        if (indefiniteLength) {
            sb.append("_ ")
        }
        var first = true
        for ((key, value) in items) {
            if (!first) {
                sb.append(", ")
            }
            first = false
            sb.append("$key -> $value")
        }
        sb.append(")")
        return sb.toString()
    }

}