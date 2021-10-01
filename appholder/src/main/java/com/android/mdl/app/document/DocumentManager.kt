package com.android.mdl.app.document

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.security.identity.*
import androidx.security.identity.IdentityCredentialStoreCapabilities.FEATURE_VERSION_202201
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.model.UnicodeString
import com.android.mdl.app.R
import com.android.mdl.app.provisioning.RefreshAuthenticationKeyFlow
import com.android.mdl.app.util.DocumentData
import com.android.mdl.app.util.DocumentData.AAMVA_NAMESPACE
import com.android.mdl.app.util.DocumentData.DUMMY_CREDENTIAL_NAME
import com.android.mdl.app.util.DocumentData.DUMMY_MVR_CREDENTIAL_NAME
import com.android.mdl.app.util.DocumentData.MDL_DOCTYPE
import com.android.mdl.app.util.DocumentData.MDL_NAMESPACE
import com.android.mdl.app.util.DocumentData.MVR_DOCTYPE
import com.android.mdl.app.util.DocumentData.MVR_NAMESPACE
import com.android.mdl.app.util.FormatUtil
import com.android.mdl.app.util.PreferencesHelper
import kotlinx.coroutines.runBlocking
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


class DocumentManager private constructor(private val context: Context) {

    companion object {
        private const val LOG_TAG = "DocumentManager"
        private const val MAX_USES_PER_KEY = 1
        private const val KEY_COUNT = 1

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DocumentManager? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DocumentManager(context).also { instance = it }
            }
    }

    private lateinit var store: IdentityCredentialStore

    // Database to store document information
    private val documentRepository = DocumentRepository.getInstance(
        DocumentDatabase.getInstance(context).credentialDao()
    )

    init {
        store = if (PreferencesHelper.hasHardwareBackedPreference(context)) {
            if (PreferencesHelper.isHardwareBacked(context)) {
                IdentityCredentialStore.getHardwareInstance(context)!!
            } else {
                IdentityCredentialStore.getSoftwareInstance(context)
            }
        } else {
            val mStore = IdentityCredentialStore.getInstance(context)
            // This app needs feature version 202201, if hardware implementation doesn't support
            // get software implementation
            if (mStore.capabilities.featureVersion != FEATURE_VERSION_202201) {
                PreferencesHelper.setHardwareBacked(context, false)
                IdentityCredentialStore.getSoftwareInstance(context)
            } else {
                PreferencesHelper.setHardwareBacked(context, mStore.capabilities.isHardwareBacked)
                mStore
            }
        }

        // We always use the same implementation once the app is installed
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sharedPreferences.contains("com.android.mdl.app.HARDWARE_BACKED")) {
            sharedPreferences.edit().putBoolean(
                "com.android.mdl.app.HARDWARE_BACKED",
                store.capabilities.isHardwareBacked
            ).apply()
        }
        runBlocking {
            // Load created documents from local database
            val documents = documentRepository.getAll()

            if (documents.isEmpty()) {
                // Create the dummy credential...
                createDummyCredential(store)?.let { document ->
                    documentRepository.insert(document)
                }
                // Create dummy mVR document...
                createDummyMvrDocument(store)?.let { document ->
                    documentRepository.insert(document)
                }
            }
        }


    }

    private fun createDummyMvrDocument(store: IdentityCredentialStore): Document? {
        createIssuingAuthorityKeyPair()?.let { iaKeyPair ->
            val iaSelfSignedCert = getSelfSignedIssuerAuthorityCertificate(iaKeyPair)
            try {
                val id = AccessControlProfileId(0)
                val ids: Collection<AccessControlProfileId> = listOf(id)
                val profile = AccessControlProfile.Builder(id)
                    .setUserAuthenticationRequired(false)
                    .build()

                val validFrom = UnicodeString("2021-04-19T22:00:00Z")
                validFrom.setTag(0)
                val validUntil = UnicodeString("2023-04-20T22:00:00Z")
                validUntil.setTag(0)
                val registrationInfo = CborBuilder().addMap()
                    .put("issuingCountry", "UT")
                    .put("competentAuthority", "RDW")
                    .put("registrationNumber", "E-01-23")
                    .put(UnicodeString("validFrom"), validFrom)
                    .put(UnicodeString("validUntil"), validFrom)
                    .end()
                    .build()[0]
                val issueDate = UnicodeString("2021-04-18")
                issueDate.setTag(1004)
                val registrationHolderAddress = CborBuilder().addMap()
                    .put("streetName", "teststraat")
                    .put("houseNumber", 86)
                    .put("postalCode", "1234 AA")
                    .put("placeOfResidence", "Samplecity")
                    .end()
                    .build()[0]
                val registrationHolderHolderInfo = CborBuilder().addMap()
                    .put("name", "Sample Name")
                    .put(UnicodeString("address"), registrationHolderAddress)
                    .end()
                    .build()[0]
                val registrationHolder = CborBuilder().addMap()
                    .put(UnicodeString("holderInfo"), registrationHolderHolderInfo)
                    .put("ownershipStatus", 2)
                    .end()
                    .build()[0]
                val basicVehicleInfo = CborBuilder().addMap()
                    .put(
                        UnicodeString("vehicle"), CborBuilder().addMap()
                            .put("make", "Dummymobile")
                            .end()
                            .build()[0]
                    )
                    .end()
                    .build()[0]

                val personalizationData = PersonalizationData.Builder()
                    .addAccessControlProfile(profile)
                    .putEntry(
                        MVR_NAMESPACE,
                        "registration_info",
                        ids,
                        FormatUtil.cborEncode(registrationInfo)
                    )
                    .putEntry(MVR_NAMESPACE, "issue_date", ids, FormatUtil.cborEncode(issueDate))
                    .putEntry(
                        MVR_NAMESPACE,
                        "registration_holder",
                        ids,
                        FormatUtil.cborEncode(registrationHolder)
                    )
                    .putEntry(
                        MVR_NAMESPACE,
                        "basic_vehicle_info",
                        ids,
                        FormatUtil.cborEncode(basicVehicleInfo)
                    )
                    .putEntryString(MVR_NAMESPACE, "vin", ids, "1M8GDM9AXKP042788")
                    .build()
                Utility.provisionSelfSignedCredential(
                    store,
                    DUMMY_MVR_CREDENTIAL_NAME,
                    iaKeyPair.private,
                    iaSelfSignedCert,
                    MVR_DOCTYPE,
                    personalizationData,
                    5,
                    1
                )
                return Document(
                    MVR_DOCTYPE,
                    DUMMY_MVR_CREDENTIAL_NAME,
                    DocumentData.MekbStaticData.VISIBLE_NAME.value,
                    BitmapFactory.decodeResource(
                        context.resources,
                        R.drawable.driving_license_bg
                    ),
                    store.capabilities.isHardwareBacked
                )
            } catch (e: IdentityCredentialException) {
                throw IllegalStateException("Error creating dummy credential", e)
            }
        }
        return null
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
                val birthDate = UnicodeString("1971-09-01")
                birthDate.setTag(1004)
                val issueDate = UnicodeString("2021-04-18")
                issueDate.setTag(1004)
                val expiryDate = UnicodeString("2026-08-31")
                birthDate.setTag(1004)
                val issueDateCatA = UnicodeString("2018-08-09")
                issueDate.setTag(1004)
                val expiryDateCatA = UnicodeString("2024-10-20")
                birthDate.setTag(1004)
                val issueDateCatB = UnicodeString("2017-02-23")
                issueDate.setTag(1004)
                val expiryDateCatB = UnicodeString("2024-10-20")
                birthDate.setTag(1004)
                val drivingPrivileges = CborBuilder().addArray()
                    .addMap()
                    .put("vehicle_category_code", "A")
                    .put(UnicodeString("issue_date"), issueDateCatA)
                    .put(UnicodeString("expiry_date"), expiryDateCatA)
                    .end()
                    .addMap()
                    .put("vehicle_category_code", "B")
                    .put(UnicodeString("issue_date"), issueDateCatB)
                    .put(UnicodeString("expiry_date"), expiryDateCatB)
                    .end()
                    .end()
                    .build()[0]
                val personalizationData = PersonalizationData.Builder()
                    .addAccessControlProfile(profile)
                    .putEntryString(MDL_NAMESPACE, "given_name", ids, "Erika")
                    .putEntryString(MDL_NAMESPACE, "family_name", ids, "Mustermann")
                    .putEntry(MDL_NAMESPACE, "birth_date", ids, FormatUtil.cborEncode(birthDate))
                    .putEntryBytestring(MDL_NAMESPACE, "portrait", ids, portrait)
                    .putEntry(MDL_NAMESPACE, "issue_date", ids, FormatUtil.cborEncode(issueDate))
                    .putEntry(MDL_NAMESPACE, "expiry_date", ids, FormatUtil.cborEncode(expiryDate))
                    .putEntryString(MDL_NAMESPACE, "issuing_country", ids, "US")
                    .putEntryString(MDL_NAMESPACE, "issuing_authority", ids, "Google")
                    .putEntryString(MDL_NAMESPACE, "document_number", ids, "NDL12345JKL")
                    .putEntry(
                        MDL_NAMESPACE,
                        "driving_privileges",
                        ids,
                        FormatUtil.cborEncode(drivingPrivileges)
                    )
                    .putEntryString(MDL_NAMESPACE, "un_distinguishing_sign", ids, "US")
                    .putEntryBoolean(AAMVA_NAMESPACE, "real_id", ids, true)
                    .build()
                Utility.provisionSelfSignedCredential(
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
                    store.capabilities.isHardwareBacked
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

    fun getDocuments(): Collection<Document> = runBlocking {
        documentRepository.getAll()
    }

    fun addDocument(
        docType: String,
        identityCredentialName: String,
        userVisibleName: String,
        serverUrl: String?,
        provisioningCode: String?
    ): Document {
        val document = Document(
            docType,
            identityCredentialName,
            userVisibleName,
            null,
            store.capabilities.isHardwareBacked,
            serverUrl,
            provisioningCode
        )
        runBlocking {
            documentRepository.insert(document)
        }
        return document
    }

    fun createCredential(credentialName: String, docType: String): WritableIdentityCredential {
        return store.createCredential(credentialName, docType)
    }

    fun deleteCredential(document: Document, credential: IdentityCredential): ByteArray? {
        val mStore = if (document.hardwareBacked)
            IdentityCredentialStore.getHardwareInstance(context)
                ?: IdentityCredentialStore.getSoftwareInstance(context)
        else
            IdentityCredentialStore.getSoftwareInstance(context)

        // Delete data from local storage
        runBlocking {
            documentRepository.delete(document)
        }

        // Delete credential provisioned on IC API
        return if (mStore.capabilities.isDeleteSupported) {
            credential.delete(byteArrayOf())
        } else {
            mStore.deleteCredentialByName(document.identityCredentialName)
        }
    }

    fun deleteCredentialByName(credentialName: String) {
        val document = runBlocking {
            documentRepository.findById(credentialName)
        } ?: return

        val mStore = if (document.hardwareBacked)
            IdentityCredentialStore.getHardwareInstance(context)
                ?: IdentityCredentialStore.getSoftwareInstance(context)
        else
            IdentityCredentialStore.getSoftwareInstance(context)

        val credential = mStore.getCredentialByName(
            document.identityCredentialName,
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
        )

        // Delete data from local storage
        runBlocking {
            documentRepository.delete(document)
        }

        // Delete credential provisioned on IC API
        if (mStore.capabilities.isDeleteSupported) {
            credential?.delete(byteArrayOf())
        } else {
            mStore.deleteCredentialByName(credentialName)
        }
    }

    fun setAvailableAuthKeys(credential: IdentityCredential) {
        credential.setAvailableAuthenticationKeys(KEY_COUNT, MAX_USES_PER_KEY)
    }

    fun getCredential(document: Document): IdentityCredential? {
        val mStore = if (document.hardwareBacked)
            IdentityCredentialStore.getHardwareInstance(context)
                ?: IdentityCredentialStore.getSoftwareInstance(context)
        else
            IdentityCredentialStore.getSoftwareInstance(context)

        return mStore.getCredentialByName(
            document.identityCredentialName,
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
        )
    }

    fun refreshAuthKeysNeedingCert() {
        getDocuments().forEach { document ->
            if (document.serverUrl?.isNotEmpty() == true) {
                getCredential(document)?.let { credential ->
                    // Will only call the server if necessary
                    refreshAuthKeysNeedingCertFromServer(credential, document.serverUrl)
                }
            }
        }
    }

    private fun refreshAuthKeysNeedingCertFromServer(
        credential: IdentityCredential,
        serverUrl: String
    ) {
        val dynAuthKeyCerts = credential.authKeysNeedingCertification
        val credentialCertificateChain = credential.credentialKeyCertificateChain

        // Start refresh auth key flow
        val refreshAuthKeyFlow =
            RefreshAuthenticationKeyFlow.getInstance(context, serverUrl)
        refreshAuthKeyFlow.setListener(object : RefreshAuthenticationKeyFlow.Listener {
            override fun onMessageSessionEnd(reason: String) {

                //Check if provisioning was successful
                if (reason == "Success") {
                    Log.d(LOG_TAG, "refreshAuthKeysNeedingCertFromServer: Done")
                }
            }

            override fun sendMessageRequestEnd(reason: String) {
                Log.d(LOG_TAG, "\n- sendMessageRequestEnd: $reason\n")
            }

            override fun onError(error: String) {
                Log.d(LOG_TAG, "\n- onError: $error\n")
            }

            override fun onMessageProveOwnership(challenge: ByteArray) {
                Log.d(
                    LOG_TAG,
                    "\n- onMessageProveOwnership: ${FormatUtil.encodeToString(challenge)}\n"
                )
                val proveOwnership = credential.proveOwnership(challenge)

                refreshAuthKeyFlow.sendMessageProveOwnership(proveOwnership)
            }

            override fun onMessageCertifyAuthKeysReady() {
                Log.d(LOG_TAG, "\n- onMessageCertifyAuthKeysReady")
                val builderArray = CborBuilder()
                    .addArray()
                dynAuthKeyCerts.forEach { cert ->
                    builderArray.add(cert.encoded)
                }
                val authKeyCerts = FormatUtil.cborEncode(builderArray.end().build()[0])
                refreshAuthKeyFlow.sendMessageAuthKeyNeedingCertification(authKeyCerts)
            }

            override fun onMessageStaticAuthData(staticAuthDataList: MutableList<ByteArray>) {
                Log.d(LOG_TAG, "\n- onMessageStaticAuthData ${staticAuthDataList.size} ")

                dynAuthKeyCerts.forEachIndexed { i, cert ->
                    Log.d(
                        LOG_TAG,
                        "Provisioned Issuer Auth ${FormatUtil.encodeToString(staticAuthDataList[i])} " +
                                "for Device Key ${FormatUtil.encodeToString(cert.publicKey.encoded)}"
                    )
                    credential.storeStaticAuthenticationData(cert, staticAuthDataList[i])
                }
                refreshAuthKeyFlow.sendMessageRequestEndSession()

            }
        })

        if (dynAuthKeyCerts.isNotEmpty()) {
            Log.d(LOG_TAG, "Device Keys needing certification ${dynAuthKeyCerts.size}")
            // returns the Cose_Sign1 Obj with the MSO in the payload
            credentialCertificateChain.first()?.publicKey?.let { publicKey ->
                val cborCoseKey = FormatUtil.cborBuildCoseKey(publicKey)
                refreshAuthKeyFlow.sendMessageCertifyAuthKeys(FormatUtil.cborEncode(cborCoseKey))
            }
        } else {
            Log.d(LOG_TAG, "No Device Keys Needing Certification for now")
        }
    }

    fun updateDocument(document: Document) {
        runBlocking {
            documentRepository.insert(document)
        }
    }
}