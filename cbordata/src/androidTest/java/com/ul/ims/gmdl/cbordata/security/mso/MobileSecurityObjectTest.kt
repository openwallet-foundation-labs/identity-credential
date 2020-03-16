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

package com.ul.ims.gmdl.cbordata.security.mso

import com.ul.ims.gmdl.cbordata.cryptoUtils.HashAlgorithms
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.cbordata.security.CoseKey
import com.ul.ims.gmdl.cbordata.security.namespace.IMsoNameSpace
import com.ul.ims.gmdl.cbordata.security.namespace.MsoMdlNamespace
import com.ul.ims.gmdl.cbordata.utils.DateUtils
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.junit.Assert.*
import org.junit.Test

class MobileSecurityObjectTest {
    private val expectedMobileSecurityObject = byteArrayOf(
        0xa5.toByte(), 0x6f.toByte(), 0x64.toByte(), 0x69.toByte(), 0x67.toByte(),
        0x65.toByte(), 0x73.toByte(), 0x74.toByte(), 0x41.toByte(), 0x6c.toByte(),
        0x67.toByte(), 0x6f.toByte(), 0x72.toByte(), 0x69.toByte(), 0x74.toByte(),
        0x68.toByte(), 0x6d.toByte(), 0x67.toByte(), 0x53.toByte(), 0x48.toByte(),
        0x41.toByte(), 0x2d.toByte(), 0x32.toByte(), 0x35.toByte(), 0x36.toByte(),
        0x6c.toByte(), 0x76.toByte(), 0x61.toByte(), 0x6c.toByte(), 0x75.toByte(),
        0x65.toByte(), 0x44.toByte(), 0x69.toByte(), 0x67.toByte(), 0x65.toByte(),
        0x73.toByte(), 0x74.toByte(), 0x73.toByte(), 0xa1.toByte(), 0x6a.toByte(),
        0x6e.toByte(), 0x61.toByte(), 0x6d.toByte(), 0x65.toByte(), 0x53.toByte(),
        0x70.toByte(), 0x61.toByte(), 0x63.toByte(), 0x65.toByte(), 0x73.toByte(),
        0xa1.toByte(), 0x71.toByte(), 0x6f.toByte(), 0x72.toByte(), 0x67.toByte(),
        0x2e.toByte(), 0x69.toByte(), 0x73.toByte(), 0x6f.toByte(), 0x2e.toByte(),
        0x31.toByte(), 0x38.toByte(), 0x30.toByte(), 0x31.toByte(), 0x33.toByte(),
        0x2e.toByte(), 0x35.toByte(), 0x2e.toByte(), 0x31.toByte(), 0xad.toByte(),
        0x01.toByte(), 0x58.toByte(), 0x20.toByte(), 0xaf.toByte(), 0x30.toByte(),
        0x7a.toByte(), 0xd5.toByte(), 0x9c.toByte(), 0x28.toByte(), 0xc5.toByte(),
        0x03.toByte(), 0x2c.toByte(), 0x1a.toByte(), 0x49.toByte(), 0x39.toByte(),
        0x4e.toByte(), 0x0b.toByte(), 0xce.toByte(), 0x7c.toByte(), 0xb6.toByte(),
        0x73.toByte(), 0xa5.toByte(), 0x33.toByte(), 0xd9.toByte(), 0xad.toByte(),
        0x12.toByte(), 0xbd.toByte(), 0x4c.toByte(), 0xa9.toByte(), 0x80.toByte(),
        0xf6.toByte(), 0xf1.toByte(), 0x2d.toByte(), 0xb5.toByte(), 0xbe.toByte(),
        0x02.toByte(), 0x58.toByte(), 0x20.toByte(), 0x76.toByte(), 0xaa.toByte(),
        0xc7.toByte(), 0xb5.toByte(), 0x2f.toByte(), 0x93.toByte(), 0x8a.toByte(),
        0x4a.toByte(), 0xea.toByte(), 0xe3.toByte(), 0x1d.toByte(), 0x79.toByte(),
        0x6b.toByte(), 0xa5.toByte(), 0xae.toByte(), 0xb2.toByte(), 0xdb.toByte(),
        0xe0.toByte(), 0xc9.toByte(), 0xd9.toByte(), 0x6a.toByte(), 0xd7.toByte(),
        0x5b.toByte(), 0x18.toByte(), 0xd7.toByte(), 0x5a.toByte(), 0xfa.toByte(),
        0xe8.toByte(), 0x75.toByte(), 0xa7.toByte(), 0x9c.toByte(), 0x76.toByte(),
        0x03.toByte(), 0x58.toByte(), 0x20.toByte(), 0xe2.toByte(), 0x50.toByte(),
        0x3b.toByte(), 0x90.toByte(), 0x5a.toByte(), 0x17.toByte(), 0x54.toByte(),
        0xef.toByte(), 0x48.toByte(), 0x3b.toByte(), 0xf5.toByte(), 0xdd.toByte(),
        0x42.toByte(), 0x9c.toByte(), 0x3a.toByte(), 0x9b.toByte(), 0xc6.toByte(),
        0x6b.toByte(), 0xdf.toByte(), 0x7b.toByte(), 0x3d.toByte(), 0x1c.toByte(),
        0x08.toByte(), 0x0c.toByte(), 0xfd.toByte(), 0xeb.toByte(), 0x4e.toByte(),
        0xc2.toByte(), 0x7a.toByte(), 0xad.toByte(), 0xee.toByte(), 0x63.toByte(),
        0x04.toByte(), 0x58.toByte(), 0x20.toByte(), 0xa9.toByte(), 0x63.toByte(),
        0x38.toByte(), 0x2a.toByte(), 0xa8.toByte(), 0xba.toByte(), 0x4c.toByte(),
        0x52.toByte(), 0xda.toByte(), 0x52.toByte(), 0x2b.toByte(), 0xc3.toByte(),
        0x1d.toByte(), 0x41.toByte(), 0xe2.toByte(), 0x56.toByte(), 0x49.toByte(),
        0x15.toByte(), 0xf5.toByte(), 0x7c.toByte(), 0xb1.toByte(), 0x44.toByte(),
        0xcb.toByte(), 0x7c.toByte(), 0x69.toByte(), 0xf4.toByte(), 0x7a.toByte(),
        0x85.toByte(), 0xb4.toByte(), 0x4a.toByte(), 0x7e.toByte(), 0x59.toByte(),
        0x05.toByte(), 0x58.toByte(), 0x20.toByte(), 0x20.toByte(), 0xee.toByte(),
        0x71.toByte(), 0x41.toByte(), 0x91.toByte(), 0x90.toByte(), 0x1e.toByte(),
        0x77.toByte(), 0x7a.toByte(), 0xe7.toByte(), 0xf4.toByte(), 0x5b.toByte(),
        0x2e.toByte(), 0xb6.toByte(), 0x55.toByte(), 0x4a.toByte(), 0xca.toByte(),
        0x8d.toByte(), 0xbb.toByte(), 0x10.toByte(), 0xf3.toByte(), 0x09.toByte(),
        0x3b.toByte(), 0xe2.toByte(), 0x67.toByte(), 0xdc.toByte(), 0xe2.toByte(),
        0xc7.toByte(), 0xf2.toByte(), 0x04.toByte(), 0xd8.toByte(), 0xe1.toByte(),
        0x06.toByte(), 0x58.toByte(), 0x20.toByte(), 0xb5.toByte(), 0x33.toByte(),
        0x92.toByte(), 0xe5.toByte(), 0x47.toByte(), 0xba.toByte(), 0x0a.toByte(),
        0x23.toByte(), 0x69.toByte(), 0xcb.toByte(), 0xa5.toByte(), 0xac.toByte(),
        0x6d.toByte(), 0x91.toByte(), 0xf4.toByte(), 0x30.toByte(), 0xfa.toByte(),
        0x8f.toByte(), 0xb7.toByte(), 0x6f.toByte(), 0x0c.toByte(), 0x4d.toByte(),
        0xf9.toByte(), 0x0f.toByte(), 0xcb.toByte(), 0x57.toByte(), 0x91.toByte(),
        0xb6.toByte(), 0xff.toByte(), 0x03.toByte(), 0x8e.toByte(), 0xb6.toByte(),
        0x07.toByte(), 0x58.toByte(), 0x20.toByte(), 0x4e.toByte(), 0x0a.toByte(),
        0xd3.toByte(), 0x33.toByte(), 0xb3.toByte(), 0xbf.toByte(), 0xd2.toByte(),
        0x42.toByte(), 0xdf.toByte(), 0x95.toByte(), 0x52.toByte(), 0xe2.toByte(),
        0x95.toByte(), 0xa4.toByte(), 0xd8.toByte(), 0x5e.toByte(), 0xfd.toByte(),
        0x82.toByte(), 0xe3.toByte(), 0x49.toByte(), 0x45.toByte(), 0x6e.toByte(),
        0xa1.toByte(), 0x0f.toByte(), 0xa8.toByte(), 0x0e.toByte(), 0xed.toByte(),
        0xd6.toByte(), 0xdc.toByte(), 0x31.toByte(), 0x95.toByte(), 0xc0.toByte(),
        0x08.toByte(), 0x58.toByte(), 0x20.toByte(), 0x46.toByte(), 0x7a.toByte(),
        0xc9.toByte(), 0x1e.toByte(), 0x0a.toByte(), 0xcd.toByte(), 0xea.toByte(),
        0xc8.toByte(), 0x5d.toByte(), 0x82.toByte(), 0x72.toByte(), 0x40.toByte(),
        0x1f.toByte(), 0x87.toByte(), 0x49.toByte(), 0xbc.toByte(), 0x2e.toByte(),
        0x1b.toByte(), 0x3f.toByte(), 0x46.toByte(), 0x38.toByte(), 0x5e.toByte(),
        0x5b.toByte(), 0x91.toByte(), 0xdb.toByte(), 0xb7.toByte(), 0x1d.toByte(),
        0x8b.toByte(), 0x9a.toByte(), 0x82.toByte(), 0x61.toByte(), 0xee.toByte(),
        0x0a.toByte(), 0x58.toByte(), 0x20.toByte(), 0xbf.toByte(), 0xad.toByte(),
        0x1f.toByte(), 0x0b.toByte(), 0xa4.toByte(), 0x2f.toByte(), 0x3f.toByte(),
        0xe5.toByte(), 0x70.toByte(), 0x68.toByte(), 0xa2.toByte(), 0xaf.toByte(),
        0xd4.toByte(), 0xf2.toByte(), 0x05.toByte(), 0x1e.toByte(), 0xa6.toByte(),
        0x0b.toByte(), 0xb9.toByte(), 0x1c.toByte(), 0xb0.toByte(), 0x81.toByte(),
        0x3f.toByte(), 0xe9.toByte(), 0x8b.toByte(), 0x32.toByte(), 0x3d.toByte(),
        0x96.toByte(), 0x9e.toByte(), 0x21.toByte(), 0x8b.toByte(), 0x45.toByte(),
        0x0b.toByte(), 0x58.toByte(), 0x20.toByte(), 0x54.toByte(), 0x1e.toByte(),
        0x4d.toByte(), 0xe6.toByte(), 0x8c.toByte(), 0xdd.toByte(), 0x21.toByte(),
        0x6d.toByte(), 0xa9.toByte(), 0xae.toByte(), 0xc2.toByte(), 0x2c.toByte(),
        0x91.toByte(), 0x22.toByte(), 0xe1.toByte(), 0xd3.toByte(), 0xc2.toByte(),
        0x5d.toByte(), 0x38.toByte(), 0xcf.toByte(), 0xa2.toByte(), 0x64.toByte(),
        0x55.toByte(), 0xdf.toByte(), 0xf5.toByte(), 0x88.toByte(), 0x2c.toByte(),
        0xc2.toByte(), 0xc3.toByte(), 0x71.toByte(), 0xf5.toByte(), 0x14.toByte(),
        0x13.toByte(), 0x58.toByte(), 0x20.toByte(), 0x41.toByte(), 0x31.toByte(),
        0x4b.toByte(), 0x90.toByte(), 0x9e.toByte(), 0xd3.toByte(), 0x60.toByte(),
        0x16.toByte(), 0xcf.toByte(), 0x90.toByte(), 0x42.toByte(), 0xe7.toByte(),
        0xb9.toByte(), 0x4a.toByte(), 0xac.toByte(), 0x41.toByte(), 0x95.toByte(),
        0x5d.toByte(), 0x45.toByte(), 0x9a.toByte(), 0xb2.toByte(), 0x58.toByte(),
        0xa1.toByte(), 0x8d.toByte(), 0x4d.toByte(), 0x87.toByte(), 0xad.toByte(),
        0x58.toByte(), 0x17.toByte(), 0xd6.toByte(), 0xc6.toByte(), 0x8b.toByte(),
        0x17.toByte(), 0x58.toByte(), 0x20.toByte(), 0xd5.toByte(), 0x36.toByte(),
        0x40.toByte(), 0x8a.toByte(), 0x50.toByte(), 0xec.toByte(), 0x28.toByte(),
        0x18.toByte(), 0xe4.toByte(), 0x26.toByte(), 0x72.toByte(), 0x70.toByte(),
        0x78.toByte(), 0xbd.toByte(), 0x44.toByte(), 0x29.toByte(), 0xa2.toByte(),
        0xd9.toByte(), 0xa0.toByte(), 0xe0.toByte(), 0x61.toByte(), 0xfe.toByte(),
        0xcd.toByte(), 0x46.toByte(), 0xf5.toByte(), 0xcf.toByte(), 0xda.toByte(),
        0xa7.toByte(), 0x7a.toByte(), 0xda.toByte(), 0x95.toByte(), 0xc0.toByte(),
        0x18.toByte(), 0x18.toByte(), 0x58.toByte(), 0x20.toByte(), 0x58.toByte(),
        0x0b.toByte(), 0xae.toByte(), 0x0e.toByte(), 0xcf.toByte(), 0x9a.toByte(),
        0x1c.toByte(), 0x4d.toByte(), 0x16.toByte(), 0x25.toByte(), 0x86.toByte(),
        0xd7.toByte(), 0x52.toByte(), 0x06.toByte(), 0x15.toByte(), 0xe2.toByte(),
        0xa7.toByte(), 0xac.toByte(), 0x14.toByte(), 0x58.toByte(), 0x49.toByte(),
        0x67.toByte(), 0xfc.toByte(), 0x9b.toByte(), 0xbe.toByte(), 0x83.toByte(),
        0x73.toByte(), 0xe5.toByte(), 0x93.toByte(), 0x02.toByte(), 0xca.toByte(),
        0x98.toByte(), 0x69.toByte(), 0x64.toByte(), 0x65.toByte(), 0x76.toByte(),
        0x69.toByte(), 0x63.toByte(), 0x65.toByte(), 0x4b.toByte(), 0x65.toByte(),
        0x79.toByte(), 0xa4.toByte(), 0x01.toByte(), 0x02.toByte(), 0x20.toByte(),
        0x01.toByte(), 0x21.toByte(), 0x58.toByte(), 0x20.toByte(), 0x91.toByte(),
        0x90.toByte(), 0x2b.toByte(), 0x05.toByte(), 0x46.toByte(), 0xb7.toByte(),
        0x82.toByte(), 0xfc.toByte(), 0x35.toByte(), 0xbd.toByte(), 0x9e.toByte(),
        0x00.toByte(), 0x76.toByte(), 0x94.toByte(), 0x5c.toByte(), 0x79.toByte(),
        0x1f.toByte(), 0xee.toByte(), 0x62.toByte(), 0xf6.toByte(), 0x51.toByte(),
        0xdd.toByte(), 0x9c.toByte(), 0x7f.toByte(), 0xf2.toByte(), 0x29.toByte(),
        0xd4.toByte(), 0x7c.toByte(), 0x44.toByte(), 0x43.toByte(), 0x72.toByte(),
        0x6e.toByte(), 0x22.toByte(), 0x58.toByte(), 0x20.toByte(), 0xb5.toByte(),
        0x55.toByte(), 0x73.toByte(), 0xf2.toByte(), 0x3e.toByte(), 0x59.toByte(),
        0x37.toByte(), 0x87.toByte(), 0x63.toByte(), 0xa5.toByte(), 0x07.toByte(),
        0x80.toByte(), 0x20.toByte(), 0x5c.toByte(), 0xff.toByte(), 0x15.toByte(),
        0xe4.toByte(), 0xe6.toByte(), 0x96.toByte(), 0xd7.toByte(), 0x00.toByte(),
        0xb5.toByte(), 0xf9.toByte(), 0xe2.toByte(), 0x64.toByte(), 0x58.toByte(),
        0x18.toByte(), 0xf7.toByte(), 0x4f.toByte(), 0xf1.toByte(), 0x8e.toByte(),
        0x86.toByte(), 0x67.toByte(), 0x64.toByte(), 0x6f.toByte(), 0x63.toByte(),
        0x54.toByte(), 0x79.toByte(), 0x70.toByte(), 0x65.toByte(), 0x75.toByte(),
        0x6f.toByte(), 0x72.toByte(), 0x67.toByte(), 0x2e.toByte(), 0x69.toByte(),
        0x73.toByte(), 0x6f.toByte(), 0x2e.toByte(), 0x31.toByte(), 0x38.toByte(),
        0x30.toByte(), 0x31.toByte(), 0x33.toByte(), 0x2e.toByte(), 0x35.toByte(),
        0x2e.toByte(), 0x31.toByte(), 0x2e.toByte(), 0x6d.toByte(), 0x44.toByte(),
        0x4c.toByte(), 0x6c.toByte(), 0x76.toByte(), 0x61.toByte(), 0x6c.toByte(),
        0x69.toByte(), 0x64.toByte(), 0x69.toByte(), 0x74.toByte(), 0x79.toByte(),
        0x49.toByte(), 0x6e.toByte(), 0x66.toByte(), 0x6f.toByte(), 0xa3.toByte(),
        0x66.toByte(), 0x73.toByte(), 0x69.toByte(), 0x67.toByte(), 0x6e.toByte(),
        0x65.toByte(), 0x64.toByte(), 0xc0.toByte(), 0x74.toByte(), 0x32.toByte(),
        0x30.toByte(), 0x31.toByte(), 0x37.toByte(), 0x2d.toByte(), 0x30.toByte(),
        0x31.toByte(), 0x2d.toByte(), 0x30.toByte(), 0x32.toByte(), 0x54.toByte(),
        0x30.toByte(), 0x30.toByte(), 0x3a.toByte(), 0x30.toByte(), 0x30.toByte(),
        0x3a.toByte(), 0x30.toByte(), 0x30.toByte(), 0x5a.toByte(), 0x69.toByte(),
        0x76.toByte(), 0x61.toByte(), 0x6c.toByte(), 0x69.toByte(), 0x64.toByte(),
        0x46.toByte(), 0x72.toByte(), 0x6f.toByte(), 0x6d.toByte(), 0xc0.toByte(),
        0x74.toByte(), 0x32.toByte(), 0x30.toByte(), 0x31.toByte(), 0x37.toByte(),
        0x2d.toByte(), 0x30.toByte(), 0x31.toByte(), 0x2d.toByte(), 0x30.toByte(),
        0x32.toByte(), 0x54.toByte(), 0x30.toByte(), 0x30.toByte(), 0x3a.toByte(),
        0x30.toByte(), 0x30.toByte(), 0x3a.toByte(), 0x30.toByte(), 0x30.toByte(),
        0x5a.toByte(), 0x6a.toByte(), 0x76.toByte(), 0x61.toByte(), 0x6c.toByte(),
        0x69.toByte(), 0x64.toByte(), 0x55.toByte(), 0x6e.toByte(), 0x74.toByte(),
        0x69.toByte(), 0x6c.toByte(), 0xc0.toByte(), 0x74.toByte(), 0x32.toByte(),
        0x30.toByte(), 0x32.toByte(), 0x37.toByte(), 0x2d.toByte(), 0x30.toByte(),
        0x31.toByte(), 0x2d.toByte(), 0x30.toByte(), 0x32.toByte(), 0x54.toByte(),
        0x30.toByte(), 0x30.toByte(), 0x3a.toByte(), 0x30.toByte(), 0x30.toByte(),
        0x3a.toByte(), 0x30.toByte(), 0x30.toByte(), 0x5a.toByte()
    )

