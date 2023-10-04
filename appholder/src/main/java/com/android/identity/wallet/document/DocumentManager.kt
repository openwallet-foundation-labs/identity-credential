package com.android.identity.wallet.document

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.res.ResourcesCompat
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.model.UnicodeString
import com.android.identity.*
import com.android.identity.android.legacy.*
import com.android.identity.credential.Credential
import com.android.identity.credential.NameSpacedData
import com.android.identity.wallet.util.ProvisioningUtil
import com.android.identity.wallet.util.ProvisioningUtil.Companion.toDocumentInformation
import com.android.identity.wallet.selfsigned.SelfSignedDocumentData
import com.android.identity.wallet.util.DocumentData
import com.android.identity.wallet.util.DocumentData.EU_PID_DOCTYPE
import com.android.identity.wallet.util.DocumentData.MDL_DOCTYPE
import com.android.identity.wallet.util.DocumentData.MICOV_DOCTYPE
import com.android.identity.wallet.util.DocumentData.MVR_DOCTYPE
import com.android.identity.wallet.util.DocumentData.MVR_NAMESPACE
import com.android.identity.wallet.util.FormatUtil
import java.io.ByteArrayOutputStream
import java.util.*
import com.android.identity.wallet.R

class DocumentManager private constructor(private val context: Context) {

