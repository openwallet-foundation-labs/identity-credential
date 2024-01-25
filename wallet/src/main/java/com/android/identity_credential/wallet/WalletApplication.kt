package com.android.identity_credential.wallet

import android.app.Application
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.CredentialStore
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.util.Logger
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Security

class WalletApplication : Application() {
    companion object {
        private const val TAG = "WalletApplication"
        const val AUTH_KEY_DOMAIN = "mdoc/MSO"
        const val PREFERENCE_CURRENT_CREDENTIAL_ID = "current_credential_id"
    }

    lateinit var credentialTypeRepository: CredentialTypeRepository
    lateinit var issuingAuthorityRepository: IssuingAuthorityRepository
    lateinit var secureAreaRepository: SecureAreaRepository
    lateinit var credentialStore: CredentialStore

    lateinit var androidKeystoreSecureArea: AndroidKeystoreSecureArea

    override fun onCreate() {
        super.onCreate()
        Logger.d(TAG, "onCreate")

        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in the OS itself.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())

        // Setup singletons
        credentialTypeRepository = CredentialTypeRepository()
        credentialTypeRepository.addCredentialType(DrivingLicense.getCredentialType())

        val storageDir = File(applicationContext.noBackupFilesDir, "identity")
        val storageEngine = AndroidStorageEngine.Builder(applicationContext, storageDir).build()
        secureAreaRepository = SecureAreaRepository()
        androidKeystoreSecureArea = AndroidKeystoreSecureArea(applicationContext, storageEngine)
        secureAreaRepository.addImplementation(androidKeystoreSecureArea);
        credentialStore = CredentialStore(storageEngine, secureAreaRepository)

        issuingAuthorityRepository = IssuingAuthorityRepository()
        issuingAuthorityRepository.add(SelfSignedMdlIssuingAuthority(this, storageEngine))
    }
}