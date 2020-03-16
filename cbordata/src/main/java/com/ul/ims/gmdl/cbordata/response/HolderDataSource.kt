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

import com.ul.ims.gmdl.cbordata.namespace.INamespace
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.cbordata.security.CoseSign1

class HolderDataSource private constructor(
    val namespace : INamespace,
    val data : Map <String, IssuerSignedItem>
) {
    class Builder {
        private var namespace : INamespace = MdlNamespace
        private var data : MutableMap<String, IssuerSignedItem> = mutableMapOf()

        fun setNamespace(namespace: INamespace) = apply {
            this.namespace = namespace
        }

        fun setDataItem(itemName : String, issuerSignedItem: IssuerSignedItem) = apply {
            data[itemName] = issuerSignedItem
        }

        fun setData(datasource: Map<String, IssuerSignedItem>) = apply {
            data = datasource.toMutableMap()
        }

        fun build() : HolderDataSource {
            return HolderDataSource(
                namespace,
                data.toMap()
            )
        }
    }
}