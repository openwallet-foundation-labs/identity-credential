package org.multipaz

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.multipaz.crypto.Crypto
import java.security.Security

actual fun testUtilSetupCryptoProvider() {
    println("In testUtilCommonSetup for JVM")

    // On the JVM load BouncyCastleProvider so we can exercise the all the tests involving Brainpool curves.
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
    Security.insertProviderAt(BouncyCastleProvider(), 1)

    println("Crypto.provider: ${Crypto.provider}")
    println("Crypto.supportedCurves: ${Crypto.supportedCurves}")
}
