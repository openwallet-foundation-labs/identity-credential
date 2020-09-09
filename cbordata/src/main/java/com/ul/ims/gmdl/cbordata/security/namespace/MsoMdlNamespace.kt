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

package com.ul.ims.gmdl.cbordata.security.namespace

class MsoMdlNamespace(
    override val namespace: String,
    override val items: Map<Int, ByteArray>
) : IMsoNameSpace {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MsoMdlNamespace

        if (namespace != other.namespace) return false
        if (items.size != other.items.size) return false
        if (!items.keys.containsAll(other.items.keys)) return false
        items.keys.forEach { key ->
            if (items[key]?.contentEquals(other.items[key] ?: byteArrayOf()) == false)
                return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = namespace.hashCode()
        result = 31 * result + items.hashCode()
        return result
    }
}
