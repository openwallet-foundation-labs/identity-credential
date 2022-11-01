package com.android.mdl.app.documentdata

import com.android.mdl.app.R

object RequestMdl : RequestDocument() {

    override val docType = "org.iso.18013.5.1.mDL"
    override val nameSpace = "org.iso.18013.5.1"
    override val dataItems = DataItems.values().asList()

    enum class DataItems(override val identifier: String, override val stringResourceId: Int) :
        RequestDataItem {
        FAMILY_NAME("family_name", R.string.family_name),
        GIVEN_NAMES("given_name", R.string.given_name),
        BIRTH_DATE("birth_date", R.string.birth_date),
        ISSUE_DATE("issue_date", R.string.issue_date),
        EXPIRY_DATE("expiry_date", R.string.expiry_date),
        ISSUING_COUNTRY("issuing_country", R.string.issuing_country),
        ISSUING_AUTHORITY("issuing_authority", R.string.issuing_authority),
        DOCUMENT_NUMBER("document_number", R.string.document_number),
        PORTRAIT("portrait", R.string.portrait),
        DRIVING_PRIVILEGES("driving_privileges", R.string.driving_privileges),
        UN_DISTINGUISHING_SIGN("un_distinguishing_sign", R.string.un_distinguishing_sign),
        ADMINISTRATIVE_NUMBER("administrative_number", R.string.administrative_number),
        HEIGHT("height", R.string.height),
        WEIGHT("weight", R.string.weight),
        EYE_COLOUR("eye_colour", R.string.eye_colour),
        HAIR_COLOUR("hair_colour", R.string.hair_colour),
        BIRTH_PLACE("birth_place", R.string.birth_place),
        RESIDENT_ADDRESS("resident_address", R.string.resident_address),
        PORTRAIT_CAPTURE_DATE("portrait_capture_date", R.string.portrait_capture_date),
        AGE_IN_YEARS("age_in_years", R.string.age_in_years),
        AGE_BIRTH_YEAR("age_birth_year", R.string.age_birth_year),
        AGE_OVER_18("age_over_18", R.string.age_over_18),
        AGE_OVER_21("age_over_21", R.string.age_over_21),
        ISSUING_JURISDICTION("issuing_jurisdiction", R.string.issuing_jurisdiction),
        NATIONALITY("nationality", R.string.nationality),
        RESIDENT_CITY("resident_city", R.string.resident_city),
        RESIDENT_STATE("resident_state", R.string.resident_state),
        RESIDENT_POSTAL_CODE("resident_postal_code", R.string.resident_postal_code),
        RESIDENT_COUNTRY("resident_country", R.string.resident_country),
        FAMILY_NAME_NATIONAL_CHARACTER(
            "family_name_national_character",
            R.string.family_name_national_character
        ),
        GIVEN_NAME_NATIONAL_CHARACTER(
            "given_name_national_character",
            R.string.given_name_national_character
        ),
        SIGNATURE_USUAL_MARK("signature_usual_mark", R.string.signature_usual_mark)
    }
}