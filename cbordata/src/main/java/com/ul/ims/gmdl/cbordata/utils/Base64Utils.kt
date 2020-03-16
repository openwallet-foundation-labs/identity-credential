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

import android.util.Base64

object Base64Utils {

    const val FLAGS = Base64.URL_SAFE + Base64.NO_PADDING

    @Throws(IllegalArgumentException::class)
    fun decode(base64String: String) : ByteArray {
        return Base64.decode(base64String, FLAGS)
    }

    fun encodeToString(input : ByteArray) : String {
        return Base64.encodeToString(input, FLAGS)
    }
}