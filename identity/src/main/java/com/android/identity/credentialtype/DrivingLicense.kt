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
 * Object containing the metadata of the Driving License Credential Type
 */
object DrivingLicense {
    const val MDL_NAMESPACE = "org.iso.18013.5.1"
    const val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"

    /**
     * Build the Driving License Credential Type
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
                "Family name",
                "Last name, surname, or primary identifier, of the mDL holder.",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "given_name",
                "Given names",
                "First name(s), other name(s), or secondary identifier, of the mDL holder",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "birth_date",
                "Date of birth",
                "Day, month and year on which the mDL holder was born. If unknown, approximate date of birth",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "issue_date",
                "Date of issue",
                "Date when mDL was issued",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "expiry_date",
                "Date of expiry",
                "Date when mDL expires",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "issuing_country",
                "Issuing country",
                "Alpha-2 country code, as defined in ISO 3166-1, of the issuing authority’s country or territory",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "issuing_authority",
                "Issuing authority",
                "Issuing authority name.",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "document_number",
                "License number",
                "The number assigned or calculated by the issuing authority.",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.PICTURE,
                "portrait",
                "Portrait of mDL holder",
                "A reproduction of the mDL holder’s portrait.",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.ComplexType("DrivingPrivilege", true),
                "driving_privileges",
                "Driving privileges",
                "Driving privileges of the mDL holder",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(Options.DISTINGUISHING_SIGN_ISO_IEC_18013_1_ANNEX_F),
                "un_distinguishing_sign",
                "UN distinguishing sign",
                "Distinguishing sign of the issuing country",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "administrative_number",
                "Administrative number",
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
                "Height (cm)",
                "mDL holder’s height in centimetres",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.NUMBER,
                "weight",
                "Weight (kg)",
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
                "Eye color",
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
                "Hair color",
                "mDL holder’s hair color",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "birth_place",
                "Place of birth",
                "Country and municipality or state/province where the mDL holder was born",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "resident_address",
                "Resident address",
                "The place where the mDL holder resides and/or may be contacted (street/house number, municipality etc.)",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "portrait_capture_date",
                "Portrait image timestamp",
                "Date when portrait was taken",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.NUMBER,
                "age_in_years",
                "Age in years",
                "The age of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.NUMBER,
                "age_birth_year",
                "Year of birth",
                "The year when the mDL holder was born",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_18",
                "Older than 18 years",
                "Indication whether the mDL holder is as old or older than 18",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_21",
                "Older than 21 years",
                "Indication whether the mDL holder is as old or older than 21",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_25",
                "Older than 25 years",
                "Indication whether the mDL holder is as old or older than 25",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_62",
                "Older than 62 years",
                "Indication whether the mDL holder is as old or older than 62",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.BOOLEAN,
                "age_over_65",
                "Older than 65 years",
                "Indication whether the mDL holder is as old or older than 65",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "issuing_jurisdiction",
                "Issuing jurisdiction",
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
                "Resident city",
                "The city where the mDL holder lives",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "resident_state",
                "Resident state",
                "The state/province/district where the mDL holder lives",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "resident_postal_code",
                "Resident postal code",
                "The postal code of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "resident_country",
                "Resident country",
                "The country where the mDL holder lives",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "family_name_national_character",
                "Family name national characters",
                "The family name of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "given_name_national_character",
                "Given name national characters",
                "The given name of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.PICTURE,
                "signature_usual_mark",
                "Signature / usual mark",
                "Image of the signature or usual mark of the mDL holder,",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.ComplexType("DomesticDrivingPrivilege", true),
                "domestic_driving_privileges",
                "aamva_domestic_driving_privileges",
                "Domestic driving privileges",
                "Vehicle types the license holder is authorized to operate",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(Options.AAMVA_NAME_SUFFIX),
                "name_suffix",
                "aamva_name_suffix",
                "Name suffix",
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
                "Organ donor",
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
                "Family name truncation",
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
                "Given name truncation",
                "A code that indicates whether either the first name or the middle name(s) have been truncated",
                true,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "aka_family_name",
                "aamva_aka_family_name_v2",
                "Alias / AKA family name",
                "Other family name by which credential holder is known.",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "aka_given_name",
                "aamva_aka_given_name_v2",
                "Alias / AKA giv-en name",
                "Other given name by which credential holder is known.",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(Options.AAMVA_NAME_SUFFIX),
                "aka_suffix",
                "aamva_aka_suffix",
                "Alias / AKA Suffix name",
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
                "Weight range",
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
                "Race / ethnicity",
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
                "Compliance type",
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
                "Limited duration document indicator",
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
                "EDL indicator",
                "Present if the credential is an EDL",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "resident_county",
                "aamva_resident_county",
                "Resident county",
                "The 3-digit county code of the county where the mDL holder lives",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "hazmat_endorsement_expiration_date",
                "aamva_hazmat_endorsement_expiration_date",
                "HAZMAT endorsement expiration date",
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
                "Biometric template face",
                "Facial biometric information of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.PICTURE,
                "biometric_template_finger",
                "Biometric template fingerprint",
                "Fingerprint of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.PICTURE,
                "biometric_template_signature_sign",
                "Biometric template signature/sign",
                "Signature/sign of the mDL holder",
                false,
                MDL_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.PICTURE,
                "biometric_template_iris",
                "Biometric template iris",
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
                "CDL indicator",
                "FMCSA required field that denotes whether the credential is a 'Commercial Driver’s License'or a 'Commercial Learner’s Permit'. This field is either absent or has value '1'(Commercial Driver’s License)."
            )
            .addVcAttribute(
                CredentialAttributeType.STRING,
                "aamva_dhs_compliance_text",
                "Non-REAL ID credential text",
                "Text, agreed on between the Issuing Authority and DHS, appearing on credentials not meeting REAL ID requirements."
            )
            /*
             * Finally the complex type definitions
             */
            // details of driving_privileges
            .addAttribute(
                CredentialAttributeType.StringOptions(
                    listOf(
                        StringOption(null, "(not set)"),
                        StringOption("A", "Motorcycles (A)"),
                        StringOption("AEU", "Motorcycles (AEU)"),
                        StringOption("B", "Light vehicles (B"),
                        StringOption("C", "Goods vehicles (C)"),
                        StringOption("D", "Passenger vehicles (D)"),
                        StringOption("BE", "Light vehicles with trailers (BE)"),
                        StringOption("CE", "Goods vehicles with trailers (CE)"),
                        StringOption("DE", "Passenger vehicles with trailers (DE)"),
                        StringOption("AM", "Mopeds (AM)"),
                        StringOption("A1", "Light motorcycles (A1)"),
                        StringOption("A1EU", "Light motorcycles (A1EU)"),
                        StringOption("A2", "Medium motorcycles (A2)"),
                        StringOption("B1", "Light vehicles (B1)"),
                        StringOption("B1EU", "Light vehicles (B1EU)"),
                        StringOption("C1", "Medium sized goods vehicles (C1)"),
                        StringOption("D1", "Medium sized passenger vehicles (e.g. minibuses) (D1)"),
                    )
                ),
                "DrivingPrivilege.vehicle_category_code",
                "Vehicle category code",
                "Vehicle category code",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "DrivingPrivilege.issue_date",
                "Date of issue",
                "Date of issue",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "DrivingPrivilege.expiry_date",
                "Date of expiry",
                "Date of expiry",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.ComplexType("Code", true),
                "DrivingPrivilege.codes",
                "Codes of driving privileges",
                "Codes of driving privileges",
                false,
                MDL_NAMESPACE
            )
            // details of DrivingPrivilege.codes
            .addAttribute(
                CredentialAttributeType.StringOptions(
                    listOf(
                        StringOption(null, "(not set)"),
                        StringOption(
                            "01",
                            "Licence holder requires eye sight correction and/or protection"
                        ),
                        StringOption(
                            "03",
                            "Licence holder requires prosthetic device for the limbs"
                        ),
                        StringOption(
                            "78",
                            "Licence holder restricted to vehicles with automatic transmission"
                        ),
                        StringOption(
                            "S01",
                            "The vehicle's maximum authorized mass (kg) shall be"
                        ),
                        StringOption(
                            "S02",
                            "The vehicle's authorized passenger seats, excluding the driver's seat, shall be"
                        ),
                        StringOption(
                            "S03",
                            "The vehicle's cylinder capacity (cm3) shall be"
                        ),
                        StringOption(
                            "S04",
                            "The vehicle's power (kW) shall be"
                        ),
                        StringOption(
                            "S05",
                            "Licence holder restricted to vehicles adapted for physically disabled"
                        )
                    )
                ),
                "Code.code",
                "Code",
                "Code",
                true,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.StringOptions(
                    listOf(
                        StringOption(null, "(not set)"),
                        StringOption("=", "Equals (=)"),
                        StringOption(">", "Greater than (>)"),
                        StringOption("<", "Less than (<)"),
                        StringOption(">=", "Greater than or equal to (≥)"),
                        StringOption("<=", "Less than or equal to (≤)")
                    )
                ),
                "Code.sign",
                "Sign",
                "Sign",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "Code.value",
                "Value",
                "Value",
                false,
                MDL_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "audit_information",
                "Audit information",
                "A string of letters and/or numbers that identifies when, where, and by whom the credential was initially provisioned. ",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.NUMBER,
                "aamva_version",
                "AAMVA version number",
                "A number identifying the version of the AAMVA mDL data element set",
                true,
                AAMVA_NAMESPACE
            )
            // details of domestic_driving_privileges
            .addAttribute(
                CredentialAttributeType.ComplexType("DomesticVehicleClass", false),
                "DomesticDrivingPrivilege.domestic_vehicle_class",
                "Domestic vehicle class",
                "Domestic vehicle class",
                true,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.ComplexType("DomesticVehicleRestriction", true),
                "DomesticDrivingPrivilege.domestic_vehicle_restrictions",
                "Domestic vehicle restrictions",
                "Domestic vehicle restrictions",
                true,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.ComplexType("DomesticVehicleEndorsement", true),
                "DomesticDrivingPrivilege.domestic_vehicle_endorsements",
                "Domestic vehicle endorsements",
                "Domestic vehicle endorsements",
                true,
                AAMVA_NAMESPACE
            )
            // details of DomesticDrivingPrivilege.domestic_vehicle_class
            .addAttribute(
                CredentialAttributeType.STRING,
                "DomesticVehicleClass.domestic_vehicle_class_code",
                "Domestic vehicle class code",
                "Vehicle category code",
                true,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "DomesticVehicleClass.domestic_vehicle_class_description",
                "Domestic vehicle class description",
                "Vehicle category description",
                true,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "DomesticVehicleClass.issue_date",
                "Date of issue",
                "Date of issue",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.DATE,
                "DomesticVehicleClass.expiry_date",
                "Date of expiry",
                "Date of expiry",
                false,
                AAMVA_NAMESPACE
            )
            // details of DomesticDrivingPrivilege.domestic_vehicle_restrictions
            .addAttribute(
                CredentialAttributeType.STRING,
                "DomesticVehicleRestriction.domestic_vehicle_restriction_code",
                "Restriction code",
                "Restriction code",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "DomesticVehicleRestriction.domestic_vehicle_restriction_description",
                "Vehicle category description",
                "Vehicle category description",
                true,
                AAMVA_NAMESPACE
            )
            // details of DomesticDrivingPrivilege.domestic_vehicle_endorsements
            .addAttribute(
                CredentialAttributeType.STRING,
                "DomesticVehicleEndorsement.domestic_vehicle_endorsement_code",
                "Endorsement code",
                "Endorsement code",
                false,
                AAMVA_NAMESPACE
            )
            .addAttribute(
                CredentialAttributeType.STRING,
                "DomesticVehicleEndorsement.domestic_vehicle_endorsement_description",
                "Vehicle endorsement description",
                "Vehicle endorsement description",
                true,
                AAMVA_NAMESPACE
            )
            .build()
    }
}

