package com.android.identity_credential.wallet

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.preference.PreferenceManager
import androidx.core.app.ActivityCompat
import androidx.core.app.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.Credential
import com.android.identity.credential.CredentialStore
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.credentialtype.knowntypes.EUPersonalID
import com.android.identity.crypto.Certificate
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.issuance.CredentialExtensions.credentialConfiguration
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.trustmanagement.TrustManager
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.util.toByteArray
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.Security

class WalletApplication : Application() {
    companion object {
        private const val TAG = "WalletApplication"

        private const val NOTIFICATION_CHANNEL_ID = "walletNotifications"

        const val AUTH_KEY_DOMAIN = "mdoc/MSO"
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
    lateinit var settingsModel: SettingsModel
    lateinit var androidKeystoreSecureArea: AndroidKeystoreSecureArea

    override fun onCreate() {
        super.onCreate()
        Logger.d(TAG, "onCreate")

        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in the OS itself.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())

        settingsModel = SettingsModel(this, sharedPreferences)

        // init CredentialTypeRepository
        credentialTypeRepository = CredentialTypeRepository()
        credentialTypeRepository.addCredentialType(DrivingLicense.getCredentialType())
        credentialTypeRepository.addCredentialType(EUPersonalID.getCredentialType())

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
            add(SelfSignedMdlIssuingAuthority(this@WalletApplication, storageEngine))
            add(SelfSignedEuPidIssuingAuthority(this@WalletApplication, storageEngine))
        }

        // init TrustManager
        trustManager.addTrustPoint(
            displayName = "OWF Identity Credential Reader",
            certificateResourceId = R.raw.owf_identity_credential_reader_cert,
            displayIconResourceId = R.drawable.owf_identity_credential_reader_display_icon
        )

        val notificationChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Wallet",
            NotificationManager.IMPORTANCE_HIGH
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)
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

    fun postNotificationForCredential(
        credential: Credential,
        message: String,
    ) {

        // TODO: include data so the user is brought to the info page for the credential
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE)

        val cardArt = credential.credentialConfiguration.cardArt
        val bitmap = BitmapFactory.decodeByteArray(cardArt, 0, cardArt.size)

        val title = credential.credentialConfiguration.displayName
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setLargeIcon(bitmap)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val notificationId = credential.name.hashCode()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
    }
}