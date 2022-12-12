package com.android.identity;

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DoublePrecisionFloat;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.SimpleValueType;
import co.nstant.in.cbor.model.UnsignedInteger;

public class CborUtilTest {

    @Test
    @SmallTest
    public void testDiagnosticsString() {
        Assert.assertEquals("\"foobar\"",
                CborUtil.toDiagnostics(Util.cborEncodeString("foobar")));
    }

    @Test
    @SmallTest
    public void testDiagnosticsStringEscapingWorks() {
        Assert.assertEquals("\"foobar \\\" foobar\"",
                CborUtil.toDiagnostics(Util.cborEncodeString("foobar \" foobar")));
    }

    @Test
    @SmallTest
    public void testDiagnosticsByteString() {
        Assert.assertEquals("h'0102f0'",
                CborUtil.toDiagnostics(Util.cborEncodeBytestring(new byte[]{0x01, 0x02, (byte) 0xf0})));
    }

    @Test
    @SmallTest
    public void testDiagnosticsNumber() {
        Assert.assertEquals("42", CborUtil.toDiagnostics(Util.cborEncodeNumber(42)));
        Assert.assertEquals("-42", CborUtil.toDiagnostics(Util.cborEncodeNumber(-42)));
    }

    @Test
    @SmallTest
    public void testDiagnosticsFloat() {
        assertEquals("1.1", CborUtil.toDiagnostics(new DoublePrecisionFloat(1.1)));
        assertEquals("-42.0000000001", CborUtil.toDiagnostics(new DoublePrecisionFloat(-42.0000000001)));
        assertEquals("-5", CborUtil.toDiagnostics(new DoublePrecisionFloat(-5)));
    }

    @Test
    @SmallTest
    public void testDiagnosticsSpecial() {
        assertEquals("false", CborUtil.toDiagnostics(new SimpleValue(SimpleValueType.FALSE)));
        assertEquals("true", CborUtil.toDiagnostics(new SimpleValue(SimpleValueType.TRUE)));
        assertEquals("null", CborUtil.toDiagnostics(new SimpleValue(SimpleValueType.NULL)));
        assertEquals("undefined", CborUtil.toDiagnostics(new SimpleValue(SimpleValueType.UNDEFINED)));
        assertEquals("reserved", CborUtil.toDiagnostics(new SimpleValue(SimpleValueType.RESERVED)));
        assertEquals("simple(42)", CborUtil.toDiagnostics(new SimpleValue(42)));
    }

    @Test
    @SmallTest
    public void testDiagnosticsArray() {
        assertEquals("[1, \"text\", h'010203']",
                CborUtil.toDiagnostics(new CborBuilder()
                        .addArray()
                        .add(1)
                        .add("text")
                        .add(new ByteString(new byte[]{1, 2, 3}))
                        .end()
                        .build().get(0)));
    }

    @Test
    @SmallTest
    public void testDiagnosticsArrayPrettyNonCompound() {
        assertEquals("[1, 2, 3]",
                CborUtil.toDiagnostics(new CborBuilder()
                        .addArray()
                        .add(1)
                        .add(2)
                        .add(3)
                        .end()
                        .build().get(0),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT));
    }

    @Test
    @SmallTest
    public void testDiagnosticsArrayPrettyWithCompound() {
        assertEquals("[\n" +
                        "  1,\n" +
                        "  2,\n" +
                        "  3,\n" +
                        "  [\"foo\", \"bar\"]\n" +
                        "]",
                CborUtil.toDiagnostics(new CborBuilder()
                                .addArray()
                                .add(1)
                                .add(2)
                                .add(3)
                                .addArray()
                                .add("foo")
                                .add("bar")
                                .end()
                                .end()
                                .build().get(0),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT));
    }

    @Test
    @SmallTest
    public void testDiagnosticsMap() {
        assertEquals("{\"foo\": 1, \"bar\": \"text\", 42: h'010203'}",
                CborUtil.toDiagnostics(new CborBuilder()
                        .addMap()
                        .put("foo", 1)
                        .put("bar", "text")
                        .put(new UnsignedInteger(42), new ByteString(new byte[]{1, 2, 3}))
                        .end()
                        .build().get(0)));
    }

    @Test
    @SmallTest
    public void testDiagnosticsMapPretty() {
        assertEquals("{\n" +
                        "  \"foo\": 1,\n" +
                        "  \"bar\": \"text\",\n" +
                        "  42: h'010203'\n" +
                        "}",
                CborUtil.toDiagnostics(new CborBuilder()
                        .addMap()
                        .put("foo", 1)
                        .put("bar", "text")
                        .put(new UnsignedInteger(42), new ByteString(new byte[]{1, 2, 3}))
                        .end()
                        .build().get(0),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT));
    }

