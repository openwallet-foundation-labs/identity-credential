/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.multipaz.android.direct_access

import android.os.Build
import androidx.annotation.RequiresApi
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.Cbor.decode
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.util.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlinx.datetime.until
import org.bouncycastle.asn1.ASN1UTCTime
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.days

/**
 * A class which handles common operations related to the DirectAccess applet.
 */
@RequiresApi(Build.VERSION_CODES.P)
object DirectAccess {

    private const val TAG = "DirectAccess"
    private const val PROVISION_BEGIN: Byte = 0
    private const val PROVISION_UPDATE: Byte = 1
    private const val PROVISION_FINISH: Byte = 2
    private const val KEY_CERT: Int = 0x01
    private const val KEY_ENC_DATA: Int = 0x00
    private const val CMD_MDOC_CREATE = 0x01
    private const val CMD_MDOC_CREATE_PRESENTATION_PKG = 0x07
    private const val CMD_MDOC_DELETE_CREDENTIAL = 0x08
    private const val CMD_MDOC_PROVISION_DATA = 0x09
    private const val CMD_MDOC_SWAP_IN = 0x06
    private const val APDU_RESPONSE_STATUS_OK = 0x9000
    private const val INS_ENVELOPE = 0xC3.toByte()

    val transport = DirectAccessOmapiTransport

    /**
     * Returns the maximum size, in bytes, of `credentialData` which can be used
     * in credentials.
     *
     * This will be at least 32 KiB.
     */
    val maximumCredentialSize: Long
        get() {
            // TODO: integrate with applet
            return 32768
        }

    private var _isDirectAccessSupported: Boolean? = null

    /**
     * Returns true if direct access is supported on the device, false otherwise.
     */
    val isDirectAccessSupported: Boolean
        get() {
            if (_isDirectAccessSupported != null) {
                return _isDirectAccessSupported!!
            }
            try {
                transport.openConnection()
            } catch (e: Throwable) {
                Logger.i(TAG, "Error opening transport connection: ${e.message}")
                _isDirectAccessSupported = false
                return false
            } finally {
                transport.closeConnection()
            }
            _isDirectAccessSupported = true
            return true
        }

    private fun setShort(buf: ByteArray, offset: Int, value: Short) {
        buf[offset] = (value.toInt() shr 8 and 0xFF).toByte()
        buf[offset + 1] = (value.toInt() and 0xFF).toByte()
    }

    private fun getAPDUResponseStatus(input: ByteArray): Int {
        // Last two bytes are the status SW0SW1
        val SW0 = input[input.size - 2]
        val SW1 = input[input.size - 1]
        return SW0.toInt() shl 8 or SW1.toInt() and 0xFFFF
    }

    @Throws(IOException::class)
    private fun makeCommandApdu(data: ByteArray): ByteArray {
        // TODO: Handle non extended length.
        check(data.size <= 0xffff) {
            "The length of the data exceeds the maximum length of extended APDU (63535)"
        }
        val bos = ByteArrayOutputStream()
        bos.write(0) // CLS
        bos.write(INS_ENVELOPE.toInt()) // INS
        bos.write(0) // P1
        bos.write(0) // P2
        // Send extended length APDU always as response size is not known to HAL.
        // Case 1: Lc > 0  CLS | INS | P1 | P2 | 00 | 2 bytes of Lc | CommandData | 2 bytes of Le
        // all set to 00.
        // Case 2: Lc = 0  CLS | INS | P1 | P2 | 3 bytes of Le all set to 00.
        bos.write(0x00)
        // Extended length 3 bytes, starts with 0x00
        if (data.isNotEmpty()) {
            bos.write(data.size shr 8)
            bos.write(data.size and 0xFF)
            // Data
            bos.write(data)
        }
        bos.write(0)
        bos.write(0)
        return bos.toByteArray()
    }

