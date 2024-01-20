package com.android.mdl.appreader

import android.app.Application
import android.content.Context
import com.android.identity.android.util.AndroidLogPrinter
import com.android.identity.util.Logger
import androidx.preference.PreferenceManager
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.credentialtype.knowntypes.EUPersonalID
import com.android.identity.credentialtype.knowntypes.VaccinationDocument
import com.android.identity.credentialtype.knowntypes.VehicleRegistration
import com.android.identity.presentationlog.PresentationLogStore
import com.android.identity.storage.GenericStorageEngine
import com.android.identity.storage.StorageEngine
import com.android.identity.trustmanagement.TrustManager
import com.android.identity.trustmanagement.TrustPoint
import com.android.mdl.appreader.settings.UserPreferences
import com.android.mdl.appreader.util.KeysAndCertificates
import com.google.android.material.color.DynamicColors
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.io.File
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

    private val credentialTypeRepository by lazy {
        CredentialTypeRepository()
    }

    /**
     * Create a PresentationLogStore
     */
    private fun createPresentationLogStore(
        context: Context,
    ): PresentationLogStore {

        fun getKeystoreBackedStorageLocation(context: Context): File {
            // As per the docs, the credential data contains reference to Keystore aliases so ensure
            // this is stored in a location where it's not automatically backed up and restored by
            // Android Backup as per https://developer.android.com/guide/topics/data/autobackup
            val storageDir = File(context.noBackupFilesDir, "identity")
            if (!storageDir.exists()) {
                storageDir.mkdir()
            }
            return storageDir;
        }

        val storageDir = getKeystoreBackedStorageLocation(context)
        val storageEngine = AndroidStorageEngine.Builder(context, storageDir).build()
        return PresentationLogStore(storageEngine)
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
        certificateStorageEngineInstance = certificateStorageEngine
        certificateStorageEngineInstance.enumerate().forEach {
            val certificate = parseCertificate(certificateStorageEngineInstance.get(it)!!)
            trustManagerInstance.addTrustPoint(TrustPoint(certificate))
        }
        KeysAndCertificates.getTrustedIssuerCertificates(this).forEach {
            trustManagerInstance.addTrustPoint(TrustPoint(it))
        }
        credentialTypeRepositoryInstance = credentialTypeRepository
        credentialTypeRepositoryInstance.addCredentialType(DrivingLicense.getCredentialType())
        credentialTypeRepositoryInstance.addCredentialType(VehicleRegistration.getCredentialType())
        credentialTypeRepositoryInstance.addCredentialType(VaccinationDocument.getCredentialType())
        credentialTypeRepositoryInstance.addCredentialType(EUPersonalID.getCredentialType())

        presentationLogStoreInstance = createPresentationLogStore(this)
    }

    companion object {

        private lateinit var userPreferencesInstance: UserPreferences
        lateinit var trustManagerInstance: TrustManager
        lateinit var certificateStorageEngineInstance: StorageEngine
        lateinit var credentialTypeRepositoryInstance: CredentialTypeRepository
        lateinit var presentationLogStoreInstance : PresentationLogStore
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