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

package com.ul.ims.gmdl.cbordata.security.sessionEncryption

import org.junit.Assert
import org.junit.Test

class SessionDataTest {
    private val data = byteArrayOf(
        0xa1.toByte(), 0x64.toByte(), 0x64.toByte(), 0x61.toByte(), 0x74.toByte(),
        0x61.toByte(), 0x48.toByte(), 0x12.toByte(), 0x34.toByte(), 0x56.toByte(),
        0x78.toByte(), 0x9a.toByte(), 0xbc.toByte(), 0xde.toByte(), 0xf0.toByte()
    )
    private val expectedEncryptedData = byteArrayOf(
        0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte()
    )

    private val errorData = byteArrayOf(
        0xA1.toByte(),
        0x65.toByte(),
        0x65.toByte(),
        0x72.toByte(),
        0x72.toByte(),
        0x6F.toByte(),
        0x72.toByte(),
        0x0A.toByte()
    )

    private val expectedError = 10

    @Test
    fun testSessionDataDecode() {
        val sessionData = SessionData.Builder().decode(data).build()

        Assert.assertNotNull(sessionData)
        Assert.assertArrayEquals(expectedEncryptedData, sessionData.encryptedData)
        Assert.assertNull(sessionData.errorCode)
    }

    @Test
    fun testSessionDataEncode() {
        val builder = SessionData.Builder()
        builder.setEncryptedData(expectedEncryptedData)
        val sd = builder.build().encode()

        Assert.assertArrayEquals(data, sd)
    }

    @Test
    fun testErrorSessionDataDecode() {
        val sessionData = SessionData.Builder().decode(errorData).build()

        Assert.assertNotNull(sessionData)
        Assert.assertNull(sessionData.encryptedData)
        Assert.assertEquals(sessionData.errorCode, expectedError)
    }

    @Test
    fun testErrorSessionDataEncode() {
        val builder = SessionData.Builder()
        builder.setErrorCode(expectedError)
        val sd = builder.build().encode()

        Assert.assertArrayEquals(errorData, sd)
    }
}

