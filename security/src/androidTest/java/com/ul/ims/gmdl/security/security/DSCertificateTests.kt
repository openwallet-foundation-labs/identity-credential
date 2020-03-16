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

package com.ul.ims.gmdl.security.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ul.ims.gmdl.security.issuerdataauthentication.DSCertificate
import com.ul.ims.gmdl.security.issuerdataauthentication.IACACertificate
import com.ul.ims.gmdl.security.issuerdataauthentication.RootCertificateInitialiser
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import java.time.Instant
import java.util.*

class DSCertificateTests {

    //DS Certificate bytes
    private val certificateBytes1 = byteArrayOf(
        0x30.toByte(), 0x82.toByte(), 0x01.toByte(), 0xe1.toByte(), 0x30.toByte(),
        0x82.toByte(), 0x01.toByte(), 0x88.toByte(), 0xa0.toByte(), 0x03.toByte(),
        0x02.toByte(), 0x01.toByte(), 0x02.toByte(), 0x02.toByte(), 0x09.toByte(),
        0x00.toByte(), 0x9f.toByte(), 0x85.toByte(), 0x0f.toByte(), 0xc4.toByte(),
        0xc7.toByte(), 0x79.toByte(), 0xda.toByte(), 0x9c.toByte(), 0x30.toByte(),
        0x09.toByte(), 0x06.toByte(), 0x07.toByte(), 0x2a.toByte(), 0x86.toByte(),
        0x48.toByte(), 0xce.toByte(), 0x3d.toByte(), 0x04.toByte(), 0x01.toByte(),
        0x30.toByte(), 0x43.toByte(), 0x31.toByte(), 0x0b.toByte(), 0x30.toByte(),
        0x09.toByte(), 0x06.toByte(), 0x03.toByte(), 0x55.toByte(), 0x04.toByte(),
        0x06.toByte(), 0x13.toByte(), 0x02.toByte(), 0x4e.toByte(), 0x4c.toByte(),
        0x31.toByte(), 0x0b.toByte(), 0x30.toByte(), 0x09.toByte(), 0x06.toByte(),
        0x03.toByte(), 0x55.toByte(), 0x04.toByte(), 0x0a.toByte(), 0x0c.toByte(),
        0x02.toByte(), 0x55.toByte(), 0x4c.toByte(), 0x31.toByte(), 0x0c.toByte(),
        0x30.toByte(), 0x0a.toByte(), 0x06.toByte(), 0x03.toByte(), 0x55.toByte(),
        0x04.toByte(), 0x0b.toByte(), 0x0c.toByte(), 0x03.toByte(), 0x49.toByte(),
        0x4d.toByte(), 0x53.toByte(), 0x31.toByte(), 0x19.toByte(), 0x30.toByte(),
        0x17.toByte(), 0x06.toByte(), 0x03.toByte(), 0x55.toByte(), 0x04.toByte(),
        0x03.toByte(), 0x0c.toByte(), 0x10.toByte(), 0x49.toByte(), 0x41.toByte(),
        0x43.toByte(), 0x41.toByte(), 0x20.toByte(), 0x55.toByte(), 0x4c.toByte(),
        0x20.toByte(), 0x49.toByte(), 0x4d.toByte(), 0x53.toByte(), 0x20.toByte(),
        0x54.toByte(), 0x65.toByte(), 0x73.toByte(), 0x74.toByte(), 0x30.toByte(),
        0x1e.toByte(), 0x17.toByte(), 0x0d.toByte(), 0x32.toByte(), 0x30.toByte(),
        0x30.toByte(), 0x32.toByte(), 0x31.toByte(), 0x37.toByte(), 0x31.toByte(),
        0x34.toByte(), 0x30.toByte(), 0x37.toByte(), 0x32.toByte(), 0x38.toByte(),
        0x5a.toByte(), 0x17.toByte(), 0x0d.toByte(), 0x32.toByte(), 0x30.toByte(),
        0x30.toByte(), 0x38.toByte(), 0x31.toByte(), 0x38.toByte(), 0x31.toByte(),
        0x34.toByte(), 0x30.toByte(), 0x37.toByte(), 0x32.toByte(), 0x38.toByte(),
        0x5a.toByte(), 0x30.toByte(), 0x43.toByte(), 0x31.toByte(), 0x0b.toByte(),
        0x30.toByte(), 0x09.toByte(), 0x06.toByte(), 0x03.toByte(), 0x55.toByte(),
        0x04.toByte(), 0x06.toByte(), 0x13.toByte(), 0x02.toByte(), 0x4e.toByte(),
        0x4c.toByte(), 0x31.toByte(), 0x0b.toByte(), 0x30.toByte(), 0x09.toByte(),
        0x06.toByte(), 0x03.toByte(), 0x55.toByte(), 0x04.toByte(), 0x0a.toByte(),
        0x0c.toByte(), 0x02.toByte(), 0x55.toByte(), 0x4c.toByte(), 0x31.toByte(),
        0x0c.toByte(), 0x30.toByte(), 0x0a.toByte(), 0x06.toByte(), 0x03.toByte(),
        0x55.toByte(), 0x04.toByte(), 0x0b.toByte(), 0x0c.toByte(), 0x03.toByte(),
        0x49.toByte(), 0x4d.toByte(), 0x53.toByte(), 0x31.toByte(), 0x19.toByte(),
        0x30.toByte(), 0x17.toByte(), 0x06.toByte(), 0x03.toByte(), 0x55.toByte(),
        0x04.toByte(), 0x03.toByte(), 0x0c.toByte(), 0x10.toByte(), 0x49.toByte(),
        0x41.toByte(), 0x44.toByte(), 0x53.toByte(), 0x20.toByte(), 0x55.toByte(),
        0x4c.toByte(), 0x20.toByte(), 0x49.toByte(), 0x4d.toByte(), 0x53.toByte(),
        0x20.toByte(), 0x54.toByte(), 0x65.toByte(), 0x73.toByte(), 0x74.toByte(),
        0x30.toByte(), 0x59.toByte(), 0x30.toByte(), 0x13.toByte(), 0x06.toByte(),
        0x07.toByte(), 0x2a.toByte(), 0x86.toByte(), 0x48.toByte(), 0xce.toByte(),
        0x3d.toByte(), 0x02.toByte(), 0x01.toByte(), 0x06.toByte(), 0x08.toByte(),
        0x2a.toByte(), 0x86.toByte(), 0x48.toByte(), 0xce.toByte(), 0x3d.toByte(),
        0x03.toByte(), 0x01.toByte(), 0x07.toByte(), 0x03.toByte(), 0x42.toByte(),
        0x00.toByte(), 0x04.toByte(), 0x33.toByte(), 0x36.toByte(), 0xc7.toByte(),
        0xa0.toByte(), 0x75.toByte(), 0xee.toByte(), 0xa3.toByte(), 0x80.toByte(),
        0xe4.toByte(), 0x86.toByte(), 0x02.toByte(), 0x86.toByte(), 0xf8.toByte(),
        0xd7.toByte(), 0x89.toByte(), 0x22.toByte(), 0x07.toByte(), 0xe5.toByte(),
        0x8e.toByte(), 0x79.toByte(), 0x7b.toByte(), 0xc4.toByte(), 0x76.toByte(),
        0x2f.toByte(), 0x4c.toByte(), 0x80.toByte(), 0xbd.toByte(), 0x4b.toByte(),
        0x74.toByte(), 0x03.toByte(), 0x03.toByte(), 0xcd.toByte(), 0x52.toByte(),
        0x88.toByte(), 0x8f.toByte(), 0x5c.toByte(), 0x11.toByte(), 0xdb.toByte(),
        0xd2.toByte(), 0x25.toByte(), 0x43.toByte(), 0x37.toByte(), 0xd5.toByte(),
        0x16.toByte(), 0x89.toByte(), 0x5e.toByte(), 0xda.toByte(), 0xd3.toByte(),
        0x37.toByte(), 0x4e.toByte(), 0x72.toByte(), 0x22.toByte(), 0x39.toByte(),
        0x66.toByte(), 0x76.toByte(), 0x3d.toByte(), 0xc6.toByte(), 0x28.toByte(),
        0x68.toByte(), 0x08.toByte(), 0x9b.toByte(), 0x5d.toByte(), 0x1f.toByte(),
        0xc4.toByte(), 0xa3.toByte(), 0x66.toByte(), 0x30.toByte(), 0x64.toByte(),
        0x30.toByte(), 0x1d.toByte(), 0x06.toByte(), 0x03.toByte(), 0x55.toByte(),
        0x1d.toByte(), 0x0e.toByte(), 0x04.toByte(), 0x16.toByte(), 0x04.toByte(),
        0x14.toByte(), 0x6b.toByte(), 0xe8.toByte(), 0xd1.toByte(), 0xec.toByte(),
        0x82.toByte(), 0x9b.toByte(), 0xd9.toByte(), 0x74.toByte(), 0xf8.toByte(),
        0x97.toByte(), 0x8a.toByte(), 0xff.toByte(), 0x00.toByte(), 0x8e.toByte(),
        0xfb.toByte(), 0x1c.toByte(), 0xad.toByte(), 0x98.toByte(), 0xe1.toByte(),
        0x6c.toByte(), 0x30.toByte(), 0x1f.toByte(), 0x06.toByte(), 0x03.toByte(),
        0x55.toByte(), 0x1d.toByte(), 0x23.toByte(), 0x04.toByte(), 0x18.toByte(),
        0x30.toByte(), 0x16.toByte(), 0x80.toByte(), 0x14.toByte(), 0x9a.toByte(),
        0xc9.toByte(), 0xfa.toByte(), 0x8c.toByte(), 0xb4.toByte(), 0xb9.toByte(),
        0x77.toByte(), 0xf9.toByte(), 0xe3.toByte(), 0xdb.toByte(), 0x84.toByte(),
        0x02.toByte(), 0xc2.toByte(), 0xa5.toByte(), 0x98.toByte(), 0x61.toByte(),
        0xb1.toByte(), 0x59.toByte(), 0x89.toByte(), 0x25.toByte(), 0x30.toByte(),
        0x0e.toByte(), 0x06.toByte(), 0x03.toByte(), 0x55.toByte(), 0x1d.toByte(),
        0x0f.toByte(), 0x01.toByte(), 0x01.toByte(), 0xff.toByte(), 0x04.toByte(),
        0x04.toByte(), 0x03.toByte(), 0x02.toByte(), 0x07.toByte(), 0x80.toByte(),
        0x30.toByte(), 0x12.toByte(), 0x06.toByte(), 0x03.toByte(), 0x55.toByte(),
        0x1d.toByte(), 0x25.toByte(), 0x04.toByte(), 0x0b.toByte(), 0x30.toByte(),
        0x09.toByte(), 0x06.toByte(), 0x07.toByte(), 0x28.toByte(), 0x81.toByte(),
        0x8c.toByte(), 0x5d.toByte(), 0x05.toByte(), 0x01.toByte(), 0x02.toByte(),
        0x30.toByte(), 0x09.toByte(), 0x06.toByte(), 0x07.toByte(), 0x2a.toByte(),
        0x86.toByte(), 0x48.toByte(), 0xce.toByte(), 0x3d.toByte(), 0x04.toByte(),
        0x01.toByte(), 0x03.toByte(), 0x48.toByte(), 0x00.toByte(), 0x30.toByte(),
        0x45.toByte(), 0x02.toByte(), 0x21.toByte(), 0x00.toByte(), 0xe2.toByte(),
        0x20.toByte(), 0xd1.toByte(), 0xf6.toByte(), 0xaa.toByte(), 0xe0.toByte(),
        0x03.toByte(), 0x73.toByte(), 0xfe.toByte(), 0x16.toByte(), 0x11.toByte(),
        0x80.toByte(), 0xfe.toByte(), 0x34.toByte(), 0x0e.toByte(), 0x63.toByte(),
        0x49.toByte(), 0x7b.toByte(), 0x53.toByte(), 0xf5.toByte(), 0x7a.toByte(),
        0x3f.toByte(), 0x82.toByte(), 0x23.toByte(), 0xc8.toByte(), 0xc5.toByte(),
        0xfd.toByte(), 0x8a.toByte(), 0x78.toByte(), 0xc8.toByte(), 0xa7.toByte(),
        0xcf.toByte(), 0x02.toByte(), 0x20.toByte(), 0x60.toByte(), 0x0d.toByte(),
        0xf0.toByte(), 0x7b.toByte(), 0x02.toByte(), 0x13.toByte(), 0xd5.toByte(),
        0xff.toByte(), 0x37.toByte(), 0x75.toByte(), 0xc7.toByte(), 0x87.toByte(),
        0x68.toByte(), 0x24.toByte(), 0x45.toByte(), 0x65.toByte(), 0xa7.toByte(),
        0xf3.toByte(), 0xd4.toByte(), 0xb5.toByte(), 0xa2.toByte(), 0x9a.toByte(),
        0x29.toByte(), 0x5a.toByte(), 0xbf.toByte(), 0x8b.toByte(), 0xcd.toByte(),
        0xa5.toByte(), 0x88.toByte(), 0x63.toByte(), 0x68.toByte(), 0xfd.toByte()
    )
    private lateinit var rootCertificatesAndPublicKeys: Map<IACACertificate, PublicKey>

