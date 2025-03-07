package org.multipaz.nfc

import org.multipaz.util.fromHex
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class CommandApduTest {

    @Test
    fun encodeDecode() {
        val noPayload = ByteString(byteArrayOf())
        val shortPayload = ByteString(byteArrayOf(1, 2, 3))
        val shortPayloadAsString = shortPayload.toHexString()
        val longPayload = ByteString(ByteArray(0x123) { 65 })
        val longPayloadAsString = longPayload.toHexString()

        val pairs: List<Pair<String, CommandApdu>> = listOf(
            // No payload, varying LE field
            Pair(
                "00810708",
                CommandApdu(cla = 0, ins = 129, p1 = 7, p2 = 8, payload = noPayload, le = 0x0000)
            ),
            Pair(
                "0081070801",
                CommandApdu(cla = 0, ins = 129, p1 = 7, p2 = 8, payload = noPayload, le = 0x0001)
            ),
            Pair(
                "00810708ff",
                CommandApdu(cla = 0, ins = 129, p1 = 7, p2 = 8, payload = noPayload, le = 0x00ff)
            ),
            Pair(
                "0081070800",
                CommandApdu(cla = 0, ins = 129, p1 = 7, p2 = 8, payload = noPayload, le = 0x0100)
            ),
            Pair(
                "00810708000101",
                CommandApdu(cla = 0, ins = 129, p1 = 7, p2 = 8, payload = noPayload, le = 0x0101)
            ),
            Pair(
                "0081070800ffff",
                CommandApdu(cla = 0, ins = 129, p1 = 7, p2 = 8, payload = noPayload, le = 0xffff)
            ),
            Pair(
                "00810708000000",
                CommandApdu(cla = 0, ins = 129, p1 = 7, p2 = 8, payload = noPayload, le = 0x10000)
            ),

            // Short payload, varying LE field
            Pair(
                "0082050603" + shortPayloadAsString,
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = shortPayload, le = 0x0000)
            ),
            Pair(
                "0082050603" + shortPayloadAsString + "01",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = shortPayload, le = 0x0001)
            ),
            Pair(
                "0082050603" + shortPayloadAsString + "ff",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = shortPayload, le = 0x00ff)
            ),
            Pair(
                "0082050603" + shortPayloadAsString + "00",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = shortPayload, le = 0x0100)
            ),
            Pair(
                "00820506000003" + shortPayloadAsString + "0101",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = shortPayload, le = 0x0101)
            ),
            Pair(
                "00820506000003" + shortPayloadAsString + "ffff",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = shortPayload, le = 0xffff)
            ),
            Pair(
                "00820506000003" + shortPayloadAsString + "0000",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = shortPayload, le = 0x10000)
            ),

            // Long payload, varying LE field
            Pair(
                "00820506000123" + longPayloadAsString,
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = longPayload, le = 0x0000)
            ),
            Pair(
                "00820506000123"  + longPayloadAsString + "0001",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = longPayload, le = 0x0001)
            ),
            Pair(
                "00820506000123"  + longPayloadAsString + "00ff",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = longPayload, le = 0x00ff)
            ),
            Pair(
                "00820506000123"  + longPayloadAsString + "0100",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = longPayload, le = 0x0100)
            ),
            Pair(
                "00820506000123"  + longPayloadAsString + "0101",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = longPayload, le = 0x0101)
            ),
            Pair(
                "00820506000123"  + longPayloadAsString + "ffff",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = longPayload, le = 0xffff)
            ),
            Pair(
                "00820506000123"  + longPayloadAsString + "0000",
                CommandApdu(cla = 0, ins = 130, p1 = 5, p2 = 6, payload = longPayload, le = 0x10000)
            ),
        )

        for ((hexEncoding, commandApdu) in pairs) {
            assertEquals(
                hexEncoding,
                commandApdu.encode().toHexString(),
                "Encoding of $commandApdu is ${commandApdu.encode()} which wasn't expected"
            )
            assertEquals(
                commandApdu,
                CommandApdu.decode(hexEncoding.fromHex()),
                "Decoding of $hexEncoding is ${CommandApdu.decode(hexEncoding.fromHex())} which wasn't expected"
            )
        }
    }
}