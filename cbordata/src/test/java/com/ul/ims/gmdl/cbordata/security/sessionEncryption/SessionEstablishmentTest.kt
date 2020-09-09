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

import com.ul.ims.gmdl.cbordata.security.CoseKey
import org.junit.Assert
import org.junit.Test

class SessionEstablishmentTest {
    private val sessionEstablishmentData = byteArrayOf(
        0xA2.toByte(), 0x6A.toByte(), 0x65.toByte(), 0x52.toByte(), 0x65.toByte(),
        0x61.toByte(), 0x64.toByte(), 0x65.toByte(), 0x72.toByte(), 0x4B.toByte(),
        0x65.toByte(), 0x79.toByte(), 0xD8.toByte(), 0x18.toByte(), 0x58.toByte(),
        0x06.toByte(), 0xA1.toByte(), 0x01.toByte(), 0x63.toByte(), 0x74.toByte(),
        0x62.toByte(), 0x64.toByte(), 0x64.toByte(), 0x64.toByte(), 0x61.toByte(),
        0x74.toByte(), 0x61.toByte(), 0x48.toByte(), 0x12.toByte(), 0x34.toByte(),
        0x56.toByte(), 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(),
        0xF0.toByte()
    )
    private val encryptedData = byteArrayOf(
        0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte()
    )
    private val coseKeyData = byteArrayOf(
        0xA1.toByte(), 0x01.toByte(), 0x63.toByte(), 0x74.toByte(), 0x62.toByte(), 0x64.toByte()
    )
    private val key = CoseKey.Builder().decode(coseKeyData).build()

    private val seExpected = byteArrayOf(
        0xa2.toByte(), 0x6a.toByte(), 0x65.toByte(), 0x52.toByte(), 0x65.toByte(),
        0x61.toByte(), 0x64.toByte(), 0x65.toByte(), 0x72.toByte(), 0x4b.toByte(),
        0x65.toByte(), 0x79.toByte(), 0xd8.toByte(), 0x18.toByte(), 0x46.toByte(),
        0xa1.toByte(), 0x01.toByte(), 0x63.toByte(), 0x74.toByte(), 0x62.toByte(),
        0x64.toByte(), 0x64.toByte(), 0x64.toByte(), 0x61.toByte(), 0x74.toByte(),
        0x61.toByte(), 0x48.toByte(), 0x12.toByte(), 0x34.toByte(), 0x56.toByte(),
        0x78.toByte(), 0x9a.toByte(), 0xbc.toByte(), 0xde.toByte(), 0xf0.toByte()
    )

    @Test
    fun testSessionEstablishmentDecode() {
        val sessionEstablishment = SessionEstablishment.Builder().decode(sessionEstablishmentData).build()

        Assert.assertNotNull(sessionEstablishment.readerKey)
        Assert.assertNotNull(sessionEstablishment.encryptedData)
    }

    @Test
    fun testSessionEstablishmentEncode() {
        val builder = SessionEstablishment.Builder()
        builder.setReaderKey(key)
        builder.setEncryptedData(encryptedData)
        val se = builder.build()

        Assert.assertNotNull(se)

        val seDecode = SessionEstablishment.Builder().decode(seExpected).build()

        Assert.assertArrayEquals(seDecode.encryptedData, se.encryptedData)
        Assert.assertEquals(seDecode.readerKey, se.readerKey)
    }
}