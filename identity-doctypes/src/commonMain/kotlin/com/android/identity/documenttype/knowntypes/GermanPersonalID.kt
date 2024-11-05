package com.android.identity.documenttype.knowntypes

import com.android.identity.cbor.CborArray
import com.android.identity.cbor.toDataItem
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.Icon
import kotlinx.datetime.LocalDate

/**
 * Object containing the metadata of the German ID Document Type.
 *
 * For now, this is a copy of EUPersonaID.
 *
 * TODO: read this (and other) VCTs for their URLs.
 */
object GermanPersonalID {
    const val EUPID_VCT = "https://example.bmi.bund.de/credential/pid/1.0"

    /**
     * Build the EU Personal ID Document Type.
     */
    fun getDocumentType(): DocumentType {
        return DocumentType.Builder("German Personal ID")
            .addVcDocumentType(EUPID_VCT)
            .addVcAttribute(
                DocumentAttributeType.String,
                "family_name",
                "Family Name",
                "Current last name(s), surname(s), or primary identifier of the PID holder",
                Icon.PERSON,
                SampleData.FAMILY_NAME.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "given_name",
                "Given Names",
                "Current first name(s), other name(s), or secondary identifier of the PID holder",
                Icon.PERSON,
                SampleData.GIVEN_NAME.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.Date,
                "birthdate",
                "Date of Birth",
                "Day, month, and year on which the PID holder was born. If unknown, approximate date of birth.",
                Icon.TODAY,
                LocalDate.parse(SampleData.BIRTH_DATE).toDataItemFullDate()
            )
            .addVcAttribute(
                DocumentAttributeType.Number,
                "age_in_years",
                "Age in Years",
                "The age of the PID holder in years",
                Icon.TODAY,
                SampleData.AGE_IN_YEARS.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.Number,
                "age_birth_year",
                "Year of Birth",
                "The year when the PID holder was born",
                Icon.TODAY,
                SampleData.AGE_BIRTH_YEAR.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.Boolean,
                "12",
                "Older Than 12",
                "Age over 12?",
                Icon.TODAY,
                SampleData.AGE_OVER.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.Boolean,
                "14",
                "Older Than 14",
                "Age over 14?",
                Icon.TODAY,
                SampleData.AGE_OVER.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.Boolean,
                "16",
                "Older Than 16",
                "Age over 16?",
                Icon.TODAY,
                SampleData.AGE_OVER_16.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.Boolean,
                "18",
                "Older Than 18",
                "Age over 18?",
                Icon.TODAY,
                SampleData.AGE_OVER_18.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.Boolean,
                "21",
                "Older Than 21",
                "Age over 21?",
                Icon.TODAY,
                SampleData.AGE_OVER_21.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.Boolean,
                "65",
                "Older Than 65",
                "Age over 65?",
                Icon.TODAY,
                SampleData.AGE_OVER.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "birth_family_name",
                "Family Name at Birth",
                "Last name(s), surname(s), or primary identifier of the PID holder at birth",
                Icon.PERSON,
                SampleData.FAMILY_NAME_BIRTH.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "birth_place",
                "Place of Birth",
                "Country and municipality or state/province where the PID holder was born",
                Icon.PLACE,
                SampleData.BIRTH_PLACE.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "birth_country",
                "Country of Birth",
                "The country where the PID User was born, as an Alpha-2 country code as specified in ISO 3166-1",
                Icon.PLACE,
                SampleData.BIRTH_COUNTRY.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "birth_state",
                "State of Birth",
                "The state, province, district, or local area where the PID User was born",
                Icon.PLACE,
                SampleData.BIRTH_STATE.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "birth_city",
                "City of Birth",
                "The municipality, city, town, or village where the PID User was born",
                Icon.PLACE,
                SampleData.BIRTH_CITY.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "street_address",
                "Resident Address",
                "The full address of the place where the PID holder currently resides and/or may be contacted (street/house number, municipality etc.)",
                Icon.PLACE,
                SampleData.RESIDENT_ADDRESS.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "locality",
                "Resident City",
                "The city where the PID holder currently resides",
                Icon.PLACE,
                SampleData.RESIDENT_CITY.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "country",
                "Resident Country",
                "The country where the PID User currently resides, as an Alpha-2 country code as specified in ISO 3166-1",
                Icon.PLACE,
                SampleData.RESIDENT_COUNTRY.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "postal_code",
                "Resident Postal Code",
                "The postal code of the place where the PID holder currently resides",
                Icon.PLACE,
                SampleData.RESIDENT_POSTAL_CODE.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.IntegerOptions(Options.SEX_ISO_IEC_5218),
                "gender",
                "Gender",
                "PID holder’s gender",
                Icon.EMERGENCY,
                SampleData.SEX_ISO218.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.ComplexType,
                "nationalities",
                "Nationality",
                "List of Alpha-2 country codes as specified in ISO 3166-1, representing the nationality of the PID User.",
                Icon.LANGUAGE,
                CborArray.builder().add(SampleData.NATIONALITY.toDataItem()).end().build()
            )
            .addVcAttribute(
                DocumentAttributeType.Date,
                "issuance_date",
                "Date of Issue",
                "Date (and possibly time) when the PID was issued.",
                Icon.DATE_RANGE,
                LocalDate.parse(SampleData.ISSUE_DATE).toDataItemFullDate()
            )
            .addVcAttribute(
                DocumentAttributeType.Date,
                "expiry_date",
                "Date of Expiry",
                "Date (and possibly time) when the PID will expire.",
                Icon.CALENDAR_CLOCK,
                LocalDate.parse(SampleData.EXPIRY_DATE).toDataItemFullDate()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "issuing_authority",
                "Issuing Authority",
                "Name of the administrative authority that has issued this PID instance, or the " +
                        "ISO 3166 Alpha-2 country code of the respective Member State if there is" +
                        "no separate authority authorized to issue PIDs.",
                Icon.ACCOUNT_BALANCE,
                SampleData.ISSUING_AUTHORITY_EU_PID.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "document_number",
                "Document Number",
                "A number for the PID, assigned by the PID Provider.",
                Icon.NUMBERS,
                SampleData.DOCUMENT_NUMBER.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "administrative_number",
                "Administrative Number",
                "A number assigned by the PID Provider for audit control or other purposes.",
                Icon.NUMBERS,
                SampleData.ADMINISTRATIVE_NUMBER.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "issuing_jurisdiction",
                "Issuing Jurisdiction",
                "Country subdivision code of the jurisdiction that issued the PID, as defined in " +
                        "ISO 3166-2:2020, Clause 8. The first part of the code SHALL be the same " +
                        "as the value for issuing_country.",
                Icon.ACCOUNT_BALANCE,
                SampleData.ISSUING_JURISDICTION.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "issuing_country",
                "Issuing Country",
                "Alpha-2 country code, as defined in ISO 3166-1, of the issuing authority’s " +
                        "country or territory",
                Icon.ACCOUNT_BALANCE,
                SampleData.ISSUING_COUNTRY.toDataItem()
            )
            .addSampleRequest(
                id = "age_over_18",
                displayName = "Age Over 18",
                vcClaims = listOf("18")
            )
            .addSampleRequest(
                id = "mandatory",
                displayName = "Mandatory Data Elements",
                vcClaims = listOf(
                    "family_name",
                    "given_name",
                    "birthdate",
                    "18",
                    "issuance_date",
                    "expiry_date",
                    "issuing_authority",
                    "issuing_country"
                )
            )
            .addSampleRequest(
                id = "full",
                displayName = "All Data Elements",
                vcClaims = listOf()
            )
            .build()
    }
}