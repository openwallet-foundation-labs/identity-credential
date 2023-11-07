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
import com.android.identity.android.mdoc.document.Document
import com.android.identity.android.mdoc.document.DocumentType
import com.android.identity.credential.Credential
import com.android.identity.credential.NameSpacedData
import com.android.identity.wallet.util.ProvisioningUtil
import com.android.identity.wallet.util.ProvisioningUtil.Companion.toDocumentInformation
import com.android.identity.wallet.selfsigned.SelfSignedDocumentData
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
            if (DocumentType.MDL.value == documentData.provisionInfo.docType) {
                provisionSelfSignedMdl(documentData)
            } else if (DocumentType.MVR.value == documentData.provisionInfo.docType) {
                provisionSelfSignedMvr(documentData)
            } else if (DocumentType.MICOV.value == documentData.provisionInfo.docType) {
                provisionSelfSignedMicov(documentData)
            } else if (DocumentType.EUPID.value == documentData.provisionInfo.docType) {
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
                Document.Mdl.Element.GIVEN_NAME.nameSpace.value,
                Document.Mdl.Element.GIVEN_NAME.elementName,
                documentData.getValueString(Document.Mdl.Element.GIVEN_NAME.elementName)
            )
            .putEntryString(
                Document.Mdl.Element.FAMILY_NAME.nameSpace.value,
                Document.Mdl.Element.FAMILY_NAME.elementName,
                documentData.getValueString(Document.Mdl.Element.FAMILY_NAME.elementName)
            )
            .putEntry(
                Document.Mdl.Element.BIRTH_DATE.nameSpace.value,
                Document.Mdl.Element.BIRTH_DATE.elementName,
                FormatUtil.cborEncode(birthDate)
            )
            .putEntryByteString(
                Document.Mdl.Element.PORTRAIT.nameSpace.value,
                Document.Mdl.Element.PORTRAIT.elementName, portrait
            )
            .putEntry(
                Document.Mdl.Element.ISSUE_DATE.nameSpace.value,
                Document.Mdl.Element.ISSUE_DATE.elementName,
                FormatUtil.cborEncode(issueDate)
            )
            .putEntry(
                Document.Mdl.Element.EXPIRY_DATE.nameSpace.value,
                Document.Mdl.Element.EXPIRY_DATE.elementName,
                FormatUtil.cborEncode(expiryDate)
            )
            .putEntryString(
                Document.Mdl.Element.ISSUING_COUNTRY.nameSpace.value,
                Document.Mdl.Element.ISSUING_COUNTRY.elementName,
                documentData.getValueString(Document.Mdl.Element.ISSUING_COUNTRY.elementName)
            )
            .putEntryString(
                Document.Mdl.Element.ISSUING_AUTHORITY.nameSpace.value,
                Document.Mdl.Element.ISSUING_AUTHORITY.elementName,
                documentData.getValueString(Document.Mdl.Element.ISSUING_AUTHORITY.elementName)
            )
            .putEntryString(
                Document.Mdl.Element.DOCUMENT_NUMBER.nameSpace.value,
                Document.Mdl.Element.DOCUMENT_NUMBER.elementName,
                documentData.getValueString(Document.Mdl.Element.DOCUMENT_NUMBER.elementName)
            )
            .putEntry(
                Document.Mdl.Element.DRIVING_PRIVILEGES.nameSpace.value,
                Document.Mdl.Element.DRIVING_PRIVILEGES.elementName,
                FormatUtil.cborEncode(drivingPrivileges)
            )
            .putEntryString(
                Document.Mdl.Element.UN_DISTINGUISHING_SIGN.nameSpace.value,
                Document.Mdl.Element.UN_DISTINGUISHING_SIGN.elementName,
                documentData.getValueString(Document.Mdl.Element.UN_DISTINGUISHING_SIGN.elementName)
            )
            .putEntryBoolean(
                Document.Mdl.Element.AGE_OVER_18.nameSpace.value,
                Document.Mdl.Element.AGE_OVER_18.elementName,
                documentData.getValueBoolean(Document.Mdl.Element.AGE_OVER_18.elementName)
            )
            .putEntryBoolean(
                Document.Mdl.Element.AGE_OVER_21.nameSpace.value,
                Document.Mdl.Element.AGE_OVER_21.elementName,
                documentData.getValueBoolean(Document.Mdl.Element.AGE_OVER_21.elementName)
            )
            .putEntryByteString(
                Document.Mdl.Element.SIGNATURE_USUAL_MARK.nameSpace.value,
                Document.Mdl.Element.SIGNATURE_USUAL_MARK.elementName,
                signature
            )
            .putEntryNumber(
                Document.Mdl.Element.SEX.nameSpace.value,
                Document.Mdl.Element.SEX.elementName,
                documentData.getValueString(Document.Mdl.Element.SEX.elementName).toLong()
            )
            .putEntryString(
                Document.Mdl.Element.AAMVA_VERSION.nameSpace.value,
                Document.Mdl.Element.AAMVA_VERSION.elementName,
                documentData.getValueString(Document.Mdl.Element.AAMVA_VERSION.elementName)
            )
            .putEntryString(
                Document.Mdl.Element.EDL_CREDENTIAL.nameSpace.value,
                Document.Mdl.Element.EDL_CREDENTIAL.elementName,
                documentData.getValueString("aamva_" + Document.Mdl.Element.EDL_CREDENTIAL.elementName)
            )
            .putEntryString(
                Document.Mdl.Element.DHS_COMPLIANCE.nameSpace.value,
                Document.Mdl.Element.DHS_COMPLIANCE.elementName,
                documentData.getValueString("aamva_" + Document.Mdl.Element.DHS_COMPLIANCE.elementName)
            )
            .putEntryString(
                Document.Mdl.Element.GIVEN_NAME_TRUNCATION.nameSpace.value,
                Document.Mdl.Element.GIVEN_NAME_TRUNCATION.elementName,
                documentData.getValueString("aamva_" + Document.Mdl.Element.GIVEN_NAME_TRUNCATION.elementName)
            )
            .putEntryString(
                Document.Mdl.Element.FAMILY_NAME_TRUNCATION.nameSpace.value,
                Document.Mdl.Element.FAMILY_NAME_TRUNCATION.elementName,
                documentData.getValueString("aamva_" + Document.Mdl.Element.FAMILY_NAME_TRUNCATION.elementName)
            )
            .putEntryNumber(
                Document.Mdl.Element.AAMVA_SEX.nameSpace.value,
                Document.Mdl.Element.AAMVA_SEX.elementName,
                documentData.getValueString("aamva_" + Document.Mdl.Element.AAMVA_SEX.elementName)
                    .toLong()
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
            .putEntry(
                Document.Mvr.Element.REGISTRATION_INFO.nameSpace.value,
                Document.Mvr.Element.REGISTRATION_INFO.elementName,
                FormatUtil.cborEncode(registrationInfo)
            )
            .putEntry(
                Document.Mvr.Element.ISSUE_DATE.nameSpace.value,
                Document.Mvr.Element.ISSUE_DATE.elementName,
                FormatUtil.cborEncode(issueDate)
            )
            .putEntry(
                Document.Mvr.Element.REGISTRATION_HOLDER.nameSpace.value,
                Document.Mvr.Element.REGISTRATION_HOLDER.elementName,
                FormatUtil.cborEncode(registrationHolder)
            )
            .putEntry(
                Document.Mvr.Element.BASIC_VEHICLE_INFO.nameSpace.value,
                Document.Mvr.Element.BASIC_VEHICLE_INFO.elementName,
                FormatUtil.cborEncode(basicVehicleInfo)
            )
            .putEntryString(
                Document.Mvr.Element.VIN.nameSpace.value,
                Document.Mvr.Element.VIN.elementName,
                "1M8GDM9AXKP042788"
            )
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
                Document.Micov.Element.FAMILY_NAME.nameSpace.value,
                Document.Micov.Element.FAMILY_NAME.elementName,
                documentData.getValueString(Document.Micov.Element.FAMILY_NAME.elementName)
            )
            .putEntryString(
                Document.Micov.Element.GIVEN_NAME.nameSpace.value,
                Document.Micov.Element.GIVEN_NAME.elementName,
                documentData.getValueString(Document.Micov.Element.GIVEN_NAME.elementName)
            )
            .putEntry(
                Document.Micov.Element.DATE_OF_BIRTH.nameSpace.value,
                Document.Micov.Element.DATE_OF_BIRTH.elementName, FormatUtil.cborEncode(dob)
            )
            .putEntryNumber(
                Document.Micov.Element.SEX.nameSpace.value,
                Document.Micov.Element.SEX.elementName,
                documentData.getValueLong(Document.Micov.Element.SEX.elementName)
            )
            .putEntry(
                Document.Micov.Element.FIRST_VACCINATION_AGAINST_RA01.nameSpace.value,
                Document.Micov.Element.FIRST_VACCINATION_AGAINST_RA01.elementName,
                FormatUtil.cborEncode(vRA011)
            )
            .putEntry(
                Document.Micov.Element.SECOND_VACCINATION_AGAINST_RA01.nameSpace.value,
                Document.Micov.Element.SECOND_VACCINATION_AGAINST_RA01.elementName,
                FormatUtil.cborEncode(vRA012)
            )
            .putEntry(
                Document.Micov.Element.ID_WITH_PASPORT_NUMBER.nameSpace.value,
                Document.Micov.Element.ID_WITH_PASPORT_NUMBER.elementName,
                FormatUtil.cborEncode(pidPPN)
            )
            .putEntry(
                Document.Micov.Element.ID_WITH_DRIVERS_LICENSE_NUMBER.nameSpace.value,
                Document.Micov.Element.ID_WITH_DRIVERS_LICENSE_NUMBER.elementName,
                FormatUtil.cborEncode(pidDL)
            )
            .putEntryNumber(
                Document.Micov.Element.INDICATION_OF_VACCINATION_YELLOW_FEVER.nameSpace.value,
                Document.Micov.Element.INDICATION_OF_VACCINATION_YELLOW_FEVER.elementName,
                documentData.getValueLong(Document.Micov.Element.INDICATION_OF_VACCINATION_YELLOW_FEVER.elementName)
            )
            .putEntryNumber(
                Document.Micov.Element.INDICATION_OF_VACCINATION_COVID_19.nameSpace.value,
                Document.Micov.Element.INDICATION_OF_VACCINATION_COVID_19.elementName,
                documentData.getValueLong(Document.Micov.Element.INDICATION_OF_VACCINATION_COVID_19.elementName)
            )
            .putEntry(
                Document.Micov.Element.INDICATION_OF_TEST_EVENT_COVID_19.nameSpace.value,
                Document.Micov.Element.INDICATION_OF_TEST_EVENT_COVID_19.elementName,
                FormatUtil.cborEncode(ra01Test)
            )
            .putEntry(
                Document.Micov.Element.SAFE_ENTRY_INDICATION.nameSpace.value,
                Document.Micov.Element.SAFE_ENTRY_INDICATION.elementName,
                FormatUtil.cborEncode(safeEntryLeisure)
            )
            .putEntryByteString(
                Document.Micov.Element.FACIAL_IMAGE.nameSpace.value,
                Document.Micov.Element.FACIAL_IMAGE.elementName, portrait
            )
            .putEntryString(
                Document.Micov.Element.FAMILY_NAME_INITIAL.nameSpace.value,
                Document.Micov.Element.FAMILY_NAME_INITIAL.elementName,
                documentData.getValueString(Document.Micov.Element.FAMILY_NAME_INITIAL.elementName)
            )
            .putEntryString(
                Document.Micov.Element.GIVEN_NAME_INITIAL.nameSpace.value,
                Document.Micov.Element.GIVEN_NAME_INITIAL.elementName,
                documentData.getValueString(Document.Micov.Element.GIVEN_NAME_INITIAL.elementName)
            )
            .putEntryNumber(
                Document.Micov.Element.BIRTH_YEAR.nameSpace.value,
                Document.Micov.Element.BIRTH_YEAR.elementName,
                documentData.getValueLong(Document.Micov.Element.BIRTH_YEAR.elementName)
            )
            .putEntryNumber(
                Document.Micov.Element.BIRTH_MONTH.nameSpace.value,
                Document.Micov.Element.BIRTH_MONTH.elementName,
                documentData.getValueLong(Document.Micov.Element.BIRTH_MONTH.elementName)
            )
            .putEntryNumber(
                Document.Micov.Element.BIRTH_DAY.nameSpace.value,
                Document.Micov.Element.BIRTH_DAY.elementName,
                documentData.getValueLong(Document.Micov.Element.BIRTH_DAY.elementName)
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
        val portraitTakenDate =
            unicodeStringFrom { documentData.getValueString("portrait_capture_date") }
        val fingerprintBitmap =
            ResourcesCompat.getDrawable(context.resources, R.drawable.img_erika_signature, null)
        val drawable = (fingerprintBitmap as BitmapDrawable).bitmap
        val byteArrayOutputStream = ByteArrayOutputStream()
        drawable.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
        val fingerprint = byteArrayOutputStream.toByteArray()

        val nameSpacedData = NameSpacedData.Builder()
            .putEntryString(
                Document.EuPid.Element.FAMILY_NAME.nameSpace.value,
                Document.EuPid.Element.FAMILY_NAME.elementName,
                documentData.getValueString(Document.EuPid.Element.FAMILY_NAME.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.FAMILY_NAME_NATIONAL_CHARACTERS.nameSpace.value,
                Document.EuPid.Element.FAMILY_NAME_NATIONAL_CHARACTERS.elementName,
                documentData.getValueString(Document.EuPid.Element.FAMILY_NAME_NATIONAL_CHARACTERS.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.GIVEN_NAME.nameSpace.value,
                Document.EuPid.Element.GIVEN_NAME.elementName,
                documentData.getValueString(Document.EuPid.Element.GIVEN_NAME.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.GIVEN_NAME_NATIONAL_CHARACTERS.nameSpace.value,
                Document.EuPid.Element.GIVEN_NAME_NATIONAL_CHARACTERS.elementName,
                documentData.getValueString(Document.EuPid.Element.GIVEN_NAME_NATIONAL_CHARACTERS.elementName)
            )
            .putEntry(
                Document.EuPid.Element.BIRTH_DATE.nameSpace.value,
                Document.EuPid.Element.BIRTH_DATE.elementName, FormatUtil.cborEncode(birthDate)
            )
            .putEntryString(
                Document.EuPid.Element.PERSISTENT_ID.nameSpace.value,
                Document.EuPid.Element.PERSISTENT_ID.elementName,
                documentData.getValueString(Document.EuPid.Element.PERSISTENT_ID.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.BIRTH_FAMILY_NAME.nameSpace.value,
                Document.EuPid.Element.BIRTH_FAMILY_NAME.elementName,
                documentData.getValueString(Document.EuPid.Element.BIRTH_FAMILY_NAME.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.BIRTH_FAMILY_NAME_NATIONAL_CHARACTERS.nameSpace.value,
                Document.EuPid.Element.BIRTH_FAMILY_NAME_NATIONAL_CHARACTERS.elementName,
                documentData.getValueString(Document.EuPid.Element.BIRTH_FAMILY_NAME_NATIONAL_CHARACTERS.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.BIRTH_FIRST_NAME.nameSpace.value,
                Document.EuPid.Element.BIRTH_FIRST_NAME.elementName,
                documentData.getValueString(Document.EuPid.Element.BIRTH_FIRST_NAME.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.BIRTH_FIRST_NAME_NATIONAL_CHARACTERS.nameSpace.value,
                Document.EuPid.Element.BIRTH_FIRST_NAME_NATIONAL_CHARACTERS.elementName,
                documentData.getValueString(Document.EuPid.Element.BIRTH_FIRST_NAME_NATIONAL_CHARACTERS.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.BIRTH_PLACE.nameSpace.value,
                Document.EuPid.Element.BIRTH_PLACE.elementName,
                documentData.getValueString(Document.EuPid.Element.BIRTH_PLACE.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.RESIDENT_ADDRESS.nameSpace.value,
                Document.EuPid.Element.RESIDENT_ADDRESS.elementName,
                documentData.getValueString(Document.EuPid.Element.RESIDENT_ADDRESS.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.RESIDENT_CITY.nameSpace.value,
                Document.EuPid.Element.RESIDENT_CITY.elementName,
                documentData.getValueString(Document.EuPid.Element.RESIDENT_CITY.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.RESIDENT_POSTAL_CODE.nameSpace.value,
                Document.EuPid.Element.RESIDENT_POSTAL_CODE.elementName,
                documentData.getValueString(Document.EuPid.Element.RESIDENT_POSTAL_CODE.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.RESIDENT_STATE.nameSpace.value,
                Document.EuPid.Element.RESIDENT_STATE.elementName,
                documentData.getValueString(Document.EuPid.Element.RESIDENT_STATE.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.RESIDENT_COUNTRY.nameSpace.value,
                Document.EuPid.Element.RESIDENT_COUNTRY.elementName,
                documentData.getValueString(Document.EuPid.Element.RESIDENT_COUNTRY.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.GENDER.nameSpace.value,
                Document.EuPid.Element.GENDER.elementName,
                documentData.getValueString(Document.EuPid.Element.GENDER.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.NATIONALITY.nameSpace.value,
                Document.EuPid.Element.NATIONALITY.elementName,
                documentData.getValueString(Document.EuPid.Element.NATIONALITY.elementName)
            )
            .putEntryByteString(
                Document.EuPid.Element.PORTRAIT.nameSpace.value,
                Document.EuPid.Element.PORTRAIT.elementName, portrait
            )
            .putEntry(
                Document.EuPid.Element.PORTRAIT_CAPTURE_DATE.nameSpace.value,
                Document.EuPid.Element.PORTRAIT_CAPTURE_DATE.elementName,
                FormatUtil.cborEncode(portraitTakenDate)
            )
            .putEntryByteString(
                Document.EuPid.Element.BIOMETRIC_TEMPLATE_FINGER.nameSpace.value,
                Document.EuPid.Element.BIOMETRIC_TEMPLATE_FINGER.elementName,
                fingerprint
            )
            .putEntryBoolean(
                Document.EuPid.Element.AGE_OVER_13.nameSpace.value,
                Document.EuPid.Element.AGE_OVER_13.elementName,
                documentData.getValueBoolean(Document.EuPid.Element.AGE_OVER_18.elementName)
            )
            .putEntryBoolean(
                Document.EuPid.Element.AGE_OVER_16.nameSpace.value,
                Document.EuPid.Element.AGE_OVER_16.elementName,
                documentData.getValueBoolean(Document.EuPid.Element.AGE_OVER_18.elementName)
            )
            .putEntryBoolean(
                Document.EuPid.Element.AGE_OVER_18.nameSpace.value,
                Document.EuPid.Element.AGE_OVER_18.elementName,
                documentData.getValueBoolean(Document.EuPid.Element.AGE_OVER_18.elementName)
            )
            .putEntryBoolean(
                Document.EuPid.Element.AGE_OVER_21.nameSpace.value,
                Document.EuPid.Element.AGE_OVER_21.elementName,
                documentData.getValueBoolean(Document.EuPid.Element.AGE_OVER_21.elementName)
            )
            .putEntryBoolean(
                Document.EuPid.Element.AGE_OVER_60.nameSpace.value,
                Document.EuPid.Element.AGE_OVER_60.elementName,
                documentData.getValueBoolean(Document.EuPid.Element.AGE_OVER_21.elementName)
            )
            .putEntryBoolean(
                Document.EuPid.Element.AGE_OVER_65.nameSpace.value,
                Document.EuPid.Element.AGE_OVER_65.elementName,
                documentData.getValueBoolean(Document.EuPid.Element.AGE_OVER_21.elementName)
            )
            .putEntryBoolean(
                Document.EuPid.Element.AGE_OVER_68.nameSpace.value,
                Document.EuPid.Element.AGE_OVER_68.elementName,
                documentData.getValueBoolean(Document.EuPid.Element.AGE_OVER_21.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.AGE_IN_YEARS.nameSpace.value,
                Document.EuPid.Element.AGE_IN_YEARS.elementName,
                documentData.getValueString(Document.EuPid.Element.AGE_IN_YEARS.elementName)
            )
            .putEntryString(
                Document.EuPid.Element.AGE_BIRTH_YEAR.nameSpace.value,
                Document.EuPid.Element.AGE_BIRTH_YEAR.elementName,
                documentData.getValueString(Document.EuPid.Element.AGE_BIRTH_YEAR.elementName)
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
