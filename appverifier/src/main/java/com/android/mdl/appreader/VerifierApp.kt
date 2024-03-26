package com.android.mdl.appreader

import android.app.Application
import com.android.identity.android.util.AndroidLogPrinter
import com.android.identity.util.Logger
import androidx.preference.PreferenceManager
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.documenttype.knowntypes.VaccinationDocument
import com.android.identity.documenttype.knowntypes.VehicleRegistration
import com.android.identity.storage.GenericStorageEngine
import com.android.identity.storage.StorageEngine
import com.android.identity.trustmanagement.TrustManager
import com.android.identity.trustmanagement.TrustPoint
import com.android.mdl.appreader.settings.UserPreferences
import com.android.mdl.appreader.util.KeysAndCertificates
import com.google.android.material.color.DynamicColors
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class VerifierApp : Application() {

    private val userPreferences by lazy {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        UserPreferences(sharedPreferences)
    }

    private val trustManager by lazy {
        TrustManager()
    }

    private val certificateStorageEngine by lazy {
        GenericStorageEngine(getDir("Certificates", MODE_PRIVATE))
    }

    private val documentTypeRepository by lazy {
        DocumentTypeRepository()
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
        Logger.isDebugEnabled = userPreferences.isDebugLoggingEnabled()
        trustManagerInstance = trustManager
        certificateStorageEngineInstance = certificateStorageEngine
        certificateStorageEngineInstance.enumerate().forEach {
            val certificate = parseCertificate(certificateStorageEngineInstance.get(it)!!)
            trustManagerInstance.addTrustPoint(TrustPoint(certificate))
        }
        KeysAndCertificates.getTrustedIssuerCertificates(this).forEach {
            trustManagerInstance.addTrustPoint(TrustPoint(it))
        }
        documentTypeRepositoryInstance = documentTypeRepository
        documentTypeRepositoryInstance.addDocumentType(DrivingLicense.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(VehicleRegistration.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(VaccinationDocument.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(EUPersonalID.getDocumentType())
    }

    companion object {

        private lateinit var userPreferencesInstance: UserPreferences
        lateinit var trustManagerInstance: TrustManager
        lateinit var certificateStorageEngineInstance: StorageEngine
        lateinit var documentTypeRepositoryInstance: DocumentTypeRepository
        fun isDebugLogEnabled(): Boolean {
            return userPreferencesInstance.isDebugLoggingEnabled()
        }
    }

    /**
     * Parse a byte array as an X509 certificate
     */
    private fun parseCertificate(certificateBytes: ByteArray): X509Certificate {
        return CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
    }
}