package com.android.identity.cbor

import com.android.identity.util.fromHex
import kotlinx.datetime.Instant
import org.junit.Assert
import org.junit.Test

class CborTests {

    private fun cborToString(item: DataItem) = Cbor.toDiagnostics(
        item,
        setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
    )

    private fun encodeNumber(value: Long) = Cbor.toDiagnostics(
        Cbor.encode(value.toDataItem),
        setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
    )

    private fun encodeFloat(value: Float) = Cbor.toDiagnostics(
        Cbor.encode(value.toDataItem),
        setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
    )

    private fun encodeDouble(value: Double) = Cbor.toDiagnostics(
        Cbor.encode(value.toDataItem),
        setOf(DiagnosticOption.EMBEDDED_CBOR, DiagnosticOption.PRETTY_PRINT)
    )

    @Test
    fun basic() {
        Assert.assertEquals(
            "[\"tstr\", h'010203fe', false, true, 42, -42]",
            cborToString(
                CborArray.builder()
                    .add("tstr")
                    .add(byteArrayOf(1, 2, 3, 254.toByte()))
                    .add(false)
                    .add(true)
                    .add(42)
                    .add(-42)
                    .end()
                    .build()
            )
        )
    }

    @Test
    fun numbers() {
        Assert.assertEquals("0", encodeNumber(0))
        Assert.assertEquals("23", encodeNumber(23))
        Assert.assertEquals("24", encodeNumber(24))
        Assert.assertEquals("255", encodeNumber(0xff))
        Assert.assertEquals("256", encodeNumber(0x100))
        Assert.assertEquals("257", encodeNumber(0x101))
        Assert.assertEquals("65535", encodeNumber(0xffff))
        Assert.assertEquals("65536", encodeNumber(0x10000))
        Assert.assertEquals("65537", encodeNumber(0x10001))
        Assert.assertEquals("4294967295", encodeNumber(0xffffffff))
        Assert.assertEquals("4294967296", encodeNumber(0x100000000))
        Assert.assertEquals("4294967297", encodeNumber(0x100000001))
        Assert.assertEquals("-23", encodeNumber(-23))
        Assert.assertEquals("-24", encodeNumber(-24))
        Assert.assertEquals("-255", encodeNumber(-0xff))
        Assert.assertEquals("-256", encodeNumber(-0x100))
        Assert.assertEquals("-257", encodeNumber(-0x101))
        Assert.assertEquals("-65535", encodeNumber(-0xffff))
        Assert.assertEquals("-65536", encodeNumber(-0x10000))
        Assert.assertEquals("-65537", encodeNumber(-0x10001))
        Assert.assertEquals("-4294967295", encodeNumber(-0xffffffff))
        Assert.assertEquals("-4294967296", encodeNumber(-0x100000000))
        Assert.assertEquals("-4294967297", encodeNumber(-0x100000001))
    }

    @Test
    fun floats() {
        Assert.assertEquals("0.0", encodeFloat(0.0f))
        Assert.assertEquals("0.1", encodeFloat(0.1f))
        Assert.assertEquals("42.0", encodeFloat(42.0f))
        Assert.assertEquals("42.1", encodeFloat(42.1f))
        Assert.assertEquals("1.4E-45", encodeFloat(Float.MIN_VALUE))
        Assert.assertEquals("3.4028235E38", encodeFloat(Float.MAX_VALUE))
        Assert.assertEquals("NaN", encodeFloat(Float.NaN))
        Assert.assertEquals("-Infinity", encodeFloat(Float.NEGATIVE_INFINITY))
        Assert.assertEquals("Infinity", encodeFloat(Float.POSITIVE_INFINITY))
    }

    @Test
    fun doubles() {
        Assert.assertEquals("0.0", encodeDouble(0.0))
        Assert.assertEquals("0.1", encodeDouble(0.1))
        Assert.assertEquals("42.0", encodeDouble(42.0))
        Assert.assertEquals("42.1", encodeDouble(42.1))
        Assert.assertEquals("4.9E-324", encodeDouble(Double.MIN_VALUE))
        Assert.assertEquals("1.7976931348623157E308", encodeDouble(Double.MAX_VALUE))
        Assert.assertEquals("NaN", encodeDouble(Double.NaN))
        Assert.assertEquals("-Infinity", encodeDouble(Double.NEGATIVE_INFINITY))
        Assert.assertEquals("Infinity", encodeDouble(Double.POSITIVE_INFINITY))
    }

    @Test
    fun array() {
        Assert.assertEquals(
            "[\"tstr\", h'010203fe', false, true, 42, -42]",
            cborToString(
                CborArray.builder()
                    .add("tstr")
                    .add(byteArrayOf(1, 2, 3, 254.toByte()))
                    .add(false)
                    .add(true)
                    .add(42)
                    .add(-42)
                    .end()
                    .build()
            )
        )
    }

