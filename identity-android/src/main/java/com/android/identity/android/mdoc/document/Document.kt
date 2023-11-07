package com.android.identity.android.mdoc.document

import com.android.identity.R


/**
 * Class containing all the necessary constants concerning the mDoc documents. The Data elements
 * are accessible from the root, e.g. [Document.Mdl.Element.AGE_BIRTH_YEAR]
 */
sealed class Document(val documentType: DocumentType) {
    abstract val elements: List<DataElement>

    /**
     * Personal identification (EU)
     */
    object EuPid : Document(DocumentType.EUPID) {
        override val elements: List<DataElement> = Element.values().asList()

        /**
         * Data elements in namespace eu.europa.ec.eudiw.pid.1
         */
        enum class Element(
            override val elementName: String,
            override val stringResourceId: Int,
            override val nameSpace: Namespace = Namespace.EUPID
        ) : DataElement {
            FAMILY_NAME("family_name", R.string.family_name),
            FAMILY_NAME_NATIONAL_CHARACTERS("family_name_national_characters", R.string.family_name_national_character),
            GIVEN_NAME("given_name", R.string.given_name),
            GIVEN_NAME_NATIONAL_CHARACTERS("given_name_national_characters", R.string.given_name_national_character),
            BIRTH_DATE("birth_date", R.string.birth_date),
            UNIQUE_IDENTIFIER("issue_date", R.string.unique_identifier),
            BIRTH_FAMILY_NAME("family_name_birth", R.string.birth_family_name),
            BIRTH_FAMILY_NAME_NATIONAL_CHARACTERS("family_name_birth_national_characters", R.string.birth_family_name_national_characters),
            BIRTH_FIRST_NAME("given_name_birth", R.string.first_name_at_birth),
            BIRTH_FIRST_NAME_NATIONAL_CHARACTERS("given_name_birth_national_characters", R.string.first_name_at_birth_national_characters),
            BIRTH_PLACE("birth_place", R.string.birth_place),
            RESIDENT_ADDRESS("resident_address", R.string.resident_address),
            RESIDENT_POSTAL_CODE("resident_postal_code", R.string.resident_postal_code),
            RESIDENT_CITY("resident_city", R.string.resident_city),
            RESIDENT_STATE("resident_state", R.string.resident_state),
            RESIDENT_COUNTRY("resident_country", R.string.resident_country),
            GENDER("gender", R.string.gender),
            NATIONALITY("nationality", R.string.nationality),
            PORTRAIT("portrait", R.string.facial_portrait),
            PORTRAIT_CAPTURE_DATE("portrait_capture_date", R.string.portrait_capture_date),
            BIOMETRIC_TEMPLATE_FINGER("biometric_template_finger", R.string.biometric_template_finger),
            AGE_OVER_13("age_over_13", R.string.age_over_13),
            AGE_OVER_16("age_over_16", R.string.age_over_16),
            AGE_OVER_18("age_over_18", R.string.age_over_18),
            AGE_OVER_21("age_over_21", R.string.age_over_21),
            AGE_OVER_60("age_over_60", R.string.age_over_60),
            AGE_OVER_65("age_over_65", R.string.age_over_65),
            AGE_OVER_68("age_over_68", R.string.age_over_68),
            AGE_IN_YEARS("age_in_years", R.string.age_in_years),
            AGE_BIRTH_YEAR("age_birth_year", R.string.age_birth_year),
            PERSISTENT_ID("persistent_id", R.string.persistent_id),
        }
    }

    /**
     * Driving license
     */
    object Mdl : Document(DocumentType.MDL) {
        override val elements: List<DataElement> = Element.values().asList()

        val elementsMdlNamespace = elements.filter { it.nameSpace == Namespace.MDL }
        val elementsOlderThan18: List<DataElement> = listOf(Element.PORTRAIT, Element.AGE_OVER_18)
        val elementsOlderThan21: List<DataElement> = listOf(Element.PORTRAIT, Element.AGE_OVER_21)
        val mandatoryElements: List<DataElement> =
            listOf(
                Element.FAMILY_NAME,
                Element.GIVEN_NAME,
                Element.BIRTH_DATE,
                Element.ISSUE_DATE,
                Element.EXPIRY_DATE,
                Element.ISSUING_COUNTRY,
                Element.ISSUING_AUTHORITY,
                Element.DOCUMENT_NUMBER,
                Element.PORTRAIT,
                Element.DRIVING_PRIVILEGES,
                Element.UN_DISTINGUISHING_SIGN
            )
        val elementsMdlWithLinkage: List<DataElement> =
            listOf(Element.PORTRAIT, Element.DOCUMENT_NUMBER)
        val elementsUsTransportation = listOf(
            Element.SEX,
            Element.PORTRAIT,
            Element.GIVEN_NAME,
            Element.BIRTH_DATE,
            Element.ISSUE_DATE,
            Element.FAMILY_NAME,
            Element.DOCUMENT_NUMBER,
            Element.ISSUING_AUTHORITY,
            Element.DHS_COMPLIANCE,
            Element.EDL_CREDENTIAL
        )

