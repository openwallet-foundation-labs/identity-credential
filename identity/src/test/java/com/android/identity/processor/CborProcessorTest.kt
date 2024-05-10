package com.android.identity.processor

import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Tstr
import org.junit.Assert
import org.junit.Test
import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseKey
import com.android.identity.cose.toCoseLabel
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import com.android.identity.util.fromHex
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

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
    val floatField: Float,
    val doubleField: Double,
    val booleanField: Boolean,
    val stringField: String,
    val nullableField: String?,
    val variantField: Variant,
    var nullableVariantField: Variant?,
    val dataItemField: DataItem,
    val instantField: Instant,
    val dateField: LocalDate,
    val enumField: TestEnum,
    val mapField: Map<String, Variant>?,
    val listField: List<Variant>?,
) {
    companion object
}

@CborSerializable
class CoseContainer(val coseKey:CoseKey) {
    companion object
}

const val ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X =
    "96313d6c63e24e3372742bfdb1a33ba2c897dcd68ab8c753e4fbd48dca6b7f9a"
const val ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y =
    "1fb3269edd418857de1b39a4e4a44b92fa484caa722c228288f01d0c03a2c3d6"


// TODO: add more tests once the CBOR annotation processor takes shape
class ProcessorTest {
    @Test
    fun roundtrip_simple() {
        val original = Container(
            intField = Int.MIN_VALUE,
            longField = Long.MAX_VALUE,
            floatField = Math.PI.toFloat(),
            doubleField = Math.E,
            booleanField = true,
            stringField = "foobar",
            nullableField = null,
            variantField = VariantString("variant"),
            nullableVariantField = null,
            dataItemField = Tstr("tstr"),
            instantField = Instant.parse("1969-07-20T20:17:00Z"),
            dateField = LocalDate.parse("1961-04-12"),
            enumField = TestEnum.TWO,
            mapField = mapOf(
                "string" to VariantString("bar"),
                "boolean" to VariantBoolean(false)
            ),
            listField = listOf(VariantLong(Long.MIN_VALUE)))
        val roundtripped = Container.fromDataItem(original.toDataItem)
        Assert.assertEquals(original, roundtripped)
    }

    @Test
    fun roundtrip_cose() {
        val key = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_X.fromHex,
            ISO_18013_5_ANNEX_D_STATIC_DEVICE_KEY_Y.fromHex
        )
        val coseKey = key.toCoseKey(
            mapOf(Cose.COSE_KEY_KID.toCoseLabel to "name@example.com".toByteArray().toDataItem)
        )
        val original = CoseContainer(coseKey)
        val roundtripped = CoseContainer.fromDataItem(original.toDataItem)
        Assert.assertEquals(original.coseKey.ecPublicKey, roundtripped.coseKey.ecPublicKey)
    }
}