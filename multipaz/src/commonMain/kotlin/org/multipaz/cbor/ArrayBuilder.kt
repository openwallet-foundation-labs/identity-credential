package org.multipaz.cbor

/**
 * Array builder.
 */
data class ArrayBuilder<T>(private val parent: T, private val array: CborArray) {
    /**
     * Adds a new data item.
     *
     * @param item the item to add.
     * @return the builder.
     */
    fun add(item: DataItem) = apply {
        array.items.add(item)
    }

    /**
     * Adds a tagged data item.
     *
     * @param tagNumber the number of the tag to use.
     * @param taggedItem the item to add.
     * @return the builder.
     */
    fun addTagged(tagNumber: Long, taggedItem: DataItem) = apply {
        array.items.add(Tagged(tagNumber, taggedItem))
    }

    /**
     * Adds a tagged bstr with encoded CBOR.
     *
     * @param encodedCbor the bytes of the encoded CBOR.
     */
    fun addTaggedEncodedCbor(encodedCbor: ByteArray) = apply {
        array.items.add(Tagged(Tagged.ENCODED_CBOR, Bstr(encodedCbor)))
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
    fun end(): T = parent

    // Convenience adders

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: ByteArray) = apply {
        add(value.toDataItem())
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: String) = apply {
        add(value.toDataItem())
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Byte) = apply {
        add(value.toDataItem())
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Short) = apply {
        add(value.toDataItem())
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Int) = apply {
        add(value.toDataItem())
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Long) = apply {
        add(value.toDataItem())
    }

    /**
     * Adds a boolean.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Boolean) = apply {
        add(value.toDataItem())
    }

    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Double) = apply {
        add(value.toDataItem())
    }


    /**
     * Adds a new value.
     *
     * @param value the value to add.
     * @return the builder.
     */
    fun add(value: Float) = apply {
        add(value.toDataItem())
    }

    /**
     * Checks if the array is empty.
     */
    fun isEmpty(): Boolean = array.items.isEmpty()
}