    @Before
    fun setUp() {
        val context : Context = ApplicationProvider.getApplicationContext()

        val rootCertificateInitialiser = RootCertificateInitialiser(context)
        rootCertificatesAndPublicKeys = rootCertificateInitialiser.rootCertificatesAndPublicKeys
    }

    @Test
    fun testDSCertificate() {

        val dsCertificate = DSCertificate(certificateBytes1, rootCertificatesAndPublicKeys, "NL")

        Assert.assertNotNull(dsCertificate.certificate)
        Assert.assertNotNull(dsCertificate.getPublicKey())
        Assert.assertNotNull(dsCertificate.rootCertificate)
        Assert.assertTrue(dsCertificate.version() == 3)
        Assert.assertTrue(dsCertificate.certificate?.issuerDN == dsCertificate.rootCertificate?.certificate?.subjectDN)
        Assert.assertTrue(dsCertificate.rootCertificate?.certificate?.issuerDN == dsCertificate.rootCertificate?.certificate?.subjectDN)
        Assert.assertNotNull(dsCertificate.certificate?.extendedKeyUsage)
        Assert.assertTrue(dsCertificate.isValid(Date.from(Instant.now())))
    }

    @Test
    fun testDSCertificate_NoIssuingCountry() {

        val dsCertificate = DSCertificate(certificateBytes1, rootCertificatesAndPublicKeys, null)

        Assert.assertNotNull(dsCertificate.certificate)
        Assert.assertNotNull(dsCertificate.getPublicKey())
        Assert.assertNotNull(dsCertificate.rootCertificate)
        Assert.assertTrue(dsCertificate.version() == 3)
        Assert.assertTrue(dsCertificate.certificate?.issuerDN == dsCertificate.rootCertificate?.certificate?.subjectDN)
        Assert.assertTrue(dsCertificate.rootCertificate?.certificate?.issuerDN == dsCertificate.rootCertificate?.certificate?.subjectDN)
        Assert.assertNotNull(dsCertificate.certificate?.extendedKeyUsage)
        Assert.assertTrue(dsCertificate.isValid(Date.from(Instant.now())))
    }
}