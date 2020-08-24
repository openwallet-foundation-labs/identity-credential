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

package com.ul.ims.gmdl.nfcofflinetransfer.utils

import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.byteArrayOfInts

class NfcConstants {
    companion object {
        val statusWordOK: ByteArray = byteArrayOfInts(0x90, 0x00)
        val statusWrongLength: ByteArray = byteArrayOfInts(0x67, 0x00)
        val statusWordInstructionNotSupported: ByteArray = byteArrayOfInts(0x6D, 0x00)
        val statusWordFileNotFound: ByteArray = byteArrayOfInts(0x6A, 0x82)
        val statusWordChainingResponse: ByteArray = byteArrayOfInts(0x61, 0x00)

        // AID = A0 00 00 02 48 04 00
        // as per ISO 18013-5 8.2.2.2 Data retrieval using Near Field communication (NFC)
        val selectAid = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x02, 0x48, 0x04, 0x00)

    }
}