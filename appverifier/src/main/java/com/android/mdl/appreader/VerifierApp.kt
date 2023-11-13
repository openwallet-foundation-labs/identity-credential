package com.android.mdl.appreader

import android.app.Application
import android.content.Context
import com.android.identity.android.util.AndroidLogPrinter
import com.android.identity.util.Logger
import androidx.preference.PreferenceManager
import com.android.identity.storage.GenericStorageEngine
import com.android.identity.trustmanagement.TrustManager
import com.android.mdl.appreader.settings.UserPreferences
import com.google.android.material.color.DynamicColors
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class VerifierApp : Application() {

    private val userPreferences by lazy {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        UserPreferences(sharedPreferences)
    }

    private val trustManager by lazy {
        TrustManager(GenericStorageEngine(getDir("Certificates", Context.MODE_PRIVATE)))
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
        trustManagerInstance = trustManager
    }

    companion object {

        private lateinit var userPreferencesInstance: UserPreferences
        lateinit var trustManagerInstance: TrustManager

        fun isDebugLogEnabled(): Boolean {
            return userPreferencesInstance.isDebugLoggingEnabled()
        }
    }
}