    @Test
    fun arrayWithMap() {
        Assert.assertEquals(
            "[\n" +
                    "  \"tstr\",\n" +
                    "  h'010203fe',\n" +
                    "  false,\n" +
                    "  true,\n" +
                    "  42,\n" +
                    "  -42,\n" +
                    "  {\n" +
                    "    \"foo\": \"bar\",\n" +
                    "    \"baz\": \"bang\"\n" +
                    "  }\n" +
                    "]",
            cborToString(
                CborArray.builder()
                    .add("tstr")
                    .add(byteArrayOf(1, 2, 3, 254.toByte()))
                    .add(false)
                    .add(true)
                    .add(42)
                    .add(-42)
                    .addMap()
                    .put("foo", "bar")
                    .put("baz", "bang")
                    .end()
                    .end()
                    .build()
            )
        )
    }

    @Test
    fun arrayWithArray() {
        Assert.assertEquals(
            "[\n" +
                    "  \"tstr\",\n" +
                    "  h'010203fe',\n" +
                    "  false,\n" +
                    "  true,\n" +
                    "  42,\n" +
                    "  -42,\n" +
                    "  [\"first\", \"second\"]\n" +
                    "]",
            cborToString(
                CborArray.builder()
                    .add("tstr")
                    .add(byteArrayOf(1, 2, 3, 254.toByte()))
                    .add(false)
                    .add(true)
                    .add(42)
                    .add(-42)
                    .addArray()
                    .add("first")
                    .add("second")
                    .end()
                    .end()
                    .build()
            )
        )
    }

    @Test
    fun map() {
        Assert.assertEquals(
            "{\n" +
                    "  \"a\": h'010203fe',\n" +
                    "  \"b\": false,\n" +
                    "  \"c\": true,\n" +
                    "  \"d\": 42,\n" +
                    "  \"e\": -42\n" +
                    "}",
            cborToString(
                CborMap.builder()
                    .put("a", "tstr")
                    .put("a", byteArrayOf(1, 2, 3, 254.toByte()))
                    .put("b", false)
                    .put("c", true)
                    .put("d", 42)
                    .put("e", -42)
                    .end()
                    .build()
            )
        )
    }

    @Test
    fun mapWithArray() {
        Assert.assertEquals(
            "{\n" +
                    "  \"a\": h'010203fe',\n" +
                    "  \"b\": false,\n" +
                    "  \"c\": true,\n" +
                    "  \"d\": 42,\n" +
                    "  \"e\": -42,\n" +
                    "  \"f\": [\"first\", \"second\"]\n" +
                    "}",
            cborToString(
                CborMap.builder()
                    .put("a", "tstr")
                    .put("a", byteArrayOf(1, 2, 3, 254.toByte()))
                    .put("b", false)
                    .put("c", true)
                    .put("d", 42)
                    .put("e", -42)
                    .putArray("f")
                    .add("first")
                    .add("second")
                    .end()
                    .end()
                    .build()
            )
        )
    }

    @Test
    fun mapWithMaps() {
        Assert.assertEquals(
            "{\n" +
                    "  \"a\": h'010203fe',\n" +
                    "  \"b\": false,\n" +
                    "  \"c\": true,\n" +
                    "  \"d\": 42,\n" +
                    "  \"e\": -42,\n" +
                    "  \"f\": {\n" +
                    "    \"aa\": \"foo\",\n" +
                    "    \"ab\": \"bar\"\n" +
                    "  }\n" +
                    "}",
            cborToString(
                CborMap.builder()
                    .put("a", "tstr")
                    .put("a", byteArrayOf(1, 2, 3, 254.toByte()))
                    .put("b", false)
                    .put("c", true)
                    .put("d", 42)
                    .put("e", -42)
                    .putMap("f")
                    .put("aa", "foo")
                    .put("ab", "bar")
                    .end()
                    .end()
                    .build()
            )
        )
    }

    @Test
    fun tags() {
        Assert.assertEquals(
            "1(1707165181)",
            cborToString(Tagged(Tagged.DATE_TIME_NUMBER, Uint(1707165181UL)))
        )

        Assert.assertEquals(
            "0(\"2024-02-05T20:33:01Z\")",
            cborToString(Tagged(Tagged.DATE_TIME_STRING, Tstr("2024-02-05T20:33:01Z")))
        )
    }

