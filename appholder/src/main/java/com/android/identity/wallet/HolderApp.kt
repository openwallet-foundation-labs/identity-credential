package com.android.identity.wallet

import android.app.Application
import android.content.Context
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.android.util.AndroidLogPrinter
import com.android.identity.credential.CredentialStore
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.DrivingLicense
import com.android.identity.credentialtype.EUPersonalID
import com.android.identity.credentialtype.VaccinationDocument
import com.android.identity.credentialtype.VehicleRegistration
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.securearea.SoftwareSecureArea
import com.android.identity.util.Logger
import com.android.identity.wallet.util.PeriodicKeysRefreshWorkRequest
import com.android.identity.wallet.util.PreferencesHelper
import com.google.android.material.color.DynamicColors
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class HolderApp: Application() {

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
        CredentialTypeRepository.addCredentialType(DrivingLicense.getCredentialType())
        CredentialTypeRepository.addCredentialType(VehicleRegistration.getCredentialType())
        CredentialTypeRepository.addCredentialType(VaccinationDocument.getCredentialType())
        CredentialTypeRepository.addCredentialType(EUPersonalID.getCredentialType())
    }

    companion object {
        fun createCredentialStore(
            context: Context,
            secureAreaRepository: SecureAreaRepository
        ): CredentialStore {
            val storageDir = PreferencesHelper.getKeystoreBackedStorageLocation(context)
            val storageEngine = AndroidStorageEngine.Builder(context, storageDir).build()

            val androidKeystoreSecureArea = AndroidKeystoreSecureArea(context, storageEngine)
            val softwareSecureArea = SoftwareSecureArea(storageEngine)

            secureAreaRepository.addImplementation(androidKeystoreSecureArea)
            secureAreaRepository.addImplementation(softwareSecureArea)
            return CredentialStore(storageEngine, secureAreaRepository)
        }
    }
}