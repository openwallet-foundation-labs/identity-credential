/*
 * Copyright 2022 The Android Open Source Project
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
package org.multipaz.mdoc.origininfo

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DiagnosticOption
import kotlin.test.Test
import kotlin.test.assertEquals

class OriginInfoTest {
    @Test
    fun testOriginInfoDomainOrigin() {
        val info = OriginInfoDomain("https://foo.com/bar")
        val decoded = OriginInfoDomain.decode(info.encode())
        assertEquals("https://foo.com/bar", decoded!!.url)
        assertEquals(
            """{
  "cat": 1,
  "type": 1,
  "details": {
    "domain": "https://foo.com/bar"
  }
}""",
            Cbor.toDiagnostics(info.encode(), setOf(DiagnosticOption.PRETTY_PRINT))
        )
    }
}
