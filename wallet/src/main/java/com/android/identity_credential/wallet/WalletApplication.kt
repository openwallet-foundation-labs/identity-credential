package com.android.identity_credential.wallet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.CredentialStore
import com.android.identity.credential.NameSpacedData
import com.android.identity.credentialtype.CredentialTypeRepository
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.internal.Util
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.issuance.evidence.EvidenceType
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.IssuingAuthorityRepository
import com.android.identity.issuance.evidence.EvidenceRequest
import com.android.identity.issuance.evidence.EvidenceRequestMessage
import com.android.identity.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceRequestQuestionString
import com.android.identity.issuance.simple.SimpleIssuingAuthority
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import kotlin.math.ceil

class WalletApplication : Application() {
    companion object {
        private const val TAG = "WalletApplication"
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