    companion object {

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DocumentManager? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DocumentManager(context).also { instance = it }
            }
    }

    fun getDocumentInformation(documentName: String): DocumentInformation? {
        val credentialStore = ProvisioningUtil.getInstance(context).credentialStore
        val credential = credentialStore.lookupCredential(documentName)
        return credential.toDocumentInformation()
    }

    fun getCredentialByName(documentName: String): Credential? {
        val documentInfo = getDocumentInformation(documentName)
        documentInfo?.let {
            val credentialStore = ProvisioningUtil.getInstance(context).credentialStore
            return credentialStore.lookupCredential(documentName)
        }
        return null
    }

    fun getDocuments(): List<DocumentInformation> {
        val credentialStore = ProvisioningUtil.getInstance(context).credentialStore
        return credentialStore.listCredentials().mapNotNull { documentName ->
            val credential = credentialStore.lookupCredential(documentName)
            credential.toDocumentInformation()
        }
    }

    fun deleteCredentialByName(documentName: String) {
        val document = getDocumentInformation(documentName)
        document?.let {
            val credentialStore = ProvisioningUtil.getInstance(context).credentialStore
            credentialStore.deleteCredential(documentName)
        }
    }

    fun createSelfSignedDocument(documentData: SelfSignedDocumentData) {
        val docName = getUniqueDocumentName(documentData)
        documentData.provisionInfo.docName = docName
        try {
            if (MDL_DOCTYPE == documentData.provisionInfo.docType) {
                provisionSelfSignedMdl(documentData)
            } else if (MVR_DOCTYPE == documentData.provisionInfo.docType) {
                provisionSelfSignedMvr(documentData)
            } else if (MICOV_DOCTYPE == documentData.provisionInfo.docType) {
                provisionSelfSignedMicov(documentData)
            } else if (EU_PID_DOCTYPE == documentData.provisionInfo.docType) {
                provisionSelfSignedEuPid(documentData)
            } else {
                throw IllegalArgumentException("Invalid docType to create self signed document ${documentData.provisionInfo.docType}")
            }
        } catch (e: IdentityCredentialException) {
            throw IllegalStateException("Error creating self signed credential", e)
        }
    }

    private fun getUniqueDocumentName(
        documentData: SelfSignedDocumentData,
        docName: String = documentData.provisionInfo.docName,
        count: Int = 1
    ): String {
        val store = ProvisioningUtil.getInstance(context).credentialStore
        store.listCredentials().forEach { name ->
            if (name == docName) {
                return getUniqueDocumentName(documentData, "$docName ($count)", count + 1)
            }
        }
        return docName
    }

    private fun provisionSelfSignedMdl(documentData: SelfSignedDocumentData) {
        val bitmap = documentData.getValueBitmap("portrait")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val portrait: ByteArray = baos.toByteArray()
        val bitmapSignature = documentData.getValueBitmap("signature_usual_mark")
        baos.reset()
        bitmapSignature.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val signature: ByteArray = baos.toByteArray()

        val birthDate = UnicodeString(documentData.getValueString("birth_date"))
        birthDate.setTag(1004)
        val issueDate = UnicodeString(documentData.getValueString("issue_date"))
        issueDate.setTag(1004)
        val expiryDate = UnicodeString(documentData.getValueString("expiry_date"))
        expiryDate.setTag(1004)
        val issueDateCatA = UnicodeString(documentData.getValueString("issue_date_1"))
        issueDateCatA.setTag(1004)
        val expiryDateCatA = UnicodeString(documentData.getValueString("expiry_date_1"))
        expiryDateCatA.setTag(1004)
        val issueDateCatB = UnicodeString(documentData.getValueString("issue_date_2"))
        issueDateCatB.setTag(1004)
        val expiryDateCatB = UnicodeString(documentData.getValueString("expiry_date_2"))
        expiryDateCatB.setTag(1004)
        val drivingPrivileges = CborBuilder().addArray()
            .addMap()
            .put("vehicle_category_code", documentData.getValueString("vehicle_category_code_1"))
            .put(UnicodeString("issue_date"), issueDateCatA)
            .put(UnicodeString("expiry_date"), expiryDateCatA)
            .end()
            .addMap()
            .put("vehicle_category_code", documentData.getValueString("vehicle_category_code_2"))
            .put(UnicodeString("issue_date"), issueDateCatB)
            .put(UnicodeString("expiry_date"), expiryDateCatB)
            .end()
            .end()
            .build()[0]

        val nameSpacedData = NameSpacedData.Builder()
            .putEntryString(
                DocumentData.MDL_NAMESPACE,
                "given_name",
                documentData.getValueString("given_name")
            )
            .putEntryString(
                DocumentData.MDL_NAMESPACE,
                "family_name",
                documentData.getValueString("family_name")
            )
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "birth_date",
                FormatUtil.cborEncode(birthDate)
            )
            .putEntryByteString(DocumentData.MDL_NAMESPACE, "portrait", portrait)
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "issue_date",
                FormatUtil.cborEncode(issueDate)
            )
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "expiry_date",
                FormatUtil.cborEncode(expiryDate)
            )
            .putEntryString(
                DocumentData.MDL_NAMESPACE,
                "issuing_country",
                documentData.getValueString("issuing_country")
            )
            .putEntryString(
                DocumentData.MDL_NAMESPACE,
                "issuing_authority",
                documentData.getValueString("issuing_authority")
            )
            .putEntryString(
                DocumentData.MDL_NAMESPACE,
                "document_number",
                documentData.getValueString("document_number")
            )
            .putEntry(
                DocumentData.MDL_NAMESPACE,
                "driving_privileges",
                FormatUtil.cborEncode(drivingPrivileges)
            )
            .putEntryString(
                DocumentData.MDL_NAMESPACE,
                "un_distinguishing_sign",
                documentData.getValueString("un_distinguishing_sign")
            )
            .putEntryBoolean(
                DocumentData.MDL_NAMESPACE,
                "age_over_18",
                documentData.getValueBoolean("age_over_18")
            )
            .putEntryBoolean(
                DocumentData.MDL_NAMESPACE,
                "age_over_21",
                documentData.getValueBoolean("age_over_21")
            )
            .putEntryByteString(
                DocumentData.MDL_NAMESPACE,
                "signature_usual_mark",
                signature
            )
            .putEntryNumber(
                DocumentData.MDL_NAMESPACE,
                "sex",
                documentData.getValueString("sex").toLong()
            )
            .putEntryString(
                DocumentData.AAMVA_NAMESPACE,
                "aamva_version",
                documentData.getValueString("aamva_version")
            )
            .putEntryString(
                DocumentData.AAMVA_NAMESPACE,
                "EDL_credential",
                documentData.getValueString("aamva_EDL_credential")
            )
            .putEntryString(
                DocumentData.AAMVA_NAMESPACE,
                "DHS_compliance",
                documentData.getValueString("aamva_DHS_compliance")
            )
            .putEntryString(
                DocumentData.AAMVA_NAMESPACE,
                "given_name_truncation",
                documentData.getValueString("aamva_given_name_truncation")
            )
            .putEntryString(
                DocumentData.AAMVA_NAMESPACE,
                "family_name_truncation",
                documentData.getValueString("aamva_family_name_truncation")
            )
            .putEntryNumber(
                DocumentData.AAMVA_NAMESPACE,
                "sex",
                documentData.getValueString("aamva_sex").toLong()
            )
            .build()

        ProvisioningUtil.getInstance(context)
            .provisionSelfSigned(nameSpacedData, documentData.provisionInfo)
    }

    private fun provisionSelfSignedMvr(documentData: SelfSignedDocumentData) {
        val validFrom = UnicodeString(documentData.getValueString("validFrom"))
        validFrom.setTag(0)
        val validUntil = UnicodeString(documentData.getValueString("validUntil"))
        validUntil.setTag(0)
        val registrationInfo = CborBuilder().addMap()
            .put("issuingCountry", documentData.getValueString("issuingCountry"))
            .put("competentAuthority", documentData.getValueString("competentAuthority"))
            .put("registrationNumber", documentData.getValueString("registrationNumber"))
            .put(UnicodeString("validFrom"), validFrom)
            .put(UnicodeString("validUntil"), validFrom)
            .end()
            .build()[0]
        val issueDate = UnicodeString(documentData.getValueString("issueDate"))
        issueDate.setTag(1004)
        val registrationHolderAddress = CborBuilder().addMap()
            .put("streetName", documentData.getValueString("streetName"))
            .put("houseNumber", documentData.getValueLong("houseNumber"))
            .put("postalCode", documentData.getValueString("postalCode"))
            .put("placeOfResidence", documentData.getValueString("placeOfResidence"))
            .end()
            .build()[0]
        val registrationHolderHolderInfo = CborBuilder().addMap()
            .put("name", documentData.getValueString("name"))
            .put(UnicodeString("address"), registrationHolderAddress)
            .end()
            .build()[0]
        val registrationHolder = CborBuilder().addMap()
            .put(UnicodeString("holderInfo"), registrationHolderHolderInfo)
            .put("ownershipStatus", documentData.getValueLong("ownershipStatus"))
            .end()
            .build()[0]
        val basicVehicleInfo = CborBuilder().addMap()
            .put(
                UnicodeString("vehicle"), CborBuilder().addMap()
                    .put("make", documentData.getValueString("make"))
                    .end()
                    .build()[0]
            )
            .end()
            .build()[0]

        val nameSpacedData = NameSpacedData.Builder()
            .putEntry(MVR_NAMESPACE, "registration_info", FormatUtil.cborEncode(registrationInfo))
            .putEntry(MVR_NAMESPACE, "issue_date", FormatUtil.cborEncode(issueDate))
            .putEntry(
                MVR_NAMESPACE,
                "registration_holder",
                FormatUtil.cborEncode(registrationHolder)
            )
            .putEntry(MVR_NAMESPACE, "basic_vehicle_info", FormatUtil.cborEncode(basicVehicleInfo))
            .putEntryString(MVR_NAMESPACE, "vin", "1M8GDM9AXKP042788")
            .build()

        ProvisioningUtil.getInstance(context)
            .provisionSelfSigned(nameSpacedData, documentData.provisionInfo)
    }

    private fun provisionSelfSignedMicov(documentData: SelfSignedDocumentData) {
        val dob = UnicodeString(documentData.getValueString("dob"))
        dob.setTag(1004)
        val ra011dt = UnicodeString(documentData.getValueString("RA01_1_dt"))
        ra011dt.setTag(1004)
        val ra011nx = UnicodeString(documentData.getValueString("RA01_1_nx"))
        ra011nx.setTag(1004)
        val ra012dt = UnicodeString(documentData.getValueString("RA01_2_dt"))
        ra011dt.setTag(1004)
        val vRA011 = CborBuilder().addMap()
            .put("tg", documentData.getValueString("RA01_1_tg"))
            .put("vp", documentData.getValueString("RA01_1_vp"))
            .put("mp", documentData.getValueString("RA01_1_mp"))
            .put("ma", documentData.getValueString("RA01_1_ma"))
            .put("bn", documentData.getValueString("RA01_1_bn"))
            .put("dn", documentData.getValueString("RA01_1_dn"))
            .put("sd", documentData.getValueString("RA01_1_sd"))
            .put(UnicodeString("dt"), ra011dt)
            .put("co", documentData.getValueString("RA01_1_co"))
            .put("ao", documentData.getValueString("RA01_1_ao"))
            .put(UnicodeString("nx"), ra011nx)
            .put("is", documentData.getValueString("RA01_1_is"))
            .put("ci", documentData.getValueString("RA01_1_ci"))
            .end()
            .build()[0]
        val vRA012 = CborBuilder().addMap()
            .put("tg", documentData.getValueString("RA01_2_tg"))
            .put("vp", documentData.getValueString("RA01_2_vp"))
            .put("mp", documentData.getValueString("RA01_2_mp"))
            .put("ma", documentData.getValueString("RA01_2_ma"))
            .put("bn", documentData.getValueString("RA01_2_bn"))
            .put("dn", documentData.getValueString("RA01_2_dn"))
            .put("sd", documentData.getValueString("RA01_2_sd"))
            .put(UnicodeString("dt"), ra012dt)
            .put("co", documentData.getValueString("RA01_2_co"))
            .put("ao", documentData.getValueString("RA01_2_ao"))
            .put("is", documentData.getValueString("RA01_2_is"))
            .put("ci", documentData.getValueString("RA01_2_ci"))
            .end()
            .build()[0]
        val pidPPN = CborBuilder().addMap()
            .put("pty", documentData.getValueString("PPN_pty"))
            .put("pnr", documentData.getValueString("PPN_pnr"))
            .put("pic", documentData.getValueString("PPN_pic"))
            .end()
            .build()[0]
        val pidDL = CborBuilder().addMap()
            .put("pty", documentData.getValueString("DL_pty"))
            .put("pnr", documentData.getValueString("DL_pnr"))
            .put("pic", documentData.getValueString("DL_pic"))
            .end()
            .build()[0]

        // org.micov.attestation.1
        val timeOfTest = UnicodeString("2021-10-12T19:00:00Z")
        timeOfTest.setTag(0)
        val seCondExpiry = UnicodeString(documentData.getValueString("SeCondExpiry"))
        seCondExpiry.setTag(0)
        val ra01Test = CborBuilder().addMap()
            .put("Result", documentData.getValueLong("Result"))
            .put("TypeOfTest", documentData.getValueString("TypeOfTest"))
            .put(UnicodeString("TimeOfTest"), timeOfTest)
            .end()
            .build()[0]
        val safeEntryLeisure = CborBuilder().addMap()
            .put("SeCondFulfilled", documentData.getValueLong("SeCondFulfilled"))
            .put("SeCondType", documentData.getValueString("SeCondType"))
            .put(UnicodeString("SeCondExpiry"), seCondExpiry)
            .end()
            .build()[0]

        val bitmap = documentData.getValueBitmap("fac")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val portrait: ByteArray = baos.toByteArray()
        val nameSpacedData = NameSpacedData.Builder()
            .putEntryString(
                DocumentData.MICOV_VTR_NAMESPACE,
                "fn",
                documentData.getValueString("fn")
            )
            .putEntryString(
                DocumentData.MICOV_VTR_NAMESPACE,
                "gn",
                documentData.getValueString("gn")
            )
            .putEntry(DocumentData.MICOV_VTR_NAMESPACE, "dob", FormatUtil.cborEncode(dob))
            .putEntryNumber(
                DocumentData.MICOV_VTR_NAMESPACE,
                "sex",
                documentData.getValueLong("sex")
            )
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "v_RA01_1",
                FormatUtil.cborEncode(vRA011)
            )
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "v_RA01_2",
                FormatUtil.cborEncode(vRA012)
            )
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "pid_PPN",
                FormatUtil.cborEncode(pidPPN)
            )
            .putEntry(
                DocumentData.MICOV_VTR_NAMESPACE,
                "pid_DL",
                FormatUtil.cborEncode(pidDL)
            )
            .putEntryNumber(
                DocumentData.MICOV_ATT_NAMESPACE,
                "1D47_vaccinated",
                documentData.getValueLong("1D47_vaccinated")
            )
            .putEntryNumber(
                DocumentData.MICOV_ATT_NAMESPACE,
                "RA01_vaccinated",
                documentData.getValueLong("RA01_vaccinated")
            )
            .putEntry(
                DocumentData.MICOV_ATT_NAMESPACE,
                "RA01_test",
                FormatUtil.cborEncode(ra01Test)
            )
            .putEntry(
                DocumentData.MICOV_ATT_NAMESPACE,
                "safeEntry_Leisure",
                FormatUtil.cborEncode(safeEntryLeisure)
            )
            .putEntryByteString(DocumentData.MICOV_ATT_NAMESPACE, "fac", portrait)
            .putEntryString(
                DocumentData.MICOV_ATT_NAMESPACE,
                "fni",
                documentData.getValueString("fni")
            )
            .putEntryString(
                DocumentData.MICOV_ATT_NAMESPACE,
                "gni",
                documentData.getValueString("gni")
            )
            .putEntryNumber(
                DocumentData.MICOV_ATT_NAMESPACE,
                "by",
                documentData.getValueLong("by")
            )
            .putEntryNumber(
                DocumentData.MICOV_ATT_NAMESPACE,
                "bm",
                documentData.getValueLong("bm")
            )
            .putEntryNumber(
                DocumentData.MICOV_ATT_NAMESPACE,
                "bd",
                documentData.getValueLong("bd")
            )
            .build()

        ProvisioningUtil.getInstance(context)
            .provisionSelfSigned(nameSpacedData, documentData.provisionInfo)
    }

    private fun provisionSelfSignedEuPid(documentData: SelfSignedDocumentData) {
        val portraitBitmap = documentData.getValueBitmap("portrait")
        val outputStream = ByteArrayOutputStream()
        portraitBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        val portrait = outputStream.toByteArray()
        val birthDate = unicodeStringFrom { documentData.getValueString("birth_date") }
        val portraitTakenDate = unicodeStringFrom { documentData.getValueString("portrait_capture_date") }
        val fingerprintBitmap = ResourcesCompat.getDrawable(context.resources, R.drawable.img_erika_signature, null)
        val drawable = (fingerprintBitmap as BitmapDrawable).bitmap
        val byteArrayOutputStream = ByteArrayOutputStream()
        drawable.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
        val fingerprint = byteArrayOutputStream.toByteArray()

        val nameSpacedData = NameSpacedData.Builder()
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "family_name",
                documentData.getValueString("family_name")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "family_name_national_characters",
                documentData.getValueString("family_name_national_characters")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "given_name",
                documentData.getValueString("given_name")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "given_name_national_characters",
                documentData.getValueString("given_name_national_characters")
            )
            .putEntry(DocumentData.EU_PID_NAMESPACE, "birth_date", FormatUtil.cborEncode(birthDate))
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "persistent_id",
                documentData.getValueString("persistent_id")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "family_name_birth",
                documentData.getValueString("family_name_birth")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "family_name_birth_national_characters",
                documentData.getValueString("family_name_birth_national_characters")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "given_name_birth",
                documentData.getValueString("given_name_birth")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "given_name_birth_national_characters",
                documentData.getValueString("given_name_birth_national_characters")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "birth_place",
                documentData.getValueString("birth_place")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "resident_address",
                documentData.getValueString("resident_address")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "resident_city",
                documentData.getValueString("resident_city")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "resident_postal_code",
                documentData.getValueString("resident_postal_code")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "resident_state",
                documentData.getValueString("resident_state")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "resident_country",
                documentData.getValueString("resident_country")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "gender",
                documentData.getValueString("gender")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "nationality",
                documentData.getValueString("nationality")
            )
            .putEntryByteString(DocumentData.EU_PID_NAMESPACE, "portrait", portrait)
            .putEntry(
                DocumentData.EU_PID_NAMESPACE,
                "portrait_capture_date",
                FormatUtil.cborEncode(portraitTakenDate)
            )
            .putEntryByteString(
                DocumentData.EU_PID_NAMESPACE,
                "biometric_template_finger",
                fingerprint
            )
            .putEntryBoolean(
                DocumentData.EU_PID_NAMESPACE,
                "age_over_13",
                documentData.getValueBoolean("age_over_18")
            )
            .putEntryBoolean(
                DocumentData.EU_PID_NAMESPACE,
                "age_over_16",
                documentData.getValueBoolean("age_over_18")
            )
            .putEntryBoolean(
                DocumentData.EU_PID_NAMESPACE,
                "age_over_18",
                documentData.getValueBoolean("age_over_18")
            )
            .putEntryBoolean(
                DocumentData.EU_PID_NAMESPACE,
                "age_over_21",
                documentData.getValueBoolean("age_over_21")
            )
            .putEntryBoolean(
                DocumentData.EU_PID_NAMESPACE,
                "age_over_60",
                documentData.getValueBoolean("age_over_21")
            )
            .putEntryBoolean(
                DocumentData.EU_PID_NAMESPACE,
                "age_over_65",
                documentData.getValueBoolean("age_over_21")
            )
            .putEntryBoolean(
                DocumentData.EU_PID_NAMESPACE,
                "age_over_68",
                documentData.getValueBoolean("age_over_21")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "age_in_years",
                documentData.getValueString("age_in_years")
            )
            .putEntryString(
                DocumentData.EU_PID_NAMESPACE,
                "age_birth_year",
                documentData.getValueString("age_birth_year")
            )
            .build()

        ProvisioningUtil.getInstance(context)
            .provisionSelfSigned(nameSpacedData, documentData.provisionInfo)
    }

    private inline fun unicodeStringFrom(tag: Long = 1004, value: () -> String): UnicodeString {
        val result = UnicodeString(value())
        result.setTag(tag)
        return result
    }

    fun refreshAuthKeys(documentName: String) {
        val documentInformation = requireNotNull(getDocumentInformation(documentName))
        val credential = requireNotNull(getCredentialByName(documentName))
        ProvisioningUtil.getInstance(context).refreshAuthKeys(credential, documentInformation)
    }
}
