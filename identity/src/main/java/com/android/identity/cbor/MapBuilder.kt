package com.android.identity.cbor

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
    fun put(key: DataItem, value: DataItem): MapBuilder<T> {
        map.items[key] = value
        return this
    }

    /**
     * Ends building the array.
     *
     * @return the containing builder.
     */
    fun end(): T {
        return parent
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
    fun putTagged(key: DataItem, tagNumber: Long, taggedItem: DataItem): MapBuilder<T> {
        map.items.put(key, Tagged(tagNumber, taggedItem))
        return this
    }

    /**
     * Puts a tagged bstr with encoded CBOR in the map.
     *
     * @param key the key.
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun putTaggedEncodedCbor(key: DataItem, encodedCbor: ByteArray): MapBuilder<T> {
        return putTagged(key, Tagged.ENCODED_CBOR, Bstr(encodedCbor))
    }


    // Convenience putters for String keys

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: DataItem): MapBuilder<T> {
        map.items[key.dataItem] = value
        return this
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: String): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: ByteArray): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Byte): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Short): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Int): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Long): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Boolean): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Double): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: String, value: Float): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
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
    fun putArray(key: String): ArrayBuilder<MapBuilder<T>> {
        return putArray(key.dataItem)
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
    fun putMap(key: String): MapBuilder<MapBuilder<T>> {
        return putMap(key.dataItem)
    }

    /**
     * Puts a tagged data item in the map.
     *
     * @param key the key.
     * @param tagNumber the number of the tag to use.
     * @param taggedItem the item to add.
     * @return the builder.
     */
    fun putTagged(key: String, tagNumber: Long, value: DataItem): MapBuilder<T> {
        return putTagged(key.dataItem, tagNumber, value)
    }

    /**
     * Puts a tagged bstr with encoded CBOR in the map.
     *
     * @param key the key.
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun putTaggedEncodedCbor(key: String, encodedCbor: ByteArray): MapBuilder<T> {
        return putTaggedEncodedCbor(key.dataItem, encodedCbor)
    }

    // Convenience putters for Long keys

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: DataItem): MapBuilder<T> {
        map.items[key.dataItem] = value
        return this
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: String): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: ByteArray): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Byte): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Short): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Int): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Long): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Boolean): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Double): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
    }

    /**
     * Puts a new value in the map
     *
     * @param key the key.
     * @param value the value.
     * @return the builder.
     */
    fun put(key: Long, value: Float): MapBuilder<T> {
        return put(key.dataItem, value.dataItem)
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
    fun putArray(key: Long): ArrayBuilder<MapBuilder<T>> {
        return putArray(key.dataItem)
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
    fun putMap(key: Long): MapBuilder<MapBuilder<T>> {
        return putMap(key.dataItem)
    }

    /**
     * Puts a tagged data item in the map.
     *
     * @param key the key.
     * @param tagNumber the number of the tag to use.
     * @param taggedItem the item to add.
     * @return the builder.
     */
    fun putTagged(key: Long, tagNumber: Long, value: DataItem): MapBuilder<T> {
        return putTagged(key.dataItem, tagNumber, value)
    }

    /**
     * Puts a tagged bstr with encoded CBOR in the map.
     *
     * @param key the key.
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun putTaggedEncodedCbor(key: Long, encodedCbor: ByteArray): MapBuilder<T> {
        return putTaggedEncodedCbor(key.dataItem, encodedCbor)
    }

}
