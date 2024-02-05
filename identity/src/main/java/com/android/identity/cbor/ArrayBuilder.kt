package com.android.identity.cbor

/**
 * Array builder.
 */
class ArrayBuilder<T>(private val parent: T, private val array: CborArray) {
    /**
     * Adds a new data item.
     *
     * @param item the item to add.
     * @return the builder.
     */
    fun add(item: DataItem): ArrayBuilder<T> {
        array.items.add(item)
        return this
    }

    /**
     * Adds a tagged data item.
     *
     * @param tagNumber the number of the tag to use.
     * @param taggedItem the item to add.
     * @return the builder.
     */
    fun addTagged(tagNumber: Long, taggedItem: DataItem): ArrayBuilder<T> {
        array.items.add(Tagged(tagNumber, taggedItem))
        return this
    }

    /**
     * Adds a tagged bstr with encoded CBOR.
     *
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun addTaggedEncodedCbor(encodedCbor: ByteArray): ArrayBuilder<T> {
        array.items.add(Tagged(Tagged.ENCODED_CBOR, Bstr(encodedCbor)))
        return this
    }

    /**
     * Adds a new map.
     *
     * This returns a new [MapBuilder], when done adding items to the map
     * [MapBuilder.end] should be called to get the current builder back.
     *
     * @return a builder for the map.
     */
    fun addMap(): MapBuilder<ArrayBuilder<T>> {
        val map = CborMap(mutableMapOf())
        add(map)
        return MapBuilder(this, map)
    }

    /**
     * Adds a new array.
     *
     * This returns a new [ArrayBuilder], when done adding items to the array,
     * [ArrayBuilder.end] should be called to get the current builder back.
     *
     * @return a builder for the array.
     */
    fun addArray(): ArrayBuilder<ArrayBuilder<T>> {
        val array = CborArray(mutableListOf())
        add(array)
        return ArrayBuilder(this, array)
    }

    /**
     * Ends building the array
     *
     * @return the containing builder.
     */
    fun end(): T {
        return parent
    }

    // Convenience adders

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: ByteArray): ArrayBuilder<T> {
        add(value.dataItem)
        return this
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: String): ArrayBuilder<T> {
        add(value.dataItem)
        return this
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Byte): ArrayBuilder<T> {
        add(value.dataItem)
        return this
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Short): ArrayBuilder<T> {
        add(value.dataItem)
        return this
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Int): ArrayBuilder<T> {
        add(value.dataItem)
        return this
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Long): ArrayBuilder<T> {
        add(value.dataItem)
        return this
    }

    /**
     * Adds a boolean.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Boolean): ArrayBuilder<T> {
        add(value.dataItem)
        return this
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Double): ArrayBuilder<T> {
        add(value.dataItem)
        return this
    }


    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Float): ArrayBuilder<T> {
        add(value.dataItem)
        return this
    }
}
