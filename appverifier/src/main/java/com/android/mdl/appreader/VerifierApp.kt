package com.android.mdl.appreader

import android.app.Application
import com.android.identity.android.util.AndroidLogPrinter
import org.multipaz.util.Logger
import androidx.preference.PreferenceManager
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.javaX509Certificate
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.VaccinationDocument
import org.multipaz.documenttype.knowntypes.VehicleRegistration
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.storage.GenericStorageEngine
import org.multipaz.storage.StorageEngine
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustPoint
import com.android.mdl.appreader.settings.UserPreferences
import com.android.mdl.appreader.util.KeysAndCertificates
import com.google.android.material.color.DynamicColors
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlinx.io.files.Path

class VerifierApp : Application() {

    private val userPreferences by lazy {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        UserPreferences(sharedPreferences)
    }

    private val trustManager by lazy {
        TrustManager()
    }

    private val certificateStorageEngine by lazy {
        GenericStorageEngine(Path(getDir("Certificates", MODE_PRIVATE).name))
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
            trustManagerInstance.addTrustPoint(TrustPoint(X509Cert(certificate.encoded)))
        }
        KeysAndCertificates.getTrustedIssuerCertificates(this).forEach {
            trustManagerInstance.addTrustPoint(TrustPoint(X509Cert(it.encoded)))
        }
        val signedVical = SignedVical.parse(
            resources.openRawResource(R.raw.austroad_test_event_vical_20241002).readBytes()
        )
        for (certInfo in signedVical.vical.certificateInfos) {
            trustManagerInstance.addTrustPoint(
                TrustPoint(
                    certInfo.certificate,
                    null,
                    null
                )
            )
        }


        documentTypeRepositoryInstance = documentTypeRepository
//        documentTypeRepositoryInstance.addDocumentType(DrivingLicense.getDocumentType())
//        documentTypeRepositoryInstance.addDocumentType(VehicleRegistration.getDocumentType())
//        documentTypeRepositoryInstance.addDocumentType(VaccinationDocument.getDocumentType())
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