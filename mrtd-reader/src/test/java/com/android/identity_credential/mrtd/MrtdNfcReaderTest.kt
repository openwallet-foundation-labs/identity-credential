package com.android.identity_credential.mrtd

import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CommandAPDU
import net.sf.scuba.smartcards.ISO7816
import net.sf.scuba.smartcards.ResponseAPDU
import org.jmrtd.Util
import org.jmrtd.protocol.SecureMessagingWrapper
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

class MrtdNfcReaderTest {
    data class Request(val command: CommandAPDU, val wrapper: SecureMessagingWrapper?)

    class MockCardService(
        private val reader: MrtdNfcReader,
        private val responses: List<ResponseAPDU>
    ) : CardService() {
        private var opened = false
        private var secureSendCounter: Long = 0
        val received = ArrayList<Request>()
        private val responseIt = responses.iterator()

        override fun open() {
            opened = true
        }

        override fun isOpen(): Boolean {
            return opened
        }

        override fun transmit(commandAPDU: CommandAPDU?): ResponseAPDU {
            val wrapper = reader.service?.wrapper
            received.add(Request(commandAPDU!!, wrapper))
            val response = responseIt.next()
            val wrapped = if (wrapper != null) {
                wrap(response, wrapper)
            } else {
                response
            }
            return wrapped
        }

        private fun wrap(response: ResponseAPDU, wrapper: SecureMessagingWrapper): ResponseAPDU {
            val cipher = Util.getCipher("DESede/CBC/NoPadding")
            val zeroIV = IvParameterSpec(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
            cipher.init(Cipher.ENCRYPT_MODE, wrapper.encryptionKey, zeroIV)
            val dataLen = response.bytes.size - 2
            val encrypted = if (dataLen == 0) {
                byteArrayOf()
            } else {
                cipher.doFinal(Util.pad(response.bytes, 0, dataLen, 8))
            }
            val buf = ByteArrayOutputStream()
            if (encrypted.isNotEmpty()) {
                buf.write(0x85) // data marker
                buf.write(0x82) // long form, length will take 2 bytes
                buf.write(encrypted.size ushr 8) // length
                buf.write(encrypted.size)
                buf.write(encrypted)
            }
            buf.write(0x99) // status word marker
            buf.write(2) // size = 2
            buf.write(response.bytes, dataLen, 2)
            // NB: Mac is required (even if not checked)
            val mac = computeMac(buf.toByteArray(), wrapper)
            buf.write(0x8E) // Mac marker
            buf.write(mac.size)
            buf.write(mac)
            buf.write(response.bytes, dataLen, 2)  // status word again
            return ResponseAPDU(buf.toByteArray())
        }

        private fun computeMac(message: ByteArray, wrapper: SecureMessagingWrapper): ByteArray {
            // Note: this won't compute correct mac until we have correct secureSendCounter
            // established.
            val mac = Util.getMac("ISO9797Alg3Mac")
            mac.init(wrapper.macKey)
            val byteArrayOutputStream = ByteArrayOutputStream()
            val dataOutputStream = DataOutputStream(byteArrayOutputStream)
            dataOutputStream.writeLong(secureSendCounter)
            secureSendCounter++
            val paddedData = Util.pad(message, 8)
            dataOutputStream.write(paddedData)
            dataOutputStream.flush()
            dataOutputStream.close()
            return mac.doFinal(byteArrayOutputStream.toByteArray())
        }

        override fun getATR(): ByteArray {
            throw RuntimeException("Unexpected call")
        }

        override fun close() {
            opened = false
        }

        override fun isConnectionLost(e: Exception?): Boolean {
            return false
        }
    }

    private val reader = MrtdNfcReader(false)  // Don't check mac

    // This test just plays out the simplest success path through the code.
    @Test
    fun simple_success() {
        // TLV streams, just need some reasonable tag values; body is arbitrary
        val expectedDG1 = tlv(5, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))
        val expectedDG2 = tlv(6, byteArrayOf(57, 57, 57, 8, 6, 5, 57, 32, 11, 15, 20, 1))
        val expectedSOD = tlv(7, byteArrayOf(34, 35, 36, 37, 1, 1, 2, 3, 8, 15))

        val cardService = MockCardService(
            reader, listOf(
                fileNotFound,  // refuse to do PACE
                noError,  // sending applet
                responseWithBody(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)),  // BAC challenge
                // BAC response. Since we do not have code to emulate BAC we just send something back.
                // This will result in some random crypto key established (and we just lift that
                // key off MrtdNfcReader for our testing.
                responseWithBody(ByteArray(40)),
                noError,  // select file, success
                responseWithBody(expectedDG1.copyOf(8)),  // prefix
                noError,  // select file, success
                responseWithBody(expectedDG2.copyOf(8)),  // prefix
                noError,  // select file, success
                responseWithBody(expectedSOD.copyOf(8)),  // prefix
                noError,  // select file, success
                responseWithBody(expectedDG1.copyOfRange(8, expectedDG1.size)),  // tail
                noError,  // select file, success
                responseWithBody(expectedDG2.copyOfRange(8, expectedDG2.size)),  // tail
                noError,  // select file, success
                responseWithBody(expectedSOD.copyOfRange(8, expectedSOD.size)),  // tail
            )
        )

        val statusList = ArrayList<MrtdNfcReader.Status>()

        val data: MrtdNfcData
        try {
            data = reader.read(cardService, MrtdMrzData("0000", "940506", "280808")) {
                statusList.add(it)
            }
        } catch (err: Exception) {
            throw err
        }

        val dg1Size = expectedDG1.size
        val dg2Size = expectedDG2.size
        val sodSize = expectedSOD.size
        val totalSize = dg1Size + dg2Size + sodSize

        Assert.assertEquals(
            listOf(
                MrtdNfcReader.Connected,
                MrtdNfcReader.PACENotSupported,
                MrtdNfcReader.AttemptingBAC,
                MrtdNfcReader.BACSucceeded,
                MrtdNfcReader.ReadingData(dg1Size, totalSize),
                MrtdNfcReader.ReadingData(dg1Size + dg2Size, totalSize),
                MrtdNfcReader.ReadingData(totalSize, totalSize),
                MrtdNfcReader.Finished
            ),
            statusList
        )

        Assert.assertArrayEquals(expectedDG1, data.dg1)
        Assert.assertArrayEquals(expectedDG2, data.dg2)
        Assert.assertArrayEquals(expectedSOD, data.sod)
    }
}

private val noError = responseFromCode(ISO7816.SW_NO_ERROR)
private val fileNotFound = responseFromCode(ISO7816.SW_FILE_NOT_FOUND)

private fun responseFromCode(code: Short): ResponseAPDU {
    return ResponseAPDU(byteArrayOf(((code.toInt()) ushr 8).toByte(), code.toByte()))
}

private fun responseWithBody(body: ByteArray, code: Short = ISO7816.SW_NO_ERROR): ResponseAPDU {
    val msg = body.copyOf(body.size + 2)
    msg[body.size] = (code.toInt() ushr 8).toByte()
    msg[body.size + 1] = code.toByte()
    return ResponseAPDU(msg)
}

private fun tlv(tag: Byte, body: ByteArray): ByteArray {
    val buf = ByteArrayOutputStream()
    buf.write(tag.toInt())
    buf.write(0x82)  // 2 byte size
    buf.write(body.size ushr 8)
    buf.write(body.size)
    buf.write(body)
    return buf.toByteArray()
}