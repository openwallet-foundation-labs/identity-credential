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

package com.ul.ims.gmdl.cbordata.generic

import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import org.junit.Assert
import org.junit.Test

class AbstractCborStructureBuilderTest {

    class Builder : AbstractCborStructureBuilder()

    @Test
    fun fromDataItemTest() {
        val builder = Builder()

        val dataItem = UnsignedInteger(1L)
        Assert.assertTrue(builder.fromDataItem(dataItem) is Int)

        val dataItem1 = UnicodeString("my string")
        Assert.assertTrue(builder.fromDataItem(dataItem1) is String)

        val dataItem2 = ByteString("my string".toByteArray())
        Assert.assertTrue(builder.fromDataItem(dataItem2) is ByteArray)
    }
}