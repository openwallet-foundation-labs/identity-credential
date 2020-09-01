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

package com.ul.ims.gmdl.security.sessionEncryption.verifier

import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.deviceEngagement.security.Security
import com.ul.ims.gmdl.security.TestUtils.CHIPER_SUITE_IDENT
import com.ul.ims.gmdl.security.TestUtils.DE_VERSION
import com.ul.ims.gmdl.security.TestUtils.genCoseKey
import com.ul.ims.gmdl.security.TestUtils.genEphemeralKeyPair
import com.ul.ims.gmdl.security.sessionencryption.verifier.VerifierSession
import org.junit.Assert
import org.junit.Test
import java.security.interfaces.ECPublicKey

class VerifierSessionTest {

    @Test
    fun getReaderPublicKeyTest() {
        val mEphemeralKeyPair = genEphemeralKeyPair()
        val pk = mEphemeralKeyPair?.public
        val security = Security.Builder()
            .setCoseKey(genCoseKey())
            .setCipherSuiteIdent(CHIPER_SUITE_IDENT)
            .build()

        // Device engagement for QR Code
        val deBuilder = DeviceEngagement.Builder()

        deBuilder.version(DE_VERSION)
        deBuilder.security(security)

        pk?.let {
            val deviceEngagement = deBuilder.build()
            val session = VerifierSession(it, deviceEngagement)

            Assert.assertNotNull(session.getReaderPublicKey())
            Assert.assertTrue(session.getReaderPublicKey() is ECPublicKey)
        }
    }
}