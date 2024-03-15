package com.android.identity.processor

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Tstr
import org.junit.Assert
import org.junit.Test
import com.android.identity.cbor.annotation.CborSerializable

enum class TestEnum {
    ONE,
    TWO,
    THREE
}

@CborSerializable
sealed class Variant {
    companion object
}

data class VariantLong(val value: Long) : Variant()
data class VariantBoolean(val value: Boolean) : Variant()
data class VariantString(val value: String) : Variant()

@CborSerializable
data class Container(
    val intField: Int,
    val longField: Long,
    val booleanField: Boolean,
    val stringField: String,
    val nullableField: String?,
    val variantField: Variant,
    val dataItemField: DataItem,
    val enumField: TestEnum,
    val mapField: Map<String, Variant>?,
    val listField: List<Variant>?
) {
    companion object
}

// TODO: add more tests once the CBOR annotation processor takes shape
class ProcessorTests {
    @Test
    fun roundtrip_simple() {
        val original = Container(
            intField = Int.MIN_VALUE,
            longField = Long.MAX_VALUE,
            booleanField = true,
            stringField = "foobar",
            nullableField = null,
            variantField = VariantString("variant"),
            dataItemField = Tstr("tstr"),
            enumField = TestEnum.TWO,
            mapField = mapOf(
                "string" to VariantString("bar"),
                "boolean" to VariantBoolean(false)
            ),
            listField = listOf(VariantLong(Long.MIN_VALUE)))
        val roundtripped = Container.fromCborDataItem(original.toCborDataItem())
        Assert.assertEquals(original, roundtripped)
    }
}