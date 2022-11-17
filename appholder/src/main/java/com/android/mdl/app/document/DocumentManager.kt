package com.android.mdl.app.document

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.model.UnicodeString
import com.android.identity.*
import com.android.identity.IdentityCredentialStore.IMPLEMENTATION_TYPE_HARDWARE
import com.android.mdl.app.util.DocumentData
import com.android.mdl.app.util.DocumentData.MDL_DOCTYPE
import com.android.mdl.app.util.DocumentData.MICOV_DOCTYPE
import com.android.mdl.app.util.DocumentData.MVR_DOCTYPE
import com.android.mdl.app.util.DocumentData.MVR_NAMESPACE
import com.android.mdl.app.util.FormatUtil
import com.android.mdl.app.util.PreferencesHelper
import com.android.mdl.app.util.PreferencesHelper.HARDWARE_BACKED_PREFERENCE
import com.android.mdl.app.util.SelfSignedDocumentData
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.security.cert.X509Certificate
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

    // Database to store document information
    private val documentRepository = DocumentRepository.getInstance(
        DocumentDatabase.getInstance(context).credentialDao()
    )

    init {
        PreferencesHelper.setHardwareBacked(context, false)

        // We always use the same implementation once the app is installed
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sharedPreferences.contains(HARDWARE_BACKED_PREFERENCE)) {
            sharedPreferences.edit().putBoolean(
                HARDWARE_BACKED_PREFERENCE,
                true
            ).apply()
        }
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
            PreferencesHelper.isHardwareBacked(context),
            selfSigned = false,
            userAuthentication = true,
            KEY_COUNT,
            MAX_USES_PER_KEY,
            provisioningCode,
            serverUrl
        )
        runBlocking {
            documentRepository.insert(document)
        }
        return document
    }

    fun createCredential(
        document: Document?,
        credentialName: String,
        docType: String
    ): WritableIdentityCredential {
        if (document == null) {
            throw IllegalArgumentException("Document is null")
        }
        val mStore = if (document.hardwareBacked)
            IdentityCredentialStore.getHardwareInstance(context)
                ?: IdentityCredentialStore.getKeystoreInstance(
                    context,
                    PreferencesHelper.getKeystoreBackedStorageLocation(context)
                )
        else
            IdentityCredentialStore.getKeystoreInstance(
                context,
                PreferencesHelper.getKeystoreBackedStorageLocation(context)
            )

        return mStore.createCredential(credentialName, docType)
    }

    fun deleteCredential(document: Document, credential: IdentityCredential): ByteArray {
        // Delete data from local storage
        runBlocking {
            documentRepository.delete(document)
        }

        // Delete credential provisioned on IC API
        return credential.delete(byteArrayOf())
    }

    fun deleteCredentialByName(credentialName: String) {
        val document = runBlocking {
            documentRepository.findById(credentialName)
        } ?: return

        val mStore = if (document.hardwareBacked)
            IdentityCredentialStore.getHardwareInstance(context)
                ?: IdentityCredentialStore.getKeystoreInstance(context,
                    PreferencesHelper.getKeystoreBackedStorageLocation(context))
        else
            IdentityCredentialStore.getKeystoreInstance(context,
                PreferencesHelper.getKeystoreBackedStorageLocation(context))

        val credential = mStore.getCredentialByName(
            document.identityCredentialName,
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
        )

        // Delete data from local storage
        runBlocking {
            documentRepository.delete(document)
        }

        // Delete credential provisioned on IC API
        credential?.delete(byteArrayOf())
    }

    fun setAvailableAuthKeys(credential: IdentityCredential) {
        credential.setAvailableAuthenticationKeys(KEY_COUNT, MAX_USES_PER_KEY)
    }

    fun getCredential(document: Document): IdentityCredential? {
        val mStore = if (document.hardwareBacked)
            IdentityCredentialStore.getHardwareInstance(context)
                ?: IdentityCredentialStore.getKeystoreInstance(context,
                    PreferencesHelper.getKeystoreBackedStorageLocation(context))
        else
            IdentityCredentialStore.getKeystoreInstance(context,
                PreferencesHelper.getKeystoreBackedStorageLocation(context))

        return mStore.getCredentialByName(
            document.identityCredentialName,
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
        )
    }

    fun updateDocument(document: Document) {
        runBlocking {
            documentRepository.insert(document)
        }
    }

    fun createSelfSignedCredential(dData: SelfSignedDocumentData) {
        runBlocking {

            val docName = getUniqueDocumentName(dData.provisionInfo.docName, 1)
            dData.provisionInfo.docName = docName

            try {
                if (MDL_DOCTYPE == dData.provisionInfo.docType) {
                    provisionSelfSignedMdl(dData)
                } else if (MVR_DOCTYPE == dData.provisionInfo.docType) {
                    provisionSelfSignedMvr(dData)
                } else if (MICOV_DOCTYPE == dData.provisionInfo.docType) {
                    provisionSelfSignedMicov(dData)
                } else {
                    throw IllegalArgumentException("Invalid docType to create self signed document ${dData.provisionInfo.docType}")
                }
                val document = Document(
                    dData.provisionInfo.docType,
                    "${dData.provisionInfo.docType}-$docName",
                    docName,
                    null,
                    dData.provisionInfo.storageImplementationType == IMPLEMENTATION_TYPE_HARDWARE,
                    selfSigned = true,
                    userAuthentication = dData.provisionInfo.userAuthentication,
                    dData.provisionInfo.numberMso,
                    dData.provisionInfo.maxUseMso,
                    cardArt = dData.provisionInfo.docColor
                )
                // Insert new document in our local database
                documentRepository.insert(document)
            } catch (e: IdentityCredentialException) {
                throw IllegalStateException("Error creating self signed credential", e)
            }
        }
    }

    private suspend fun getUniqueDocumentName(docName: String, count: Int): String {
        documentRepository.getAll().forEach { doc ->
            if (doc.userVisibleName == docName) {
                return getUniqueDocumentName("$docName ($count)", count + 1)
            }
        }
        return docName
    }

    private fun provisionSelfSignedMdl(dData: SelfSignedDocumentData) {

        val idSelf = AccessControlProfileId(0)
        val profileSelfBuilder = AccessControlProfile.Builder(idSelf)
            .setUserAuthenticationRequired(dData.provisionInfo.userAuthentication)
        if (dData.provisionInfo.userAuthentication) {
            profileSelfBuilder.setUserAuthenticationTimeout(30 * 1000)
        }
        val profileSelf = profileSelfBuilder.build()
        val idsSelf = listOf(idSelf)

        val iaSelfSignedCert = KeysAndCertificates.getMdlDsCertificate(context)
        val bitmap = dData.getValueBitmap("portrait")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val portrait: ByteArray = baos.toByteArray()
        val bitmapSignature = dData.getValueBitmap("signature_usual_mark")
        baos.reset()
        bitmapSignature.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val signature: ByteArray = baos.toByteArray()

        val birthDate = UnicodeString(dData.getValueString("birth_date"))
        birthDate.setTag(1004)
        val issueDate = UnicodeString(dData.getValueString("issue_date"))
        issueDate.setTag(1004)
        val expiryDate = UnicodeString(dData.getValueString("expiry_date"))
        expiryDate.setTag(1004)
        val issueDateCatA = UnicodeString(dData.getValueString("issue_date_1"))
        issueDateCatA.setTag(1004)
        val expiryDateCatA = UnicodeString(dData.getValueString("expiry_date_1"))
        expiryDateCatA.setTag(1004)
        val issueDateCatB = UnicodeString(dData.getValueString("issue_date_2"))
        issueDateCatB.setTag(1004)
        val expiryDateCatB = UnicodeString(dData.getValueString("expiry_date_2"))
        expiryDateCatB.setTag(1004)
        val drivingPrivileges = CborBuilder().addArray()
            .addMap()
            .put("vehicle_category_code", dData.getValueString("vehicle_category_code_1"))
            .put(UnicodeString("issue_date"), issueDateCatA)
            .put(UnicodeString("expiry_date"), expiryDateCatA)
            .end()
            .addMap()
            .put("vehicle_category_code", dData.getValueString("vehicle_category_code_2"))
            .put(UnicodeString("issue_date"), issueDateCatB)
            .put(UnicodeString("expiry_date"), expiryDateCatB)
            .end()
            .end()
            .build()[0]

        val personalizationData = PersonalizationData.Builder()
            .putEntryString(DocumentData.MDL_NAMESPACE, "given_name", idsSelf, dData.getValueString("given_name"))
            .putEntryString(DocumentData.MDL_NAMESPACE, "family_name", idsSelf, dData.getValueString("family_name"))
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "birth_date",
                idsSelf,
                FormatUtil.cborEncode(birthDate)
            )
            .putEntryBytestring(DocumentData.MDL_NAMESPACE, "portrait", idsSelf, portrait)
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "issue_date",
                idsSelf,
                FormatUtil.cborEncode(issueDate)
            )
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "expiry_date",
                idsSelf,
                FormatUtil.cborEncode(expiryDate)
            )
            .putEntryString(
                DocumentData.MDL_NAMESPACE,
                "issuing_country",
                idsSelf,
                dData.getValueString("issuing_country")
            )
            .putEntryString(
                DocumentData.MDL_NAMESPACE,
                "issuing_authority",
                idsSelf,
                dData.getValueString("issuing_authority")
            )
            .putEntryString(
                DocumentData.MDL_NAMESPACE,
                "document_number",
                idsSelf,
                dData.getValueString("document_number")
            )
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "driving_privileges",
                idsSelf,
                FormatUtil.cborEncode(drivingPrivileges)
            )
            .putEntryString(
                DocumentData.MDL_NAMESPACE,
                "un_distinguishing_sign",
                idsSelf,
                dData.getValueString("un_distinguishing_sign")
            )
            .putEntryBoolean(
                DocumentData.MDL_NAMESPACE,
                "age_over_18",
                idsSelf,
                dData.getValueBoolean("age_over_18")
            )
            .putEntryBoolean(
                DocumentData.MDL_NAMESPACE,
                "age_over_21",
                idsSelf,
                dData.getValueBoolean("age_over_21")
            )
            .putEntryBytestring(
                DocumentData.MDL_NAMESPACE,
                "signature_usual_mark",
                idsSelf,
                signature
            )
            .putEntryInteger(DocumentData.MDL_NAMESPACE, "sex", idsSelf, dData.getValueString("sex").toLong())
            .putEntryString(DocumentData.AAMVA_NAMESPACE, "aamva_version", idsSelf, dData.getValueString("aamva_version"))
            .putEntryString(DocumentData.AAMVA_NAMESPACE, "EDL_credential", idsSelf, dData.getValueString("aamva_EDL_credential"))
            .putEntryString(DocumentData.AAMVA_NAMESPACE, "DHS_compliance", idsSelf, dData.getValueString("aamva_DHS_compliance"))
            .putEntryString(DocumentData.AAMVA_NAMESPACE, "given_name_truncation", idsSelf, dData.getValueString("aamva_given_name_truncation"))
            .putEntryString(DocumentData.AAMVA_NAMESPACE, "family_name_truncation", idsSelf, dData.getValueString("aamva_family_name_truncation"))
            .putEntryInteger(DocumentData.AAMVA_NAMESPACE, "sex", idsSelf, dData.getValueString("aamva_sex").toLong())

        personalizationData.addAccessControlProfile(profileSelf)

        provisionSelfSigned(dData, iaSelfSignedCert, personalizationData.build())
    }

    private fun provisionSelfSignedMvr(dData: SelfSignedDocumentData) {

        val idSelf = AccessControlProfileId(0)
        val profileSelfBuilder = AccessControlProfile.Builder(idSelf)
            .setUserAuthenticationRequired(dData.provisionInfo.userAuthentication)
        if (dData.provisionInfo.userAuthentication) {
            profileSelfBuilder.setUserAuthenticationTimeout(30 * 1000)
        }
        val profileSelf = profileSelfBuilder.build()
        val idsSelf = listOf(idSelf)
        val iaSelfSignedCert = KeysAndCertificates.getMekbDsCertificate(context)

        val validFrom = UnicodeString(dData.getValueString("validFrom"))
        validFrom.setTag(0)
        val validUntil = UnicodeString(dData.getValueString("validUntil"))
        validUntil.setTag(0)
        val registrationInfo = CborBuilder().addMap()
            .put("issuingCountry", dData.getValueString("issuingCountry"))
            .put("competentAuthority", dData.getValueString("competentAuthority"))
            .put("registrationNumber", dData.getValueString("registrationNumber"))
            .put(UnicodeString("validFrom"), validFrom)
            .put(UnicodeString("validUntil"), validFrom)
            .end()
            .build()[0]
        val issueDate = UnicodeString(dData.getValueString("issueDate"))
        issueDate.setTag(1004)
        val registrationHolderAddress = CborBuilder().addMap()
            .put("streetName", dData.getValueString("streetName"))
            .put("houseNumber", dData.getValueLong("houseNumber"))
            .put("postalCode", dData.getValueString("postalCode"))
            .put("placeOfResidence", dData.getValueString("placeOfResidence"))
            .end()
            .build()[0]
        val registrationHolderHolderInfo = CborBuilder().addMap()
            .put("name", dData.getValueString("name"))
            .put(UnicodeString("address"), registrationHolderAddress)
            .end()
            .build()[0]
        val registrationHolder = CborBuilder().addMap()
            .put(UnicodeString("holderInfo"), registrationHolderHolderInfo)
            .put("ownershipStatus", dData.getValueLong("ownershipStatus"))
            .end()
            .build()[0]
        val basicVehicleInfo = CborBuilder().addMap()
            .put(
                UnicodeString("vehicle"), CborBuilder().addMap()
                    .put("make", dData.getValueString("make"))
                    .end()
                    .build()[0]
            )
            .end()
            .build()[0]

        val personalizationData = PersonalizationData.Builder()
            .putEntry(
                MVR_NAMESPACE,
                "registration_info",
                idsSelf,
                FormatUtil.cborEncode(registrationInfo)
            )
            .putEntry(MVR_NAMESPACE, "issue_date", idsSelf, FormatUtil.cborEncode(issueDate))
            .putEntry(
                MVR_NAMESPACE,
                "registration_holder",
                idsSelf,
                FormatUtil.cborEncode(registrationHolder)
            )
            .putEntry(
                MVR_NAMESPACE,
                "basic_vehicle_info",
                idsSelf,
                FormatUtil.cborEncode(basicVehicleInfo)
            )
            .putEntryString(MVR_NAMESPACE, "vin", idsSelf, "1M8GDM9AXKP042788")

        personalizationData.addAccessControlProfile(profileSelf)

        provisionSelfSigned(dData, iaSelfSignedCert, personalizationData.build())
    }

    private fun provisionSelfSignedMicov(dData: SelfSignedDocumentData) {

        val idSelf = AccessControlProfileId(0)
        val profileSelfBuilder = AccessControlProfile.Builder(idSelf)
            .setUserAuthenticationRequired(dData.provisionInfo.userAuthentication)
        if (dData.provisionInfo.userAuthentication) {
            profileSelfBuilder.setUserAuthenticationTimeout(30 * 1000)
        }
        val profileSelf = profileSelfBuilder.build()
        val idsSelf = listOf(idSelf)
        val iaSelfSignedCert = KeysAndCertificates.getMicovDsCertificate(context)

        // org.micov.vtr.1
        val dob = UnicodeString(dData.getValueString("dob"))
        dob.setTag(1004)
        val ra011dt = UnicodeString(dData.getValueString("RA01_1_dt"))
        ra011dt.setTag(1004)
        val ra011nx = UnicodeString(dData.getValueString("RA01_1_nx"))
        ra011nx.setTag(1004)
        val ra012dt = UnicodeString(dData.getValueString("RA01_2_dt"))
        ra011dt.setTag(1004)
        val vRA011 = CborBuilder().addMap()
            .put("tg", dData.getValueString("RA01_1_tg"))
            .put("vp", dData.getValueString("RA01_1_vp"))
            .put("mp", dData.getValueString("RA01_1_mp"))
            .put("ma", dData.getValueString("RA01_1_ma"))
            .put("bn", dData.getValueString("RA01_1_bn"))
            .put("dn", dData.getValueString("RA01_1_dn"))
            .put("sd", dData.getValueString("RA01_1_sd"))
            .put(UnicodeString("dt"), ra011dt)
            .put("co", dData.getValueString("RA01_1_co"))
            .put("ao", dData.getValueString("RA01_1_ao"))
            .put(UnicodeString("nx"), ra011nx)
            .put("is", dData.getValueString("RA01_1_is"))
            .put("ci", dData.getValueString("RA01_1_ci"))
            .end()
            .build()[0]
        val vRA012 = CborBuilder().addMap()
            .put("tg", dData.getValueString("RA01_2_tg"))
            .put("vp", dData.getValueString("RA01_2_vp"))
            .put("mp", dData.getValueString("RA01_2_mp"))
            .put("ma", dData.getValueString("RA01_2_ma"))
            .put("bn", dData.getValueString("RA01_2_bn"))
            .put("dn", dData.getValueString("RA01_2_dn"))
            .put("sd", dData.getValueString("RA01_2_sd"))
            .put(UnicodeString("dt"), ra012dt)
            .put("co", dData.getValueString("RA01_2_co"))
            .put("ao", dData.getValueString("RA01_2_ao"))
            .put("is", dData.getValueString("RA01_2_is"))
            .put("ci", dData.getValueString("RA01_2_ci"))
            .end()
            .build()[0]
        val pidPPN = CborBuilder().addMap()
            .put("pty", dData.getValueString("PPN_pty"))
            .put("pnr", dData.getValueString("PPN_pnr"))
            .put("pic", dData.getValueString("PPN_pic"))
            .end()
            .build()[0]
        val pidDL = CborBuilder().addMap()
            .put("pty", dData.getValueString("DL_pty"))
            .put("pnr", dData.getValueString("DL_pnr"))
            .put("pic", dData.getValueString("DL_pic"))
            .end()
            .build()[0]

        // org.micov.attestation.1
        val timeOfTest = UnicodeString("2021-10-12T19:00:00Z")
        timeOfTest.setTag(0)
        val seCondExpiry = UnicodeString(dData.getValueString("SeCondExpiry"))
        seCondExpiry.setTag(0)
        val ra01Test = CborBuilder().addMap()
            .put("Result", dData.getValueLong("Result"))
            .put("TypeOfTest", dData.getValueString("TypeOfTest"))
            .put(UnicodeString("TimeOfTest"), timeOfTest)
            .end()
            .build()[0]
        val safeEntryLeisure = CborBuilder().addMap()
            .put("SeCondFulfilled", dData.getValueLong("SeCondFulfilled"))
            .put("SeCondType", dData.getValueString("SeCondType"))
            .put(UnicodeString("SeCondExpiry"), seCondExpiry)
            .end()
            .build()[0]

        val bitmap = dData.getValueBitmap("fac")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val portrait: ByteArray = baos.toByteArray()
        val personalizationData = PersonalizationData.Builder()
            .putEntryString(DocumentData.MICOV_VTR_NAMESPACE, "fn", idsSelf, dData.getValueString("fn"))
            .putEntryString(DocumentData.MICOV_VTR_NAMESPACE, "gn", idsSelf, dData.getValueString("gn"))
            .putEntry(DocumentData.MICOV_VTR_NAMESPACE, "dob", idsSelf, FormatUtil.cborEncode(dob))
            .putEntryInteger(DocumentData.MICOV_VTR_NAMESPACE, "sex", idsSelf, dData.getValueLong("sex"))
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "v_RA01_1",
                idsSelf,
                FormatUtil.cborEncode(vRA011)
            )
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "v_RA01_2",
                idsSelf,
                FormatUtil.cborEncode(vRA012)
            )
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "pid_PPN",
                idsSelf,
                FormatUtil.cborEncode(pidPPN)
            )
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "pid_DL",
                idsSelf,
                FormatUtil.cborEncode(pidDL)
            )
            .putEntryInteger(DocumentData.MICOV_ATT_NAMESPACE, "1D47_vaccinated", idsSelf, dData.getValueLong("1D47_vaccinated"))
            .putEntryInteger(DocumentData.MICOV_ATT_NAMESPACE, "RA01_vaccinated", idsSelf, dData.getValueLong("RA01_vaccinated"))
            .putEntry(
                DocumentData.MICOV_ATT_NAMESPACE,
                "RA01_test",
                idsSelf,
                FormatUtil.cborEncode(ra01Test)
            )
            .putEntry(
                DocumentData.MICOV_ATT_NAMESPACE,
                "safeEntry_Leisure",
                idsSelf,
                FormatUtil.cborEncode(safeEntryLeisure)
            )
            .putEntryBytestring(DocumentData.MICOV_ATT_NAMESPACE, "fac", idsSelf, portrait)
            .putEntryString(DocumentData.MICOV_ATT_NAMESPACE, "fni", idsSelf, dData.getValueString("fni"))
            .putEntryString(
                DocumentData.MICOV_ATT_NAMESPACE,
                "gni",
                idsSelf,
                dData.getValueString("gni")
            )
            .putEntryInteger(
                DocumentData.MICOV_ATT_NAMESPACE,
                "by",
                idsSelf,
                dData.getValueLong("by")
            )
            .putEntryInteger(
                DocumentData.MICOV_ATT_NAMESPACE,
                "bm",
                idsSelf,
                dData.getValueLong("bm")
            )
            .putEntryInteger(
                DocumentData.MICOV_ATT_NAMESPACE,
                "bd",
                idsSelf,
                dData.getValueLong("bd")
            )


        personalizationData.addAccessControlProfile(profileSelf)

        provisionSelfSigned(dData, iaSelfSignedCert, personalizationData.build())
    }

    private fun provisionSelfSigned(
        dData: SelfSignedDocumentData,
        iaSelfSignedCert: X509Certificate,
        personalizationData: PersonalizationData
    ) {
        val mStore =
            if (dData.provisionInfo.storageImplementationType == IMPLEMENTATION_TYPE_HARDWARE)
                IdentityCredentialStore.getHardwareInstance(context)
                    ?: IdentityCredentialStore.getKeystoreInstance(
                        context,
                        PreferencesHelper.getKeystoreBackedStorageLocation(context)
                    )
            else
                IdentityCredentialStore.getKeystoreInstance(
                    context,
                    PreferencesHelper.getKeystoreBackedStorageLocation(context)
                )

        val dsPrivateKey = if (MDL_DOCTYPE == dData.provisionInfo.docType) {
            KeysAndCertificates.getMdlDsKeyPair(context).private
        } else if (MVR_DOCTYPE == dData.provisionInfo.docType) {
            KeysAndCertificates.getMekbDsKeyPair(context).private
        } else if (MICOV_DOCTYPE == dData.provisionInfo.docType) {
            KeysAndCertificates.getMicovDsKeyPair(context).private
        } else {
            throw IllegalArgumentException("DS key pair not found to docType: ${dData.provisionInfo.docType}")
        }

        Utility.provisionSelfSignedCredential(
            mStore,
            "${dData.provisionInfo.docType}-${dData.provisionInfo.docName}",
            dsPrivateKey,
            iaSelfSignedCert,
            dData.provisionInfo.docType,
            personalizationData,
            dData.provisionInfo.numberMso,
            dData.provisionInfo.maxUseMso
        )
    }
}