        /**
         * Data elements in namespace org.iso.18013.5.1 and org.iso.18013.5.1.aamva
         */
        enum class Element(
            override val elementName: String,
            override val stringResourceId: Int,
            override val nameSpace: Namespace
        ) : DataElement {
            SEX("sex", R.string.sex, Namespace.MDL),
            FAMILY_NAME("family_name", R.string.family_name, Namespace.MDL),
            GIVEN_NAME("given_name", R.string.given_name, Namespace.MDL),
            BIRTH_DATE("birth_date", R.string.birth_date, Namespace.MDL),
            ISSUE_DATE("issue_date", R.string.issue_date, Namespace.MDL),
            EXPIRY_DATE("expiry_date", R.string.expiry_date, Namespace.MDL),
            ISSUING_COUNTRY("issuing_country", R.string.issuing_country, Namespace.MDL),
            ISSUING_AUTHORITY("issuing_authority", R.string.issuing_authority, Namespace.MDL),
            DOCUMENT_NUMBER("document_number", R.string.document_number, Namespace.MDL),
            PORTRAIT("portrait", R.string.portrait, Namespace.MDL),
            DRIVING_PRIVILEGES("driving_privileges", R.string.driving_privileges, Namespace.MDL),
            UN_DISTINGUISHING_SIGN("un_distinguishing_sign", R.string.un_distinguishing_sign, Namespace.MDL),
            ADMINISTRATIVE_NUMBER("administrative_number", R.string.administrative_number, Namespace.MDL),
            HEIGHT("height", R.string.height, Namespace.MDL),
            WEIGHT("weight", R.string.weight, Namespace.MDL),
            EYE_COLOUR("eye_colour", R.string.eye_colour, Namespace.MDL),
            HAIR_COLOUR("hair_colour", R.string.hair_colour, Namespace.MDL),
            BIRTH_PLACE("birth_place", R.string.birth_place, Namespace.MDL),
            RESIDENT_ADDRESS("resident_address", R.string.resident_address, Namespace.MDL),
            PORTRAIT_CAPTURE_DATE("portrait_capture_date", R.string.portrait_capture_date, Namespace.MDL),
            AGE_IN_YEARS("age_in_years", R.string.age_in_years, Namespace.MDL),
            AGE_BIRTH_YEAR("age_birth_year", R.string.age_birth_year, Namespace.MDL),
            AGE_OVER_18("age_over_18", R.string.age_over_18, Namespace.MDL),
            AGE_OVER_21("age_over_21", R.string.age_over_21, Namespace.MDL),
            ISSUING_JURISDICTION("issuing_jurisdiction",R.string.issuing_jurisdiction, Namespace.MDL),
            NATIONALITY("nationality", R.string.nationality, Namespace.MDL),
            RESIDENT_CITY("resident_city", R.string.resident_city, Namespace.MDL),
            RESIDENT_STATE("resident_state", R.string.resident_state, Namespace.MDL),
            RESIDENT_POSTAL_CODE("resident_postal_code", R.string.resident_postal_code, Namespace.MDL),
            RESIDENT_COUNTRY("resident_country", R.string.resident_country, Namespace.MDL),
            FAMILY_NAME_NATIONAL_CHARACTER("family_name_national_character", R.string.family_name_national_character, Namespace.MDL),
            GIVEN_NAME_NATIONAL_CHARACTER("given_name_national_character", R.string.given_name_national_character, Namespace.MDL),
            SIGNATURE_USUAL_MARK("signature_usual_mark", R.string.signature_usual_mark, Namespace.MDL),
            DHS_COMPLIANCE("DHS_compliance", R.string.DHS_compliance, Namespace.MDL_AAMVA),
            EDL_CREDENTIAL("EDL_credential", R.string.EDL_credential, Namespace.MDL_AAMVA),
            AAMVA_VERSION("aamva_version", R.string.aamva_version, Namespace.MDL_AAMVA),
            GIVEN_NAME_TRUNCATION("given_name_truncation", R.string.given_name_truncation, Namespace.MDL_AAMVA),
            FAMILY_NAME_TRUNCATION("family_name_truncation", R.string.family_name_truncation, Namespace.MDL_AAMVA),
            AAMVA_SEX("sex", R.string.sex, Namespace.MDL_AAMVA)

        }
    }

