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

package com.android.identity.documenttype.knowntypes

import com.android.identity.cbor.CborArray
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.Tstr
import com.android.identity.cbor.toDataItem
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.Icon
import com.android.identity.documenttype.IntegerOption
import com.android.identity.documenttype.StringOption
import com.android.identity.util.fromBase64Url
import com.android.identity.util.fromHex
import kotlinx.datetime.LocalDate

/**
 * Object containing the metadata of the Driving License
 * Document Type.
 */
object DrivingLicense {
    const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
    const val MDL_NAMESPACE = "org.iso.18013.5.1"
    const val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"

    /**
     * Build the Driving License Document Type.
     */
    fun getDocumentType(): DocumentType {
        return DocumentType.Builder("Driving License")
            .addMdocDocumentType(MDL_DOCTYPE)
            .addVcDocumentType("Iso18013DriversLicenseCredential")
            /*
             * First the attributes that the mDL and VC Credential Type have in common
             */
            .addAttribute(
                DocumentAttributeType.String,
                "family_name",
                "Family Name",
                "Last name, surname, or primary identifier, of the mDL holder.",
                true,
                MDL_NAMESPACE,
                Icon.PERSON,
                SampleData.FAMILY_NAME.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "given_name",
                "Given Names",
                "First name(s), other name(s), or secondary identifier, of the mDL holder",
                true,
                MDL_NAMESPACE,
                Icon.PERSON,
                SampleData.GIVEN_NAME.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Date,
                "birth_date",
                "Date of Birth",
                "Day, month and year on which the mDL holder was born. If unknown, approximate date of birth",
                true,
                MDL_NAMESPACE,
                Icon.TODAY,
                LocalDate.parse(SampleData.BIRTH_DATE).toDataItemFullDate()
            )
            .addAttribute(
                DocumentAttributeType.Date,
                "issue_date",
                "Date of Issue",
                "Date when mDL was issued",
                true,
                MDL_NAMESPACE,
                Icon.DATE_RANGE,
                LocalDate.parse(SampleData.ISSUE_DATE).toDataItemFullDate()
            )
            .addAttribute(
                DocumentAttributeType.Date,
                "expiry_date",
                "Date of Expiry",
                "Date when mDL expires",
                true,
                MDL_NAMESPACE,
                Icon.CALENDAR_CLOCK,
                LocalDate.parse(SampleData.EXPIRY_DATE).toDataItemFullDate()
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "issuing_country",
                "Issuing Country",
                "Alpha-2 country code, as defined in ISO 3166-1, of the issuing authority’s country or territory",
                true,
                MDL_NAMESPACE,
                Icon.ACCOUNT_BALANCE,
                SampleData.ISSUING_COUNTRY.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "issuing_authority",
                "Issuing Authority",
                "Issuing authority name.",
                true,
                MDL_NAMESPACE,
                Icon.ACCOUNT_BALANCE,
                SampleData.ISSUING_AUTHORITY_MDL.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "document_number",
                "License Number",
                "The number assigned or calculated by the issuing authority.",
                true,
                MDL_NAMESPACE,
                Icon.NUMBERS,
                SampleData.DOCUMENT_NUMBER.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Picture,
                "portrait",
                "Photo of Holder",
                "A reproduction of the mDL holder’s portrait.",
                true,
                MDL_NAMESPACE,
                Icon.ACCOUNT_BOX,
                SampleData.PORTRAIT_BASE64URL.fromBase64Url().toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.ComplexType,
                "driving_privileges",
                "Driving Privileges",
                "Driving privileges of the mDL holder",
                true,
                MDL_NAMESPACE,
                Icon.DIRECTIONS_CAR,
                CborArray.builder()
                    .addMap()
                    .put("vehicle_category_code", "A")
                    .put("issue_date", Tagged(1004, Tstr("2018-08-09")))
                    .put("expiry_date", Tagged(1004, Tstr("2028-09-01")))
                    .end()
                    .addMap()
                    .put("vehicle_category_code", "B")
                    .put("issue_date", Tagged(1004, Tstr("2017-02-23")))
                    .put("expiry_date", Tagged(1004, Tstr("2028-09-01")))
                    .end()
                    .end()
                    .build()
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(Options.DISTINGUISHING_SIGN_ISO_IEC_18013_1_ANNEX_F),
                "un_distinguishing_sign",
                "UN Distinguishing Sign",
                "Distinguishing sign of the issuing country",
                true,
                MDL_NAMESPACE,
                Icon.LANGUAGE,
                SampleData.UN_DISTINGUISHING_SIGN.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "administrative_number",
                "Administrative Number",
                "An audit control number assigned by the issuing authority",
                false,
                MDL_NAMESPACE,
                Icon.NUMBERS,
                SampleData.ADMINISTRATIVE_NUMBER.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.IntegerOptions(Options.SEX_ISO_IEC_5218),
                "sex",
                "Sex",
                "mDL holder’s sex",
                false,
                MDL_NAMESPACE,
                Icon.EMERGENCY,
                SampleData.SEX_ISO218.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Number,
                "height",
                "Height",
                "mDL holder’s height in centimetres",
                false,
                MDL_NAMESPACE,
                Icon.EMERGENCY,
                SampleData.HEIGHT_CM.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Number,
                "weight",
                "Weight",
                "mDL holder’s weight in kilograms",
                false,
                MDL_NAMESPACE,
                Icon.EMERGENCY,
                SampleData.WEIGHT_KG.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(
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
                MDL_NAMESPACE,
                Icon.PERSON,
                "blue".toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(
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
                MDL_NAMESPACE,
                Icon.PERSON,
                "blond".toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "birth_place",
                "Place of Birth",
                "Country and municipality or state/province where the mDL holder was born",
                false,
                MDL_NAMESPACE,
                Icon.PLACE,
                SampleData.BIRTH_PLACE.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "resident_address",
                "Resident Address",
                "The place where the mDL holder resides and/or may be contacted (street/house number, municipality etc.)",
                false,
                MDL_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_ADDRESS.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Date,
                "portrait_capture_date",
                "Portrait Image Timestamp",
                "Date when portrait was taken",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                LocalDate.parse(SampleData.PORTRAIT_CAPTURE_DATE).toDataItemFullDate()
            )
            .addAttribute(
                DocumentAttributeType.Number,
                "age_in_years",
                "Age in Years",
                "The age of the mDL holder",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_IN_YEARS.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Number,
                "age_birth_year",
                "Year of Birth",
                "The year when the mDL holder was born",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_BIRTH_YEAR.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "age_over_13",
                "Older Than 13 Years",
                "Indication whether the mDL holder is as old or older than 13",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_13.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "age_over_16",
                "Older Than 16 Years",
                "Indication whether the mDL holder is as old or older than 16",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_16.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Boolean,
                "age_over_18",
                "Older Than 18 Years",
                "Indication whether the mDL holder is as old or older than 18",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_18.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Boolean,
                "age_over_21",
                "Older Than 21 Years",
                "Indication whether the mDL holder is as old or older than 21",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_21.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Boolean,
                "age_over_25",
                "Older Than 25 Years",
                "Indication whether the mDL holder is as old or older than 25",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_25.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Boolean,
                "age_over_60",
                "Older Than 60 Years",
                "Indication whether the mDL holder is as old or older than 60",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_60.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Boolean,
                "age_over_62",
                "Older Than 62 Years",
                "Indication whether the mDL holder is as old or older than 62",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_62.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Boolean,
                "age_over_65",
                "Older Than 65 Years",
                "Indication whether the mDL holder is as old or older than 65",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_65.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Boolean,
                "age_over_68",
                "Older Than 68 Years",
                "Indication whether the mDL holder is as old or older than 68",
                false,
                MDL_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_68.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "issuing_jurisdiction",
                "Issuing Jurisdiction",
                "Country subdivision code of the jurisdiction that issued the mDL",
                false,
                MDL_NAMESPACE,
                Icon.ACCOUNT_BALANCE,
                SampleData.ISSUING_JURISDICTION.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "nationality",
                "Nationality",
                "Nationality of the mDL holder",
                false,
                MDL_NAMESPACE,
                Icon.LANGUAGE,
                SampleData.NATIONALITY.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "resident_city",
                "Resident City",
                "The city where the mDL holder lives",
                false,
                MDL_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_CITY.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "resident_state",
                "Resident State",
                "The state/province/district where the mDL holder lives",
                false,
                MDL_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_STATE.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "resident_postal_code",
                "Resident Postal Code",
                "The postal code of the mDL holder",
                false,
                MDL_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_POSTAL_CODE.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "resident_country",
                "Resident Country",
                "The country where the mDL holder lives",
                false,
                MDL_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_COUNTRY.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "family_name_national_character",
                "Family Name National Characters",
                "The family name of the mDL holder",
                false,
                MDL_NAMESPACE,
                Icon.PERSON,
                SampleData.FAMILY_NAME_NATIONAL_CHARACTER.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "given_name_national_character",
                "Given Name National Characters",
                "The given name of the mDL holder",
                false,
                MDL_NAMESPACE,
                Icon.PERSON,
                SampleData.GIVEN_NAMES_NATIONAL_CHARACTER.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Picture,
                "signature_usual_mark",
                "Signature / Usual Mark",
                "Image of the signature or usual mark of the mDL holder,",
                false,
                MDL_NAMESPACE,
                Icon.SIGNATURE,
                SampleData.SIGNATURE_OR_USUAL_MARK_BASE64URL.fromBase64Url().toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.ComplexType,
                "domestic_driving_privileges",
                "aamva_domestic_driving_privileges",
                "Domestic Driving Privileges",
                "Vehicle types the license holder is authorized to operate",
                false,
                AAMVA_NAMESPACE,
                Icon.DIRECTIONS_CAR,
                null
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(Options.AAMVA_NAME_SUFFIX),
                "name_suffix",
                "aamva_name_suffix",
                "Name Suffix",
                "Name suffix of the individual that has been issued the driver license or identification document.",
                false,
                AAMVA_NAMESPACE,
                Icon.PERSON,
                null
            )
            .addAttribute(
                DocumentAttributeType.IntegerOptions(
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
                AAMVA_NAMESPACE,
                Icon.EMERGENCY,
                1.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.IntegerOptions(
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
                AAMVA_NAMESPACE,
                Icon.MILITARY_TECH,
                null
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(
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
                AAMVA_NAMESPACE,
                Icon.PERSON,
                null
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(
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
                AAMVA_NAMESPACE,
                Icon.PERSON,
                null
            )
            .addAttribute(
                DocumentAttributeType.String,
                "aka_family_name",
                "aamva_aka_family_name_v2",
                "Alias / AKA Family Name",
                "Other family name by which credential holder is known.",
                false,
                AAMVA_NAMESPACE,
                Icon.PERSON,
                null
            )
            .addAttribute(
                DocumentAttributeType.String,
                "aka_given_name",
                "aamva_aka_given_name_v2",
                "Alias / AKA Given Name",
                "Other given name by which credential holder is known.",
                false,
                AAMVA_NAMESPACE,
                Icon.PERSON,
                null
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(Options.AAMVA_NAME_SUFFIX),
                "aka_suffix",
                "aamva_aka_suffix",
                "Alias / AKA Suffix Name",
                "Other suffix by which credential holder is known.",
                false,
                AAMVA_NAMESPACE,
                Icon.PERSON,
                null
            )
            .addAttribute(
                DocumentAttributeType.IntegerOptions(
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
                AAMVA_NAMESPACE,
                Icon.EMERGENCY,
                3.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(
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
                AAMVA_NAMESPACE,
                Icon.EMERGENCY,
                "W".toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(
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
                AAMVA_NAMESPACE,
                Icon.STARS,
                "F".toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.IntegerOptions(
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
                AAMVA_NAMESPACE,
                Icon.STARS,
                null
            )
            .addAttribute(
                DocumentAttributeType.IntegerOptions(
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
                AAMVA_NAMESPACE,
                Icon.DIRECTIONS_CAR,
                1.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "resident_county",
                "aamva_resident_county",
                "Resident County",
                "The 3-digit county code of the county where the mDL holder lives",
                false,
                AAMVA_NAMESPACE,
                Icon.PLACE,
                null
            )
            .addAttribute(
                DocumentAttributeType.Date,
                "hazmat_endorsement_expiration_date",
                "aamva_hazmat_endorsement_expiration_date",
                "HAZMAT Endorsement Expiration Date",
                "Date on which the hazardous material endorsement granted by the document is no longer valid.",
                true,
                AAMVA_NAMESPACE,
                Icon.CALENDAR_CLOCK,
                null
            )
            .addAttribute(
                DocumentAttributeType.IntegerOptions(Options.SEX_ISO_IEC_5218),
                "sex",
                "aamva_sex",
                "Sex",
                "mDL holder’s sex",
                true,
                AAMVA_NAMESPACE,
                Icon.EMERGENCY,
                SampleData.SEX_ISO218.toDataItem()
            )
            /*
             * Then the attributes that exist only in the mDL Credential Type and not in the VC Credential Type
             */
            .addMdocAttribute(
                DocumentAttributeType.Picture,
                "biometric_template_face",
                "Biometric Template Face",
                "Facial biometric information of the mDL holder",
                false,
                MDL_NAMESPACE,
                Icon.FACE,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Picture,
                "biometric_template_finger",
                "Biometric Template Fingerprint",
                "Fingerprint of the mDL holder",
                false,
                MDL_NAMESPACE,
                Icon.FINGERPRINT,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Picture,
                "biometric_template_signature_sign",
                "Biometric Template Signature/Sign",
                "Signature/sign of the mDL holder",
                false,
                MDL_NAMESPACE,
                Icon.SIGNATURE,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Picture,
                "biometric_template_iris",
                "Biometric Template Iris",
                "Iris of the mDL holder",
                false,
                MDL_NAMESPACE,
                Icon.EYE_TRACKING,
                null
            )
            /*
             * Then attributes that exist only in the VC Credential Type and not in the mDL Credential Type
             */
            .addVcAttribute(
                DocumentAttributeType.Number,
                "aamva_cdl_indicator",
                "CDL Indicator",
                "FMCSA required field that denotes whether the credential is a 'Commercial " +
                        "Driver’s License' or a 'Commercial Learner’s Permit'. This field is " +
                        "either absent or has value '1' (Commercial Driver’s License).",
                Icon.AIRPORT_SHUTTLE,
                null
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "aamva_dhs_compliance_text",
                "Non-REAL ID Credential Text",
                "Text, agreed on between the Issuing Authority and DHS, appearing on credentials " +
                        "not meeting REAL ID requirements.",
                Icon.STARS,
                null
            )
            .addAttribute(
                DocumentAttributeType.String,
                "audit_information",
                "Audit Information",
                "A string of letters and/or numbers that identifies when, where, and by whom the credential was initially provisioned.",
                false,
                AAMVA_NAMESPACE,
                Icon.STARS,
                null
            )
            .addAttribute(
                DocumentAttributeType.Number,
                "aamva_version",
                "AAMVA Version Number",
                "A number identifying the version of the AAMVA mDL data element set",
                true,
                AAMVA_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addSampleRequest(
                id = "us-transportation",
                displayName = "US Transportation",
                mdocDataElements = mapOf(
                    MDL_NAMESPACE to mapOf(
                        "sex" to false,
                        "portrait" to false,
                        "given_name" to false,
                        "issue_date" to false,
                        "expiry_date" to false,
                        "family_name" to false,
                        "document_number" to false,
                        "issuing_authority" to false
                    ),
                    AAMVA_NAMESPACE to mapOf(
                        "DHS_compliance" to false,
                        "EDL_credential" to false
                    ),
                )
            )
            .addSampleRequest(
                id = "age_over_18",
                displayName ="Age Over 18",
                mdocDataElements = mapOf(
                    MDL_NAMESPACE to mapOf(
                        "age_over_18" to false,
                    )
                ),
            )
            .addSampleRequest(
                id = "age_over_21",
                displayName ="Age Over 21",
                mdocDataElements = mapOf(
                    MDL_NAMESPACE to mapOf(
                        "age_over_21" to false,
                    )
                ),
            )
            .addSampleRequest(
                id = "age_over_18_and_portrait",
                displayName ="Age Over 18 + Portrait",
                mdocDataElements = mapOf(
                    MDL_NAMESPACE to mapOf(
                        "age_over_18" to false,
                        "portrait" to false
                    )
                ),
            )
            .addSampleRequest(
                id = "age_over_21_and_portrait",
                displayName ="Age Over 21 + Portrait",
                mdocDataElements = mapOf(
                    MDL_NAMESPACE to mapOf(
                        "age_over_21" to false,
                        "portrait" to false
                    )
                ),
            )
            .addSampleRequest(
                id = "mandatory",
                displayName = "Mandatory Data Elements",
                mdocDataElements = mapOf(
                    MDL_NAMESPACE to mapOf(
                        "family_name" to false,
                        "given_name" to false,
                        "birth_date" to false,
                        "issue_date" to false,
                        "expiry_date" to false,
                        "issuing_country" to false,
                        "issuing_authority" to false,
                        "document_number" to false,
                        "portrait" to false,
                        "driving_privileges" to false,
                        "un_distinguishing_sign" to false,
                    )
                )
            )
            .addSampleRequest(
                id = "full",
                displayName ="All Data Elements",
                mdocDataElements = mapOf(
                    MDL_NAMESPACE to mapOf(),
                    AAMVA_NAMESPACE to mapOf()
                )
            )
            .addSampleRequest(
                id = "name-and-address-partially-stored",
                displayName = "Name and Address (Partially Stored)",
                mdocDataElements = mapOf(
                    MDL_NAMESPACE to mapOf(
                        "family_name" to true,
                        "given_name" to true,
                        "issuing_authority" to false,
                        "portrait" to false,
                        "resident_address" to true,
                        "resident_city" to true,
                        "resident_state" to true,
                        "resident_postal_code" to true,
                        "resident_country" to true,
                    ),
                    AAMVA_NAMESPACE to mapOf(
                        "resident_county" to true,
                    )
                )
            )
            .addSampleRequest(
                id = "name-and-address-all-stored",
                displayName = "Name and Address (All Stored)",
                mdocDataElements = mapOf(
                    MDL_NAMESPACE to mapOf(
                        "family_name" to true,
                        "given_name" to true,
                        "issuing_authority" to true,
                        "portrait" to true,
                        "resident_address" to true,
                        "resident_city" to true,
                        "resident_state" to true,
                        "resident_postal_code" to true,
                        "resident_country" to true,
                    ),
                    AAMVA_NAMESPACE to mapOf(
                        "resident_county" to true,
                    )
                )
            )
            .build()
    }
}

