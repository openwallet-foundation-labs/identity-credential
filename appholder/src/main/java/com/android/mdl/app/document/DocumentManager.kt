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
import com.android.mdl.app.util.DocumentData.DUMMY_CREDENTIAL_NAME
import com.android.mdl.app.util.DocumentData.DUMMY_MICOV_CREDENTIAL_NAME
import com.android.mdl.app.util.DocumentData.DUMMY_MVR_CREDENTIAL_NAME
import com.android.mdl.app.util.DocumentData.MDL_DOCTYPE
import com.android.mdl.app.util.DocumentData.MICOV_DOCTYPE
import com.android.mdl.app.util.DocumentData.MVR_DOCTYPE
import com.android.mdl.app.util.DocumentData.MVR_NAMESPACE
import com.android.mdl.app.util.FormatUtil
import com.android.mdl.app.util.PreferencesHelper
import com.android.mdl.app.util.PreferencesHelper.HARDWARE_BACKED_PREFERENCE
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

    private val id = AccessControlProfileId(0)
    private val profile = AccessControlProfile.Builder(id)
        .setUserAuthenticationRequired(false)  // TODO: set to true at some point
        .build()

    private var ids: Collection<AccessControlProfileId> = listOf(id)

    private val store = if (PreferencesHelper.hasHardwareBackedPreference(context)) {
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

    // Database to store document information
    private val documentRepository = DocumentRepository.getInstance(
        DocumentDatabase.getInstance(context).credentialDao()
    )

    init {
        // We always use the same implementation once the app is installed
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sharedPreferences.contains(HARDWARE_BACKED_PREFERENCE)) {
            sharedPreferences.edit().putBoolean(
                HARDWARE_BACKED_PREFERENCE,
                store.capabilities.isHardwareBacked
            ).apply()
        }
        runBlocking {
            // Load created documents from local database
            val documents = documentRepository.getAll()

            if (documents.isEmpty()) {
                // Create the dummy credential...
                documentRepository.insert(createDummyCredential(store))
                // Create dummy mVR document...
                documentRepository.insert(createDummyMvrDocument(store))
                // Create dummy micov document...
                documentRepository.insert(createDummyMicovDocument(store))
            }
        }
    }

    private fun createDummyMicovDocument(store: IdentityCredentialStore): Document {
        try {
            provisionMicovDocument()
            return Document(
                MICOV_DOCTYPE,
                DUMMY_MICOV_CREDENTIAL_NAME,
                DocumentData.MicovStaticData.VISIBLE_NAME.value,
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

    fun provisionMicovDocument() {

        val iaSelfSignedCert = KeysAndCertificates.getMicovDsCertificate(context)

        ids = getAccessProfileIds()

        // org.micov.vtr.1
        val dob = UnicodeString("1964-08-12")
        dob.setTag(1004)
        val ra011dt = UnicodeString("2021-04-08")
        ra011dt.setTag(1004)
        val ra011nx = UnicodeString("2021-05-20")
        ra011nx.setTag(1004)
        val ra012dt = UnicodeString("2021-05-18")
        ra011dt.setTag(1004)
        val vRA011 = CborBuilder().addMap()
            .put("tg", "840539006")
            .put("vp", "1119349007")
            .put("mp", "EU/1/20/1528")
            .put("ma", "ORG-100030215")
            .put("bn", "B12345/67")
            .put("dn", "1")
            .put("sd", "2")
            .put(UnicodeString("dt"), ra011dt)
            .put("co", "UT")
            .put("ao", "RHI")
            .put(UnicodeString("nx"), ra011nx)
            .put("is", "SC17")
            .put("ci", "URN:UVCI:01:UT:187/37512422923")
            .end()
            .build()[0]
        val vRA012 = CborBuilder().addMap()
            .put("tg", "840539006")
            .put("vp", "1119349007")
            .put("mp", "EU/1/20/1528")
            .put("ma", "ORG-100030215")
            .put("bn", "B67890/12")
            .put("dn", "2")
            .put("sd", "2")
            .put(UnicodeString("dt"), ra012dt)
            .put("co", "UT")
            .put("ao", "RHI")
            .put("is", "SC17")
            .put("ci", "URN:UVCI:01:UT:187/37512533044")
            .end()
            .build()[0]
        val pidPPN = CborBuilder().addMap()
            .put("pty", "PPN")
            .put("pnr", "476284728")
            .put("pic", "UT")
            .end()
            .build()[0]
        val pidDL = CborBuilder().addMap()
            .put("pty", "DL")
            .put("pnr", "987654321")
            .put("pic", "UT")
            .end()
            .build()[0]

        // org.micov.attestation.1
        val timeOfTest = UnicodeString("2021-10-12T19:00:00Z")
        timeOfTest.setTag(0)
        val seCondExpiry = UnicodeString("2021-10-13T19:00:00Z")
        seCondExpiry.setTag(0)
        val ra01Test = CborBuilder().addMap()
            .put("Result", "260415000")
            .put("TypeOfTest", "LP6464-4")
            .put(UnicodeString("TimeOfTest"), timeOfTest)
            .end()
            .build()[0]
        val safeEntryLeisure = CborBuilder().addMap()
            .put("SeCondFulfilled", 1)
            .put("SeCondType", "leisure")
            .put(UnicodeString("SeCondExpiry"), seCondExpiry)
            .end()
            .build()[0]
        val bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.img_erika_portrait
        )
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val portrait: ByteArray = baos.toByteArray()
        val personalizationData = PersonalizationData.Builder()
            .putEntryString(DocumentData.MICOV_VTR_NAMESPACE, "fn", ids, "Mustermann")
            .putEntryString(DocumentData.MICOV_VTR_NAMESPACE, "gn", ids, "Erika")
            .putEntry(DocumentData.MICOV_VTR_NAMESPACE, "dob", ids, FormatUtil.cborEncode(dob))
            .putEntryInteger(DocumentData.MICOV_VTR_NAMESPACE, "sex", ids, 2)
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "v_RA01_1",
                ids,
                FormatUtil.cborEncode(vRA011)
            )
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "v_RA01_2",
                ids,
                FormatUtil.cborEncode(vRA012)
            )
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "pid_PPN",
                ids,
                FormatUtil.cborEncode(pidPPN)
            )
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "pid_DL",
                ids,
                FormatUtil.cborEncode(pidDL)
            )
            .putEntryInteger(DocumentData.MICOV_ATT_NAMESPACE, "1D47_vaccinated", ids, 2)
            .putEntryInteger(DocumentData.MICOV_ATT_NAMESPACE, "RA01_vaccinated", ids, 2)
            .putEntry(
                DocumentData.MICOV_ATT_NAMESPACE,
                "RA01_test",
                ids,
                FormatUtil.cborEncode(ra01Test)
            )
            .putEntry(
                DocumentData.MICOV_ATT_NAMESPACE,
                "safeEntry_Leisure",
                ids,
                FormatUtil.cborEncode(safeEntryLeisure)
            )
            .putEntryBytestring(DocumentData.MICOV_ATT_NAMESPACE, "fac", ids, portrait)
            .putEntryString(DocumentData.MICOV_ATT_NAMESPACE, "fni", ids, "M")
            .putEntryString(DocumentData.MICOV_ATT_NAMESPACE, "gni", ids, "E")
            .putEntryInteger(DocumentData.MICOV_ATT_NAMESPACE, "by", ids, 1964)
            .putEntryInteger(DocumentData.MICOV_ATT_NAMESPACE, "bm", ids, 8)
            .putEntryInteger(DocumentData.MICOV_ATT_NAMESPACE, "bd", ids, 12)

        // Add access control profile depending on the settings
        setAccessControlProfile(personalizationData)

        Utility.provisionSelfSignedCredential(
            store,
            DUMMY_MICOV_CREDENTIAL_NAME,
            KeysAndCertificates.getMicovDsKeyPair(context).private,
            iaSelfSignedCert,
            MICOV_DOCTYPE,
            personalizationData.build(),
            5,
            1
        )
    }

    // Access control profile based on settings
    private fun getAccessProfileIds(): Collection<AccessControlProfileId> {
        return listOf(id)
    }

    // Access control profile based on settings
    private fun setAccessControlProfile(personalizationData: PersonalizationData.Builder) {
        personalizationData.addAccessControlProfile(profile)
    }

    private fun createDummyMvrDocument(store: IdentityCredentialStore): Document {
        try {
            provisionMvrDocument()

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

    fun provisionMvrDocument() {
        val iaSelfSignedCert = KeysAndCertificates.getMekbDsCertificate(context)
        ids = getAccessProfileIds()

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

        setAccessControlProfile(personalizationData)

        Utility.provisionSelfSignedCredential(
            store,
            DUMMY_MVR_CREDENTIAL_NAME,
            KeysAndCertificates.getMekbDsKeyPair(context).private,
            iaSelfSignedCert,
            MVR_DOCTYPE,
            personalizationData.build(),
            5,
            1
        )
    }

    private fun createDummyCredential(store: IdentityCredentialStore): Document {
        try {
            provisionMdlDocument()
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

    fun provisionMdlDocument() {
        val iaSelfSignedCert = KeysAndCertificates.getMdlDsCertificate(context)
        val bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.img_erika_portrait
        )
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val portrait: ByteArray = baos.toByteArray()
        ids = getAccessProfileIds()

        val birthDate = UnicodeString("1971-09-01")
        birthDate.setTag(1004)
        val issueDate = UnicodeString("2021-04-18")
        issueDate.setTag(1004)
        val expiryDate = UnicodeString("2026-08-31")
        expiryDate.setTag(1004)
        val issueDateCatA = UnicodeString("2018-08-09")
        issueDateCatA.setTag(1004)
        val expiryDateCatA = UnicodeString("2024-10-20")
        expiryDateCatA.setTag(1004)
        val issueDateCatB = UnicodeString("2017-02-23")
        issueDateCatB.setTag(1004)
        val expiryDateCatB = UnicodeString("2024-10-20")
        expiryDateCatB.setTag(1004)
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
            .putEntryString(DocumentData.MDL_NAMESPACE, "given_name", ids, "Erika")
            .putEntryString(DocumentData.MDL_NAMESPACE, "family_name", ids, "Mustermann")
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "birth_date",
                ids,
                FormatUtil.cborEncode(birthDate)
            )
            .putEntryBytestring(DocumentData.MDL_NAMESPACE, "portrait", ids, portrait)
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "issue_date",
                ids,
                FormatUtil.cborEncode(issueDate)
            )
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "expiry_date",
                ids,
                FormatUtil.cborEncode(expiryDate)
            )
            .putEntryString(DocumentData.MDL_NAMESPACE, "issuing_country", ids, "UT")
            .putEntryString(DocumentData.MDL_NAMESPACE, "issuing_authority", ids, "Google")
            .putEntryString(DocumentData.MDL_NAMESPACE, "document_number", ids, "987654321")
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "driving_privileges",
                ids,
                FormatUtil.cborEncode(drivingPrivileges)
            )
            .putEntryString(DocumentData.MDL_NAMESPACE, "un_distinguishing_sign", ids, "UT")
            .putEntryBoolean(DocumentData.MDL_NAMESPACE, "age_over_18", ids, true)
            .putEntryBoolean(DocumentData.MDL_NAMESPACE, "age_over_21", ids, true)
            .putEntryBoolean(DocumentData.AAMVA_NAMESPACE, "real_id", ids, true)

        setAccessControlProfile(personalizationData)

        Utility.provisionSelfSignedCredential(
            store,
            DUMMY_CREDENTIAL_NAME,
            KeysAndCertificates.getMdlDsKeyPair(context).private,
            iaSelfSignedCert,
            MDL_DOCTYPE,
            personalizationData.build(),
            5,
            1
        )
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