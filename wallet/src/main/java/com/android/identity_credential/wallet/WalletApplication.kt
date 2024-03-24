package com.android.identity_credential.wallet

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.preference.PreferenceManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.crypto.Certificate
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.issuance.DocumentExtensions.documentConfiguration
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.trustmanagement.TrustManager
import com.android.identity.trustmanagement.TrustPoint
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.util.toByteArray
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Security
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

class WalletApplication : Application() {
    companion object {
        private const val TAG = "WalletApplication"

        private const val NOTIFICATION_CHANNEL_ID = "walletNotifications"

        private const val NOTIFICATION_ID_FOR_MISSING_PROXIMITY_PRESENTATION_PERMISSIONS = 42

        const val CREDENTIAL_DOMAIN = "mdoc/MSO"

        // The permissions needed to perform 18013-5 presentations. This only include the
        // BLE permissions because that's the only transport we currently support in the
        // application.
        val MDOC_PROXIMITY_PERMISSIONS: List<String> =
            if (Build.VERSION.SDK_INT >= 31) {
                listOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }


    // immediate instantiations
    val trustManager = TrustManager()

    // lazy instantiations
    val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
    }

    // late instantiations
    lateinit var documentTypeRepository: DocumentTypeRepository
    lateinit var issuingAuthorityRepository: IssuingAuthorityRepository
    lateinit var secureAreaRepository: SecureAreaRepository
    lateinit var documentStore: DocumentStore
    lateinit var settingsModel: SettingsModel
    lateinit var documentModel: DocumentModel
    lateinit var androidKeystoreSecureArea: AndroidKeystoreSecureArea

    override fun onCreate() {
        super.onCreate()
        Logger.d(TAG, "onCreate")

        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in the OS itself.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())

        settingsModel = SettingsModel(this, sharedPreferences)

        // init documentTypeRepository
        documentTypeRepository = DocumentTypeRepository()
        documentTypeRepository.addDocumentType(DrivingLicense.getDocumentType())
        documentTypeRepository.addDocumentType(EUPersonalID.getDocumentType())

        // secure storage properties
        val storageDir = File(applicationContext.noBackupFilesDir, "identity")
        val storageEngine = AndroidStorageEngine.Builder(applicationContext, storageDir).build()

        // init AndroidKeyStoreSecureArea
        androidKeystoreSecureArea = AndroidKeystoreSecureArea(applicationContext, storageEngine)

        // init SecureAreaRepository
        secureAreaRepository = SecureAreaRepository()
        secureAreaRepository.addImplementation(androidKeystoreSecureArea)

        // init documentStore
        documentStore = DocumentStore(storageEngine, secureAreaRepository)

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

        documentModel = DocumentModel(
            applicationContext,
            settingsModel,
            documentStore,
            issuingAuthorityRepository,
            secureAreaRepository,
            documentTypeRepository,
            this
        )

        val notificationChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            resources.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)

        // Configure a worker for invoking cardModel.periodicSyncForAllCredentials()
        // on a daily basis.
        //
        val workRequest =
            PeriodicWorkRequestBuilder<SyncCredentialWithIssuerWorker>(
                1, TimeUnit.DAYS
            ).setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).setInitialDelay(
                Random.Default.nextInt(1, 24).hours.toJavaDuration()
            ).build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                "PeriodicSyncWithIssuers",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }

    class SyncCredentialWithIssuerWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {
        override fun doWork(): Result {
            Logger.i(TAG, "Starting periodic syncing work")
            try {
                val walletApplication = applicationContext as WalletApplication
                walletApplication.documentModel.periodicSyncForAllDocuments()
            } catch (e: Throwable) {
                Logger.i(TAG, "Ending periodic syncing work (failed)", e)
                return Result.failure()
            }
            Logger.i(TAG, "Ending periodic syncing work (success)")
            return Result.success()
        }
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

    fun postNotificationForMissingMdocProximityPermissions() {
        // Go to main page, the user can request the permission there
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentTitle(applicationContext.getString(R.string.proximity_permissions_nfc_notification_title))
            .setContentText(applicationContext.getString(R.string.proximity_permissions_nfc_notification_content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        NotificationManagerCompat.from(applicationContext).notify(
            NOTIFICATION_ID_FOR_MISSING_PROXIMITY_PRESENTATION_PERMISSIONS,
            builder.build())
    }

    fun postNotificationForDocument(
        document: Document,
        message: String,
    ) {
        // TODO: include data so the user is brought to the info page for the document
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE)

        val cardArt = document.documentConfiguration.cardArt
        val bitmap = BitmapFactory.decodeByteArray(cardArt, 0, cardArt.size)

        val title = document.documentConfiguration.displayName
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

        val notificationId = document.name.hashCode()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
    }
}