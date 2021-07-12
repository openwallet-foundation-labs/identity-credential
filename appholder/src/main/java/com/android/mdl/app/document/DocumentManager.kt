package com.android.mdl.app.document

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyProperties
import androidx.security.identity.*
import com.android.mdl.app.R
import com.android.mdl.app.util.DocumentData
import com.android.mdl.app.util.DocumentData.AAMVA_NAMESPACE
import com.android.mdl.app.util.DocumentData.DUMMY_CREDENTIAL_NAME
import com.android.mdl.app.util.DocumentData.MDL_DOCTYPE
import com.android.mdl.app.util.DocumentData.MDL_NAMESPACE
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.OperatorCreationException
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.*
import kotlin.collections.Collection
import kotlin.collections.mutableListOf


class DocumentManager private constructor(private val context: Context) {

    companion object {
        private const val LOG_TAG = "DocumentManager"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DocumentManager? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DocumentManager(context).also { instance = it }
            }
    }

    // TODO: Review to add support for both software and hardware implementations
    private val store: IdentityCredentialStore =
        IdentityCredentialStore.getSoftwareInstance(context)

    private val documents = mutableListOf<Document>()

    init {
        // Create the dummy credential...
        createDummyCredential(store)?.let { document ->
            documents.add(document)
        }
    }

    private fun createDummyCredential(store: IdentityCredentialStore): Document? {
        createIssuingAuthorityKeyPair()?.let { iaKeyPair ->
            val iaSelfSignedCert = getSelfSignedIssuerAuthorityCertificate(iaKeyPair)
            val bitmap = BitmapFactory.decodeResource(
                context.resources,
                R.drawable.img_erika_portrait
            )
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
            val portrait: ByteArray = baos.toByteArray()
            try {
                val id = AccessControlProfileId(0)
                val ids: Collection<AccessControlProfileId> = listOf(id)
                val profile = AccessControlProfile.Builder(id)
                    .setUserAuthenticationRequired(false)
                    .build()
                val personalizationData = PersonalizationData.Builder()
                    .addAccessControlProfile(profile)
                    .putEntryString(MDL_NAMESPACE, "given_name", ids, "Erika")
                    .putEntryString(MDL_NAMESPACE, "family_name", ids, "Mustermann")
                    .putEntryBytestring(MDL_NAMESPACE, "portrait", ids, portrait)
                    .putEntryBoolean(AAMVA_NAMESPACE, "real_id", ids, true)
                    .build()
                Helpers.provisionSelfSignedCredential(
                    store,
                    DUMMY_CREDENTIAL_NAME,
                    iaKeyPair.private,
                    iaSelfSignedCert,
                    MDL_DOCTYPE,
                    personalizationData,
                    5,
                    1
                )
                return Document(
                    MDL_DOCTYPE,
                    DUMMY_CREDENTIAL_NAME,
                    DocumentData.ErikaStaticData.VISIBLE_NAME.value,
                    BitmapFactory.decodeResource(
                        context.resources,
                        R.drawable.driving_license_bg
                    ),
                    false
                )
            } catch (e: IdentityCredentialException) {
                throw IllegalStateException("Error creating dummy credential", e)
            }
        }
        return null
    }


    private fun getSelfSignedIssuerAuthorityCertificate(keyPair: KeyPair): X509Certificate {
        val issuer = X500Name("CN=State Of Utopia")
        val subject = X500Name("CN=State Of Utopia Issuing Authority Signing Key")

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
            keyPair.public
        )
        return try {
            val signer: ContentSigner = JcaContentSignerBuilder("SHA256withECDSA")
                .build(keyPair.private)
            val encodedCert: ByteArray = builder.build(signer).encoded
            val cf: CertificateFactory = CertificateFactory.getInstance("X.509")
            val bais = ByteArrayInputStream(encodedCert)
            cf.generateCertificate(bais) as X509Certificate
        } catch (e: OperatorCreationException) {
            throw RuntimeException(
                "Error generating self-signed issuer authority certificate",
                e
            )
        } catch (e: CertificateException) {
            throw RuntimeException(
                "Error generating self-signed issuer authority certificate",
                e
            )
        } catch (e: IOException) {
            throw RuntimeException(
                "Error generating self-signed issuer authority certificate",
                e
            )
        }
    }

    private fun createIssuingAuthorityKeyPair(): KeyPair? {
        try {
            val kpg: KeyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            val ecSpec = ECGenParameterSpec("prime256v1")
            kpg.initialize(ecSpec)
            return kpg.generateKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: InvalidAlgorithmParameterException) {
            e.printStackTrace()
        }
        return null
    }

    fun getDocuments(): Collection<Document> {
        return documents
    }

    fun addDocument(document: Document) {
        documents.add(document)
    }
}