    @Test
    @SmallTest
    public void testDiagnosticsTagged() {
        assertEquals("0(\"2013-03-21T20:04:00Z\")",
                CborUtil.toDiagnostics(new CborBuilder()
                        .add("2013-03-21T20:04:00Z").tagged(0)
                        .build().get(0)));
    }

    @Test
    @SmallTest
    public void testDiagnosticsEmbeddedCborOff() {
        assertEquals("24(h'66666f6f626172')",
                CborUtil.toDiagnostics(new CborBuilder()
                        .add(Util.cborEncodeString("foobar")).tagged(24)
                        .build().get(0)));
    }

    @Test
    @SmallTest
    public void testDiagnosticsEmbeddedCborOn() {
        assertEquals("24(<< \"foobar\" >>)",
                CborUtil.toDiagnostics(new CborBuilder()
                        .add(Util.cborEncodeString("foobar")).tagged(24)
                        .build().get(0), CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
    }

    @Test
    @SmallTest
    public void testDiagnosticsTestVector() {
        assertEquals(
                "{0: \"1.0\", 1: [1, 24(<< {1: 2, -1: 1, -2: h'5a88d182bce5f42efa59943f33359d2e8a968ff289d93e5fa444b624343167fe', -3: h'b16e8cf858ddc7690407ba61d4c338237a8cfcf3de6aa672fc60a557aa32fc67'} >>)], 2: [[2, 1, {0: false, 1: true, 11: h'45efef742b2c4837a9a3b0e1d05a6917'}]]}",
                CborUtil.toDiagnostics(
                        Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_ENGAGEMENT),
                        CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
    }

    @Test
    @SmallTest
    public void testDiagnosticsTestVectorPretty() {
        assertEquals(
                "{\n" +
                        "  0: \"1.0\",\n" +
                        "  1: [1, 24(h'a4010220012158205a88d182bce5f42efa59943f33359d2e8a968ff289d93e5fa444b624343167fe225820b16e8cf858ddc7690407ba61d4c338237a8cfcf3de6aa672fc60a557aa32fc67')],\n" +
                        "  2: [\n" +
                        "    [\n" +
                        "      2,\n" +
                        "      1,\n" +
                        "      {\n" +
                        "        0: false,\n" +
                        "        1: true,\n" +
                        "        11: h'45efef742b2c4837a9a3b0e1d05a6917'\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  ]\n" +
                        "}",
                CborUtil.toDiagnostics(
                        Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_ENGAGEMENT),
                        CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT));
    }

    @Test
    @SmallTest
    public void testDiagnosticsTestVectorPrettyAndWithEmbedded() {
        assertEquals(
                "{\n" +
                        "  0: \"1.0\",\n" +
                        "  1: [\n" +
                        "    1,\n" +
                        "    24(<< {\n" +
                        "      1: 2,\n" +
                        "      -1: 1,\n" +
                        "      -2: h'5a88d182bce5f42efa59943f33359d2e8a968ff289d93e5fa444b624343167fe',\n" +
                        "      -3: h'b16e8cf858ddc7690407ba61d4c338237a8cfcf3de6aa672fc60a557aa32fc67'\n" +
                        "    } >>)\n" +
                        "  ],\n" +
                        "  2: [\n" +
                        "    [\n" +
                        "      2,\n" +
                        "      1,\n" +
                        "      {\n" +
                        "        0: false,\n" +
                        "        1: true,\n" +
                        "        11: h'45efef742b2c4837a9a3b0e1d05a6917'\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  ]\n" +
                        "}",
                CborUtil.toDiagnostics(
                        Util.fromHex(TestVectors.ISO_18013_5_ANNEX_D_DEVICE_ENGAGEMENT),
                        CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR |
                                CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT));
    }

    @Test
    @SmallTest
    public void testDiagnosticsInvalidCbor() {
        // Make sure we don't throw if data to print isn't valid CBOR
        assertEquals("Error Decoding CBOR",
                CborUtil.toDiagnostics(new byte[] {(byte) 0x83, 0x01, 0x02}));
    }

    @Test
    @SmallTest
    public void testDiagnosticsEmbeddedInvalidCbor() {
        // Make sure we don't throw if embedded CBOR to print isn't valid CBOR
        assertEquals("24(<< Error Decoding CBOR >>)",
                CborUtil.toDiagnostics(new CborBuilder()
                        .add(new byte[] {(byte) 0x83, 0x01, 0x02}).tagged(24)
                        .build().get(0),
                        CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
    }

}