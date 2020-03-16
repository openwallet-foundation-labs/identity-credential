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

package com.ul.ims.gmdl.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.util.*

/**
 * Extracted from com.google.zxing.client.android.encode.QRCodeEncoder
 * http://code.google.com/p/zxing/source/browse/trunk/android/src/com/google/zxing/client/android/encode/
 * QRCodeEncoder.java
 *
 * **/
object QrCode {

    private val WHITE = -0x1
    private val BLACK = -0x1000000

    @Throws(WriterException::class)
    fun encodeAsBitmap(contents: String?, format: BarcodeFormat, imgWidth: Int, imgHeight: Int): Bitmap? {
        if (contents == null) {
            return null
        }
        var hints: MutableMap<EncodeHintType, Any>? = null
        val encoding = guessAppropriateEncoding(contents)
        if (encoding != null) {
            hints = EnumMap(EncodeHintType::class.java)
            hints[EncodeHintType.CHARACTER_SET] = encoding
        }
        val writer = MultiFormatWriter()
        val result: BitMatrix
        try {
            result = writer.encode(contents, format, imgWidth, imgHeight, hints)
        } catch (iae: IllegalArgumentException) {
            // Unsupported format
            return null
        }

        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (result.get(x, y)) BLACK else WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(
            width, height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun guessAppropriateEncoding(contents: CharSequence): String? {
        // Very crude at the moment
        for (element in contents) {
            if (element.toInt() > 0xFF) {
                return "UTF-8"
            }
        }
        return null
    }
}