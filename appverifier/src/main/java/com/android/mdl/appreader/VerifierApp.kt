package com.android.mdl.appreader

import android.app.Application
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
import com.android.mdl.appreader.trustmanagement.getSubjectKeyIdentifier
import com.android.mdl.appreader.util.KeysAndCertificates
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlinx.io.files.Path
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.trustmanagement.LocalTrustManager
import org.multipaz.trustmanagement.TrustPointAlreadyExistsException
import org.multipaz.trustmanagement.TrustPointMetadata
import org.multipaz.trustmanagement.X509CertTrustPoint
import java.io.File

class VerifierApp : Application() {

    private val userPreferences by lazy {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        UserPreferences(sharedPreferences)
    }

    private val trustManager by lazy {
        // We import certificates on every startup so no point in using persistent storage
        LocalTrustManager(EphemeralStorage())
    }

    private val certificateStorageEngine by lazy {
        val certDir = getDir("Certificates", MODE_PRIVATE)
        val certFile = "imported_certs"
        GenericStorageEngine(Path(File(certDir, certFile).absolutePath))
    }

    private val documentTypeRepository by lazy {
        DocumentTypeRepository()
    }

    override fun onCreate() {
        super.onCreate()
        // Do NOT add BouncyCastle here - we want to use the normal AndroidOpenSSL JCA provider
        DynamicColors.applyToActivitiesIfAvailable(this)
        userPreferencesInstance = userPreferences
        Logger.isDebugEnabled = userPreferences.isDebugLoggingEnabled()
        trustManagerInstance = trustManager
        certificateStorageEngineInstance = certificateStorageEngine
        runBlocking {
            certificateStorageEngineInstance.enumerate().forEach {
                val certificate = parseCertificate(certificateStorageEngineInstance.get(it)!!)
                try {
                    trustManagerInstance.addTrustPoint(X509Cert(certificate.encoded), TrustPointMetadata())
                } catch (_: TrustPointAlreadyExistsException) {
                    Logger.w(TAG, "Trust Point already exists for ski ${certificate.getSubjectKeyIdentifier()}")
                }
            }
            KeysAndCertificates.getTrustedIssuerCertificates(this@VerifierApp).forEach {
                try {
                    trustManagerInstance.addTrustPoint(X509Cert(it.encoded), TrustPointMetadata())
                } catch (_: TrustPointAlreadyExistsException) {
                    Logger.w(TAG, "Trust Point already exists for ski ${it.getSubjectKeyIdentifier()}")
                }
            }
            val signedVical = SignedVical.parse(
                resources.openRawResource(R.raw.austroad_test_event_vical_20241002).readBytes()
            )
            for (certInfo in signedVical.vical.certificateInfos) {
                try {
                    trustManagerInstance.addTrustPoint(certInfo.certificate, TrustPointMetadata())
                } catch (_: TrustPointAlreadyExistsException) {
                    Logger.w(TAG, "Trust Point already exists for ski ${certInfo.certificate.subjectKeyIdentifier}")
                }
            }
        }

        documentTypeRepositoryInstance = documentTypeRepository
        documentTypeRepositoryInstance.addDocumentType(DrivingLicense.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(VehicleRegistration.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(VaccinationDocument.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(EUPersonalID.getDocumentType())
    }

    companion object {
        private const val TAG = "VerifierApp"

        private lateinit var userPreferencesInstance: UserPreferences
        lateinit var trustManagerInstance: LocalTrustManager
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