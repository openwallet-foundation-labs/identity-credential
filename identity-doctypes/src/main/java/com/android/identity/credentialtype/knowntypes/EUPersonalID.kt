/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity.credentialtype.knowntypes

import com.android.identity.cbor.toDataItem
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.CredentialType

/**
 * Object containing the metadata of the EU Personal ID Credential Type.
 *
 * Source: https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework/blob/main/docs/annexes/annex-06-pid-rulebook.md
 */
object EUPersonalID {
    const val EUPID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
    const val EUPID_NAMESPACE = "eu.europa.ec.eudi.pid.1"

    /**
     * Build the EU Personal ID Credential Type.
     */
    fun getCredentialType(): CredentialType {
        return CredentialType.Builder("EU Personal ID")
            .addMdocCredentialType(EUPID_DOCTYPE)
            .addMdocAttribute(
                CredentialAttributeType.String,
                "family_name",
                "Family Name",
                "Current last name(s), surname(s), or primary identifier of the PID holder",
                true,
                EUPID_NAMESPACE,
                SampleData.familyName.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "given_name",
                "Given Names",
                "Current first name(s), other name(s), or secondary identifier of the PID holder",
                true,
                EUPID_NAMESPACE,
                SampleData.givenName.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.Date,
                "birth_date",
                "Date of Birth",
                "Day, month, and year on which the PID holder was born. If unknown, approximate date of birth.",
                true,
                EUPID_NAMESPACE,
                SampleData.birthDate.toDataItemFullDate
            )
            .addMdocAttribute(
                CredentialAttributeType.Number,
                "age_in_years",
                "Age in Years",
                "The age of the PID holder in years",
                false,
                EUPID_NAMESPACE,
                SampleData.ageInYears.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.Number,
                "age_birth_year",
                "Year of Birth",
                "The year when the PID holder was born",
                false,
                EUPID_NAMESPACE,
                SampleData.ageBirthYear.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.Boolean,
                "age_over_18",
                "Older Than 18",
                "Age over 18?",
                false,
                EUPID_NAMESPACE,
                SampleData.ageOver18.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.Boolean,
                "age_over_21",
                "Older Than 21",
                "Age over 21?",
                false,
                EUPID_NAMESPACE,
                SampleData.ageOver21.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "family_name_birth",
                "Family Name at Birth",
                "Last name(s), surname(s), or primary identifier of the PID holder at birth",
                false,
                EUPID_NAMESPACE,
                SampleData.familyNameBirth.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "given_name_birth",
                "First Name at Birth",
                "First name(s), other name(s), or secondary identifier of the PID holder at birth",
                false,
                EUPID_NAMESPACE,
                SampleData.givenNameBirth.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "birth_place",
                "Place of Birth",
                "Country and municipality or state/province where the PID holder was born",
                false,
                EUPID_NAMESPACE,
                SampleData.birthPlace.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "birth_country",
                "Country of Birth",
                "The country where the PID User was born, as an Alpha-2 country code as specified in ISO 3166-1",
                false,
                EUPID_NAMESPACE,
                SampleData.birthCountry.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "birth_state",
                "State of Birth",
                "The state, province, district, or local area where the PID User was born",
                false,
                EUPID_NAMESPACE,
                SampleData.birthState.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "birth_city",
                "City of Birth",
                "The municipality, city, town, or village where the PID User was born",
                false,
                EUPID_NAMESPACE,
                SampleData.birthCity.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "resident_address",
                "Resident Address",
                "The full address of the place where the PID holder currently resides and/or may be contacted (street/house number, municipality etc.)",
                false,
                EUPID_NAMESPACE,
                SampleData.residentAddress.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "resident_country",
                "Resident Country",
                "The country where the PID User currently resides, as an Alpha-2 country code as specified in ISO 3166-1",
                false,
                EUPID_NAMESPACE,
                SampleData.residentCountry.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "resident_state",
                "Resident State",
                "The state, province, district, or local area where the PID User currently resides.",
                false,
                EUPID_NAMESPACE,
                SampleData.residentState.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "resident_city",
                "Resident City",
                "The city where the PID holder currently resides",
                false,
                EUPID_NAMESPACE,
                SampleData.residentCity.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "resident_postal_code",
                "Resident Postal Code",
                "The postal code of the place where the PID holder currently resides",
                false,
                EUPID_NAMESPACE,
                SampleData.residentPostalCode.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "resident_street",
                "Resident Street",
                "The name of the street where the PID User currently resides.",
                false,
                EUPID_NAMESPACE,
                SampleData.residentStreet.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "resident_house_number",
                "Resident House Number",
                "The house number where the PID User currently resides, including any affix or suffix",
                false,
                EUPID_NAMESPACE,
                SampleData.residentHouseNumber.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.IntegerOptions(Options.SEX_ISO_IEC_5218),
                "gender",
                "Gender",
                "PID holder’s gender",
                false,
                EUPID_NAMESPACE,
                SampleData.sexIso5218.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "nationality",
                "Nationality",
                "Alpha-2 country code as specified in ISO 3166-1, representing the nationality of the PID User.",
                true,
                EUPID_NAMESPACE,
                SampleData.nationality.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.Date,
                "issuance_date",
                "Date of Issue",
                "Date (and possibly time) when the PID was issued.",
                true,
                EUPID_NAMESPACE,
                SampleData.issueDate.toDataItemFullDate
            )
            .addMdocAttribute(
                CredentialAttributeType.Date,
                "expiry_date",
                "Date of Expiry",
                "Date (and possibly time) when the PID will expire.",
                true,
                EUPID_NAMESPACE,
                SampleData.expiryDate.toDataItemFullDate
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "issuing_authority",
                "Issuing Authority",
                "Name of the administrative authority that has issued this PID instance, or the " +
                        "ISO 3166 Alpha-2 country code of the respective Member State if there is" +
                        "no separate authority authorized to issue PIDs.",
                true,
                EUPID_NAMESPACE,
                SampleData.issuingAuthorityEuPid.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "document_number",
                "Document Number",
                "A number for the PID, assigned by the PID Provider.",
                false,
                EUPID_NAMESPACE,
                SampleData.documentNumber.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "administrative_number",
                "Administrative Number",
                "A number assigned by the PID Provider for audit control or other purposes.",
                false,
                EUPID_NAMESPACE,
                SampleData.administrativeNumber.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.String,
                "issuing_jurisdiction",
                "Issuing Jurisdiction",
                "Country subdivision code of the jurisdiction that issued the PID, as defined in " +
                        "ISO 3166-2:2020, Clause 8. The first part of the code SHALL be the same " +
                        "as the value for issuing_country.",
                false,
                EUPID_NAMESPACE,
                SampleData.issuingJurisdiction.toDataItem
            )
            .addMdocAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "issuing_country",
                "Issuing Country",
                "Alpha-2 country code, as defined in ISO 3166-1, of the issuing authority’s " +
                        "country or territory",
                true,
                EUPID_NAMESPACE,
                SampleData.issuingCountry.toDataItem
            )
            .build()
    }
}