    @Throws(IOException::class)
    private fun sendApdu(
        cmd: Int,
        slot: Int,
        data: ByteArray,
        offset: Int,
        length: Int,
        operation: Byte,
    ): ByteArray? {
        check(slot in 0..127) {
            "slot is out of the valid range (0-127)"
        }
        val bb = ByteBuffer.allocate(length)
        bb.put(data, offset, length)
        val scratchpad = ByteArray(2)
        val bos = ByteArrayOutputStream()
        // set instruction
        setShort(scratchpad, 0, cmd.toShort())
        bos.write(scratchpad)
        bos.write(slot)
        bos.write(operation.toInt())
        bos.write(Cbor.encode((Bstr(bb.array()))))
        val beginApdu = makeCommandApdu(bos.toByteArray())

        val response: ByteArray = transport.sendData(beginApdu)
        val status = getAPDUResponseStatus(response)
        check(APDU_RESPONSE_STATUS_OK == status) {
            "Operation failed. Response status: $status" }
        if (response.size > 2) {
            val input = response.copyOf(response.size - 2)
            return decode(input).asBstr
        }
        return null
    }

    /**
     * Use this function to ensure the transport is initialized (since Kotlin
     * singletons are initialized on first access) so that the app can begin
     * connecting to the applet in the background.
     */
    fun warmupTransport() {
        DirectAccessOmapiTransport
    }

    /**
     * Allocates space (a slot) in the Direct Access applet for a
     * document to be provisioned to.
     *
     * @return ID of the allocated slot, -1 if no slot is available
     */
    fun allocateDocumentSlot(): Int {
        val apdu: ByteArray?
        val response: ByteArray?
        try {
            transport.closeConnection()
            transport.openConnection()

            val scratchpad = ByteArray(2)
            val bos = ByteArrayOutputStream()
            // set instruction
            setShort(scratchpad, 0, CMD_MDOC_CREATE.toShort())
            bos.write(scratchpad)
            apdu = makeCommandApdu(bos.toByteArray())

            response = transport.sendData(apdu)
            check(response.size == 3) {
                "allocateDocumentSlot APDU response size != 3"
            }
            check(getAPDUResponseStatus(response) == APDU_RESPONSE_STATUS_OK)
            return response[0].toInt()
        } catch (e: IOException) {
            throw java.lang.IllegalStateException("Failed to send createCredential APDU command")
        } finally {
            transport.closeConnection()
        }
    }

    /**
     * Clears a slot.
     *
     * @param documentSlot the slot to delete any existing document at
     * @return true if the slot is cleared, false otherwise
     */
    fun clearDocumentSlot(
        documentSlot: Int
    ): Boolean {
        check(documentSlot in 0..127) {
            "documentSlot is out of the valid range (0-127)."
        }
        try {
            transport.closeConnection()
            transport.openConnection()

            val scratchpad = ByteArray(2)
            val bos = ByteArrayOutputStream()
            setShort(scratchpad, 0, CMD_MDOC_DELETE_CREDENTIAL.toShort())
            bos.write(scratchpad)
            bos.write(documentSlot)
            val apdu = makeCommandApdu(bos.toByteArray())

            val response: ByteArray = transport.sendData(apdu)
            val status = getAPDUResponseStatus(response)
            if (APDU_RESPONSE_STATUS_OK != status) {
                Logger.i(TAG, "clearDocumentSlot failed. Response status: $status")
                return false
            }
            return true
        } catch (e: IOException) {
            Logger.i(TAG, "Failed to delete MDoc")
            return false
        } finally {
            transport.closeConnection()
        }
    }

    /**
     * Enumerates slots which have already been allocated.
     *
     * @return a list of slot IDs
     */
    fun enumerateAllocatedSlots(): List<Int> {
        TODO("Not yet implemented")
    }

    // TODO: These Time formatting methods (with DateTimeComponents) might benefit from unit tests covering edge cases.
    @Throws(IOException::class)
    private fun encodeValidityTime(instant: Instant): ByteArray {
        val y2k = Instant.parse("2000-01-01T00:00:00Z")
        val asn1UtcTime = ASN1UTCTime(instant.format(DateTimeComponents.Format {
            yearTwoDigits(
                if (y2k.until(
                        instant,
                        DateTimeUnit.Companion.SECOND,
                        TimeZone.UTC
                    ) >= 0
                ) 2000 else 1900
            )
            monthNumber()
            dayOfMonth()
            hour()
            minute()
            second()
            char('Z')
        }))
        return asn1UtcTime.encoded
    }

