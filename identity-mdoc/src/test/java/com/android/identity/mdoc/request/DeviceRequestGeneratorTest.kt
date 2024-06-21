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
package com.android.identity.mdoc.request

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.Tstr
import com.android.identity.cbor.toDataItem
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.create
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.Security

// TODO: Add test which generates the exact bytes of TestVectors#ISO_18013_5_ANNEX_D_DEVICE_REQUEST
//

class DeviceRequestGeneratorTest {
    @Before
    fun setUp() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun testDeviceRequestBuilder() {
        val encodedSessionTranscript = Cbor.encode(Bstr(byteArrayOf(0x01, 0x02)))
        val mdlItemsToRequest: MutableMap<String, Map<String, Boolean>> = HashMap()
        val mdlNsItems: MutableMap<String, Boolean> = HashMap()
        mdlNsItems["family_name"] = true
        mdlNsItems["portrait"] = false
        mdlItemsToRequest[MDL_NAMESPACE] = mdlNsItems
        val aamvaNsItems: MutableMap<String, Boolean> = HashMap()
        aamvaNsItems["real_id"] = false
        mdlItemsToRequest[AAMVA_NAMESPACE] = aamvaNsItems
        val mvrItemsToRequest: MutableMap<String, Map<String, Boolean>> = HashMap()
        val mvrNsItems: MutableMap<String, Boolean> = HashMap()
        mvrNsItems["vehicle_number"] = true
        mvrItemsToRequest[MVR_NAMESPACE] = mvrNsItems
        val readerKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val validFrom = Clock.System.now()
        val validUntil = Instant.fromEpochMilliseconds(
            validFrom.toEpochMilliseconds() + 30L * 24 * 60 * 60 * 1000
        )
        val readerCert = X509Cert.create(
            readerKey.publicKey,
            readerKey,
            null,
            Algorithm.ES256,
            "1",
            "CN=Test Key",
            "CN=Test Key",
            validFrom,
            validUntil,
            setOf(),
            listOf()
        )
        val readerCertChain = X509CertChain(listOf(readerCert))
        val mdlRequestInfo: MutableMap<String, ByteArray> = HashMap()
        mdlRequestInfo["foo"] = Cbor.encode(Tstr("bar"))
        mdlRequestInfo["bar"] = Cbor.encode(42.toDataItem)
        val encodedDeviceRequest = DeviceRequestGenerator(encodedSessionTranscript)
            .addDocumentRequest(
                MDL_DOCTYPE,
                mdlItemsToRequest,
                mdlRequestInfo,
                readerKey,
                readerKey.curve.defaultSigningAlgorithm,
                readerCertChain
            )
            .addDocumentRequest(
                MVR_DOCTYPE,
                mvrItemsToRequest,
                null,
                readerKey,
                readerKey.curve.defaultSigningAlgorithm,
                readerCertChain
            )
            .generate()
        val deviceRequest = DeviceRequestParser(
            encodedDeviceRequest,
            encodedSessionTranscript
        )
            .parse()
        Assert.assertEquals("1.0", deviceRequest.version)
        val documentRequests = deviceRequest.docRequests
        Assert.assertEquals(2, documentRequests.size.toLong())
        val it = deviceRequest.docRequests.iterator()
        var docRequest = it.next()
        Assert.assertTrue(docRequest.readerAuthenticated)
        Assert.assertEquals(MDL_DOCTYPE, docRequest.docType)
        Assert.assertEquals(2, docRequest.namespaces.size.toLong())
        Assert.assertEquals(2, docRequest.getEntryNames(MDL_NAMESPACE).size.toLong())
        Assert.assertTrue(docRequest.getIntentToRetain(MDL_NAMESPACE, "family_name"))
        Assert.assertFalse(docRequest.getIntentToRetain(MDL_NAMESPACE, "portrait"))
        try {
            docRequest.getIntentToRetain(MDL_NAMESPACE, "non-existent")
            Assert.fail()
        } catch (_: IllegalArgumentException) {}
        Assert.assertEquals(1, docRequest.getEntryNames(AAMVA_NAMESPACE).size.toLong())
        Assert.assertFalse(docRequest.getIntentToRetain(AAMVA_NAMESPACE, "real_id"))
        try {
            docRequest.getIntentToRetain("non-existent", "non-existent")
            Assert.fail()
        } catch (_: IllegalArgumentException) {}
        try {
            docRequest.getEntryNames("non-existent")
            Assert.fail()
        } catch (_: IllegalArgumentException) {}
        Assert.assertEquals(1, docRequest.readerCertificateChain!!.certificates.size.toLong())
        Assert.assertEquals(readerCertChain, docRequest.readerCertificateChain)
        val requestInfo = docRequest.requestInfo
        Assert.assertNotNull(requestInfo)
        Assert.assertEquals(2, requestInfo.keys.size.toLong())
        Assert.assertArrayEquals(Cbor.encode(Tstr("bar")), requestInfo["foo"])
        Assert.assertArrayEquals(Cbor.encode(42.toDataItem), requestInfo["bar"])
        docRequest = it.next()
        Assert.assertTrue(docRequest.readerAuthenticated)
        Assert.assertEquals(MVR_DOCTYPE, docRequest.docType)
        Assert.assertEquals(1, docRequest.namespaces.size.toLong())
        Assert.assertEquals(1, docRequest.getEntryNames(MVR_NAMESPACE).size.toLong())
        Assert.assertTrue(docRequest.getIntentToRetain(MVR_NAMESPACE, "vehicle_number"))
        Assert.assertEquals(1, docRequest.readerCertificateChain!!.certificates.size.toLong())
        Assert.assertEquals(readerCertChain, docRequest.readerCertificateChain)
        Assert.assertEquals(0, docRequest.requestInfo.size.toLong())
    }

    companion object {
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
        private const val AAMVA_NAMESPACE = "org.aamva.18013.5.1"
        private const val MVR_DOCTYPE = "org.iso.18013.7.1.mVR"
        private const val MVR_NAMESPACE = "org.iso.18013.7.1"
    }
}
