package org.multipaz.cbor

import org.multipaz.cose.CoseKey
import org.multipaz.cose.CoseSign1
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.CoseMac0
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.securearea.KeyAttestation
import org.multipaz.securearea.fromDataItem
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.io.bytestring.ByteStringBuilder

/**
 * Abstract base class for CBOR data items.
 *
 * Instances of [DataItem] are immutable and implement the [equals], [hashCode], and [toString]
 * methods.
 *
 * To assist with parsing, a number of helper properties are included on this class
 * to access the data using specific types. These include [asBstr], [asTstr], [asNumber]
 * etc. for primitive types and also include higher-level types such as [asCoseSign1]
 * to get a [CoseSign1].
 *
 * CBOR maps and CBOR arrays can be accessed using the [get] method which is also available via
 * the bracket operator. Note that [get] will throw if the requested key isn't available in the
 * map. The [hasKey], [getOrDefault], or [getOrNull] methods can be used in situations where the
 * key is optional.
 *
 * @param majorType the CBOR major type of the data item.
 */
sealed class DataItem(
    val majorType: MajorType
) {
    internal abstract fun encode(builder: ByteStringBuilder)

    /**
     * The value of a [Bstr] data item.
     *
     * @throws IllegalArgumentException if not the data item isn't of type [Bstr].
     */
    val asBstr: ByteArray
        get() {
            require(this is Bstr)
            return value
        }

    /**
     * The value of a [Tstr] data item.
     *
     * @throws IllegalArgumentException if not the data item isn't of type [Tstr].
     */
    val asTstr: String
        get() {
            require(this is Tstr)
            return value
        }

    /**
     * The value of a [Uint] or [Nint] data item.
     *
     * @throws IllegalArgumentException if not the data item isn't of type [Uint] or [Nint].
     */
    val asNumber: Long
        get() {
            when (this) {
                is Uint -> return value.toLong()
                is Nint -> return -value.toLong()
                else -> {
                    throw IllegalArgumentException("Data item is not Uint or Nint")
                }
            }
        }

    /**
     * The value of a [Double] data item.
     *
     * @throws IllegalArgumentException if not the data item isn't of type [Double].
     */
    val asDouble: Double
        get() {
            require(this is CborDouble)
            return value
        }

    /**
     * The value of a [Float] data item.
     *
     * @throws IllegalArgumentException if not the data item isn't of type [Float].
     */
    val asFloat: Float
        get() {
            require(this is CborFloat)
            return value
        }

    /**
     * The value of a [Simple] data item containing either true/false.
     *
     * @throws IllegalArgumentException if not the data item isn't of type [Simple] containing
     * either true/false.
     */
    val asBoolean: Boolean
        get() {
            when (this) {
                is Simple -> {
                    return when (value) {
                        Simple.TRUE.value -> true
                        Simple.FALSE.value -> false
                        else -> {
                            throw IllegalArgumentException("Unexpected simple value $value")
                        }
                    }
                }
                else -> {
                    throw IllegalArgumentException("Data item is not a Simple")
                }
            }
        }

    /**
     * The value of a [CborMap] data item.
     *
     * @throws IllegalArgumentException if not the data item isn't of type [CborMap].
     */
    val asMap: Map<DataItem, DataItem>
        get() {
            require(this is CborMap)
            return this.items
        }

    /**
     * The value of a [CborArray] data item.
     *
     * @throws IllegalArgumentException if not the data item isn't of type [CborArray].
     */
    val asArray: List<DataItem>
        get() {
            require(this is CborArray)
            return this.items
        }

    /**
     * The value of a [Tagged] data item.
     *
     * @throws IllegalArgumentException if not the data item isn't of type [Tagged].
     */
    val asTagged: DataItem
        get() {
            require(this is Tagged)
            return this.taggedItem
        }

    /**
     * The decoded CBOR from a bstr with tag [Tagged.ENCODED_CBOR].
     *
     * @throws IllegalArgumentException if the data item isn't a tag with tag [Tagged.ENCODED_CBOR]
     * containing a bstr with valid CBOR.
     */
    val asTaggedEncodedCbor: DataItem
        get() {
            require(this is Tagged)
            require(this.tagNumber == Tagged.ENCODED_CBOR)
            val child = this.taggedItem
            require(child is Bstr)
            return Cbor.decode(child.value)
        }

    /**
     * The date-time from a tstr with tag [Tagged.DATE_TIME_STRING].
     *
     * @throws IllegalArgumentException if the data item isn't a tag with tag [Tagged.DATE_TIME_STRING]
     * containing a tstr with valid date-time format according to RFC 3339.
     */
    val asDateTimeString: Instant
        get() {
            require(this is Tagged)
            require(this.tagNumber == Tagged.DATE_TIME_STRING)
            require(this.taggedItem is Tstr)
            return Instant.parse(this.taggedItem.value)
        }

    /**
     * The date-time from a tstr with tag [Tagged.FULL_DATE_STRING].
     *
     * @throws IllegalArgumentException if the data item isn't a tag with tag [Tagged.FULL_DATE_STRING]
     * containing a tstr with valid date format.
     */
    val asDateString: LocalDate
        get() {
            require(this is Tagged)
            require(this.tagNumber == Tagged.FULL_DATE_STRING)
            require(this.taggedItem is Tstr)
            return LocalDate.parse(this.taggedItem.value)
        }

    /**
     * Returns whether a map has a value for a given key
     *
     * @param key the key to check for.
     * @return whether the key exists in the map.
     * @throws IllegalArgumentException if the data item isn't a [CborMap].
     */
    fun hasKey(key: DataItem): Boolean {
        require(this is CborMap)
        return items.get(key) != null
    }

    /**
     * Returns whether a map has a value for a given key
     *
     * @param key the key to check for.
     * @return whether the key exists in the map.
     * @throws IllegalArgumentException if the data item isn't a [CborMap].
     */
    fun hasKey(key: String): Boolean {
        require(this is CborMap)
        return items.get(key.toDataItem()) != null
    }

    /**
     * Returns whether a map has a value for a given key
     *
     * @param key the key to check for.
     * @return whether the key exists in the map.
     * @throws IllegalArgumentException if the data item isn't a [CborMap].
     */
    fun hasKey(key: Long): Boolean {
        require(this is CborMap)
        return items.get(key.toDataItem()) != null
    }

    /**
     * Gets the value of a key in a map.
     *
     * @param key the key to get the value for.
     * @return the [DataItem] for the given key.
     * @throws IllegalArgumentException if the data item isn't a [CborMap].
     * @throws IllegalStateException if the value doesn't exist in the map.
     */
    operator fun get(key: DataItem): DataItem {
        require(this is CborMap)
        val value = items.get(key)
        if (value == null) {
            throw IllegalStateException("Key $key doesn't exist in map")
        }
        return value
    }

    /**
     * Gets the value of a key in a map.
     *
     * @param key the key to get the value for.
     * @return the [DataItem] for the given key.
     * @throws IllegalArgumentException if the data item isn't a [CborMap].
     * @throws IllegalStateException if the value doesn't exist in the map.
     */
    operator fun get(key: String): DataItem {
        return get(key.toDataItem())
    }

    /**
     * Gets the value of a key in a map or an index in an array
     *
     * @param key the key or index to get the value for.
     * @return the [DataItem] for the given key or index.
     * @throws IllegalArgumentException if the data item isn't a [CborMap] or [CborArray].
     * @throws IllegalStateException if the value doesn't exist in the map.
     * @throws IndexOutOfBoundsException if an array and the index is out of bounds.
     */
    operator fun get(key: Long): DataItem {
        return when (this) {
            is CborArray -> {
                items[key.toInt()]
            }
            else -> {
                get(key.toDataItem())
            }
        }
    }

    /**
     * Gets the value of a key in a map or a default value if the key doesn't exist.
     *
     * @param key the key to get the value for.
     * @param defaultValue the value to return if the key doesn't exist in the map.
     * @return the [DataItem] for the given key or the default value.
     * @throws IllegalArgumentException if the data item isn't a [CborMap].
     */
    fun getOrDefault(key: DataItem, defaultValue: DataItem): DataItem {
        require(this is CborMap)
        val value = items.get(key)
        if (value == null) {
            return defaultValue
        }
        return value
    }

    /**
     * Gets the value of a key in a map or a default value if the key doesn't exist.
     *
     * @param key the key to get the value for.
     * @param defaultValue the value to return if the key doesn't exist in the map.
     * @return the [DataItem] for the given key or the default value.
     * @throws IllegalArgumentException if the data item isn't a [CborMap].
     */
    fun getOrDefault(key: String, defaultValue: DataItem): DataItem {
        require(this is CborMap)
        val value = items.get(key.toDataItem())
        if (value == null) {
            return defaultValue
        }
        return value
    }

    /**
     * Gets the value of a key in a map or a default value if the key doesn't exist.
     *
     * @param key the key to get the value for.
     * @param defaultValue the value to return if the key doesn't exist in the map.
     * @return the [DataItem] for the given key or the default value.
     * @throws IllegalArgumentException if the data item isn't a [CborMap].
     */
    fun getOrDefault(key: Long, defaultValue: DataItem): DataItem {
        require(this is CborMap)
        val value = items.get(key.toDataItem())
        if (value == null) {
            return defaultValue
        }
        return value
    }

    /**
     * Gets the value of a key in a map or null if the key doesn't exist.
     *
     * @param key the key to get the value for.
     * @return the [DataItem] for the given key or null if the key doesn't exist.
     * @throws IllegalArgumentException if the data item isn't a [CborMap].
     */
    fun getOrNull(key: DataItem): DataItem? {
        require(this is CborMap)
        return items.get(key)
    }

    /**
     * Gets the value of a key in a map or null if the key doesn't exist.
     *
     * @param key the key to get the value for.
     * @return the [DataItem] for the given key or null if the key doesn't exist.
     * @throws IllegalArgumentException if the data item isn't a [CborMap].
     */
    fun getOrNull(key: String): DataItem? {
        require(this is CborMap)
        return items.get(key.toDataItem())
    }

    /**
     * Gets the value of a key in a map or null if the key doesn't exist.
     *
     * @param key the key to get the value for.
     * @return the [DataItem] for the given key or null if the key doesn't exist.
     * @throws IllegalArgumentException if the data item isn't a [CborMap].
     */
    fun getOrNull(key: Long): DataItem? {
        require(this is CborMap)
        return items.get(key.toDataItem())
    }

    /**
     * The value of a data item containing a COSE_Key.
     *
     * This is equivalent to calling [CoseKey.fromDataItem].
     *
     * @throws IllegalArgumentException if not the data item isn't a COSE_Key.
     */
    val asCoseKey: CoseKey
        get() = CoseKey.fromDataItem(this)

    /**
     * The value of a data item containing a COSE_Sign1.
     *
     * This is equivalent to calling [CoseSign1.fromDataItem].
     *
     * @throws IllegalArgumentException if not the data item isn't a COSE_Sign1.
     */
    val asCoseSign1: CoseSign1
        get() = CoseSign1.fromDataItem(this)

    /**
     * The value of a data item containing a COSE_Mac0.
     *
     * This is equivalent to calling [CoseMac0.fromDataItem].
     *
     * @throws IllegalArgumentException if not the data item isn't a COSE_Sign1.
     */
    val asCoseMac0: CoseMac0
        get() = CoseMac0.fromDataItem(this)

    /**
     * The value of a data item containing a COSE label (either number or string)
     *
     * This is equivalent to calling [CoseLabel.fromDataItem]
     *
     * @throws IllegalArgumentException if not the data item isn't a COSE label
     */
    val asCoseLabel: CoseLabel
        get() = CoseLabel.fromDataItem(this)

    /**
     * The value of a data item containing a certificate.
     *
     * This is equivalent to calling [X509Cert.fromDataItem].
     *
     * @throws IllegalArgumentException if not the data item isn't a certificate.
     */
    val asX509Cert: X509Cert
        get() = X509Cert.fromDataItem(this)

    /**
     * The value of a data item containing a certificate chain.
     *
     * This is equivalent to calling [X509CertChain.fromDataItem].
     *
     * @throws IllegalArgumentException if not the data item isn't a certificate chain.
     */
    val asX509CertChain: X509CertChain
        get() = X509CertChain.fromDataItem(this)
}