    /**
     * Creates a credential at the given slot.
     *
     * This is called during initialization of a [DirectAccessCredential], so there
     * is no need to call this explicitly when dealing with DirectAccessCredentials.
     *
     * Note: this operation will not alter the state of the applet, ie. would not
     * result in the newly created credential being set as the active credential.
     * In order to use the created credential, [certifyCredential] and
     * [setActiveCredential] must still be called.
     *
     * @param documentSlot the slot to provision a credential in
     * @return signingKeyCertificate, encryptedPresentationData
     */
    fun createCredential(
        documentSlot: Int
    ): Pair<X509CertChain, ByteArray> {
        val response: ByteArray?
        try {
            transport.closeConnection()
            transport.openConnection()

            val scratchpad = ByteArray(2)
            val bos = ByteArrayOutputStream()
            // set instruction
            setShort(scratchpad, 0, CMD_MDOC_CREATE_PRESENTATION_PKG.toShort())
            bos.write(scratchpad)
            bos.write(documentSlot)

            // TODO: @venkat the validity duration should be set during certification as opposed to creation
            val now = Clock.System.now()
            val notBeforeBytes = encodeValidityTime(now)
            val notAfterBytes = encodeValidityTime(now + 3650.days)

            // Set Not Before
            setShort(scratchpad, 0, notBeforeBytes.size.toShort())
            bos.write(scratchpad)
            bos.write(notBeforeBytes)

            // Set Not After
            setShort(scratchpad, 0, notAfterBytes.size.toShort())
            bos.write(scratchpad)
            bos.write(notAfterBytes)
            val apdu = makeCommandApdu(bos.toByteArray())

            response = transport.sendData(apdu)
            val status = getAPDUResponseStatus(response)
            check(APDU_RESPONSE_STATUS_OK == status) {
                "createPresentationPackage failed. Response status: $status" }
            val input = response.copyOf(response.size - 2)

            var signingCert: X509CertChain? = null
            var encryptedData: ByteArray? = null
            val map = decode(input)
            val keys = map.asMap.keys
            for (keyItem in keys) {
                val value = keyItem.asNumber.toInt()
                when (value) {
                    KEY_CERT -> {
                        signingCert = X509CertChain(listOf(X509Cert(map[keyItem].asBstr)))
                    }

                    KEY_ENC_DATA -> {
                        val encBytesItem = map[keyItem]
                        encryptedData = encBytesItem.asBstr
                    }

                    else -> throw IllegalStateException("createPresentationPackage unknown key item")
                }
            }
            return Pair(signingCert!!, encryptedData!!)
        } catch (e: IOException) {
            Logger.d(TAG, "Failed to create presentation package")
            throw java.lang.IllegalStateException("Failed to create presentation package", e)
        } finally {
            transport.closeConnection()
        }
    }

