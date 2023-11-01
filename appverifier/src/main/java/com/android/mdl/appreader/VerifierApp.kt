package com.android.mdl.appreader

import android.app.Application
import com.android.identity.android.util.AndroidLogPrinter
import com.android.identity.util.Logger
import androidx.preference.PreferenceManager
import com.android.mdl.appreader.issuerauth.CaCertificateStore
import com.android.mdl.appreader.issuerauth.TrustManager
import com.android.mdl.appreader.issuerauth.VicalStore
import com.android.mdl.appreader.settings.UserPreferences
import com.google.android.material.color.DynamicColors
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class VerifierApp : Application() {

    private val userPreferences by lazy {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        UserPreferences(sharedPreferences)
    }

    private val caCertificateStore by lazy {
        CaCertificateStore(this)
    }

    private val vicalStore by lazy {
        VicalStore(this)
    }

    private val trustManager by lazy {
        TrustManager({ caCertificateStoreInstance.getAll() }, { vicalStoreInstance.getAll() })
    }

    override fun onCreate() {
        super.onCreate()
        Logger.setLogPrinter(AndroidLogPrinter())
        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in the OS itself.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
        DynamicColors.applyToActivitiesIfAvailable(this)
        userPreferencesInstance = userPreferences
        Logger.setDebugEnabled(userPreferences.isDebugLoggingEnabled())

        caCertificateStoreInstance = caCertificateStore
        vicalStoreInstance = vicalStore
        trustManagerInstance = trustManager
    }

    companion object {

        private lateinit var userPreferencesInstance: UserPreferences
        lateinit var caCertificateStoreInstance: CaCertificateStore
        lateinit var vicalStoreInstance: VicalStore
        lateinit var trustManagerInstance: TrustManager

        fun isDebugLogEnabled(): Boolean {
            return userPreferencesInstance.isDebugLoggingEnabled()
        }
    }
}