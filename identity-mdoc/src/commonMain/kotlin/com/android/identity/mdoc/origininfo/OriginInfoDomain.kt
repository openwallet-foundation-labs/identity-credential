/*
 * Copyright 2023 The Android Open Source Project
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
package com.android.identity.mdoc.origininfo

import com.android.identity.cbor.CborMap.Companion.builder
import com.android.identity.cbor.DataItem

class OriginInfoDomain(val url: String) : OriginInfo() {

    override fun encode(): DataItem =
        builder()
            .put("cat", CAT)
            .put("type", TYPE)
            .putMap("details").put("domain", url).end()
            .end()
            .build()

    companion object {
        const val CAT: Long = 1
        const val TYPE = 1

        fun decode(oiDataItem: DataItem): OriginInfoDomain? {
            val cat = oiDataItem["cat"].asNumber
            val type = oiDataItem["type"].asNumber
            require(cat == 1L && type == 1L) {
                "This CBOR object has the wrong category or type. Expected cat = $CAT, " +
                        "type = $TYPE but got cat = $cat, type = $type"
            }
            val details = oiDataItem["details"]
            return OriginInfoDomain(details["domain"].asTstr)
        }
    }
}
