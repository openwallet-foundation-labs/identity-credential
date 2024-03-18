package com.android.identity.credentialtype.knowntypes

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

    const val givenName = "Erika"
    const val familyName = "Mustermann"
    const val givenNameBirth = "Erika"
    const val familyNameBirth = "Mustermann"
    const val givenNamesNationalCharacter = "Ерика"
    const val familyNameNationalCharacter = "Бабіак"

    val birthDate = LocalDate.parse("1971-09-01")
    const val birthCountry = "UT"  // Note: UT doesn't exist in ISO-3166-1 Alpha-2
    val issueDate = LocalDate.parse("2024-03-15")
    val expiryDate = LocalDate.parse("2028-09-01")
    const val issuingCountry = "UT"  // Note: UT doesn't exist in ISO-3166-1 Alpha-2
    const val issuingAuthorityMdl = "Utopia Department of Motor Vehicles"
    const val issuingAuthorityEuPid = "Utopia Central Registry"
    const val documentNumber = "987654321"

    const val unDistinguishingSign = "UTO"
    const val administrativeNumber = "123456789"
    const val sexIso5218 = 2
    const val heightCm = 175
    const val weightKg = 68
    const val birthPlace = "Sample City"
    const val birthState = "Sample State"
    const val birthCity = "Sample City"
    const val residentAddress = "Sample Street 123, 12345 Sample City, Sample State, Utopia"
    val portraitCaptureDate = LocalDate.parse("2020-03-14")
    const val ageInYears = 53
    const val ageBirthYear = 1971
    const val ageOver13 = true
    const val ageOver16 = true
    const val ageOver18 = true
    const val ageOver21 = true
    const val ageOver25 = true
    const val ageOver60 = false
    const val ageOver62 = false
    const val ageOver65 = false
    const val ageOver68 = false
    const val issuingJurisdiction = "State of Utopia"
    const val nationality = "UT"  // Note: UT doesn't exist in ISO-3166-1 Alpha-2
    const val residentStreet = "Sample Street"
    const val residentHouseNumber = "123"
    const val residentPostalCode = "12345"
    const val residentCity = "Sample City"
    const val residentState = "Sample State"
    const val residentCountry = "UT"  // Note: UT doesn't exist in ISO-3166-1 Alpha-2

    // TODO
    //val portrait
    //val signatureUsualMark

}