package com.android.identity.wallet.viewmodel

import android.app.Application
import android.graphics.BitmapFactory
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.android.identity.android.mdoc.document.DataElement
import com.android.identity.android.mdoc.document.Document
import com.android.identity.android.mdoc.document.DocumentType
import com.android.identity.wallet.R
import com.android.identity.wallet.document.DocumentManager
import com.android.identity.wallet.selfsigned.SelfSignedDocumentData
import com.android.identity.wallet.util.Field
import com.android.identity.wallet.util.FieldType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelfSignedViewModel(val app: Application) :
    AndroidViewModel(app) {

    companion object {
        private const val LOG_TAG = "SelfSignedViewModel"
    }

    private val documentManager = DocumentManager.getInstance(app.applicationContext)
    val loading = MutableLiveData<Int>()
    val created = MutableLiveData<Boolean>()
    private val fieldsMdl = mutableListOf<Field>()
    private val fieldsMvr = mutableListOf<Field>()
    private val fieldsMicov = mutableListOf<Field>()
    private val fieldsEuPid = mutableListOf<Field>()

    init {
        loading.value = View.GONE
        var id = 1

        // Pre fill default values for MDL document
        val bitmap = BitmapFactory.decodeResource(
            app.resources,
            R.drawable.img_erika_portrait
        )
        val signature = BitmapFactory.decodeResource(
            app.resources,
            R.drawable.img_erika_signature
        )
        fieldsMdl.add(createField(id++, Document.Mdl.Element.PORTRAIT, FieldType.BITMAP, bitmap))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.GIVEN_NAME, FieldType.STRING, "Erika"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.FAMILY_NAME, FieldType.STRING, "Mustermann"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.BIRTH_DATE, FieldType.DATE, "1971-09-01"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.ISSUE_DATE, FieldType.DATE, "2021-04-18"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.EXPIRY_DATE, FieldType.DATE,"2026-04-18"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.ISSUING_COUNTRY, FieldType.STRING,"US"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.ISSUING_AUTHORITY, FieldType.STRING,"Google"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.DOCUMENT_NUMBER, FieldType.STRING,"987654321"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.SIGNATURE_USUAL_MARK, FieldType.BITMAP, signature))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.UN_DISTINGUISHING_SIGN, FieldType.STRING,"US"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.AGE_OVER_18, FieldType.BOOLEAN,"true"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.AGE_OVER_21, FieldType.BOOLEAN,"true"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.SEX, FieldType.STRING, "2"))
        // TODO: Add driving_privileges dynamically
        fieldsMdl.add(Field(id++, "vehicle Category Code 1","vehicle_category_code_1", FieldType.STRING,"A"))
        fieldsMdl.add(Field(id++,"Issue Date 1", "issue_date_1", FieldType.DATE, "2018-08-09"))
        fieldsMdl.add(Field(id++,"Expiry Date 1", "expiry_date_1", FieldType.DATE, "2024-10-20"))
        fieldsMdl.add(Field(id++,"vehicle Category Code 2", "vehicle_category_code_2", FieldType.STRING,"B"))
        fieldsMdl.add(Field(id++, "Issue Date 2", "issue_date_2", FieldType.DATE, "2017-02-23"))
        fieldsMdl.add(Field(id++, "Expiry Date 2", "expiry_date_2", FieldType.DATE, "2024-10-20"))
        fieldsMdl.add(createField(id++, Document.Mdl.Element.AAMVA_VERSION, FieldType.STRING, "2"))
        fieldsMdl.add(Field(id++,"AAMVA DHS Compliance (Real ID)", "aamva_DHS_compliance",FieldType.STRING,"F"))
        fieldsMdl.add(Field(id++,"AAMVA EDL Credential","aamva_EDL_credential", FieldType.STRING,"1"))
        fieldsMdl.add(Field(id++, "AAMVA Given Name Truncation", "aamva_given_name_truncation", FieldType.STRING,"N"))
        fieldsMdl.add(Field(id++, "AAMVA Family Name Truncation", "aamva_family_name_truncation", FieldType.STRING, "N"))
        fieldsMdl.add(Field(id, "AAMVA Sex", "aamva_sex", FieldType.STRING, "2"))

        // Pre fill default values for MRV document
        id = 1
        fieldsMvr.add(Field(id++, "Issuing Country", "issuingCountry", FieldType.STRING, "UT"))
        fieldsMvr.add(Field(id++, "Competent Authority", "competentAuthority", FieldType.STRING, "RDW"))
        fieldsMvr.add(Field(id++, "Registration Number", "registrationNumber", FieldType.STRING, "E-01-23"))
        fieldsMvr.add(Field(id++, "Issue Date", "issueDate", FieldType.DATE, "2021-04-18"))
        fieldsMvr.add(Field(id++, "Valid From", "validFrom", FieldType.DATE, "2021-04-19"))
        fieldsMvr.add(Field(id++, "Valid Until", "validUntil", FieldType.DATE, "2023-04-20"))
        fieldsMvr.add(createField(id++, Document.Mvr.Element.ISSUE_DATE, FieldType.DATE, "2021-04-18"))
        fieldsMvr.add(Field(id++, "Registration Holder Name", "name", FieldType.STRING, "Erika"))
        fieldsMvr.add(Field(id++, "Street Name", "streetName", FieldType.STRING, "teststraat"))
        fieldsMvr.add(Field(id++, "House Number", "houseNumber", FieldType.STRING, "86"))
        fieldsMvr.add(Field(id++, "Postal Code", "postalCode", FieldType.STRING, "1234 AA"))
        fieldsMvr.add(Field(id++, "Place Of Residence", "placeOfResidence", FieldType.STRING, "Samplecity"))
        fieldsMvr.add(Field(id++, "Ownership Status", "ownershipStatus", FieldType.STRING, "2"))
        fieldsMvr.add(Field(id++, "Vehicle Maker", "make", FieldType.STRING, "Dummymobile"))
        fieldsMvr.add(createField(id, Document.Mvr.Element.VIN, FieldType.STRING, "1M8GDM9AXKP042788"))

        // Pre fill default values for MICOV document
        id = 1
        fieldsMicov.add(createField(id++, Document.Micov.Element.FAMILY_NAME_INITIAL, FieldType.STRING, "M"))
        fieldsMicov.add(createField(id++, Document.Micov.Element.FAMILY_NAME, FieldType.STRING, "Mustermann"))
        fieldsMicov.add(createField(id++, Document.Micov.Element.GIVEN_NAME_INITIAL, FieldType.STRING, "E"))
        fieldsMicov.add(createField(id++, Document.Micov.Element.GIVEN_NAME, FieldType.STRING, "Erika"))
        fieldsMicov.add(createField(id++, Document.Micov.Element.DATE_OF_BIRTH, FieldType.DATE, "1964-08-12"))
        fieldsMicov.add(createField(id++, Document.Micov.Element.SEX, FieldType.STRING, "2"))
        fieldsMicov.add(createField(id++, Document.Micov.Element.INDICATION_OF_VACCINATION_YELLOW_FEVER, FieldType.STRING, "2"))
        fieldsMicov.add(createField(id++, Document.Micov.Element.INDICATION_OF_VACCINATION_COVID_19, FieldType.STRING, "2"))
        fieldsMicov.add(createField(id++, Document.Micov.Element.FACIAL_IMAGE, FieldType.BITMAP, bitmap))
        fieldsMicov.add(createField(id++, Document.Micov.Element.FAMILY_NAME_INITIAL, FieldType.STRING, "M"))
        fieldsMicov.add(createField(id++, Document.Micov.Element.BIRTH_YEAR, FieldType.STRING,"1964"))
        fieldsMicov.add(createField(id++, Document.Micov.Element.BIRTH_MONTH, FieldType.STRING, "8"))
        fieldsMicov.add(createField(id++, Document.Micov.Element.BIRTH_DAY, FieldType.STRING, "12"))

        fieldsMicov.add(Field(id++, "RA01 1 Disease or agent targeted", "RA01_1_tg", FieldType.STRING, "840539006"))
        fieldsMicov.add(Field(id++, "RA01 1 Vaccine or prophylaxis", "RA01_1_vp", FieldType.STRING, "1119349007"))
        fieldsMicov.add(Field(id++, "RA01 1 Vaccine medicinal product", "RA01_1_mp", FieldType.STRING, "EU/1/20/1528"))
        fieldsMicov.add(Field(id++, "RA01 1 Manufacturer", "RA01_1_ma", FieldType.STRING, "ORG-100030215"))
        fieldsMicov.add(Field(id++, "RA01 1 Batch number", "RA01_1_bn", FieldType.STRING, "B12345/67"))
        fieldsMicov.add(Field(id++, "RA01 1 Dose number", "RA01_1_dn", FieldType.STRING, "1"))
        fieldsMicov.add(Field(id++, "RA01 1 Total series of doses", "RA01_1_sd", FieldType.STRING, "2"))
        fieldsMicov.add(Field(id++, "RA01 1 Date of vaccination", "RA01_1_dt", FieldType.DATE, "2021-04-08"))
        fieldsMicov.add(Field(id++, "RA01 1 Country of vaccination", "RA01_1_co", FieldType.STRING, "UT"))
        fieldsMicov.add(Field(id++, "RA01 1 Administering organization", "RA01_1_ao", FieldType.STRING, "RHI"))
        fieldsMicov.add(Field(id++, "RA01 1 Due date of next dose", "RA01_1_nx", FieldType.STRING, "2021-05-20"))
        fieldsMicov.add(Field(id++, "RA01 1 Certificate issuer", "RA01_1_is", FieldType.STRING, "SC17"))
        fieldsMicov.add(Field(id++, "RA01 1 Unique certificate identifier (UVCI)", "RA01_1_ci", FieldType.STRING, "URN:UVCI:01:UT:187/37512422923"))

        fieldsMicov.add(Field(id++, "RA01 2 Disease or agent targeted", "RA01_2_tg", FieldType.STRING, "840539006"))
        fieldsMicov.add(Field(id++, "RA01 2 Vaccine or prophylaxis", "RA01_2_vp", FieldType.STRING, "1119349007"))
        fieldsMicov.add(Field(id++, "RA01 2 Vaccine medicinal product", "RA01_2_mp", FieldType.STRING, "EU/1/20/1528"))
        fieldsMicov.add(Field(id++, "RA01 2 Manufacturer", "RA01_2_ma", FieldType.STRING, "ORG-100030215"))
        fieldsMicov.add(Field(id++, "RA01 2 Batch number", "RA01_2_bn", FieldType.STRING, "B12345/67"))
        fieldsMicov.add(Field(id++, "RA01 2 Dose number", "RA01_2_dn", FieldType.STRING, "2"))
        fieldsMicov.add(Field(id++, "RA01 2 Total series of doses", "RA01_2_sd", FieldType.STRING, "2"))
        fieldsMicov.add(Field(id++, "RA01 2 Date of vaccination", "RA01_2_dt", FieldType.DATE, "2021-05-18"))
        fieldsMicov.add(Field(id++, "RA01 2 Country of vaccination", "RA01_2_co", FieldType.STRING, "UT"))
        fieldsMicov.add(Field(id++, "RA01 2 Administering organization", "RA01_2_ao", FieldType.STRING, "RHI"))
        fieldsMicov.add(Field(id++, "RA01 2 Certificate issuer", "RA01_2_is", FieldType.STRING, "SC17"))
        fieldsMicov.add(Field(id++, "RA01 2 Unique certificate identifier (UVCI)", "RA01_2_ci", FieldType.STRING, "URN:UVCI:01:UT:187/37512533044"))

        fieldsMicov.add(Field(id++, "Type of person identifier", "PPN_pty", FieldType.STRING, "PPN"))
        fieldsMicov.add(Field(id++, "Unique number", "PPN_pnr", FieldType.STRING, "476284728"))
        fieldsMicov.add(Field(id++, "pic", "PPN_pic", FieldType.STRING, "UT"))

        fieldsMicov.add(Field(id++, "Type of person identifier", "DL_pty", FieldType.STRING, "DL"))
        fieldsMicov.add(Field(id++, "Unique number", "DL_pnr", FieldType.STRING, "987654321"))
        fieldsMicov.add(Field(id++, "pic", "DL_pic", FieldType.STRING, "UT"))

        fieldsMicov.add(Field(id++, "Test Result", "Result", FieldType.STRING, "260415000"))
        fieldsMicov.add(Field(id++, "Type Of Test", "TypeOfTest", FieldType.STRING, "LP6464-4"))
        fieldsMicov.add(Field(id++, "Time Of Test", "TimeOfTest", FieldType.DATE, "2021-10-12"))

        fieldsMicov.add(Field(id++, "SafeEntry", "SeCondFulfilled", FieldType.STRING, "1"))
        fieldsMicov.add(Field(id++, "SafeEntry type", "SeCondType", FieldType.STRING, "leisure"))
        fieldsMicov.add(Field(id, "SafeEntry expiry", "SeCondExpiry", FieldType.DATE, "2021-10-13"))

        // Pre fill default values for EU PID document
        id = 1
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.FAMILY_NAME, FieldType.STRING, "Mustermann"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.FAMILY_NAME_NATIONAL_CHARACTERS, FieldType.STRING, "Бабіак"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.GIVEN_NAME, FieldType.STRING, "Erika"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.GIVEN_NAME_NATIONAL_CHARACTERS, FieldType.STRING, "Ерика"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.BIRTH_DATE, FieldType.DATE, "1986-03-14"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.PERSISTENT_ID, FieldType.STRING, "0128196532"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.BIRTH_FAMILY_NAME, FieldType.STRING, "Mustermann"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.BIRTH_FAMILY_NAME_NATIONAL_CHARACTERS, FieldType.STRING, "Бабіак"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.BIRTH_FIRST_NAME, FieldType.STRING, "Erika"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.BIRTH_FIRST_NAME_NATIONAL_CHARACTERS, FieldType.STRING, "Ерика"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.BIRTH_PLACE, FieldType.STRING, "Place of birth"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.RESIDENT_ADDRESS, FieldType.STRING, "Address"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.RESIDENT_CITY, FieldType.STRING, "City"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.RESIDENT_POSTAL_CODE, FieldType.STRING, "Postcode"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.RESIDENT_STATE, FieldType.STRING, "State"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.RESIDENT_COUNTRY, FieldType.STRING, "Country"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.GENDER, FieldType.STRING, "female"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.NATIONALITY, FieldType.STRING, "NL"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.PORTRAIT, FieldType.BITMAP, bitmap))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.PORTRAIT_CAPTURE_DATE, FieldType.DATE, "2022-11-14"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.BIOMETRIC_TEMPLATE_FINGER, FieldType.STRING, "yes"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.AGE_OVER_13, FieldType.BOOLEAN, "true"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.AGE_OVER_16, FieldType.BOOLEAN, "true"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.AGE_OVER_18, FieldType.BOOLEAN, "true"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.AGE_OVER_21, FieldType.BOOLEAN, "true"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.AGE_OVER_60, FieldType.BOOLEAN, "false"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.AGE_OVER_65, FieldType.BOOLEAN, "false"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.AGE_OVER_68, FieldType.BOOLEAN, "false"))
        fieldsEuPid.add(createField(id++, Document.EuPid.Element.AGE_IN_YEARS, FieldType.STRING, "36"))
        fieldsEuPid.add(createField(id, Document.EuPid.Element.AGE_BIRTH_YEAR, FieldType.STRING, "1986"))
    }

    fun getFields(docType: String): MutableList<Field> {
        return if (DocumentType.MDL.value == docType) {
            fieldsMdl
        } else if (DocumentType.MVR.value == docType) {
            fieldsMvr
        } else if (DocumentType.MICOV.value == docType) {
            fieldsMicov
        } else if (DocumentType.EUPID.value == docType) {
            fieldsEuPid
        } else {
            throw IllegalArgumentException("No field list valid for $docType")
        }
    }

    fun createSelfSigned(documentData: SelfSignedDocumentData) {
        loading.value = View.VISIBLE
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                documentManager.createSelfSignedDocument(documentData)
            }
            created.value = true
            loading.value = View.GONE
        }
    }

    fun createField(id: Int, element: DataElement, fieldType: FieldType, value: Any): Field {
        return Field(
            id,
            app.resources.getString(element.stringResourceId),
            element.elementName,
            fieldType,
            value
        )
    }

}