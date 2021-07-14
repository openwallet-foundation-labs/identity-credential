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

package com.ul.ims.gmdl.wifiofflinetransfer.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WIFI_STATE_ENABLED
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.util.DigestFactory
import org.bouncycastle.util.encoders.Hex
import java.util.*

object WifiUtils {

    fun isWifiEnabled(context: Context): Boolean {
        return getWifiManager(context)?.wifiState == WIFI_STATE_ENABLED
    }

    fun getWifiManager(context: Context): WifiManager? {
        val wifiManager: WifiManager? by lazy(LazyThreadSafetyMode.NONE) {
            context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        }

        return wifiManager
    }

    fun getPassphrase(publicKey: ByteArray): String {
        val salt = byteArrayOf(0x02)

        val hkdfBytesGenerator = HKDFBytesGenerator(DigestFactory.createSHA256())

        hkdfBytesGenerator.init(HKDFParameters(publicKey, salt, null))

        val passphrase = ByteArray(16)
        hkdfBytesGenerator.generateBytes(passphrase, 0, 16)

        return Hex.toHexString(passphrase).toUpperCase(Locale.getDefault())
    }

    fun getSupportedBands(context: Context): ByteArray {
        var supportedBandsBitmap = 0x04 // Bit 2: 2.4 GHz

        if (getWifiManager(context)?.is5GHzBandSupported == true) {
            supportedBandsBitmap =
                supportedBandsBitmap or 0x10 // Bit 4: 4.9 and 5 GHz
        }
        return byteArrayOf((supportedBandsBitmap and 0xff).toByte())
    }

    fun getServiceName(publicKey: ByteArray): String {
        val salt = byteArrayOf(0x01)

        val hkdfBytesGenerator = HKDFBytesGenerator(DigestFactory.createSHA256())

        hkdfBytesGenerator.init(HKDFParameters(publicKey, salt, null))

        val serviceName = ByteArray(16)
        hkdfBytesGenerator.generateBytes(serviceName, 0, 16)

        return Hex.toHexString(serviceName).toUpperCase(Locale.getDefault())
    }
}