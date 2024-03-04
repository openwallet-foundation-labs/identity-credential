package com.android.identity_credential.wallet

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.core.content.res.ResourcesCompat
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.CredentialStore
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.crypto.Certificate
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.trustmanagement.TrustManager
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.util.toByteArray
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Security

class WalletApplication : Application() {
    companion object {
        private const val TAG = "WalletApplication"

        // Preference names
        const val AUTH_KEY_DOMAIN = "mdoc/MSO"
        const val PREFERENCE_CURRENT_CREDENTIAL_ID = "current_credential_id"
        const val LOG_TO_FILE = "log_to_file"
    }


    // immediate instantiations
    val trustManager = TrustManager()

    // lazy instantiations
    val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
    }

    // late instantiations
    lateinit var credentialTypeRepository: CredentialTypeRepository
    lateinit var issuingAuthorityRepository: IssuingAuthorityRepository
    lateinit var secureAreaRepository: SecureAreaRepository
    lateinit var credentialStore: CredentialStore
    lateinit var loggerModel: LoggerModel
    lateinit var androidKeystoreSecureArea: AndroidKeystoreSecureArea

    override fun onCreate() {
        super.onCreate()
        Logger.d(TAG, "onCreate")

        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in the OS itself.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())

        // init LoggerModel
        loggerModel = LoggerModel(this, sharedPreferences)
        loggerModel.init()

        // init CredentialTypeRepository
        credentialTypeRepository = CredentialTypeRepository()
        credentialTypeRepository.addCredentialType(DrivingLicense.getCredentialType())

        // secure storage properties
        val storageDir = File(applicationContext.noBackupFilesDir, "identity")
        val storageEngine = AndroidStorageEngine.Builder(applicationContext, storageDir).build()

        // init AndroidKeyStoreSecureArea
        androidKeystoreSecureArea = AndroidKeystoreSecureArea(applicationContext, storageEngine)

        // init SecureAreaRepository
        secureAreaRepository = SecureAreaRepository()
        secureAreaRepository.addImplementation(androidKeystoreSecureArea)

        // init CredentialStore
        credentialStore = CredentialStore(storageEngine, secureAreaRepository)

        // init IssuingAuthorityRepository
        issuingAuthorityRepository = IssuingAuthorityRepository().apply {
            add(SelfCertificationIssuingAuthority(this@WalletApplication, storageEngine))
            add(PassportBasedIssuingAuthority(this@WalletApplication, storageEngine))
        }

        // init TrustManager
        trustManager.addTrustPoint(
            displayName = "OWF Identity Credential Reader",
            certificateResourceId = R.raw.owf_identity_credential_reader_cert,
            displayIconResourceId = R.drawable.owf_identity_credential_reader_display_icon
        )
    }

    /**
     * Extend TrustManager to add a TrustPoint via the individual data point resources that make
     * a TrustPoint.
     *
     * This extension function belongs to WalletApplication so it can use context.resources.
     */
    fun TrustManager.addTrustPoint(
        displayName: String,
        certificateResourceId: Int,
        displayIconResourceId: Int?
    ) = addTrustPoint(
        TrustPoint(
            certificate = Certificate.fromPem(
                String(
                    resources.openRawResource(certificateResourceId).readBytes()
                )
            ).javaX509Certificate,
            displayName = displayName,
            displayIcon = displayIconResourceId?.let { iconId ->
                ResourcesCompat.getDrawable(resources, iconId, null)?.toByteArray()
            }
        )
    )
}