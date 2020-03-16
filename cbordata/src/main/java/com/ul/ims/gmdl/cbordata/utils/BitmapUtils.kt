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

package com.ul.ims.gmdl.cbordata.utils

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream


object BitmapUtils {

    fun decodeBitmapResource(res: Resources, resId : Int) : Bitmap {
        return BitmapFactory.decodeResource(res, resId)
    }

    fun decodeBitmapBytes(bytes : ByteArray) : Bitmap {
        return BitmapFactory.Options().run {
            inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    fun encodeBitmap(bitmap: Bitmap?) : ByteArray? {
        var bitmapData: ByteArray? = null

        bitmap?.let {
            val stream = ByteArrayOutputStream()

            it.compress(Bitmap.CompressFormat.JPEG, 15, stream)

            bitmapData = stream.toByteArray()
        }

        return bitmapData
    }
}