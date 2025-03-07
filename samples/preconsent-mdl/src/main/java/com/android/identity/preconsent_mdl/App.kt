package org.multipaz.preconsent_mdl

import android.app.Application
import org.multipaz.util.Logger
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class App : Application() {
    companion object {
        private const val TAG = "App"
    }

    private lateinit var transferHelper: TransferHelper

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "onCreate")

        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in the OS itself.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())

        transferHelper = TransferHelper.getInstance(applicationContext)
        Logger.isDebugEnabled = transferHelper.getDebugEnabled()
    }
}