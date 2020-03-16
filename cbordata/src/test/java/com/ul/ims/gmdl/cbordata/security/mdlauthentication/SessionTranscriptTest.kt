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

package com.ul.ims.gmdl.cbordata.security.mdlauthentication

import org.junit.Assert
import org.junit.Test

class SessionTranscriptTest {

    val readerKey = byteArrayOf(
        0xa4.toByte(), 0x01.toByte(), 0x02.toByte(), 0x20.toByte(), 0x01.toByte(),
        0x21.toByte(), 0x58.toByte(), 0x20.toByte(), 0x79.toByte(), 0x8c.toByte(),
        0x08.toByte(), 0xe0.toByte(), 0xb9.toByte(), 0x54.toByte(), 0x03.toByte(),
        0x86.toByte(), 0x41.toByte(), 0x50.toByte(), 0xc6.toByte(), 0x3b.toByte(),
        0x69.toByte(), 0xc9.toByte(), 0xc1.toByte(), 0x18.toByte(), 0xa7.toByte(),
        0xa9.toByte(), 0x46.toByte(), 0xe1.toByte(), 0xf6.toByte(), 0xa2.toByte(),
        0x93.toByte(), 0x25.toByte(), 0x18.toByte(), 0x83.toByte(), 0xfc.toByte(),
        0x61.toByte(), 0x91.toByte(), 0xca.toByte(), 0xb7.toByte(), 0xf4.toByte(),
        0x22.toByte(), 0x58.toByte(), 0x20.toByte(), 0x5c.toByte(), 0x94.toByte(),
        0x1d.toByte(), 0xf2.toByte(), 0xac.toByte(), 0x31.toByte(), 0x3a.toByte(),
        0x94.toByte(), 0x9a.toByte(), 0x24.toByte(), 0xf7.toByte(), 0x19.toByte(),
        0x33.toByte(), 0x3c.toByte(), 0x30.toByte(), 0x56.toByte(), 0x38.toByte(),
        0x42.toByte(), 0xfe.toByte(), 0x46.toByte(), 0x6e.toByte(), 0x55.toByte(),
        0x82.toByte(), 0x27.toByte(), 0xfc.toByte(), 0x79.toByte(), 0x84.toByte(),
        0xf2.toByte(), 0x11.toByte(), 0xe0.toByte(), 0x62.toByte(), 0x7f.toByte()
    )

    val deviceEngagement = byteArrayOf(
        0x84.toByte(), 0x01.toByte(), 0x82.toByte(), 0xa4.toByte(), 0x01.toByte(),
        0x02.toByte(), 0x20.toByte(), 0x01.toByte(), 0x21.toByte(), 0x58.toByte(),
        0x20.toByte(), 0x26.toByte(), 0x37.toByte(), 0x4c.toByte(), 0x7d.toByte(),
        0x29.toByte(), 0x98.toByte(), 0x2b.toByte(), 0x6f.toByte(), 0x81.toByte(),
        0xe0.toByte(), 0xb4.toByte(), 0x29.toByte(), 0xf2.toByte(), 0x53.toByte(),
        0x42.toByte(), 0xcc.toByte(), 0xdb.toByte(), 0x9f.toByte(), 0x83.toByte(),
        0x52.toByte(), 0x33.toByte(), 0x5f.toByte(), 0x33.toByte(), 0x25.toByte(),
        0x59.toByte(), 0x07.toByte(), 0x6c.toByte(), 0xd0.toByte(), 0x77.toByte(),
        0x7f.toByte(), 0x55.toByte(), 0xc6.toByte(), 0x22.toByte(), 0x58.toByte(),
        0x20.toByte(), 0xf9.toByte(), 0xdd.toByte(), 0x1c.toByte(), 0x81.toByte(),
        0x22.toByte(), 0x58.toByte(), 0xe7.toByte(), 0x8e.toByte(), 0x87.toByte(),
        0x22.toByte(), 0x37.toByte(), 0x7b.toByte(), 0x3c.toByte(), 0x8c.toByte(),
        0x44.toByte(), 0x7d.toByte(), 0x8f.toByte(), 0x5a.toByte(), 0x7a.toByte(),
        0x2d.toByte(), 0xfa.toByte(), 0xfe.toByte(), 0x66.toByte(), 0x53.toByte(),
        0x8c.toByte(), 0x3e.toByte(), 0x14.toByte(), 0xe9.toByte(), 0xf4.toByte(),
        0xda.toByte(), 0x28.toByte(), 0xd6.toByte(), 0x01.toByte(), 0x81.toByte(),
        0x83.toByte(), 0x02.toByte(), 0x01.toByte(), 0xa2.toByte(), 0x00.toByte(),
        0xf5.toByte(), 0x01.toByte(), 0xf5.toByte(), 0xa0.toByte()
    )

