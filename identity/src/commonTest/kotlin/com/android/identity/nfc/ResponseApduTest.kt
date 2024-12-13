package com.android.identity.nfc

import com.android.identity.util.fromHex
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
                ResponseApdu(noPayload, 0x9000)
            ),
            Pair(
                "6a82",
                ResponseApdu(noPayload, 0x6a82)
            ),

            Pair(
                payloadAsString + "9000",
                ResponseApdu(payload, 0x9000)
            ),
            Pair(
                payloadAsString + "6a82",
                ResponseApdu(payload, 0x6a82)
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