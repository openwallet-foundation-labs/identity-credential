package com.android.identity.documenttype.knowntypes

import kotlinx.datetime.LocalDate

/**
 * Sample data used across multiple document types.
 *
 * This data is based on the fictional person
 * [Erika Mustermann](https://en.wiktionary.org/wiki/Erika_Mustermann)
 * and a fictional country called Utopia.
 *
 * Note: The ISO-3166-1 Alpha-2 country code used for Utopia is UT. This value does not
 * appear in that standard.
 */
internal object SampleData {

    const val GIVEN_NAME = "Erika"
    const val FAMILY_NAME = "Mustermann"
    const val GIVEN_NAME_BIRTH = "Erika"
    const val FAMILY_NAME_BIRTH = "Mustermann"
    const val GIVEN_NAMES_NATIONAL_CHARACTER = "Ерика"
    const val FAMILY_NAME_NATIONAL_CHARACTER = "Бабіак"

    val birthDate = LocalDate.parse("1971-09-01")
    const val BIRTH_COUNTRY = "ZZ"  // Note: ZZ is a user-assigned country-code as per ISO 3166-1
    val issueDate = LocalDate.parse("2024-03-15")
    val expiryDate = LocalDate.parse("2028-09-01")
    const val ISSUING_COUNTRY = "ZZ"  // Note: ZZ is a user-assigned country-code as per ISO 3166-1
    const val ISSUING_AUTHORITY_MDL = "Utopia Department of Motor Vehicles"
    const val ISSUING_AUTHORITY_EU_PID = "Utopia Central Registry"
    const val ISSUING_AUTHORITY_PHOTO_ID = "Utopia Central Registry"
    const val DOCUMENT_NUMBER = "987654321"
    const val PERSON_ID = "24601"

    const val UN_DISTINGUISHING_SIGN = "UTO"
    const val ADMINISTRATIVE_NUMBER = "123456789"
    const val SEX_ISO218 = 2
    const val HEIGHT_CM = 175
    const val WEIGHT_KG = 68
    const val BIRTH_PLACE = "Sample City"
    const val BIRTH_STATE = "Sample State"
    const val BIRTH_CITY = "Sample City"
    const val RESIDENT_ADDRESS = "Sample Street 123, 12345 Sample City, Sample State, Utopia"
    val portraitCaptureDate = LocalDate.parse("2020-03-14")
    const val AGE_IN_YEARS = 53
    const val AGE_BIRTH_YEAR = 1971
    const val AGE_OVER = true  // Generic "age over" value
    const val AGE_OVER_13 = true
    const val AGE_OVER_16 = true
    const val AGE_OVER_18 = true
    const val AGE_OVER_21 = true
    const val AGE_OVER_25 = true
    const val AGE_OVER_60 = false
    const val AGE_OVER_62 = false
    const val AGE_OVER_65 = false
    const val AGE_OVER_68 = false
    const val ISSUING_JURISDICTION = "State of Utopia"
    const val NATIONALITY = "ZZ"  // Note: ZZ is a user-assigned country-code as per ISO 3166-1
    const val RESIDENT_STREET = "Sample Street"
    const val RESIDENT_HOUSE_NUMBER = "123"
    const val RESIDENT_POSTAL_CODE = "12345"
    const val RESIDENT_CITY = "Sample City"
    const val RESIDENT_STATE = "Sample State"
    const val RESIDENT_COUNTRY = "ZZ"  // Note: ZZ is a user-assigned country-code as per ISO 3166-1

    // TODO
    //val portrait
    //val signatureUsualMark

}