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

import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.CredentialType
import com.android.identity.credentialtype.IntegerOption
import com.android.identity.credentialtype.StringOption

/**
 * Object containing the metadata of the Driving License
 * Credential Type.
 */
object DrivingLicense {
    const val MDL_NAMESPACE = "org.iso.18013.5.1"
    const val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"

    /**
     * Build the Driving License Credential Type.
     */
    fun getCredentialType(): CredentialType {
        return CredentialType.Builder("Driving License")
            .addMdocCredentialType("org.iso.18013.5.1.mDL")
            .addVcCredentialType("Iso18013DriversLicenseCredential")/*
             * First the attributes that the mDL and VC Credential Type have in common
             */
            .addAttribute(
                CredentialAttributeType.STRING,
                "family_name",
                "Family Name",
                "Last name, surname, or primary identifier, of the mDL holder.",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "given_name",
                "Given Names",
                "First name(s), other name(s), or secondary identifier, of the mDL holder",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "birth_date",
                "Date of Birth",
                "Day, month and year on which the mDL holder was born. If unknown, approximate date of birth",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "issue_date",
                "Date of Issue",
                "Date when mDL was issued",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "expiry_date",
                "Date of Expiry",
                "Date when mDL expires",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "issuing_country",
                "Issuing Country",
                "Alpha-2 country code, as defined in ISO 3166-1, of the issuing authority’s country or territory",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "issuing_authority",
                "Issuing Authority",
                "Issuing authority name.",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "document_number",
                "License Number",
                "The number assigned or calculated by the issuing authority.",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.PICTURE,
                "portrait",
                "Photo of Holder",
                "A reproduction of the mDL holder’s portrait.",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.COMPLEX_TYPE,
                "driving_privileges",
                "Driving Privileges",
                "Driving privileges of the mDL holder",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(Options.DISTINGUISHING_SIGN_ISO_IEC_18013_1_ANNEX_F),
                "un_distinguishing_sign",
                "UN Distinguishing Sign",
                "Distinguishing sign of the issuing country",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "administrative_number",
                "Administrative Number",
                "An audit control number assigned by the issuing authority",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.IntegerOptions(Options.SEX_ISO_IEC_5218),
                "sex",
                "Sex",
                "mDL holder’s sex",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.NUMBER,
                "height",
                "Height",
                "mDL holder’s height in centimetres",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.NUMBER,
                "weight",
                "Weight",
                "mDL holder’s weight in kilograms",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(
                    listOf(
                        StringOption(null, "(not set)"),
                        StringOption("black", "Black"),
                        StringOption("blue", "Blue"),
                        StringOption("brown", "Brown"),
                        StringOption("dichromatic", "Dichromatic"),
                        StringOption("grey", "Grey"),
                        StringOption("green", "Green"),
                        StringOption("hazel", "Hazel"),
                        StringOption("maroon", "Maroon"),
                        StringOption("pink", "Pink"),
                        StringOption("unknown", "Unknown")
                    )
                ),
                "eye_colour",
                "Eye Color",
                "mDL holder’s eye color",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(
                    listOf(
                        StringOption(null, "(not set)"),
                        StringOption("bald", "Bald"),
                        StringOption("black", "Black"),
                        StringOption("blond", "Blond"),
                        StringOption("brown", "Brown"),
                        StringOption("grey", "Grey"),
                        StringOption("red", "Red"),
                        StringOption("auburn", "Auburn"),
                        StringOption("sandy", "Sandy"),
                        StringOption("white", "White"),
                        StringOption("unknown", "Unknown"),
                    )
                ),
                "hair_colour",
                "Hair Color",
                "mDL holder’s hair color",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "birth_place",
                "Place of Birth",
                "Country and municipality or state/province where the mDL holder was born",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "resident_address",
                "Resident Address",
                "The place where the mDL holder resides and/or may be contacted (street/house number, municipality etc.)",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "portrait_capture_date",
                "Portrait Image Timestamp",
                "Date when portrait was taken",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.NUMBER,
                "age_in_years",
                "Age in Years",
                "The age of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.NUMBER,
                "age_birth_year",
                "Year of Birth",
                "The year when the mDL holder was born",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_18",
                "Older Than 18 Years",
                "Indication whether the mDL holder is as old or older than 18",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_21",
                "Older Than 21 Years",
                "Indication whether the mDL holder is as old or older than 21",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_25",
                "Older Than 25 Years",
                "Indication whether the mDL holder is as old or older than 25",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_62",
                "Older Than 62 Years",
                "Indication whether the mDL holder is as old or older than 62",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_65",
                "Older Than 65 Years",
                "Indication whether the mDL holder is as old or older than 65",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "issuing_jurisdiction",
                "Issuing Jurisdiction",
                "Country subdivision code of the jurisdiction that issued the mDL",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "nationality",
                "Nationality",
                "Nationality of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "resident_city",
                "Resident City",
                "The city where the mDL holder lives",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "resident_state",
                "Resident State",
                "The state/province/district where the mDL holder lives",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "resident_postal_code",
                "Resident Postal Code",
                "The postal code of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "resident_country",
                "Resident Country",
                "The country where the mDL holder lives",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "family_name_national_character",
                "Family Name National Characters",
                "The family name of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "given_name_national_character",
                "Given Name National Characters",
                "The given name of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.PICTURE,
                "signature_usual_mark",
                "Signature / Usual Mark",
                "Image of the signature or usual mark of the mDL holder,",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.COMPLEX_TYPE,
                "domestic_driving_privileges",
                "aamva_domestic_driving_privileges",
                "Domestic Driving Privileges",
                "Vehicle types the license holder is authorized to operate",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(Options.AAMVA_NAME_SUFFIX),
                "name_suffix",
                "aamva_name_suffix",
                "Name Suffix",
                "Name suffix of the individual that has been issued the driver license or identification document.",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.IntegerOptions(
                    listOf(
                        IntegerOption(null, "(not set)"),
                        IntegerOption(1, "Donor")
                    )
                ),
                "organ_donor",
                "aamva_organ_donor",
                "Organ Donor",
                "An indicator that denotes whether the credential holder is an organ donor.",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.IntegerOptions(
                    listOf(
                        IntegerOption(null, "(not set)"),
                        IntegerOption(1, "Veteran")
                    )
                ),
                "veteran",
                "aamva_veteran",
                "Veteran",
                "An indicator that denotes whether the credential holder is a veteran.",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(
                    listOf(
                        StringOption(null, "(not set)"),
                        StringOption("T", "Truncated"),
                        StringOption("N", "Not truncated"),
                        StringOption("U", "Unknown whether truncated"),
                    )
                ),
                "family_name_truncation",
                "aamva_family_name_truncation",
                "Family Name Truncation",
                "A code that indicates whether the field has been truncated",
                true,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(
                    listOf(
                        StringOption(null, "(not set)"),
                        StringOption("T", "Truncated"),
                        StringOption("N", "Not truncated"),
                        StringOption("U", "Unknown whether truncated"),
                    )
                ),
                "given_name_truncation",
                "aamva_given_name_truncation",
                "Given Name Truncation",
                "A code that indicates whether either the first name or the middle name(s) have been truncated",
                true,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "aka_family_name",
                "aamva_aka_family_name_v2",
                "Alias / AKA Family Name",
                "Other family name by which credential holder is known.",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "aka_given_name",
                "aamva_aka_given_name_v2",
                "Alias / AKA Given Name",
                "Other given name by which credential holder is known.",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(Options.AAMVA_NAME_SUFFIX),
                "aka_suffix",
                "aamva_aka_suffix",
                "Alias / AKA Suffix Name",
                "Other suffix by which credential holder is known.",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.IntegerOptions(
                    listOf(
                        IntegerOption(null, "(not set)"),
                        IntegerOption(0, "Up to 31 kg (up to 70 lbs.)"),
                        IntegerOption(1, "32 – 45 kg (71 – 100 lbs.)"),
                        IntegerOption(2, "46 - 59 kg (101 – 130 lbs.)"),
                        IntegerOption(3, "60 - 70 kg (131 – 160 lbs.)"),
                        IntegerOption(4, "71 - 86 kg (161 – 190 lbs.)"),
                        IntegerOption(5, "87 - 100 kg (191 – 220 lbs.)"),
                        IntegerOption(6, "101 - 113 kg (221 – 250 lbs.)"),
                        IntegerOption(7, "114 - 127 kg (251 – 280 lbs.)"),
                        IntegerOption(8, "128 – 145 kg (281 – 320 lbs.)"),
                        IntegerOption(9, "146+ kg (321+ lbs.)"),
                    )
                ),
                "weight_range",
                "aamva_weight_range",
                "Weight Range",
                "Indicates the approximate weight range of the cardholder",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(
                    listOf(
                        StringOption(null, "(not set)"),
                        StringOption("AI", "Alaskan or American Indian"),
                        StringOption("AP", "Asian or Pacific Islander"),
                        StringOption("BK", "Black"),
                        StringOption("H", "Hispanic Origin"),
                        StringOption("O", "Non-hispanic"),
                        StringOption("U", "Unknown"),
                        StringOption("W", "White")
                    )
                ),
                "race_ethnicity",
                "aamva_race_ethnicity",
                "Race / Ethnicity",
                "Codes for race or ethnicity of the cardholder",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(
                    listOf(
                        StringOption(null, "(not set)"),
                        StringOption("F", "Fully compliant"),
                        StringOption("N", "Non-compliant"),
                    )
                ),
                "DHS_compliance",
                "aamva_dhs_compliance",
                "Compliance Type",
                "DHS required field that indicates compliance",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.IntegerOptions(
                    listOf(
                        IntegerOption(null, "(not set)"),
                        IntegerOption(1, "Temporary lawful status")
                    )
                ),
                "DHS_temporary_lawful_status",
                "aamva_dhs_temporary_lawful_status",
                "Limited Duration Document Indicator",
                "DHS required field that denotes whether the credential holder has temporary lawful status. 1: Temporary lawful status",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.IntegerOptions(
                    listOf(
                        IntegerOption(null, "(not set)"),
                        IntegerOption(1, "Driver's license"),
                        IntegerOption(2, "Identification card")
                    )
                ),
                "EDL_credential",
                "aamva_edl_credential",
                "EDL Indicator",
                "Present if the credential is an EDL",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "resident_county",
                "aamva_resident_county",
                "Resident County",
                "The 3-digit county code of the county where the mDL holder lives",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "hazmat_endorsement_expiration_date",
                "aamva_hazmat_endorsement_expiration_date",
                "HAZMAT Endorsement Expiration Date",
                "Date on which the hazardous material endorsement granted by the document is no longer valid.",
                true,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.IntegerOptions(Options.SEX_ISO_IEC_5218),
                "sex",
                "aamva_sex",
                "Sex",
                "mDL holder’s sex",
                true,
                AAMVA_NAMESPACE
            )
            /*
             * Then the attributes that exist only in the mDL Credential Type and not in the VC Credential Type
             */
            .addMdocAttribute(
                CredentialAttributeType.PICTURE,
                "biometric_template_face",
                "Biometric Template Face",
                "Facial biometric information of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.PICTURE,
                "biometric_template_finger",
                "Biometric Template Fingerprint",
                "Fingerprint of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.PICTURE,
                "biometric_template_signature_sign",
                "Biometric Template Signature/Sign",
                "Signature/sign of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.PICTURE,
                "biometric_template_iris",
                "Biometric Template Iris",
                "Iris of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            /*
             * Then attributes that exist only in the VC Credential Type and not in the mDL Credential Type
             */
            .addVcAttribute(
                CredentialAttributeType.NUMBER,
                "aamva_cdl_indicator",
                "CDL Indicator",
                "FMCSA required field that denotes whether the credential is a 'Commercial Driver’s License'or a 'Commercial Learner’s Permit'. This field is either absent or has value '1'(Commercial Driver’s License)."
            )
            .addVcAttribute(
                CredentialAttributeType.STRING,
                "aamva_dhs_compliance_text",
                "Non-REAL ID Credential Text",
                "Text, agreed on between the Issuing Authority and DHS, appearing on credentials not meeting REAL ID requirements."
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "audit_information",
                "Audit Information",
                "A string of letters and/or numbers that identifies when, where, and by whom the credential was initially provisioned. ",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.NUMBER,
                "aamva_version",
                "AAMVA Version Number",
                "A number identifying the version of the AAMVA mDL data element set",
                true,
                AAMVA_NAMESPACE
            )
            .build()
    }
}

