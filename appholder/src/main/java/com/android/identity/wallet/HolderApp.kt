package com.android.identity.wallet

import android.app.Application
import android.content.Context
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.android.util.AndroidLogPrinter
import com.android.identity.credential.CredentialFactory
import com.android.identity.document.DocumentStore
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.documenttype.knowntypes.VaccinationDocument
import com.android.identity.documenttype.knowntypes.VehicleRegistration
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.storage.GenericStorageEngine
import com.android.identity.storage.StorageEngine
import com.android.identity.trustmanagement.TrustManager
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Logger
import com.android.identity.wallet.document.KeysAndCertificates
import com.android.identity.wallet.util.PeriodicKeysRefreshWorkRequest
import com.android.identity.wallet.util.PreferencesHelper
import com.google.android.material.color.DynamicColors
import kotlinx.io.files.Path
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class HolderApp: Application() {

    private val documentTypeRepository by lazy {
        DocumentTypeRepository()
    }

    private val trustManager by lazy {
        TrustManager()
    }

    private val certificateStorageEngine by lazy {
        GenericStorageEngine(Path(getDir("Certificates", MODE_PRIVATE).name))
    }

    override fun onCreate() {
        super.onCreate()
        Logger.setLogPrinter(AndroidLogPrinter())
        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in the OS itself.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
        DynamicColors.applyToActivitiesIfAvailable(this)
        PreferencesHelper.initialize(this)
        PeriodicKeysRefreshWorkRequest(this).schedulePeriodicKeysRefreshing()
        documentTypeRepositoryInstance = documentTypeRepository
        documentTypeRepositoryInstance.addDocumentType(DrivingLicense.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(VehicleRegistration.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(VaccinationDocument.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(EUPersonalID.getDocumentType())
        trustManagerInstance = trustManager
        certificateStorageEngineInstance = certificateStorageEngine
        certificateStorageEngineInstance.enumerate().forEach {
            val certificate = parseCertificate(certificateStorageEngineInstance.get(it)!!)
            trustManagerInstance.addTrustPoint(TrustPoint(certificate))
        }
        KeysAndCertificates.getTrustedReaderCertificates(this).forEach {
            trustManagerInstance.addTrustPoint(TrustPoint(it))
        }
    }

    companion object {

        lateinit var documentTypeRepositoryInstance: DocumentTypeRepository
        lateinit var trustManagerInstance: TrustManager
        lateinit var certificateStorageEngineInstance: StorageEngine
        fun createDocumentStore(
            context: Context,
            secureAreaRepository: SecureAreaRepository
        ): DocumentStore {
            val storageFile = Path(PreferencesHelper.getKeystoreBackedStorageLocation(context).path)
            val storageEngine = AndroidStorageEngine.Builder(context, storageFile).build()

            val androidKeystoreSecureArea = AndroidKeystoreSecureArea(context, storageEngine)
            val softwareSecureArea = SoftwareSecureArea(storageEngine)

            secureAreaRepository.addImplementation(androidKeystoreSecureArea)
            secureAreaRepository.addImplementation(softwareSecureArea)

            var credentialFactory = CredentialFactory()
            credentialFactory.addCredentialImplementation(MdocCredential::class) {
                document, dataItem -> MdocCredential(document, dataItem)
            }
            return DocumentStore(storageEngine, secureAreaRepository, credentialFactory)
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