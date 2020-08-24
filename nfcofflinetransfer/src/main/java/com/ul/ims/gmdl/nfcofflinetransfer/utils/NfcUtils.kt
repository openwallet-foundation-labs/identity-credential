/*
 * Copyright (C) 2019 Google LLC
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

package com.ul.ims.gmdl.nfcofflinetransfer.utils

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager

object NfcUtils {
    /**
     * Return true if Nfc is currently enabled and ready for use
     * **/
    fun isNfcEnabled(context: Context): Boolean {
        return getNfcAdapter(context)?.isEnabled ?: false
    }

    /**
     * Get the default NFC Adapter for this device
     * **/
    private fun getNfcAdapter(context: Context): NfcAdapter? {
        val nfcAdapter: NfcAdapter? by lazy(LazyThreadSafetyMode.NONE) {
            val nfcManager =
                getNfcManager(context)
            nfcManager?.defaultAdapter
        }

        return nfcAdapter
    }

    /**
     * Get the NFC Manager for this device
     * **/
    private fun getNfcManager(context: Context): NfcManager? {
        val nfcManager: NfcManager? by lazy(LazyThreadSafetyMode.NONE) {
            context.getSystemService(Context.NFC_SERVICE) as NfcManager
        }

        return nfcManager
    }

    fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }

    fun twoBytesToInt(byteArray: ByteArray): Int {
        return toInt(byteArray[0]).shl(8) + toInt(byteArray[1])
    }

    fun toHexString(byteArray: ByteArray?): String {
        byteArray?.let {
            var hexString = ""
            for (b: Byte in it) {
                hexString += String.format("%02X ", b)
            }
            return hexString
        }

        return "null"
    }

    fun intAsTwoBytes(int: Int): ByteArray {
        return byteArrayOfInts(int.ushr(8), int)
    }

    fun toInt(byte: Byte) = byte.toInt().and(0xFF)

    /**
     * Nest the payload in a DO'53' BER-TLV data object.
     *
     * @param value Encrypted CBOR message to be nested in a DO'53'
     * @return data field is DO’53’ encapsulating an encrypted CBOR blob.
     */
    fun createBERTLV(value: ByteArray?): List<Byte> {
        val dataField = mutableListOf<Byte>()
        // Create the data object as BER-TLV
        // Add tag
        dataField.add(0x53.toByte())
        value?.let {
            // Add length
            dataField.addAll(formatBERTLVLength(value.size))
            // Add value
            dataField.addAll(value.toList())
        }
        return dataField
    }

    private fun formatBERTLVLength(length: Int): List<Byte> {
        val lengthArray = mutableListOf<Byte>()
        when {
            length < 0x80 -> {
                lengthArray.add(length.toByte())
            }
            length < 0x100 -> {
                lengthArray.add(0x81.toByte())
                lengthArray.add(length.toByte())
            }
            length < 0x10000 -> {
                lengthArray.add(0x82.toByte())
                lengthArray.add((length / 0x100).toByte())
                lengthArray.add((length % 0x100).toByte())
            }
            length < 0x1000000 -> {
                lengthArray.add(0x83.toByte())
                lengthArray.add(length.toByte())
                lengthArray.add((length / 0x10000).toByte())
                lengthArray.add((length / 0x100).toByte())
                lengthArray.add((length % 0x100).toByte())
            }
        }
        return lengthArray
    }

    fun formatLcField(length: Int, extendedLength: Boolean): List<Byte> {
        return if (extendedLength) {
            mutableListOf<Byte>().also {
                it.add((0x00).toByte())
                it.add((length / 0x100).toByte())
                it.add((length % 0x100).toByte())
            }
        } else {
            if (length <= 0xFF) {
                mutableListOf<Byte>().also { it.add(length.toByte()) }
            } else {
                mutableListOf<Byte>().also { it.add(0xFF.toByte()) }
            }
        }
    }

    fun parseLcField(command: ByteArray): Int {
        val length = command[4]

        // Check if it is extended length
        return if (length == 0x00.toByte() && command.size > 5) {
            val lengthExtended = command.copyOfRange(5, 8)
            var lengthInt = (toInt(lengthExtended[0]) * 256)
            lengthInt += toInt(lengthExtended[1])
            lengthInt
        } else {
            toInt(length)
        }
    }

    fun getBERTLVValue(berTlv: ByteArray): ByteArray {
        return when (berTlv[1]) {
            0x81.toByte() -> {
                val length = toInt(berTlv[2])
                berTlv.copyOfRange(3, 3 + length)
            }
            0x82.toByte() -> {
                var length = (toInt(berTlv[2]) * 256)
                length += toInt(berTlv[3])
                berTlv.copyOfRange(4, 4 + length)
            }
            0x83.toByte() -> {
                var length = (toInt(berTlv[2]) * 256 * 256)
                length += (toInt(berTlv[3]) * 256)
                length += toInt(berTlv[4])
                berTlv.copyOfRange(5, 5 + length)
            }
            else -> {
                val length = toInt(berTlv[1])
                berTlv.copyOfRange(2, 2 + length)
            }
        }
    }
}