    @Test
    fun tagsInArraysAndMaps() {
        Assert.assertEquals(
            "[\n" +
                    "  1(1707165181),\n" +
                    "  0(\"2024-02-05T20:33:01Z\"),\n" +
                    "  {\n" +
                    "    \"a\": 1(1707165181),\n" +
                    "    \"b\": 0(\"2024-02-05T20:33:01Z\")\n" +
                    "  }\n" +
                    "]",
            cborToString(
                CborArray.builder()
                    .addTagged(Tagged.DATE_TIME_NUMBER, Uint(1707165181UL))
                    .addTagged(Tagged.DATE_TIME_STRING, Tstr("2024-02-05T20:33:01Z"))
                    .addMap()
                    .putTagged("a", Tagged.DATE_TIME_NUMBER, Uint(1707165181UL))
                    .putTagged("b", Tagged.DATE_TIME_STRING, Tstr("2024-02-05T20:33:01Z"))
                    .end()
                    .end()
                    .build()
            )
        )
    }

    @Test
    fun tagEncodedCbor() {
        Assert.assertEquals(
            "[\n" +
                    "  1(1707165181),\n" +
                    "  24(<< [\"first\", \"second\"] >>)\n" +
                    "]",
            cborToString(
                CborArray.builder()
                    .addTagged(Tagged.DATE_TIME_NUMBER, Uint(1707165181UL))
                    .addTagged(
                        Tagged.ENCODED_CBOR, Bstr(
                            Cbor.encode(
                                CborArray.builder()
                                    .add("first")
                                    .add("second")
                                    .end()
                                    .build()
                            )
                        )
                    )
                    .end()
                    .build()
            )
        )
    }

    @Test
    fun rawCborItem() {
        Assert.assertEquals(
            "[\n" +
                    "  [\"first\", \"second\"],\n" +
                    "  \"some tstr\",\n" +
                    "  \"Sprinkled in item\",\n" +
                    "  42\n" +
                    "]",
            cborToString(
                CborArray.builder()
                    .add(
                        RawCbor(
                            Cbor.encode(
                                CborArray.builder()
                                    .add("first")
                                    .add("second")
                                    .end()
                                    .build()
                            )
                        )
                    )
                    .add(RawCbor(Cbor.encode("some tstr".toDataItem)))
                    .add("Sprinkled in item")
                    .add(RawCbor(Cbor.encode(42.toDataItem)))
                    .end()
                    .build()
            )
        )
    }