    /**
     * Certifies the credential with the given encryptedPresentationData and
     * returns the updated encryptedPresentationData.
     *
     * Note: this operation will not alter the state of the applet, ie. would
     * not result in the newly certified credential being set as the active
     * credential. In order to use the certified credential,
     * [setActiveCredential] must still be called.
     *
     * The |credentialData| parameter must be CBOR conforming to the following CDDL:
     *
     *   CredentialData = {
     *     "issuerNameSpaces": IssuerNameSpaces,
     *     "issuerAuth" : IssuerAuth,
     *     "readerAccess" : ReaderAccess
     *   }
     *
     *   IssuerNameSpaces = {
     *     NameSpace => [ + IssuerSignedItemBytes ]
     *   }
     *
     *   ReaderAccess = [ * COSE_Key ]
     *
     * @param documentSlot the slot to certify a credential in
     * @param credentialData the data being used to certify the credential
     * @param encryptedPresentationData the data representing the provisioned,
     *                                  uncertified credential
     * @return updated encryptedPresentationData
     */
    fun certifyCredential(
        documentSlot: Int,
        credentialData: ByteArray,
        encryptedPresentationData: ByteArray
    ): ByteArray {
        val bao = ByteArrayOutputStream()
        try {
            transport.closeConnection()
            transport.openConnection()
            // BEGIN
            val encryptedData = encryptedPresentationData
            bao.write(sendApdu(
                cmd = CMD_MDOC_PROVISION_DATA,
                slot = documentSlot,
                data = encryptedData,
                offset = 0,
                length = encryptedData.size,
                operation= PROVISION_BEGIN
            ))

            // UPDATE
            val encodedCredData = Cbor.encode(Bstr(credentialData))
            var remaining = encodedCredData.size
            var start = 0
            val maxTransmitBufSize = 512
            while (remaining > maxTransmitBufSize) {
                bao.write(sendApdu(
                    cmd = CMD_MDOC_PROVISION_DATA,
                    slot = documentSlot,
                    data = encodedCredData,
                    offset = start,
                    length = maxTransmitBufSize,
                    operation = PROVISION_UPDATE
                ))
                start += maxTransmitBufSize
                remaining -= maxTransmitBufSize
            }

            // Finish
            bao.write(sendApdu(
                cmd = CMD_MDOC_PROVISION_DATA,
                slot = documentSlot,
                data = encodedCredData,
                offset = start,
                length = remaining,
                operation = PROVISION_FINISH
            ))
        } catch (e: IOException) {
            throw java.lang.IllegalStateException("Failed to provision credential data $e")
        } finally {
            transport.closeConnection()
        }

        // Return updated presentation package.
        return bao.toByteArray()
    }

    /**
     * Returns the number of times the credential in the slot has been used in a
     * presentation since it was set as the active credential or since the usage
     * count was reset with [clearCredentialUsageCount].
     *
     * @param documentSlot the slot
     * @return the number of times the credential in the slot was used
     * @throws Throwable if no active credential
     */
    fun getCredentialUsageCount(
        documentSlot: Int,
    ): Int {
        TODO("Not yet implemented")
    }

    /**
     * Resets the usage count of the credential in the slot to 0.
     *
     * @param documentSlot the slot
     * @return the number of times the credential in the slot was used since
     *     [clearCredentialUsageCount] was last called.
     * @throws Throwable if no active credential
     */
    fun clearCredentialUsageCount(
        documentSlot: Int,
    ): Int {
        TODO("Not yet implemented")
    }

    /**
     * Sets the credential represented by the encryptedPresentationData as the
     * active credential in the slot (ie. it would be the one used during
     * presentation). If encryptedPresentationData is null, clears active
     * credential.
     *
     * This is called during [DirectAccessCredential.setAsActiveCredential], so
     * there is no need to call this explicitly when dealing with
     * [DirectAccessCredential]s.
     *
     * @param documentSlot the slot
     * @param encryptedPresentationData the data representing the provisioned
     *      credential.
     * @throws Throwable on failure
     */
    fun setActiveCredential(
        documentSlot: Int,
        encryptedPresentationData: ByteArray?
    ) {
        try {
            transport.closeConnection()
            transport.openConnection()
            val encryptedData = encryptedPresentationData
            var remaining = encryptedData!!.size // TODO: allow for null/clearing active cred
            var start = 0
            val maxTransmitBufSize = 512
            // BEGIN
            sendApdu(
                CMD_MDOC_SWAP_IN,
                documentSlot,
                encryptedData,
                0,
                maxTransmitBufSize,
                PROVISION_BEGIN
            )
            start += maxTransmitBufSize
            remaining -= maxTransmitBufSize

            // UPDATE
            while (remaining > maxTransmitBufSize) {
                sendApdu(
                    CMD_MDOC_SWAP_IN,
                    documentSlot,
                    encryptedData,
                    start,
                    maxTransmitBufSize,
                    PROVISION_UPDATE
                )
                start += maxTransmitBufSize
                remaining -= maxTransmitBufSize
            }

            // Finish
            sendApdu(
                CMD_MDOC_SWAP_IN,
                documentSlot,
                encryptedData,
                start,
                remaining,
                PROVISION_FINISH
            )
        } catch (e: IOException) {
            throw java.lang.IllegalStateException("Failed to provision credential data $e")
        } finally {
            transport.closeConnection()
        }
    }
}
