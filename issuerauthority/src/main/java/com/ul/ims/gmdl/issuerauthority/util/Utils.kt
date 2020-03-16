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

package com.ul.ims.gmdl.issuerauthority.util

import android.util.Log
import kotlin.math.min


/** Encodes a byte array to hex.  */
fun hexEncode(bytes: ByteArray): String? {
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        sb.append(String.format("%02x", b))
    }
    return sb.toString()
}

/** Decodes a hex string to a byte array. */
fun hexDecode(hex: String): ByteArray? {
    require(hex.length % 2 == 0) { "Expected a string of even length" }
    val size = hex.length / 2
    val result = ByteArray(size)
    for (i in 0 until size) {
        val hi = Character.digit(hex[2 * i], 16)
        val lo = Character.digit(hex[2 * i + 1], 16)
        require(!(hi == -1 || lo == -1)) { "input is not hexadecimal" }
        result[i] = (16 * hi + lo).toByte()
    }
    return result
}

const val CHUNK_SIZE = 2048

/* Debug print */
fun debugPrint(tag: String, message: String) {
    var i = 0
    while (i < message.length) {
        Log.d(tag, message.substring(i, min(message.length, i + CHUNK_SIZE)))
        i += CHUNK_SIZE
    }
}