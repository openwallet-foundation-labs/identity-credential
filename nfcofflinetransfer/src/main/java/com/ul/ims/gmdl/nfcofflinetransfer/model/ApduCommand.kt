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

package com.ul.ims.gmdl.nfcofflinetransfer.model

import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.formatLcField
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.toInt
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.twoBytesToInt
import java.io.ByteArrayOutputStream
import kotlin.experimental.and


class ApduCommand private constructor(
    val cla: Byte,
    val ins: Byte,
    val p1p2: ByteArray,
    val lc: ByteArray?,
    val dataField: ByteArray?,
    val le: ByteArray?
) {

    companion object {
        const val CLA_DEFAULT = 0x00.toByte()
        const val CLA_CHAIN = 0x10.toByte()
        const val INS_SELECT_COMMAND = 0xa4.toByte()
        const val INS_ENVELOP_COMMAND = 0xc3.toByte()
        const val INS_RESPONSE_COMMAND = 0xc0.toByte()
        val P1P2_SELECT_COMMAND = byteArrayOf(0x04, 0x0c)
        val P1P2_ENVELOP_COMMAND = byteArrayOf(0x00, 0x00)
        val P1P2_RESPONSE_COMMAND = byteArrayOf(0x00, 0x00)
    }

    fun encode(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        outputStream.write(cla.toInt())
        outputStream.write(ins.toInt())
        outputStream.write(p1p2)
        lc?.let {
            outputStream.write(it)
            dataField?.let { df ->
                outputStream.write(df)
            }
        }
        le?.let {
            outputStream.write(it)
        }

        return outputStream.toByteArray()
    }

    fun isChain() = cla.and(CLA_CHAIN) == CLA_CHAIN

    class Builder {
        private var cla: Byte = 0
        private var ins: Byte = 0
        private var p1p2: ByteArray = byteArrayOf(0x00, 0x00)
        private var lc: ByteArray? = null
        private var dataField: ByteArray? = null
        private var le: ByteArray? = null

        fun setSelectCommand(dataField: ByteArray?) = apply {
            cla =
                CLA_DEFAULT
            ins =
                INS_SELECT_COMMAND
            p1p2 =
                P1P2_SELECT_COMMAND
            dataField?.let {
                lc = formatLcField(it.size, it.size > 0xFF).toByteArray()
                this.dataField = it
            }
        }

        fun setEnvelopeCommand(dataField: ByteArray?, leInt: Int?, lastChain: Boolean) = apply {
            cla = if (lastChain) CLA_DEFAULT else CLA_CHAIN
            ins =
                INS_ENVELOP_COMMAND
            p1p2 =
                P1P2_ENVELOP_COMMAND
            dataField?.let {
                lc = formatLcField(it.size, it.size > 0xFF || leInt ?: 0 > 0xFF).toByteArray()
                this.dataField = it
                // Set Le field
                if (lastChain) {
                    leInt?.let { leI ->
                        le = if (it.size > 0xFF || leI > 0xFF) {
                            // When Lc is present Le should be encoded with 2 bytes
                            val leList = formatLcField(leI, true)
                            leList.subList(1, 3).toByteArray()
                        } else {
                            formatLcField(leI, false).toByteArray()
                        }
                    }
                }
            } ?: if (lastChain) {
                leInt?.let { leI ->
                    le = formatLcField(leI, leI > 0xFF).toByteArray()
                }
            }
        }

        fun setResponseCommand(leInt: Int?, useExtendedLength: Boolean) = apply {
            cla =
                CLA_DEFAULT
            ins =
                INS_RESPONSE_COMMAND
            p1p2 =
                P1P2_RESPONSE_COMMAND
            le = leInt?.let {
                if (useExtendedLength) {
                    if (it > twoBytesToInt(byteArrayOf(0xFF.toByte(), 0xFF.toByte()))) {
                        // 65536
                        byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte())
                    } else {
                        // 65535 or less
                        formatLcField(it, true).toByteArray()
                    }
                } else {
                    if (it > 0xFF) {
                        // 256
                        byteArrayOf(0x00.toByte())
                    } else {
                        // 255 or less
                        formatLcField(it, false).toByteArray()
                    }
                }
            }
        }

        fun decode(commandApdu: ByteArray?) = apply {
            commandApdu?.let { apdu ->
                if (apdu.size > 4) {
                    cla = apdu[0]
                    ins = apdu[1]
                    p1p2 = apdu.copyOfRange(2, 4)
                    val length = apdu[4]
                    if (length == 0x00.toByte()) {
                        when (apdu.size) {
                            5 -> le = byteArrayOf(length)
                            7 -> le = apdu.copyOfRange(4, 7)
                            else -> {
                                lc = apdu.copyOfRange(4, 7)
                                val lcInt = twoBytesToInt(apdu.copyOfRange(5, 7))
                                if (apdu.size >= 7 + lcInt) {
                                    dataField = apdu.copyOfRange(7, 7 + lcInt)
                                    if (apdu.size > 7 + lcInt) {
                                        le = apdu.copyOfRange(7 + lcInt, apdu.size)
                                    }
                                }
                            }
                        }
                    } else {
                        val lcInt = toInt(apdu[4])
                        if (apdu.size >= 5 + lcInt) {
                            lc = byteArrayOf(apdu[4])
                            dataField = apdu.copyOfRange(5, 5 + lcInt)
                            if (apdu.size > 5 + lcInt) {
                                le = apdu.copyOfRange(5 + lcInt, apdu.size)
                            }
                        } else {
                            le = byteArrayOf(apdu[4])
                        }
                    }
                }
            }
        }

        fun build(): ApduCommand {
            return ApduCommand(
                cla,
                ins,
                p1p2,
                lc,
                dataField,
                le
            )
        }
    }
}