    @Test
    fun indefiniteLengthBstr() {
        Assert.assertEquals(
            "(_ h'010203', h'040506')",
            cborToString(IndefLengthBstr(listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))))
        )
    }

    @Test
    fun indefiniteLengthTstr() {
        Assert.assertEquals(
            "(_ \"hello \", \"world\")",
            cborToString(IndefLengthTstr(listOf("hello ", "world")))
        )
    }

    @Test
    fun indefiniteLengthArray() {
        Assert.assertEquals(
            "[_ \"foo\", \"bar\"]",
            cborToString(CborArray(mutableListOf("foo".toDataItem, "bar".toDataItem), true))
        )
    }

    @Test
    fun indefiniteLengthMap() {
        Assert.assertEquals(
            "{_\n" +
                    "  \"foo\": \"fooVal\",\n" +
                    "  \"bar\": \"barVar\"\n" +
                    "}",
            cborToString(
                CborMap(
                    mutableMapOf(
                        Pair("foo".toDataItem, "fooVal".toDataItem),
                        Pair("bar".toDataItem, "barVar".toDataItem)
                    ),
                    true
                )
            )
        )
    }

    private fun encodeAndDecodeNumber(value: Long): String {
        val encodedData = Cbor.encode(value.toDataItem)
        return cborToString(Cbor.decode(encodedData))
    }

    fun assertEncodeDecode(item: DataItem) {
        val encodedData = Cbor.encode(item)
        val decodedItem = Cbor.decode(encodedData)
        Assert.assertEquals(decodedItem, item)
    }

    @Test
    fun decodeBasic() {
        assertEncodeDecode("tstr".toDataItem)
        assertEncodeDecode(byteArrayOf(1, 2, 3).toDataItem)
        assertEncodeDecode(42.toDataItem)
        assertEncodeDecode((-42).toDataItem)
        assertEncodeDecode(true.toDataItem)
        assertEncodeDecode(false.toDataItem)
        assertEncodeDecode(Simple.NULL)
        assertEncodeDecode(Simple.UNDEFINED)
        assertEncodeDecode(0.0.toDataItem)
        assertEncodeDecode(42.0.toDataItem)
        assertEncodeDecode((-42.1).toDataItem)
        assertEncodeDecode(Double.MIN_VALUE.toDataItem)
        assertEncodeDecode(Double.MAX_VALUE.toDataItem)
        assertEncodeDecode(Double.NaN.toDataItem)
        assertEncodeDecode(Double.NEGATIVE_INFINITY.toDataItem)
        assertEncodeDecode(Double.POSITIVE_INFINITY.toDataItem)
        assertEncodeDecode(0.0f.toDataItem)
        assertEncodeDecode(42.0f.toDataItem)
        assertEncodeDecode((-42.1f).toDataItem)
        assertEncodeDecode(Float.MIN_VALUE.toDataItem)
        assertEncodeDecode(Float.MAX_VALUE.toDataItem)
        assertEncodeDecode(Float.NaN.toDataItem)
        assertEncodeDecode(Float.NEGATIVE_INFINITY.toDataItem)
        assertEncodeDecode(Float.POSITIVE_INFINITY.toDataItem)
    }

    @Test
    fun decodeNumbers() {
        Assert.assertEquals("0", encodeAndDecodeNumber(0))
        Assert.assertEquals("23", encodeAndDecodeNumber(23))
        Assert.assertEquals("24", encodeAndDecodeNumber(24))
        Assert.assertEquals("255", encodeAndDecodeNumber(0xff))
        Assert.assertEquals("256", encodeAndDecodeNumber(0x100))
        Assert.assertEquals("257", encodeAndDecodeNumber(0x101))
        Assert.assertEquals("65535", encodeAndDecodeNumber(0xffff))
        Assert.assertEquals("65536", encodeAndDecodeNumber(0x10000))
        Assert.assertEquals("65537", encodeAndDecodeNumber(0x10001))
        Assert.assertEquals("4294967295", encodeAndDecodeNumber(0xffffffff))
        Assert.assertEquals("4294967296", encodeAndDecodeNumber(0x100000000))
        Assert.assertEquals("4294967297", encodeAndDecodeNumber(0x100000001))
        Assert.assertEquals("-23", encodeAndDecodeNumber(-23))
        Assert.assertEquals("-24", encodeAndDecodeNumber(-24))
        Assert.assertEquals("-255", encodeAndDecodeNumber(-0xff))
        Assert.assertEquals("-256", encodeAndDecodeNumber(-0x100))
        Assert.assertEquals("-257", encodeAndDecodeNumber(-0x101))
        Assert.assertEquals("-65535", encodeAndDecodeNumber(-0xffff))
        Assert.assertEquals("-65536", encodeAndDecodeNumber(-0x10000))
        Assert.assertEquals("-65537", encodeAndDecodeNumber(-0x10001))
        Assert.assertEquals("-4294967295", encodeAndDecodeNumber(-0xffffffff))
        Assert.assertEquals("-4294967296", encodeAndDecodeNumber(-0x100000000))
        Assert.assertEquals("-4294967297", encodeAndDecodeNumber(-0x100000001))
    }

    @Test
    fun decodeArray() {
        assertEncodeDecode(
            CborArray.builder()
                .add("foo")
                .add("bar")
                .end()
                .build()
        )
    }

    @Test
    fun decodeMap() {
        assertEncodeDecode(
            CborMap.builder()
                .put("foo", "a")
                .put("bar", "b")
                .end()
                .build()
        )
    }

    @Test
    fun decodeTags() {
        assertEncodeDecode(
            CborArray.builder()
                .addTagged(Tagged.DATE_TIME_NUMBER, Uint(1707165181UL))
                .addTagged(Tagged.DATE_TIME_STRING, Tstr("2024-02-05T20:33:01Z"))
                .end()
                .build()
        )
    }

    @Test
    fun decodeRawCbor() {
        // This is a bit special because when we decode we never create any RawCborItem instances
        val itemForRaw = CborArray.builder()
            .add("first")
            .add("second")
            .end()
            .build()
        val encodedData = Cbor.encode(
            CborArray.builder()
                .add(RawCbor(Cbor.encode(itemForRaw)))
                .add("a string")
                .end().build()
        )
        val decodedItem = Cbor.decode(encodedData)

        val expectedItem = CborArray.builder()
            .add(itemForRaw)
            .add("a string")
            .end()
            .build()
        Assert.assertEquals(expectedItem, decodedItem)
    }

    private fun encHalfFloat(first: Int, second: Int): ByteArray {
        return byteArrayOf(
            ((MajorType.SPECIAL.type shl 5) + 25).toByte(),
            first.toByte(),
            second.toByte()
        )
    }

    // https://en.wikipedia.org/wiki/Half-precision_floating-point_format#Half_precision_examples
    @Test
    fun decodeHalfFloats() {
        Assert.assertEquals(0.0f, (Cbor.decode(encHalfFloat(0x00, 0x00)) as CborFloat).value)
        Assert.assertEquals(
            0.000000059604645f,
            (Cbor.decode(encHalfFloat(0x00, 0x01)) as CborFloat).value
        )
        Assert.assertEquals(
            0.000060975552f,
            (Cbor.decode(encHalfFloat(0x03, 0xff)) as CborFloat).value
        )
        Assert.assertEquals(0.33325195f, (Cbor.decode(encHalfFloat(0x35, 0x55)) as CborFloat).value)
        Assert.assertEquals(0.99951172f, (Cbor.decode(encHalfFloat(0x3b, 0xff)) as CborFloat).value)
        Assert.assertEquals(1.0f, (Cbor.decode(encHalfFloat(0x3c, 0x00)) as CborFloat).value)
        Assert.assertEquals(1.0009766f, (Cbor.decode(encHalfFloat(0x3c, 0x01)) as CborFloat).value)
        Assert.assertEquals(65504.0f, (Cbor.decode(encHalfFloat(0x7b, 0xff)) as CborFloat).value)
        Assert.assertEquals(
            Float.POSITIVE_INFINITY,
            (Cbor.decode(encHalfFloat(0x7c, 0x00)) as CborFloat).value
        )
        Assert.assertEquals(-0.0f, (Cbor.decode(encHalfFloat(0x80, 0x00)) as CborFloat).value)
        Assert.assertEquals(-2.0f, (Cbor.decode(encHalfFloat(0xc0, 0x00)) as CborFloat).value)
        Assert.assertEquals(
            Float.NEGATIVE_INFINITY,
            (Cbor.decode(encHalfFloat(0xfc, 0x00)) as CborFloat).value
        )
    }

    @Test
    fun itemToString() {
        Assert.assertEquals("Bstr(0102037f)", Bstr(byteArrayOf(1, 2, 3, 0x7f)).toString())
        Assert.assertEquals("Tstr(\"foo\")", Tstr("foo").toString())
        Assert.assertEquals("Uint(42)", Uint(42UL).toString())
        Assert.assertEquals("Nint(42)", Nint(42UL).toString())
        Assert.assertEquals("Simple(FALSE)", Simple.FALSE.toString())
        Assert.assertEquals("Simple(TRUE)", Simple.TRUE.toString())
        Assert.assertEquals("Simple(UNDEFINED)", Simple.UNDEFINED.toString())
        Assert.assertEquals("Simple(NULL)", Simple.NULL.toString())
        Assert.assertEquals("Simple(19)", Simple(19U).toString())
        Assert.assertEquals(
            "TaggedItem(42, Tstr(\"bar\"))",
            Tagged(42L, Tstr("bar")).toString()
        )
        Assert.assertEquals(
            "CborArray(Tstr(\"a\"), Tstr(\"b\"), Nint(5))",
            CborArray.builder()
                .add("a")
                .add("b")
                .add(-5)
                .end()
                .build()
                .toString()
        )
        Assert.assertEquals(
            "CborMap(Tstr(\"a\") -> Tstr(\"foo\"), Nint(5) -> Tstr(\"baz\"))",
            CborMap.builder()
                .put("a", "foo")
                .put(-5, "baz")
                .end()
                .build()
                .toString()
        )
    }

    data class TestVector(
        val expectedDiagnostics: String,
        val hexEncoding: String
    )

    // This is from https://www.rfc-editor.org/rfc/rfc8949.html#name-examples-of-encoded-cbor-da
    // and modified to work with the specifics of our implementation of diagnostics
    //
    private val diagnosticsTestVectors = listOf(
        TestVector("0", "00"),
        TestVector("1", "01"),
        TestVector("10", "0a"),
        TestVector("23", "17"),
        TestVector("24", "1818"),
        TestVector("25", "1819"),
        TestVector("100", "1864"),
        TestVector("1000", "1903e8"),
        TestVector("1000000", "1a000f4240"),
        TestVector("1000000000000", "1b000000e8d4a51000"),
        // TODO: add bignum support, from Appendix A:
        //  In the diagnostic notation provided for bignums, their intended numeric value is
        //  shown as a decimal number (such as 18446744073709551616) instead of a tagged byte
        //  string (such as 2(h'010000000000000000')).
        //TestVector("18446744073709551615", "1bffffffffffffffff"),
        //TestVector("18446744073709551616", "c249010000000000000000"),
        //TestVector("-18446744073709551616", "3bffffffffffffffff"),
        //TestVector("-18446744073709551617", "c349010000000000000000"),
        TestVector("-1", "20"),
        TestVector("-10", "29"),
        TestVector("-100", "3863"),
        TestVector("-1000", "3903e7"),
        TestVector("0.0", "f90000"),
        TestVector("-0.0", "f98000"),
        TestVector("1.0", "f93c00"),
        TestVector("1.1", "fb3ff199999999999a"),
        TestVector("1.5", "f93e00"),
        TestVector("65504.0", "f97bff"),
        TestVector("100000.0", "fa47c35000"),
        TestVector("3.4028235E38", "fa7f7fffff"),
        TestVector("1.0E300", "fb7e37e43c8800759c"),
        TestVector("5.9604645E-8", "f90001"),
        TestVector("6.1035156E-5", "f90400"),
        TestVector("-4.0", "f9c400"),
        TestVector("-4.1", "fbc010666666666666"),
        TestVector("Infinity", "f97c00"),
        TestVector("NaN", "f97e00"),
        TestVector("-Infinity", "f9fc00"),
        TestVector("Infinity", "fa7f800000"),
        TestVector("NaN", "fa7fc00000"),
        TestVector("-Infinity", "faff800000"),
        TestVector("Infinity", "fb7ff0000000000000"),
        TestVector("NaN", "fb7ff8000000000000"),
        TestVector("-Infinity", "fbfff0000000000000"),
        TestVector("false", "f4"),
        TestVector("true", "f5"),
        TestVector("null", "f6"),
        TestVector("undefined", "f7"),
        TestVector("simple(16)", "f0"),
        TestVector("simple(255)", "f8ff"),
        TestVector("0(\"2013-03-21T20:04:00Z\")", "c074323031332d30332d32315432303a30343a30305a"),
        TestVector("1(1363896240)", "c11a514b67b0"),
        TestVector("1(1.3638962405E9)", "c1fb41d452d9ec200000"),
        TestVector("23(h'01020304')", "d74401020304"),
        TestVector("24(<< \"IETF\" >>)", "d818456449455446"),
        TestVector(
            "32(\"http://www.example.com\")",
            "d82076687474703a2f2f7777772e6578616d706c652e636f6d"
        ),
        TestVector("h''", "40"),
        TestVector("h'01020304'", "4401020304"),
        TestVector("\"\"", "60"),
        TestVector("\"a\"", "6161"),
        TestVector("\"IETF\"", "6449455446"),
        TestVector("\"\\\"\\\\\"", "62225c"),
        TestVector("\"\u00fc\"", "62c3bc"),
        TestVector("\"\u6c34\"", "63e6b0b4"),
        TestVector("\"\ud800\udd51\"", "64f0908591"),
        TestVector("[]", "80"),
        TestVector("[1, 2, 3]", "83010203"),
        TestVector("[1, [2, 3], [4, 5]]", "8301820203820405"),
        TestVector(
            "[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25]",
            "98190102030405060708090a0b0c0d0e0f101112131415161718181819"
        ),
        TestVector("{}", "a0"),
        TestVector("{1: 2, 3: 4}", "a201020304"),
        TestVector("{\"a\": 1, \"b\": [2, 3]}", "a26161016162820203"),
        TestVector("[\"a\", {\"b\": \"c\"}]", "826161a161626163"),
        TestVector(
            "{\"a\": \"A\", \"b\": \"B\", \"c\": \"C\", \"d\": \"D\", \"e\": \"E\"}",
            "a56161614161626142616361436164614461656145"
        ),
        TestVector("(_ h'0102', h'030405')", "5f42010243030405ff"),
        TestVector("(_ \"strea\", \"ming\")", "7f657374726561646d696e67ff"),
        TestVector("[_ ]", "9fff"),
        TestVector("[_ 1, [2, 3], [_ 4, 5]]", "9f018202039f0405ffff"),
        TestVector("[_ 1, [2, 3], [4, 5]]", "9f01820203820405ff"),
        TestVector("[1, [2, 3], [_ 4, 5]]", "83018202039f0405ff"),
        TestVector("[1, [_ 2, 3], [4, 5]]", "83019f0203ff820405"),
        TestVector(
            "[_ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25]",
            "9f0102030405060708090a0b0c0d0e0f101112131415161718181819ff"
        ),
        TestVector("{_ \"a\": 1, \"b\": [_ 2, 3]}", "bf61610161629f0203ffff"),
        TestVector("[\"a\", {_ \"b\": \"c\"}]", "826161bf61626163ff"),
        TestVector("{_ \"Fun\": true, \"Amt\": -2}", "bf6346756ef563416d7421ff")
    )

    @Test
    fun diagnosticsVectors() {
        for (testVector in diagnosticsTestVectors) {
            val encodedCbor = testVector.hexEncoding.fromHex
            Assert.assertEquals(testVector.expectedDiagnostics, Cbor.toDiagnostics(encodedCbor))
        }
    }

    // These are from https://www.rfc-editor.org/rfc/rfc8949.html#name-examples-of-cbor-data-items
    private val nonWellformedExamples = listOf(
        // End of input in a head:
        "18", "19", "1a", "1b", "19 01", "1a 01 02", "1b 01 02 03 04 05 06 07", "38", "58",
        "78", "98", "9a 01 ff 00", "b8", "d8", "f8", "f9 00", "fa 00 00", "fb 00 00 00",

        // Definite-length strings with short data:
        "41", "61", "5a ff ff ff ff 00", "5b ff ff ff ff ff ff ff ff 01 02 03",
        "7a ff ff ff ff 00", "7b 7f ff ff ff ff ff ff ff 01 02 03",

        // Definite-length maps and arrays not closed with enough items:
        "81", "81 81 81 81 81 81 81 81 81", "82 00", "a1", "a2 01 02", "a1 00", "a2 00 00 00",

        // Tag number not followed by tag content:
        "c0",

        // Indefinite-length strings not closed by a "break" stop code:
        "5f 41 00", "7f 61 00",

        // Indefinite-length maps and arrays not closed by a "break" stop code:
        "9f", "9f 01 02", "bf", "bf 01 02 01 02", "81 9f", "9f 80 00",
        "9f 9f 9f 9f 9f ff ff ff ff", "9f 81 9f 81 9f 9f ff ff ff",

        // A few examples for the five subkinds of well-formedness error kind 3 (syntax error)
        // are shown below.
        //
        // Subkind 1:
        // Reserved additional information values:
        "1c", "1d", "1e", "3c", "3d", "3e", "5c", "5d", "5e", "7c", "7d", "7e", "9c", "9d", "9e",
        "bc", "bd", "be", "dc", "dd", "de", "fc", "fd", "fe",

        // Subkind 2:
        // Reserved two-byte encodings of simple values:
        "f8 00", "f8 01", "f8 18", "f8 1f",

        // Subkind 3:
        // Indefinite-length string chunks not of the correct type:
        "5f 00 ff", "5f 21 ff", "5f 61 00 ff", "5f 80 ff", "5f a0 ff", "5f c0 00 ff", "5f e0 ff",
        "7f 41 00 ff",
        // Indefinite-length string chunks not definite length:
        "5f 5f 41 00 ff ff", "7f 7f 61 00 ff ff",

        // Subkind 4:
        // Break occurring on its own outside of an indefinite-length item:
        "ff",
        // Break occurring in a definite-length array or map or a tag:
        "81 ff", "82 00 ff", "a1 ff", "a1 ff 00", "a1 00 ff", "a2 00 00 ff", "9f 81 ff",
        "9f 82 9f 81 9f 9f ff ff ff ff",
        // Break in an indefinite-length map that would lead to an odd number of items (break in a
        // value position):
        "bf 00 ff", "bf 00 00 00 ff",
        // Subkind 5:
        // Major type 0, 1, 6 with additional information 31:
        "1f", "3f", "df",
    )

    @Test
    fun nonWellformedThrowsWhenDecoding() {
        for (hexEncodedData in nonWellformedExamples) {
            val data = hexEncodedData.replace(" ", "").fromHex
            Assert.assertThrows(IllegalArgumentException::class.java) {
                Cbor.decode(data)
            }
        }
    }

    // ---

    @Test
    fun parseHelperBasic() {
        Assert.assertArrayEquals(byteArrayOf(1, 42), byteArrayOf(1, 42).toDataItem.asBstr)
        Assert.assertEquals("Tstr", "Tstr".toDataItem.asTstr)
        Assert.assertEquals(42, 42.toDataItem.asNumber)
        Assert.assertEquals(-35, (-35).toDataItem.asNumber)
        Assert.assertEquals(42.0, 42.0.toDataItem.asDouble, 0.01)
        Assert.assertEquals(43.0f, 43.0f.toDataItem.asFloat, 0.01f)
        Assert.assertEquals(true, true.toDataItem.asBoolean)
        Assert.assertEquals(false, false.toDataItem.asBoolean)

        Assert.assertThrows(IllegalArgumentException::class.java) { 42.toDataItem.asBstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { 42.toDataItem.asTstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { 4.0.toDataItem.asNumber }
        Assert.assertThrows(IllegalArgumentException::class.java) { 42.toDataItem.asDouble }
        Assert.assertThrows(IllegalArgumentException::class.java) { 42.toDataItem.asFloat }
        Assert.assertThrows(IllegalArgumentException::class.java) { 42.toDataItem.asBoolean }
    }

    @Test
    fun parseHelperMap() {
        val map = CborMap.builder()
            .put("foo0", "Tstr".toDataItem)
            .put("foo1", byteArrayOf(1, 2, 3))
            .put("foo2", 42)
            .put("foo3", -35)
            .put("foo4", 42.0)
            .put("foo5", 43.0f)
            .put("foo6", true)
            .put("foo7", false)
            .end().build()
        Assert.assertEquals("Tstr", map["foo0"].asTstr)
        Assert.assertArrayEquals(byteArrayOf(1, 2, 3), map["foo1"].asBstr)
        Assert.assertEquals(42, map["foo2"].asNumber)
        Assert.assertEquals(-35, map["foo3"].asNumber)
        Assert.assertEquals(42.0, map["foo4"].asDouble, 0.01)
        Assert.assertEquals(43.0f, map["foo5"].asFloat, 0.01f)
        Assert.assertEquals(true, map["foo6"].asBoolean)
        Assert.assertEquals(false, map["foo7"].asBoolean)

        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo1"].asTstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo2"].asTstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo3"].asTstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo4"].asTstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo5"].asTstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo6"].asTstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo7"].asTstr }

        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo0"].asBstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo2"].asBstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo3"].asBstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo4"].asBstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo5"].asBstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo6"].asBstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo7"].asBstr }

        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo0"].asNumber }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo1"].asNumber }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo4"].asNumber }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo5"].asNumber }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo6"].asNumber }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo7"].asNumber }

        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo0"].asDouble }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo1"].asDouble }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo2"].asDouble }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo3"].asDouble }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo5"].asDouble }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo6"].asDouble }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo7"].asDouble }

        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo0"].asFloat }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo1"].asFloat }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo2"].asFloat }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo3"].asFloat }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo4"].asFloat }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo6"].asFloat }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo7"].asFloat }

        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo0"].asBoolean }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo1"].asBoolean }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo2"].asBoolean }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo3"].asBoolean }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo4"].asBoolean }
        Assert.assertThrows(IllegalArgumentException::class.java) { map["foo5"].asBoolean }
    }

    @Test
    fun parseHelperArray() {
        val array = CborArray.builder()
            .add("Tstr".toDataItem)
            .add(byteArrayOf(1, 2, 3))
            .add(42)
            .add(-35)
            .add(42.0)
            .add(43.0f)
            .add(true)
            .add(false)
            .end().build()

        Assert.assertEquals("Tstr", array[0].asTstr)
        Assert.assertArrayEquals(byteArrayOf(1, 2, 3), array[1].asBstr)
        Assert.assertEquals(42, array[2].asNumber)
        Assert.assertEquals(-35, array[3].asNumber)
        Assert.assertEquals(42.0, array[4].asDouble, 0.01)
        Assert.assertEquals(43.0f, array[5].asFloat, 0.01f)
        Assert.assertEquals(true, array[6].asBoolean)
        Assert.assertEquals(false, array[7].asBoolean)

        Assert.assertThrows(IllegalArgumentException::class.java) { array[1].asTstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { array[0].asBstr }
        Assert.assertThrows(IllegalArgumentException::class.java) { array[0].asNumber }
        Assert.assertThrows(IllegalArgumentException::class.java) { array[0].asDouble }
        Assert.assertThrows(IllegalArgumentException::class.java) { array[0].asFloat }
        Assert.assertThrows(IllegalArgumentException::class.java) { array[0].asBoolean }
    }

    @Test
    fun mapLookupException() {
        val map = CborMap.builder()
            .put("foo0", "Tstr".toDataItem)
            .end().build()
        Assert.assertEquals("Tstr", map["foo0"].asTstr)
        Assert.assertThrows(IllegalStateException::class.java) { map["foo1"] }
    }

    @Test
    fun mapGetOrDefault() {
        val map = CborMap.builder()
            .put("foo0", "Tstr".toDataItem)
            .end().build()
        Assert.assertEquals("Tstr", map.getOrDefault("foo0", "dTstr".toDataItem).asTstr)
        Assert.assertEquals("dTstr", map.getOrDefault("foo1", "dTstr".toDataItem).asTstr)
    }

    @Test
    fun mapGetOrNull() {
        val map = CborMap.builder()
            .put("foo0", "Tstr".toDataItem)
            .end().build()
        Assert.assertEquals("Tstr".toDataItem, map.getOrNull("foo0"))
        Assert.assertEquals(null, map.getOrNull("foo1"))
    }

    @Test
    fun arrayLookupException() {
        val array = CborArray.builder()
            .add("Tstr".toDataItem)
            .add("OtherTstr".toDataItem)
            .end().build()
        Assert.assertEquals("Tstr", array[0].asTstr)
        Assert.assertEquals("OtherTstr", array[1].asTstr)
        Assert.assertThrows(IndexOutOfBoundsException::class.java) { array[2] }
    }

    @Test
    fun parseHelperTag() {
        val innerDataItem = CborArray.builder()
            .add("first")
            .add("second")
            .end()
            .build()
        val dataItem = Tagged(Tagged.ENCODED_CBOR, Bstr(Cbor.encode(innerDataItem)))
        Assert.assertEquals(innerDataItem, dataItem.asTaggedEncodedCbor)
    }

    @Test
    fun toDateTimeString() {
        Assert.assertEquals(
            "0(\"1970-01-01T00:00:00Z\")",
            Cbor.toDiagnostics(Instant.fromEpochMilliseconds(0).toDataItemDateTimeString))

        Assert.assertEquals(
            "0(\"2001-09-09T01:46:40Z\")",
            Cbor.toDiagnostics(Instant.fromEpochMilliseconds(1000000000000).toDataItemDateTimeString))
    }
}