    private val digestAlgorithm: ASN1ObjectIdentifier = ASN1ObjectIdentifier.getInstance(
        NISTObjectIdentifiers.id_sha256
    )

    private val mdlNameSpace = MsoMdlNamespace(
        "org.iso.18013.5.1",
        linkedMapOf(
            Pair(
                1,
                byteArrayOf(
                    0xAF.toByte(), 0x30.toByte(), 0x7A.toByte(), 0xD5.toByte(), 0x9C.toByte(),
                    0x28.toByte(), 0xC5.toByte(), 0x03.toByte(), 0x2C.toByte(), 0x1A.toByte(),
                    0x49.toByte(), 0x39.toByte(), 0x4E.toByte(), 0x0B.toByte(), 0xCE.toByte(),
                    0x7C.toByte(), 0xB6.toByte(), 0x73.toByte(), 0xA5.toByte(), 0x33.toByte(),
                    0xD9.toByte(), 0xAD.toByte(), 0x12.toByte(), 0xBD.toByte(), 0x4C.toByte(),
                    0xA9.toByte(), 0x80.toByte(), 0xF6.toByte(), 0xF1.toByte(), 0x2D.toByte(),
                    0xB5.toByte(), 0xBE.toByte()
                )
            ),
            Pair(
                2,
                byteArrayOf(
                    0x76.toByte(), 0xAA.toByte(), 0xC7.toByte(), 0xB5.toByte(), 0x2F.toByte(),
                    0x93.toByte(), 0x8A.toByte(), 0x4A.toByte(), 0xEA.toByte(), 0xE3.toByte(),
                    0x1D.toByte(), 0x79.toByte(), 0x6B.toByte(), 0xA5.toByte(), 0xAE.toByte(),
                    0xB2.toByte(), 0xDB.toByte(), 0xE0.toByte(), 0xC9.toByte(), 0xD9.toByte(),
                    0x6A.toByte(), 0xD7.toByte(), 0x5B.toByte(), 0x18.toByte(), 0xD7.toByte(),
                    0x5A.toByte(), 0xFA.toByte(), 0xE8.toByte(), 0x75.toByte(), 0xA7.toByte(),
                    0x9C.toByte(), 0x76.toByte()
                )
            ),
            Pair(
                3,
                byteArrayOf(
                    0xE2.toByte(), 0x50.toByte(), 0x3B.toByte(), 0x90.toByte(), 0x5A.toByte(),
                    0x17.toByte(), 0x54.toByte(), 0xEF.toByte(), 0x48.toByte(), 0x3B.toByte(),
                    0xF5.toByte(), 0xDD.toByte(), 0x42.toByte(), 0x9C.toByte(), 0x3A.toByte(),
                    0x9B.toByte(), 0xC6.toByte(), 0x6B.toByte(), 0xDF.toByte(), 0x7B.toByte(),
                    0x3D.toByte(), 0x1C.toByte(), 0x08.toByte(), 0x0C.toByte(), 0xFD.toByte(),
                    0xEB.toByte(), 0x4E.toByte(), 0xC2.toByte(), 0x7A.toByte(), 0xAD.toByte(),
                    0xEE.toByte(), 0x63.toByte()
                )
            ),
            Pair(
                4,
                byteArrayOf(
                    0xA9.toByte(), 0x63.toByte(), 0x38.toByte(), 0x2A.toByte(), 0xA8.toByte(),
                    0xBA.toByte(), 0x4C.toByte(), 0x52.toByte(), 0xDA.toByte(), 0x52.toByte(),
                    0x2B.toByte(), 0xC3.toByte(), 0x1D.toByte(), 0x41.toByte(), 0xE2.toByte(),
                    0x56.toByte(), 0x49.toByte(), 0x15.toByte(), 0xF5.toByte(), 0x7C.toByte(),
                    0xB1.toByte(), 0x44.toByte(), 0xCB.toByte(), 0x7C.toByte(), 0x69.toByte(),
                    0xF4.toByte(), 0x7A.toByte(), 0x85.toByte(), 0xB4.toByte(), 0x4A.toByte(),
                    0x7E.toByte(), 0x59.toByte()
                )
            ),
            Pair(
                5,
                byteArrayOf(
                    0x20.toByte(), 0xEE.toByte(), 0x71.toByte(), 0x41.toByte(), 0x91.toByte(),
                    0x90.toByte(), 0x1E.toByte(), 0x77.toByte(), 0x7A.toByte(), 0xE7.toByte(),
                    0xF4.toByte(), 0x5B.toByte(), 0x2E.toByte(), 0xB6.toByte(), 0x55.toByte(),
                    0x4A.toByte(), 0xCA.toByte(), 0x8D.toByte(), 0xBB.toByte(), 0x10.toByte(),
                    0xF3.toByte(), 0x09.toByte(), 0x3B.toByte(), 0xE2.toByte(), 0x67.toByte(),
                    0xDC.toByte(), 0xE2.toByte(), 0xC7.toByte(), 0xF2.toByte(), 0x04.toByte(),
                    0xD8.toByte(), 0xE1.toByte()
                )
            ),
            Pair(
                6,
                byteArrayOf(
                    0xB5.toByte(), 0x33.toByte(), 0x92.toByte(), 0xE5.toByte(), 0x47.toByte(),
                    0xBA.toByte(), 0x0A.toByte(), 0x23.toByte(), 0x69.toByte(), 0xCB.toByte(),
                    0xA5.toByte(), 0xAC.toByte(), 0x6D.toByte(), 0x91.toByte(), 0xF4.toByte(),
                    0x30.toByte(), 0xFA.toByte(), 0x8F.toByte(), 0xB7.toByte(), 0x6F.toByte(),
                    0x0C.toByte(), 0x4D.toByte(), 0xF9.toByte(), 0x0F.toByte(), 0xCB.toByte(),
                    0x57.toByte(), 0x91.toByte(), 0xB6.toByte(), 0xFF.toByte(), 0x03.toByte(),
                    0x8E.toByte(), 0xB6.toByte()
                )
            ),
            Pair(
                7,
                byteArrayOf(
                    0x4E.toByte(), 0x0A.toByte(), 0xD3.toByte(), 0x33.toByte(), 0xB3.toByte(),
                    0xBF.toByte(), 0xD2.toByte(), 0x42.toByte(), 0xDF.toByte(), 0x95.toByte(),
                    0x52.toByte(), 0xE2.toByte(), 0x95.toByte(), 0xA4.toByte(), 0xD8.toByte(),
                    0x5E.toByte(), 0xFD.toByte(), 0x82.toByte(), 0xE3.toByte(), 0x49.toByte(),
                    0x45.toByte(), 0x6E.toByte(), 0xA1.toByte(), 0x0F.toByte(), 0xA8.toByte(),
                    0x0E.toByte(), 0xED.toByte(), 0xD6.toByte(), 0xDC.toByte(), 0x31.toByte(),
                    0x95.toByte(), 0xC0.toByte()
                )
            ),
            Pair(
                8,
                byteArrayOf(
                    0x46.toByte(), 0x7A.toByte(), 0xC9.toByte(), 0x1E.toByte(), 0x0A.toByte(),
                    0xCD.toByte(), 0xEA.toByte(), 0xC8.toByte(), 0x5D.toByte(), 0x82.toByte(),
                    0x72.toByte(), 0x40.toByte(), 0x1F.toByte(), 0x87.toByte(), 0x49.toByte(),
                    0xBC.toByte(), 0x2E.toByte(), 0x1B.toByte(), 0x3F.toByte(), 0x46.toByte(),
                    0x38.toByte(), 0x5E.toByte(), 0x5B.toByte(), 0x91.toByte(), 0xDB.toByte(),
                    0xB7.toByte(), 0x1D.toByte(), 0x8B.toByte(), 0x9A.toByte(), 0x82.toByte(),
                    0x61.toByte(), 0xEE.toByte()
                )
            ),
            Pair(
                10,
                byteArrayOf(
                    0xBF.toByte(), 0xAD.toByte(), 0x1F.toByte(), 0x0B.toByte(), 0xA4.toByte(),
                    0x2F.toByte(), 0x3F.toByte(), 0xE5.toByte(), 0x70.toByte(), 0x68.toByte(),
                    0xA2.toByte(), 0xAF.toByte(), 0xD4.toByte(), 0xF2.toByte(), 0x05.toByte(),
                    0x1E.toByte(), 0xA6.toByte(), 0x0B.toByte(), 0xB9.toByte(), 0x1C.toByte(),
                    0xB0.toByte(), 0x81.toByte(), 0x3F.toByte(), 0xE9.toByte(), 0x8B.toByte(),
                    0x32.toByte(), 0x3D.toByte(), 0x96.toByte(), 0x9E.toByte(), 0x21.toByte(),
                    0x8B.toByte(), 0x45.toByte()
                )
            ),
            Pair(
                11,
                byteArrayOf(
                    0x54.toByte(), 0x1E.toByte(), 0x4D.toByte(), 0xE6.toByte(), 0x8C.toByte(),
                    0xDD.toByte(), 0x21.toByte(), 0x6D.toByte(), 0xA9.toByte(), 0xAE.toByte(),
                    0xC2.toByte(), 0x2C.toByte(), 0x91.toByte(), 0x22.toByte(), 0xE1.toByte(),
                    0xD3.toByte(), 0xC2.toByte(), 0x5D.toByte(), 0x38.toByte(), 0xCF.toByte(),
                    0xA2.toByte(), 0x64.toByte(), 0x55.toByte(), 0xDF.toByte(), 0xF5.toByte(),
                    0x88.toByte(), 0x2C.toByte(), 0xC2.toByte(), 0xC3.toByte(), 0x71.toByte(),
                    0xF5.toByte(), 0x14.toByte()
                )
            ),
            Pair(
                19,
                byteArrayOf(
                    0x41.toByte(), 0x31.toByte(), 0x4B.toByte(), 0x90.toByte(), 0x9E.toByte(),
                    0xD3.toByte(), 0x60.toByte(), 0x16.toByte(), 0xCF.toByte(), 0x90.toByte(),
                    0x42.toByte(), 0xE7.toByte(), 0xB9.toByte(), 0x4A.toByte(), 0xAC.toByte(),
                    0x41.toByte(), 0x95.toByte(), 0x5D.toByte(), 0x45.toByte(), 0x9A.toByte(),
                    0xB2.toByte(), 0x58.toByte(), 0xA1.toByte(), 0x8D.toByte(), 0x4D.toByte(),
                    0x87.toByte(), 0xAD.toByte(), 0x58.toByte(), 0x17.toByte(), 0xD6.toByte(),
                    0xC6.toByte(), 0x8B.toByte()
                )
            ),
            Pair(
                23,
                byteArrayOf(
                    0xD5.toByte(), 0x36.toByte(), 0x40.toByte(), 0x8A.toByte(), 0x50.toByte(),
                    0xEC.toByte(), 0x28.toByte(), 0x18.toByte(), 0xE4.toByte(), 0x26.toByte(),
                    0x72.toByte(), 0x70.toByte(), 0x78.toByte(), 0xBD.toByte(), 0x44.toByte(),
                    0x29.toByte(), 0xA2.toByte(), 0xD9.toByte(), 0xA0.toByte(), 0xE0.toByte(),
                    0x61.toByte(), 0xFE.toByte(), 0xCD.toByte(), 0x46.toByte(), 0xF5.toByte(),
                    0xCF.toByte(), 0xDA.toByte(), 0xA7.toByte(), 0x7A.toByte(), 0xDA.toByte(),
                    0x95.toByte(), 0xC0.toByte()
                )
            ),
            Pair(
                24,
                byteArrayOf(
                    0x58.toByte(), 0x0B.toByte(), 0xAE.toByte(), 0x0E.toByte(), 0xCF.toByte(),
                    0x9A.toByte(), 0x1C.toByte(), 0x4D.toByte(), 0x16.toByte(), 0x25.toByte(),
                    0x86.toByte(), 0xD7.toByte(), 0x52.toByte(), 0x06.toByte(), 0x15.toByte(),
                    0xE2.toByte(), 0xA7.toByte(), 0xAC.toByte(), 0x14.toByte(), 0x58.toByte(),
                    0x49.toByte(), 0x67.toByte(), 0xFC.toByte(), 0x9B.toByte(), 0xBE.toByte(),
                    0x83.toByte(), 0x73.toByte(), 0xE5.toByte(), 0x93.toByte(), 0x02.toByte(),
                    0xCA.toByte(), 0x98.toByte()
                )
            )
        )
    )

