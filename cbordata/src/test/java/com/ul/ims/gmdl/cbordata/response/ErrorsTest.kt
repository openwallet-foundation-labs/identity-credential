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

import org.junit.Assert
import org.junit.Test

class ErrorsTest {

    val namespace = "com.rdw.nl"

    private val errorItem1 = ErrorItem("NameAuth", 1)
    private val errorItem2 = ErrorItem("DateReg", 2)

    @Test
    fun builderTest() {
        val errors = Errors.Builder()
            .setError(namespace, arrayOf(errorItem1, errorItem2))
            .build()

        Assert.assertNotNull(errors)
        Assert.assertTrue(1 == errors.errors.size)
        Assert.assertTrue(errors.errors.containsKey(namespace))
        Assert.assertEquals(errorItem1, errors.errors[namespace]?.get(0))
        Assert.assertEquals(errorItem2, errors.errors[namespace]?.get(1))
    }
}