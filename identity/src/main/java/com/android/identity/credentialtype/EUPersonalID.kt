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

package com.android.identity.credentialtype

/**
 * Object containing the metadata of the EU Personal ID Credential Type
 */
object EUPersonalID {
    const val EUPID_NAMESPACE = "eu.europa.ec.eudiw.pid.1"

    /**
     * Build the EU Personal ID Credential Type
     */
    fun getCredentialType(): CredentialType {
        return CredentialType.Builder("EU Personal ID")
            .addMdocCredentialType("eu.europa.ec.eudiw.pid.1")
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "family_name",
                "Family Name",
                "Current last name(s), surname(s), or primary identifier of the PID holder",
                true,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "family_name_national_characters",
                "Family name in national characters",
                "The family name of the PID holder in national (non-Latin) characters",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "given_name",
                "Given Names",
                "Current first name(s), other name(s), or secondary identifier of the PID holder",
                true,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "given_name_national_characters",
                "Given name in national characters",
                "The given name of the PID holder in national (non-Latin) characters",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE,
                "birth_date",
                "Date of birth",
                "Day, month, and year on which the PID holder was born. If unknown, approximate date of birth.",
                true,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "persistent_id",
                "Unique Identifier",
                "The persistent identifier assigned to the PID holder by the PID provider",
                true,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "family_name_birth",
                "Family Name at Birth",
                "Last name(s), surname(s), or primary identifier of the PID holder at birth",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "family_name_birth_national_characters",
                "Family Name at Birth in national characters",
                "The family name of the PID holder at birth in national (non-Latin) characters",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "given_name_birth",
                "First Name at Birth",
                "First name(s), other name(s), or secondary identifier of the PID holder at birth",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "given_name_birth_national_characters",
                "First Name at Birth in national characters",
                "The given name of the PID holder at birth in national (non-Latin) characters",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "birth_place",
                "Place of birth",
                "Country and municipality or state/province where the PID holder was born",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "resident_address",
                "Permanent place of residence",
                "The full address of the place where the PID holder currently resides and/or may be contacted (street/house number, municipality etc.)",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "resident_city",
                "Resident city",
                "The city where the PID holder currently resides",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "resident_postal_code",
                "Resident postal code",
                "The postal code of the place where the PID holder currently resides",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "resident_state",
                "Resident state",
                "The state, province, district, or local area where the PID holder currently resides",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "resident_country",
                "Resident country",
                "The country where the PID holder currently resides",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "gender",
                "Gender",
                "PID holder’s gender",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "nationality",
                "Nationality",
                "Alpha-2 country code",
                true,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.PICTURE,
                "portrait",
                "FacialImage/Portrait",
                "A reproduction of the PID holder’s portrait",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE,
                "portrait_capture_date",
                "Portrait image timestamp",
                "Date when portrait was taken",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.PICTURE,
                "biometric_template_finger",
                "Fingerprint",
                "Fingerprint",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_13",
                "Age over 13?",
                "Age over 13?",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_16",
                "Age over 16?",
                "Age over 16?",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_18",
                "Age over 18?",
                "Age over 18?",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_21",
                "Age over 21?",
                "Age over 21?",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_60",
                "Age over 60?",
                "Age over 60?",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_65",
                "Age over 65?",
                "Age over 65?",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_68",
                "Age over 68?",
                "Age over 68?",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.NUMBER,
                "age_in_years",
                "How old are you (in years)?",
                "The age of the PID holder in years",
                false,
                EUPID_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.NUMBER,
                "age_birth_year",
                "In what year were you born?",
                "The year when the PID holder was born",
                false,
                EUPID_NAMESPACE
            )
            .build()
    }
}