    private val xCoordinate = byteArrayOf(
        0x91.toByte(), 0x90.toByte(), 0x2b.toByte(), 0x05.toByte(), 0x46.toByte(),
        0xb7.toByte(), 0x82.toByte(), 0xfc.toByte(), 0x35.toByte(), 0xbd.toByte(),
        0x9e.toByte(), 0x00.toByte(), 0x76.toByte(), 0x94.toByte(), 0x5c.toByte(),
        0x79.toByte(), 0x1f.toByte(), 0xee.toByte(), 0x62.toByte(), 0xf6.toByte(),
        0x51.toByte(), 0xdd.toByte(), 0x9c.toByte(), 0x7f.toByte(), 0xf2.toByte(),
        0x29.toByte(), 0xd4.toByte(), 0x7c.toByte(), 0x44.toByte(), 0x43.toByte(),
        0x72.toByte(), 0x6e.toByte()
    )
    private val yCoordinate = byteArrayOf(
        0xb5.toByte(), 0x55.toByte(), 0x73.toByte(), 0xf2.toByte(), 0x3e.toByte(),
        0x59.toByte(), 0x37.toByte(), 0x87.toByte(), 0x63.toByte(), 0xa5.toByte(),
        0x07.toByte(), 0x80.toByte(), 0x20.toByte(), 0x5c.toByte(), 0xff.toByte(),
        0x15.toByte(), 0xe4.toByte(), 0xe6.toByte(), 0x96.toByte(), 0xd7.toByte(),
        0x00.toByte(), 0xb5.toByte(), 0xf9.toByte(), 0xe2.toByte(), 0x64.toByte(),
        0x58.toByte(), 0x18.toByte(), 0xf7.toByte(), 0x4f.toByte(), 0xf1.toByte(),
        0x8e.toByte(), 0x86.toByte()
    )

