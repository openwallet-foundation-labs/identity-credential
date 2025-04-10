package org.multipaz.rpc

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.util.fromBase64Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

enum class Enum1 { A, B, C }

enum class Enum2 { C, B, A }

enum class Enum3 { C, B, A, Z }

@CborSerializable(
    schemaHash = "QTbOW5BOs_M7r4aLEgjxGy6F8FGKtNfiXakQLuAKl_0"
)
internal data class Simple0(val d: Enum1, val b: Long, val c: Set<String>, val a: ByteString) {
    companion object
}

// Field order does not affect structural equivalency and schema hashes.
@CborSerializable(
    schemaHash = "QTbOW5BOs_M7r4aLEgjxGy6F8FGKtNfiXakQLuAKl_0"
)
data class Simple1(val a: ByteString, val b: Long, val c: Set<String>, val d: Enum1) {
    companion object
}

// Structurally equivalent classes have the same schema hashes.
@CborSerializable(
    schemaHash = "QTbOW5BOs_M7r4aLEgjxGy6F8FGKtNfiXakQLuAKl_0"
)
class Simple2(val a: ByteArray, val b: Int, val c: List<String>, val d: Enum2) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Simple2) return false
        return a.contentEquals(other.a) && b == other.b && c == other.c && d == other.d
    }

    override fun hashCode(): Int {
        return 31 * (31 * (31 * a.contentHashCode() + b) + c.hashCode()) + d.hashCode()
    }

    companion object
}

// Nullable (optional) vs non-nullable (required) values affect the schema.
@CborSerializable(
    schemaHash = "tWBm9GBdz8UMH963R8CM6EQ5ZtaBcFXWGcLfeJVdqZw"
)
data class Simple3(val a: ByteString?, val b: Int, val c: List<String>, val d: Enum2) {
    companion object
}

// Enum value set affects the schema.
@CborSerializable(
    schemaHash = "thsN6pcQgLYb0_5UANhkYaMnk3DPyHK3pACppMELzD0"
)
data class Simple4(val a: ByteString?, val b: Int, val c: List<String>, val d: Enum3) {
    companion object
}

// Field names affect the schema.
@CborSerializable(
    schemaHash = "EY5shPCnwtg6QZauhUHW6afxu2IzqMZjwqc3k_Iywck"
)
data class Simple5(val z: ByteString?, val b: Int, val c: List<String>, val d: Enum2) {
    companion object
}

// Self-referential data structures, will cause compilation error without schemaId
@CborSerializable(
    typeKey = "type",
    schemaHash = "LIMaWzbx9GoLnJfTc0bwbTVbKWp5ikeIJFk1tq_SZC4",
    schemaId = "9uoN7E5Qk2s2m0R-b7LrskkMB8Os-mBicFLp11tXRMA"
)
internal sealed class Node {
    companion object
}

internal data class LeafNode(val content: String): Node()

internal data class ContainerNode(val nodes: List<Node>): Node()

class CborSerializationTest {
    @Test
    fun structuralEquivalency() {
        // Types which are structurally equivalent are always compatible.
        val simple0 = Simple0(Enum1.B, 57L, setOf("foo", "bar"), ByteString(3, 7, 1))
        val simple1 = Simple1(ByteString(3, 7, 1), 57L, setOf("foo", "bar"), Enum1.B)
        val simple2 = Simple2(byteArrayOf(3, 7, 1), 57, listOf("foo", "bar"), Enum2.B)
        assertEquals(simple0, Simple0.fromCbor(simple2.toCbor()))
        assertEquals(simple1, Simple1.fromCbor(simple2.toCbor()))
        assertEquals(simple2, Simple2.fromCbor(simple0.toCbor()))
        assertEquals(simple2, Simple2.fromCbor(simple1.toCbor()))
    }

    @Test
    fun structuralCompatibility_nonNullNullable() {
        // Certain changes in schema are compatible: nullable field can take non-null values
        val simple1 = Simple1(ByteString(3, 7, 1), 57L, setOf("foo", "bar"), Enum1.B)
        val simple3 = Simple3(ByteString(3, 7, 1), 57, listOf("foo", "bar"), Enum2.B)
        assertEquals(simple1, Simple1.fromCbor(simple3.toCbor()))
        assertEquals(simple3, Simple3.fromCbor(simple1.toCbor()))
    }

    @Test
    fun structuralCompatibility_nullValuesAreSkipped() {
        // null values are absent in serialization
        val simple3 = Simple3(null, 57, listOf("foo", "bar"), Enum2.B)
        val simple5 = Simple5(null, 57, listOf("foo", "bar"), Enum2.B)
        assertEquals(simple5, Simple5.fromCbor(simple3.toCbor()))
        assertEquals(simple3, Simple3.fromCbor(simple5.toCbor()))
    }

    @Test
    fun structuralCompatibility_enumValueSet() {
        // when possible values are added to an enum, old values still work
        val simple3 = Simple3(ByteString(3, 7, 1), 57, listOf("foo", "bar"), Enum2.B)
        val simple4 = Simple4(ByteString(3, 7, 1), 57, listOf("foo", "bar"), Enum3.B)
        assertEquals(simple4, Simple4.fromCbor(simple3.toCbor()))
        assertEquals(simple3, Simple3.fromCbor(simple4.toCbor()))
    }

    @Test
    fun unknownFieldIgnored() {
        // if a field is not known, it is silently ignored.
        val simple3 = Simple3(null, 57, listOf("foo", "bar"), Enum2.B)
        val simple5 = Simple5(ByteString(3, 7, 1), 57, listOf("foo", "bar"), Enum2.B)
        assertEquals(simple3, Simple3.fromCbor(simple5.toCbor()))
    }

    @Test
    fun unknownEnumValuesFail() {
        val simple4 = Simple4(ByteString(2, 3, 1), 57, listOf("foo", "bar"), Enum3.Z)
        try {
            Simple5.fromCbor(simple4.toCbor())
            fail()
        } catch (err: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun sealed() {
        val containerNode = ContainerNode(
            listOf(
                ContainerNode(
                    listOf(
                        LeafNode("foo"),
                        LeafNode("bar")
                    )
                ),
                LeafNode("baz")
            )
        )
        assertEquals(containerNode, Node.fromCbor(containerNode.toCbor()))
    }

    @Test
    fun schemaIds() {
        // When schemaId is explicitly specified
        assertEquals(Node.cborSchemaId,
            ByteString("9uoN7E5Qk2s2m0R-b7LrskkMB8Os-mBicFLp11tXRMA".fromBase64Url()))
        // When schemaId is not specified, schemaHash is used as id
        assertEquals(Simple5.cborSchemaId,
            ByteString("EY5shPCnwtg6QZauhUHW6afxu2IzqMZjwqc3k_Iywck".fromBase64Url()))
    }
}