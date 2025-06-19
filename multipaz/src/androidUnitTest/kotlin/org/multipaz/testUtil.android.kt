package org.multipaz

import org.multipaz.crypto.Crypto

actual fun testUtilSetupCryptoProvider() {
    println("In testUtilCommonSetup for Android")
    println("Crypto.provider: ${Crypto.provider}")
    println("Crypto.supportedCurves: ${Crypto.supportedCurves}")
}