    private val keyType = 2
    private val curveID = 1
    private val signed = DateUtils.getDateOfIssue()
    private val validFrom = DateUtils.getDateOfIssue()
    private val validUntil = DateUtils.getDateOfExpiry()

    @Test
    fun testMsoDecode() {
        val mobileSecurityObject =
            MobileSecurityObject.Builder().decode(expectedMobileSecurityObject).build()

        assertNotNull(mobileSecurityObject)

        mobileSecurityObject?.let {
            assertTrue(mobileSecurityObject.digestAlgorithm == digestAlgorithm)
            assertTrue(mdlNameSpaceExists(mobileSecurityObject.listOfNameSpaces))
            assertTrue(mobileSecurityObject.coseKey?.keyType == keyType)
            assertTrue(mobileSecurityObject.coseKey?.curve?.id == curveID)
            assertArrayEquals(mobileSecurityObject.coseKey?.curve?.xCoordinate, xCoordinate)
            assertArrayEquals(mobileSecurityObject.coseKey?.curve?.yCoordinate, yCoordinate)
            assertNull(mobileSecurityObject.coseKey?.curve?.privateKey)
            assertEquals(mobileSecurityObject.documentType, MdlDoctype.docType)
            assertEquals(
                mobileSecurityObject.validityInfo?.signed?.timeInMillis,
                signed.timeInMillis
            )
            assertEquals(
                mobileSecurityObject.validityInfo?.validFrom?.timeInMillis,
                validFrom.timeInMillis
            )
            assertEquals(
                mobileSecurityObject.validityInfo?.validUntil?.timeInMillis,
                validUntil.timeInMillis
            )
            assertNull(mobileSecurityObject.validityInfo?.expectedUpdate)
        }
    }

    private fun mdlNameSpaceExists(listOfNameSpaceDigests: List<IMsoNameSpace>): Boolean {
        for (i in listOfNameSpaceDigests) {
            if (i.namespace == MdlNamespace.namespace) {
                return true
            }
        }
        return false
    }

    @Test
    fun testMsoEncode() {
        val deviceKey = CoseKey.Builder()
            .setKeyType(keyType)
            .setCurve(curveID, xCoordinate, yCoordinate, null)
            .build()

        val validityInfo = ValidityInfo.Builder()
            .setSigned(signed)
            .setValidFrom(validFrom)
            .setValidUntil(validUntil)
            .build()

        val mso = MobileSecurityObject.Builder()
            .setDigestAlgorithm(HashAlgorithms.SHA_256)
            .setListOfNameSpaces(listOf(mdlNameSpace))
            .setDeviceKey(deviceKey)
            .setDocumentType(MdlDoctype.docType)
            .setValidityInfo(validityInfo)
            .build()

        assertNotNull(mso)

        mso?.let {
            val msoData = mso.encode()
            assertArrayEquals(expectedMobileSecurityObject, msoData)
        }
    }
}