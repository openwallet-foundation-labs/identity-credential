package org.multipaz.nfc

import org.multipaz.util.fromHex
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponseApduTest {

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun encodeDecode() {
        val noPayload = ByteString(byteArrayOf())
        val payload = ByteString(ByteArray(0x123) { 65 })
        val payloadAsString = payload.toHexString()

        val pairs: List<Pair<String, ResponseApdu>> = listOf(
            Pair(
                "9000",
                ResponseApdu(Nfc.RESPONSE_STATUS_SUCCESS)
            ),
            Pair(
                "6a82",
                ResponseApdu(0x6a82)
            ),

            Pair(
                payloadAsString + "9000",
                ResponseApdu(Nfc.RESPONSE_STATUS_SUCCESS, payload)
            ),
            Pair(
                payloadAsString + "6a82",
                ResponseApdu(0x6a82, payload)
            ),
        )

        for ((hexEncoding, responseApdu) in pairs) {
            assertEquals(
                hexEncoding,
                responseApdu.encode().toHexString(),
                "Encoding of $responseApdu is ${responseApdu.encode()} which wasn't expected"
            )
            assertEquals(
                responseApdu,
                ResponseApdu.decode(hexEncoding.fromHex()),
                "Decoding of $hexEncoding is ${ResponseApdu.decode(hexEncoding.fromHex())} which wasn't expected"
            )
        }

        assertEquals(0x6a, ResponseApdu.decode("6a82".fromHex()).sw1)
        assertEquals(0x82, ResponseApdu.decode("6a82".fromHex()).sw2)

    }
}