    object Micov : Document(DocumentType.MICOV) {
        override val elements: List<DataElement> = Element.values().asList()
        val elementsAtt = elements.filter { it.nameSpace == Namespace.MICOV_ATT }
        val elementsVtr = elements.filter { it.nameSpace == Namespace.MICOV_VTR }
        val elementsMulti003 =
            listOf(Element.ID_WITH_DRIVERS_LICENSE_NUMBER, Element.SAFE_ENTRY_INDICATION)

        /**
         * Data elements in namespace org.micov.attestation.1
         */
        enum class Element(
            override val elementName: String,
            override val stringResourceId: Int,
            override val nameSpace: Namespace
        ) : DataElement {
            FAMILY_NAME("fn", R.string.micov_vtr_fn, Namespace.MICOV_VTR),
            GIVEN_NAME("gn", R.string.micov_vtr_gn, Namespace.MICOV_VTR),
            DATE_OF_BIRTH("dob", R.string.micov_vtr_dob, Namespace.MICOV_VTR),
            SEX("sex", R.string.micov_vtr_sex, Namespace.MICOV_VTR),
            FIRST_VACCINATION_AGAINST_RA01("v_RA01_1", R.string.micov_vtr_v_RA01_1, Namespace.MICOV_VTR),
            SECOND_VACCINATION_AGAINST_RA01("v_RA01_2", R.string.micov_vtr_v_RA01_2, Namespace.MICOV_VTR),
            ID_WITH_PASPORT_NUMBER("pid_PPN", R.string.micov_vtr_pid_PPN, Namespace.MICOV_VTR),
            ID_WITH_DRIVERS_LICENSE_NUMBER("pid_DL", R.string.micov_vtr_pid_DL, Namespace.MICOV_VTR),
            INDICATION_OF_VACCINATION_YELLOW_FEVER("1D47_vaccinated", R.string.micov_att_1D47_vaccinated, Namespace.MICOV_ATT),
            INDICATION_OF_VACCINATION_COVID_19("RA01_vaccinated", R.string.micov_att_RA01_vaccinated, Namespace.MICOV_ATT),
            INDICATION_OF_TEST_EVENT_COVID_19("RA01_test", R.string.micov_att_RA01_test, Namespace.MICOV_ATT),
            SAFE_ENTRY_INDICATION("safeEntry_Leisure", R.string.micov_att_safeEntry_Leisure, Namespace.MICOV_ATT),
            FACIAL_IMAGE("fac", R.string.micov_att_fac, Namespace.MICOV_ATT),
            FAMILY_NAME_INITIAL("fni", R.string.micov_att_fni, Namespace.MICOV_ATT),
            GIVEN_NAME_INITIAL("gni", R.string.micov_att_gni, Namespace.MICOV_ATT),
            BIRTH_YEAR("by", R.string.micov_att_by, Namespace.MICOV_ATT),
            BIRTH_MONTH("bm", R.string.micov_att_bm, Namespace.MICOV_ATT),
            BIRTH_DAY("bd", R.string.micov_att_bd, Namespace.MICOV_ATT)
        }
    }

    /**
     * Vehicle registation
     */
    object Mvr : Document(DocumentType.MVR) {
        override val elements: List<DataElement> = Element.values().asList()

        /**
         * Data elements in namespace nl.rdw.mekb.1
         */
        enum class Element(
            override val elementName: String,
            override val stringResourceId: Int,
            override val nameSpace: Namespace = Namespace.MVR
        ) : DataElement {
            REGISTRATION_INFO("registration_info", R.string.registration_info),
            ISSUE_DATE("issue_date", R.string.issue_date),
            REGISTRATION_HOLDER("registration_holder", R.string.registration_holder),
            BASIC_VEHICLE_INFO("basic_vehicle_info", R.string.basic_vehicle_info),
            VIN("vin", R.string.vin)
        }
    }
}