    val expected = byteArrayOf(
        0x82.toByte(), 0xd8.toByte(), 0x18.toByte(), 0x58.toByte(), 0x59.toByte(),
        0x84.toByte(), 0x01.toByte(), 0x82.toByte(), 0xa4.toByte(), 0x01.toByte(),
        0x02.toByte(), 0x20.toByte(), 0x01.toByte(), 0x21.toByte(), 0x58.toByte(),
        0x20.toByte(), 0x26.toByte(), 0x37.toByte(), 0x4c.toByte(), 0x7d.toByte(),
        0x29.toByte(), 0x98.toByte(), 0x2b.toByte(), 0x6f.toByte(), 0x81.toByte(),
        0xe0.toByte(), 0xb4.toByte(), 0x29.toByte(), 0xf2.toByte(), 0x53.toByte(),
        0x42.toByte(), 0xcc.toByte(), 0xdb.toByte(), 0x9f.toByte(), 0x83.toByte(),
        0x52.toByte(), 0x33.toByte(), 0x5f.toByte(), 0x33.toByte(), 0x25.toByte(),
        0x59.toByte(), 0x07.toByte(), 0x6c.toByte(), 0xd0.toByte(), 0x77.toByte(),
        0x7f.toByte(), 0x55.toByte(), 0xc6.toByte(), 0x22.toByte(), 0x58.toByte(),
        0x20.toByte(), 0xf9.toByte(), 0xdd.toByte(), 0x1c.toByte(), 0x81.toByte(),
        0x22.toByte(), 0x58.toByte(), 0xe7.toByte(), 0x8e.toByte(), 0x87.toByte(),
        0x22.toByte(), 0x37.toByte(), 0x7b.toByte(), 0x3c.toByte(), 0x8c.toByte(),
        0x44.toByte(), 0x7d.toByte(), 0x8f.toByte(), 0x5a.toByte(), 0x7a.toByte(),
        0x2d.toByte(), 0xfa.toByte(), 0xfe.toByte(), 0x66.toByte(), 0x53.toByte(),
        0x8c.toByte(), 0x3e.toByte(), 0x14.toByte(), 0xe9.toByte(), 0xf4.toByte(),
        0xda.toByte(), 0x28.toByte(), 0xd6.toByte(), 0x01.toByte(), 0x81.toByte(),
        0x83.toByte(), 0x02.toByte(), 0x01.toByte(), 0xa2.toByte(), 0x00.toByte(),
        0xf5.toByte(), 0x01.toByte(), 0xf5.toByte(), 0xa0.toByte(), 0xd8.toByte(),
        0x18.toByte(), 0x58.toByte(), 0x4b.toByte(), 0xa4.toByte(), 0x01.toByte(),
        0x02.toByte(), 0x20.toByte(), 0x01.toByte(), 0x21.toByte(), 0x58.toByte(),
        0x20.toByte(), 0x79.toByte(), 0x8c.toByte(), 0x08.toByte(), 0xe0.toByte(),
        0xb9.toByte(), 0x54.toByte(), 0x03.toByte(), 0x86.toByte(), 0x41.toByte(),
        0x50.toByte(), 0xc6.toByte(), 0x3b.toByte(), 0x69.toByte(), 0xc9.toByte(),
        0xc1.toByte(), 0x18.toByte(), 0xa7.toByte(), 0xa9.toByte(), 0x46.toByte(),
        0xe1.toByte(), 0xf6.toByte(), 0xa2.toByte(), 0x93.toByte(), 0x25.toByte(),
        0x18.toByte(), 0x83.toByte(), 0xfc.toByte(), 0x61.toByte(), 0x91.toByte(),
        0xca.toByte(), 0xb7.toByte(), 0xf4.toByte(), 0x22.toByte(), 0x58.toByte(),
        0x20.toByte(), 0x5c.toByte(), 0x94.toByte(), 0x1d.toByte(), 0xf2.toByte(),
        0xac.toByte(), 0x31.toByte(), 0x3a.toByte(), 0x94.toByte(), 0x9a.toByte(),
        0x24.toByte(), 0xf7.toByte(), 0x19.toByte(), 0x33.toByte(), 0x3c.toByte(),
        0x30.toByte(), 0x56.toByte(), 0x38.toByte(), 0x42.toByte(), 0xfe.toByte(),
        0x46.toByte(), 0x6e.toByte(), 0x55.toByte(), 0x82.toByte(), 0x27.toByte(),
        0xfc.toByte(), 0x79.toByte(), 0x84.toByte(), 0xf2.toByte(), 0x11.toByte(),
        0xe0.toByte(), 0x62.toByte(), 0x7f.toByte()
    )

    @Test
    fun testEncode() {
        val sessionTranscript= SessionTranscript.Builder()
            .setReaderKey(readerKey)
            .setDeviceEngagement(deviceEngagement)
            .build()

        val encoded = sessionTranscript.encode()
        Assert.assertNotNull(encoded)
        Assert.assertArrayEquals(expected, encoded)
    }
}