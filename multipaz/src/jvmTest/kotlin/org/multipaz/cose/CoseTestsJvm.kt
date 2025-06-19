package org.multipaz.cose

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.cbor.toDataItem
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate

import org.multipaz.crypto.BigIntegersAsUnsignedByteArray
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class CoseTestsJvm {

    // TODO: Move to CommonTest when we have BigInteger support

    @Test
    fun coseKey() {
        // This checks the encoding of X and Y are encoded as specified in
        // Section 2.3.5 Field-Element-to-Octet-String Conversion of
        // SEC 1: Elliptic Curve Cryptography (https://www.secg.org/sec1-v2.pdf).
        assertEquals(
            "{\n" +
                    "  1: 2,\n" +
                    "  -1: 1,\n" +
                    "  -2: h'0000000000000000000000000000000000000000000000000000000000000001',\n" +
                    "  -3: h'0000000000000000000000000000000000000000000000000000000000000001'\n" +
                    "}",
            Cbor.toDiagnostics(
                EcPublicKeyDoubleCoordinate(
                    EcCurve.P256,
                    BigInteger.valueOf(1).sec1EncodeFieldElementAsOctetString(32),
                    BigInteger.valueOf(1).sec1EncodeFieldElementAsOctetString(32)
                ).toCoseKey().toDataItem(),
                setOf(DiagnosticOption.PRETTY_PRINT)
            )
        )
    }

    @Test
    fun coseKeyWithAdditionalLabels() {
        // Check we can add additional labels to a CoseKey
        val key = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            BigInteger.valueOf(1).sec1EncodeFieldElementAsOctetString(32),
            BigInteger.valueOf(1).sec1EncodeFieldElementAsOctetString(32)
        )
        val coseKey = key.toCoseKey(
            mapOf(Pair(Cose.COSE_KEY_KID.toCoseLabel, "name@example.com".toByteArray().toDataItem()))
        )
        assertEquals(
            "{\n" +
                    "  1: 2,\n" +
                    "  -1: 1,\n" +
                    "  -2: h'0000000000000000000000000000000000000000000000000000000000000001',\n" +
                    "  -3: h'0000000000000000000000000000000000000000000000000000000000000001',\n" +
                    "  2: h'6e616d65406578616d706c652e636f6d'\n" +
                    "}",
            Cbor.toDiagnostics(coseKey.toDataItem(), setOf(DiagnosticOption.PRETTY_PRINT))
        )
    }
}

/* Encodes an integer according to Section 2.3.5 Field-Element-to-Octet-String Conversion
 * of SEC 1: Elliptic Curve Cryptography (https://www.secg.org/sec1-v2.pdf).
 */
private fun BigInteger.sec1EncodeFieldElementAsOctetString(octetStringSize: Int): ByteArray {
    return BigIntegersAsUnsignedByteArray(octetStringSize, this)
}
