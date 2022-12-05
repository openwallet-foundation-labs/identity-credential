package com.android.mdl.app.document

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.model.UnicodeString
import com.android.identity.*
import com.android.identity.IdentityCredentialStore.IMPLEMENTATION_TYPE_HARDWARE
import com.android.mdl.app.util.DocumentData
import com.android.mdl.app.util.DocumentData.EU_PID_DOCTYPE
import com.android.mdl.app.util.DocumentData.MDL_DOCTYPE
import com.android.mdl.app.util.DocumentData.MICOV_DOCTYPE
import com.android.mdl.app.util.DocumentData.MVR_DOCTYPE
import com.android.mdl.app.util.DocumentData.MVR_NAMESPACE
import com.android.mdl.app.util.FormatUtil
import com.android.mdl.app.util.PreferencesHelper
import com.android.mdl.app.util.SelfSignedDocumentData
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.security.cert.X509Certificate
import java.util.*

class DocumentManager private constructor(private val context: Context) {

    companion object {
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
            PreferencesHelper.isHardwareBacked(),
            selfSigned = false,
            userAuthentication = true,
            KEY_COUNT,
            MAX_USES_PER_KEY,
            provisioningCode,
            serverUrl
        )
        insertDocument(document)
        return document
    }

    private fun insertDocument(document: Document) {
        // Set preferences if we don't have it or it will be the first document
        if (!PreferencesHelper.hasHardwareBackedPreference() || getDocuments().isEmpty()) {
            PreferencesHelper.setHardwareBacked(document.hardwareBacked)
        }
        runBlocking {
            documentRepository.insert(document)
        }
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

        deleteDocument(document)

        // Delete credential provisioned on IC API
        credential?.delete(byteArrayOf())
    }

    private fun deleteDocument(document: Document) {
        // Delete data from local storage
        runBlocking {
            documentRepository.delete(document)
        }

        // Reset storage preference implementation with first document available
        val documents = getDocuments()
        if (documents.isNotEmpty()) {
            PreferencesHelper.setHardwareBacked(documents.first().hardwareBacked)
        }
    }

    fun setAvailableAuthKeys(credential: IdentityCredential) {
        credential.setAvailableAuthenticationKeys(KEY_COUNT, MAX_USES_PER_KEY)
    }

    fun getCredential(document: Document): IdentityCredential? {
        val mStore = if (document.hardwareBacked)
            IdentityCredentialStore.getHardwareInstance(context)
                ?: IdentityCredentialStore.getKeystoreInstance(
                    context,
                    PreferencesHelper.getKeystoreBackedStorageLocation(context)
                )
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
                } else if (EU_PID_DOCTYPE == dData.provisionInfo.docType) {
                    provisionSelfSignedEuPid(dData)
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
                insertDocument(document)
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

    private fun provisionSelfSignedEuPid(dData: SelfSignedDocumentData) {
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
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        val portrait: ByteArray = outputStream.toByteArray()
        val birthDate = unicodeStringFrom { dData.getValueString("birth_date") }
        val portraitTakenDate = unicodeStringFrom { dData.getValueString("portrait_capture_date") }
        // Always add sample fingerprint data
        val fingerprint = Base64.getDecoder()
            .decode("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")

        val personalizationData = PersonalizationData.Builder()
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "family_name", idsSelf, dData.getValueString("family_name"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "family_name_national_characters", idsSelf, dData.getValueString("family_name_national_characters"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "given_name", idsSelf, dData.getValueString("given_name"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "given_name_national_characters", idsSelf, dData.getValueString("given_name_national_characters"))
            .putEntry(DocumentData.EU_PID_NAMESPACE, "birth_date", idsSelf, FormatUtil.cborEncode(birthDate))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "persistent_id", idsSelf, dData.getValueString("persistent_id"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "family_name_birth", idsSelf, dData.getValueString("family_name_birth"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "family_name_birth_national_characters", idsSelf, dData.getValueString("family_name_birth_national_characters"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "given_name_birth", idsSelf, dData.getValueString("given_name_birth"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "given_name_birth_national_characters", idsSelf, dData.getValueString("given_name_birth_national_characters"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "birth_place", idsSelf, dData.getValueString("birth_place"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "resident_address", idsSelf, dData.getValueString("resident_address"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "resident_city", idsSelf, dData.getValueString("resident_city"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "resident_postal_code", idsSelf, dData.getValueString("resident_postal_code"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "resident_state", idsSelf, dData.getValueString("resident_state"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "resident_country", idsSelf, dData.getValueString("resident_country"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "gender", idsSelf, dData.getValueString("gender"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "nationality", idsSelf, dData.getValueString("nationality"))
            .putEntryBytestring(DocumentData.EU_PID_NAMESPACE, "portrait", idsSelf, portrait)
            .putEntry(DocumentData.EU_PID_NAMESPACE, "portrait_capture_date", idsSelf, FormatUtil.cborEncode(portraitTakenDate))
            .putEntryBytestring(DocumentData.EU_PID_NAMESPACE, "biometric_template_finger", idsSelf, fingerprint)
            .putEntryBoolean(DocumentData.EU_PID_NAMESPACE, "age_over_13", idsSelf, dData.getValueBoolean("age_over_18"))
            .putEntryBoolean(DocumentData.EU_PID_NAMESPACE, "age_over_16", idsSelf, dData.getValueBoolean("age_over_18"))
            .putEntryBoolean(DocumentData.EU_PID_NAMESPACE, "age_over_18", idsSelf, dData.getValueBoolean("age_over_18"))
            .putEntryBoolean(DocumentData.EU_PID_NAMESPACE, "age_over_21", idsSelf, dData.getValueBoolean("age_over_21"))
            .putEntryBoolean(DocumentData.EU_PID_NAMESPACE, "age_over_60", idsSelf, dData.getValueBoolean("age_over_21"))
            .putEntryBoolean(DocumentData.EU_PID_NAMESPACE, "age_over_65", idsSelf, dData.getValueBoolean("age_over_21"))
            .putEntryBoolean(DocumentData.EU_PID_NAMESPACE, "age_over_68", idsSelf, dData.getValueBoolean("age_over_21"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "age_in_years", idsSelf, dData.getValueString("age_in_years"))
            .putEntryString(DocumentData.EU_PID_NAMESPACE, "age_birth_year", idsSelf, dData.getValueString("age_birth_year"))
        personalizationData.addAccessControlProfile(profileSelf)
        provisionSelfSigned(dData, iaSelfSignedCert, personalizationData.build())
    }

    private inline fun unicodeStringFrom(tag: Long = 1004, value: () -> String): UnicodeString {
        val result = UnicodeString(value())
        result.setTag(tag)
        return result
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
        } else if (EU_PID_DOCTYPE == dData.provisionInfo.docType) {
            KeysAndCertificates.getMdlDsKeyPair(context).private
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