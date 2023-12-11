package com.android.identity_credential.wallet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.widget.Toast
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.CredentialStore
import com.android.identity.credential.NameSpacedData
import com.android.identity.internal.Util
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
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.Random
import kotlin.math.ceil

class WalletApplication : Application() {
    companion object {
        private const val TAG = "WalletApplication"

        val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        val MDL_NAMESPACE = "org.iso.18013.5.1"
        val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"
    }

    lateinit var secureAreaRepository: SecureAreaRepository
    lateinit var credentialStore: CredentialStore

    private lateinit var androidKeystoreSecureArea: AndroidKeystoreSecureArea

    override fun onCreate() {
        super.onCreate()
        Logger.d(TAG, "onCreate")

        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in the OS itself.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())

        // Setup singletons
        val storageDir = File(applicationContext.noBackupFilesDir, "identity")
        val storageEngine = AndroidStorageEngine.Builder(applicationContext, storageDir).build()
        secureAreaRepository = SecureAreaRepository()
        androidKeystoreSecureArea = AndroidKeystoreSecureArea(applicationContext, storageEngine)
        secureAreaRepository.addImplementation(androidKeystoreSecureArea);
        credentialStore = CredentialStore(storageEngine, secureAreaRepository)
    }

    private fun createArtwork(color1: Int,
                              color2: Int,
                              artworkText: String): ByteArray {
        val width = 800
        val height = ceil(width.toFloat() * 2.125 / 3.375).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint()
        bgPaint.setShader(
            RadialGradient(
                width / 2f, height / 2f,
                height / 0.5f, color1, color2, Shader.TileMode.MIRROR
            )
        )
        val round = bitmap.width / 25f
        canvas.drawRoundRect(
            0f,
            0f,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            round,
            round,
            bgPaint
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.setColor(android.graphics.Color.WHITE)
        paint.textSize = bitmap.width / 10.0f
        paint.setShadowLayer(2.0f, 1.0f, 1.0f, android.graphics.Color.BLACK)
        val bounds = Rect()
        paint.getTextBounds(artworkText, 0, artworkText.length, bounds)
        val textPadding = bitmap.width/25f
        val x: Float = textPadding
        val y: Float = bitmap.height - bounds.height() - textPadding + paint.textSize/2
        canvas.drawText(artworkText, x, y, paint)

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }

    // Returns true if the mDL was added, false otherwise
    fun addSelfsignedMdl(): Boolean {
        if (credentialStore.lookupCredential("mDL_Erika") == null) {
            provisionCredential(
                "mDL_Erika",
                "Erika's Driving License",
                android.graphics.Color.rgb(64, 255, 64),
                android.graphics.Color.rgb(0, 96, 0),
                "E MUS",
                "Erika",
                "Mustermann",
                R.drawable.img_erika_portrait
            )
            return true
        }
        if (credentialStore.lookupCredential("mDL_Max") == null) {
            provisionCredential(
                "mDL_Max",
                "Max's Driving License",
                android.graphics.Color.rgb(64, 64, 255),
                android.graphics.Color.rgb(0, 0, 96),
                "M EXA",
                "Max",
                "Example-Person",
                R.drawable.img_erika_portrait
            )
            return true
        }
        return false
    }

    private fun provisionCredential(
        credentialId: String,
        displayName: String,
        color1: Int,
        color2: Int,
        artworkText: String,
        givenName: String,
        familyName: String,
        portrait_id: Int
    ) {
        val credential = credentialStore.createCredential(credentialId)

        credential.applicationData.setData("artwork", createArtwork(color1, color2, artworkText))
        credential.applicationData.setString("displayName", displayName)
        credential.applicationData.setString("docType", "org.iso.18013.5.1.mDL")

        val baos = ByteArrayOutputStream()
        BitmapFactory.decodeResource(
            applicationContext.resources,
            portrait_id
        ).compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val portrait: ByteArray = baos.toByteArray()

        val now = Timestamp.now()
        val issueDate = now
        val expiryDate = Timestamp.ofEpochMilli(issueDate.toEpochMilli() + 5*365*24*3600*1000L)

        val credentialData = NameSpacedData.Builder()
            .putEntryString(MDL_NAMESPACE, "given_name", givenName)
            .putEntryString(MDL_NAMESPACE, "family_name", familyName)
            .putEntryByteString(MDL_NAMESPACE, "portrait", portrait)
            .putEntryNumber(MDL_NAMESPACE, "sex", 2)
            .putEntry(MDL_NAMESPACE, "issue_date", Util.cborEncodeDateTime(issueDate))
            .putEntry(MDL_NAMESPACE, "expiry_date", Util.cborEncodeDateTime(expiryDate))
            .putEntryString(MDL_NAMESPACE, "document_number", "1234567890")
            .putEntryString(MDL_NAMESPACE, "issuing_authority", "State of Utopia")
            .putEntryString(AAMVA_NAMESPACE, "DHS_compliance", "F")
            .putEntryNumber(AAMVA_NAMESPACE, "EDL_credential", 1)
            .putEntryBoolean(MDL_NAMESPACE, "age_over_18", true)
            .putEntryBoolean(MDL_NAMESPACE, "age_over_21", true)
            .build()
        credential.applicationData.setNameSpacedData("credentialData", credentialData)
        credential.applicationData.setString("docType", MDL_DOCTYPE)

        // Create AuthKeys and MSOs, make sure they're valid for a long time
        val timeSigned = now
        val validFrom = now
        val validUntil = Timestamp.ofEpochMilli(validFrom.toEpochMilli() + 365*24*3600*1000L)

        // Create three authentication keys and certify them
        for (n in 0..2) {
            val pendingAuthKey = credential.createPendingAuthenticationKey(
                "mdoc",
                androidKeystoreSecureArea,
                SecureArea.CreateKeySettings("".toByteArray()),
                null
            )

            // Generate an MSO and issuer-signed data for this authentication key.
            val msoGenerator = MobileSecurityObjectGenerator(
                "SHA-256",
                MDL_DOCTYPE,
                pendingAuthKey.attestation[0].publicKey,
                SecureArea.EC_CURVE_P256
            )
            msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)
            val deterministicRandomProvider = Random(42)
            val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                credentialData,
                deterministicRandomProvider,
                16,
                null
            )
            for (nameSpaceName in issuerNameSpaces.keys) {
                val digests = MdocUtil.calculateDigestsForNameSpace(
                    nameSpaceName,
                    issuerNameSpaces,
                    "SHA-256"
                )
                msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
            }
            val issuerKeyPair: KeyPair = generateIssuingAuthorityKeyPair()
            val issuerCert = getSelfSignedIssuerAuthorityCertificate(issuerKeyPair)

            val mso = msoGenerator.generate()
            val taggedEncodedMso = Util.cborEncode(Util.cborBuildTaggedByteString(mso))
            val issuerCertChain = listOf(issuerCert)
            val encodedIssuerAuth = Util.cborEncode(
                Util.coseSign1Sign(
                    issuerKeyPair.private,
                    "SHA256withECDSA", taggedEncodedMso,
                    null,
                    issuerCertChain
                )
            )

            val issuerProvidedAuthenticationData = StaticAuthDataGenerator(
                MdocUtil.stripIssuerNameSpaces(issuerNameSpaces, null),
                encodedIssuerAuth
            ).generate()

            pendingAuthKey.certify(issuerProvidedAuthenticationData, validFrom, validUntil)
        }
        Logger.d(TAG, "Created credential with name ${credential.name}")
    }

    private fun generateIssuingAuthorityKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        kpg.initialize(ecSpec)
        return kpg.generateKeyPair()
    }

    private fun getSelfSignedIssuerAuthorityCertificate(
        issuerAuthorityKeyPair: KeyPair
    ): X509Certificate {
        val issuer: X500Name = X500Name("CN=State Of Utopia")
        val subject: X500Name = X500Name("CN=State Of Utopia Issuing Authority Signing Key")

        // Valid from now to five years from now.
        val now = Date()
        val kMilliSecsInOneYear = 365L * 24 * 60 * 60 * 1000
        val expirationDate = Date(now.time + 5 * kMilliSecsInOneYear)
        val serial = BigInteger("42")
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            now,
            expirationDate,
            subject,
            issuerAuthorityKeyPair.public
        )
        val signer: ContentSigner = JcaContentSignerBuilder("SHA256withECDSA")
            .build(issuerAuthorityKeyPair.private)
        val certHolder: X509CertificateHolder = builder.build(signer)
        return JcaX509CertificateConverter().getCertificate(certHolder)
    }
}