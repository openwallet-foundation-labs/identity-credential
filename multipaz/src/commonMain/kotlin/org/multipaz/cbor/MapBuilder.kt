package org.multipaz.cbor

/**
 * Map builder.
 */
class MapBuilder<T>(private val parent: T, private val map: CborMap) {

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: DataItem, value: DataItem) = apply {
        map.items[key] = value
    }

    /**
     * Ends building the array.
     *
     * @return the containing builder.
     */
    fun end(): T = parent

    /**
     * Puts a new array in the map.
     *
     * This returns a new [ArrayBuilder], when done adding items to the array,
     * [ArrayBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [ArrayBuilder].
     */
    fun putArray(key: DataItem): ArrayBuilder<MapBuilder<T>> {
        val array = CborArray(mutableListOf())
        put(key, array)
        return ArrayBuilder(this, array)
    }

    /**
     * Puts a new map in the map.
     *
     * This returns a new [MapBuilder], when done adding items to the map
     * [MapBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [MapBuilder].
     */
    fun putMap(key: DataItem): MapBuilder<MapBuilder<T>> {
        val map = CborMap(mutableMapOf())
        put(key, map)
        return MapBuilder(this, map)
    }

    /**
     * Puts a tagged data item in the map.
     *
     * @param key the key.
     * @param tagNumber the number of the tag to use.
     * @param taggedItem the item to add.
     * @return the builder.
     */
    fun putTagged(key: DataItem, tagNumber: Long, taggedItem: DataItem) = apply {
        map.items[key] = Tagged(tagNumber, taggedItem)
    }

    /**
     * Puts a tagged bstr with encoded CBOR in the map.
     *
     * @param key the key.
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun putTaggedEncodedCbor(key: DataItem, encodedCbor: ByteArray) = apply {
        putTagged(key, Tagged.ENCODED_CBOR, Bstr(encodedCbor))
    }


    // Convenience putters for String keys

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: DataItem) = apply {
        map.items[key.toDataItem()] = value
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: String) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: ByteArray) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Byte) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Short) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Int) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Long) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Boolean) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Double) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Float) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new array in the map.
     *
     * This returns a new [ArrayBuilder], when done adding items to the array,
     * [ArrayBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [ArrayBuilder].
     */
    fun putArray(key: String): ArrayBuilder<MapBuilder<T>> = putArray(key.toDataItem())

    /**
     * Puts a new map in the map.
     *
     * This returns a new [MapBuilder], when done adding items to the map
     * [MapBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [MapBuilder].
     */
    fun putMap(key: String): MapBuilder<MapBuilder<T>> {
        return putMap(key.toDataItem())
    }

    /**
     * Puts a tagged data item in the map.
     *
     * @param key the key.
     * @param tagNumber the number of the tag to use.
     * @param taggedItem the item to add.
     * @return the builder.
     */
    fun putTagged(key: String, tagNumber: Long, value: DataItem) = apply {
        putTagged(key.toDataItem(), tagNumber, value)
    }

    /**
     * Puts a tagged bstr with encoded CBOR in the map.
     *
     * @param key the key.
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun putTaggedEncodedCbor(key: String, encodedCbor: ByteArray) = apply {
        putTaggedEncodedCbor(key.toDataItem(), encodedCbor)
    }

    // Convenience putters for Long keys

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: DataItem) = apply {
        map.items[key.toDataItem()] = value
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: String) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: ByteArray) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Byte) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Short) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Int) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Long) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Boolean) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Double) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Float) = apply {
        put(key.toDataItem(), value.toDataItem())
    }

    /**
     * Puts a new array in the map.
     *
     * This returns a new [ArrayBuilder], when done adding items to the array,
     * [ArrayBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [ArrayBuilder].
     */
    fun putArray(key: Long): ArrayBuilder<MapBuilder<T>> = putArray(key.toDataItem())

    /**
     * Puts a new map in the map.
     *
     * This returns a new [MapBuilder], when done adding items to the map
     * [MapBuilder.end] should be called to get the current builder back.
     *
     * @param key the key.
     * @return a [MapBuilder].
     */
    fun putMap(key: Long): MapBuilder<MapBuilder<T>> = putMap(key.toDataItem())

    /**
     * Puts a tagged data item in the map.
     *
     * @param key the key.
     * @param tagNumber the number of the tag to use.
     * @param taggedItem the item to add.
     * @return the builder.
     */
    fun putTagged(key: Long, tagNumber: Long, value: DataItem): MapBuilder<T> =
        putTagged(key.toDataItem(), tagNumber, value)


    /**
     * Puts a tagged bstr with encoded CBOR in the map.
     *
     * @param key the key.
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun putTaggedEncodedCbor(key: Long, encodedCbor: ByteArray) = apply {
        putTaggedEncodedCbor(key.toDataItem(), encodedCbor)
    }
}
