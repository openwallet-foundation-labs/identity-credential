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

package com.ul.ims.gmdl.cbordata.response

import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import java.io.Serializable

class Errors private constructor(
        val errors : Map<String, Array<ErrorItem>>
) : Serializable {
    class Builder {
        private var errors = mutableMapOf<String, Array<ErrorItem>>()

        fun setError(namespace : String, arr: Array<ErrorItem>) = apply {
            errors[namespace] = arr
        }

        fun decode(map : co.nstant.`in`.cbor.model.Map?) = apply {
            map?.keys?.forEach {k->
                val key = k as? UnicodeString
                val value = map.get(k) as? co.nstant.`in`.cbor.model.Array

                value?.dataItems?.forEach {i->
                    val itemMap = i as? co.nstant.`in`.cbor.model.Map
                    var listItems = arrayListOf<ErrorItem>()
                    var namespace = i as? UnicodeString
                    namespace?.string?.let { n->
                        itemMap?.keys?.forEach {
                            val dataItemName = it as? UnicodeString
                            val errorCode = itemMap.get(it) as? UnsignedInteger

                            dataItemName?.let {d->
                                errorCode?.let {e->
                                    listItems.add(
                                        ErrorItem(
                                            d.string,
                                            e.value.toInt()
                                        )
                                    )
                                }
                            }
                        }
                        setError(n, listItems.toTypedArray())
                    }
                }
            }
        }

        fun build() : Errors {
            return Errors(errors)
        }
    }
}