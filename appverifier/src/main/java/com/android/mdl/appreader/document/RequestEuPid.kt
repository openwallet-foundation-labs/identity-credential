package com.android.mdl.appreader.document

import com.android.mdl.appreader.R

object RequestEuPid: RequestDocument() {

    override val docType = "eu.europa.ec.eudiw.pid.1"
    override val nameSpace = "eu.europa.ec.eudiw.pid.1"
    override val dataItems = DataItems.values().asList()

    enum class DataItems(
        override val identifier: String,
        override val stringResourceId: Int
    ) : RequestDataItem {
        FAMILY_NAME("family_name", R.string.family_name),
        FAMILY_NAME_NATIONAL_CHARACTER("family_name_national_characters", R.string.family_name_national_character),
        GIVEN_NAMES("given_name", R.string.given_name),
        GIVEN_NAME_NATIONAL_CHARACTER("given_name_national_characters", R.string.given_name_national_character),
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
    }
}