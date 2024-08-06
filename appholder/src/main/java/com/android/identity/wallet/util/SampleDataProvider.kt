package com.android.identity.wallet.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.wallet.R

object SampleDataProvider {
    const val MDL_NAMESPACE = "org.iso.18013.5.1"
    const val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"
    const val MVR_NAMESPACE = "nl.rdw.mekb.1"
    const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
    const val MICOV_VTR_NAMESPACE = "org.micov.vtr.1"
    const val EUPID_NAMESPACE = "eu.europa.ec.eudi.pid.1"

    fun getSampleValue(
        context: Context,
        namespace: String,
        identifier: String,
        type: DocumentAttributeType,
        identifierParent: String? = null
    ): Any? {
        return when (namespace) {
            MDL_NAMESPACE -> when (identifier) {
                "family_name" -> "Mustermann"
                "given_name" -> "Erika"
                "birth_date" -> "1971-09-01"
                "issue_date" -> "2021-04-18"
                "expiry_date" -> "2026-04-18"
                "issuing_country" -> "US"
                "issuing_authority" -> "Google"
                "document_number" -> "987654321"
                "portrait" -> BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.img_erika_portrait
                )

                "un_distinguishing_sign" -> "USA"
                "administrative_number" -> "123456789"
                "sex" -> 2
                "height" -> 175
                "weight" -> 68
                "eye_colour" -> "blue"
                "hair_colour" -> "blond"
                "birth_place" -> "Sample City"
                "resident_address" -> "Sample address"
                "portrait_capture_date" -> "2021-04-18"
                "age_in_years" -> 52
                "age_birth_year" -> 1971
                "age_over_18" -> true
                "age_over_21" -> true
                "age_over_25" -> true
                "age_over_62" -> false
                "age_over_65" -> false
                "issuing_jurisdiction" -> "Sample issuing jurisdiction"
                "nationality" -> "US"
                "resident_city" -> "Sample City"
                "resident_state" -> "Sample State"
                "resident_postal_code" -> "18013"
                "resident_country" -> "US"
                "family_name_national_character" -> "Бабіак"
                "given_name_national_character" -> "Ерика"
                "signature_usual_mark" -> BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.img_erika_signature
                )

                "biometric_template_face",
                "biometric_template_finger",
                "biometric_template_signature_sign",
                "biometric_template_iris" -> Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)

                else -> defaultValue(type)
            }

            AAMVA_NAMESPACE -> when (identifier) {
                "name_suffix" -> "SR"
                "organ_donor" -> 1
                "veteran" -> null
                "family_name_truncation" -> "N"
                "given_name_truncation" -> "N"
                "aka_family_name" -> "Muster"
                "aka_given_name" -> "Erik"
                "aka_suffix" -> "JR"
                "weight_range" -> 3
                "race_ethnicity" -> "W"
                "DHS_compliance" -> "F"
                "DHS_temporary_lawful_status" -> null
                "EDL_credential" -> null
                "resident_county" -> "123"
                "hazmat_endorsement_expiration_date" -> "2026-04-18"
                "sex" -> 2
                "audit_information" -> "Sample auditor"
                "aamva_version" -> 2
                "domestic_vehicle_class_code" -> "B"
                "domestic_vehicle_class_description" -> "Light vehicles"
                "issue_date" -> "2021-04-18"
                "expiry_date" -> "2026-04-18"
                else -> defaultValue(type)
            }

            MVR_NAMESPACE -> when (identifier) {
                "issue_date" -> "2021-04-18"
                "vin" -> "1M8GDM9AXKP042788"
                "issuingCountry" -> "NL"
                "competentAuthority" -> "RDW"
                "registrationNumber" -> "E-01-23"
                "validFrom" -> "2021-04-19"
                "validUntil" -> "2023-04-20"
                "ownershipStatus" -> 2
                "name" -> "Erika"
                "streetName" -> "Teststraat"
                "houseNumber" -> "86"
                "houseNumberSuffix" -> "A"
                "postalCode" -> "1234 AA"
                "placeOfResidence" -> "Samplecity"
                "make" -> "Dummymobile"
                else -> defaultValue(type)
            }

            MICOV_ATT_NAMESPACE -> when (identifier) {
                "1D47_vaccinated" -> true
                "RA01_vaccinated" -> true
                "fac" -> BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.img_erika_portrait
                )

                "fni" -> "M"
                "gni" -> "E"
                "by" -> 1964
                "bm" -> 8
                "bd" -> 12
                "Result" -> "260415000"
                "TypeOfTest" -> "LP6464-4"
                "TimeOfTest" -> "2021-10-12"
                "SeCondFulfilled" -> true
                "SeCondType" -> "leisure"
                "SeCondExpiry" -> "2021-10-13"
                else -> defaultValue(type)
            }

            MICOV_VTR_NAMESPACE -> when (identifier) {
                "fn" -> "Mustermann"
                "gn" -> "Erika"
                "dob" -> "1964-08-12"
                "sex" -> 2
                "tg" -> "840539006"
                "vp" -> "1119349007"
                "mp" -> "EU/1/20/1528"
                "br" -> "Sample brand"
                "ma" -> "ORG-100030215"
                "bn" -> when (identifierParent != null && identifierParent == "v_RA01_1") {
                    true -> "B12345/67"
                    else -> "B67890/12"
                }

                "dn" -> when (identifierParent != null && identifierParent == "v_RA01_1") {
                    true -> 1
                    else -> 2
                }

                "sd" -> 2
                "dt" -> when (identifierParent != null && identifierParent == "v_RA01_1") {
                    true -> "2021-04-08"
                    else -> "2021-05-18"
                }

                "co" -> "US"
                "ao" -> "RHI"
                "ap" -> ""
                "nx" -> "2021-05-20"
                "is" -> "SC17"
                "ci" -> when (identifierParent != null && identifierParent == "v_RA01_1") {
                    true -> "URN:UVCI:01:UT:187/37512422923"
                    else -> "URN:UVCI:01:UT:187/37512533044"
                }

                "pd" -> ""
                "vf" -> "2021-05-27"
                "vu" -> "2022-05-27"
                "pty" -> when (identifierParent != null && identifierParent == "pid_PPN") {
                    true -> "PPN"
                    else -> "DL"
                }

                "pnr" -> when (identifierParent != null && identifierParent == "pid_PPN") {
                    true -> "476284728"
                    else -> "987654321"
                }

                "pic" -> "US"
                "pia" -> ""
                else -> defaultValue(type)
            }

            EUPID_NAMESPACE -> when (identifier) {
                "family_name" -> "Mustermann"
                "family_name_national_characters" -> "Бабіак"
                "given_name" -> "Erika"
                "given_name_national_characters" -> "Ерика"
                "birth_date" -> "1986-03-14"
                "persistent_id" -> "0128196532"
                "family_name_birth" -> "Mustermann"
                "family_name_birth_national_characters" -> "Бабіак"
                "given_name_birth" -> "Erika"
                "given_name_birth_national_characters" -> "Ерика"
                "birth_place" -> "Place of birth"
                "resident_address" -> "Resident address"
                "resident_city" -> "Resident City"
                "resident_postal_code" -> "Resident postal code"
                "resident_state" -> "Resident state"
                "resident_country" -> "Resident country"
                "gender" -> "female"
                "nationality" -> "NL"
                "portrait" -> BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.img_erika_portrait
                )
                "portrait_capture_date" -> "2022-11-14"
                "biometric_template_finger" -> Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
                "age_over_13" -> true
                "age_over_16" -> true
                "age_over_18" -> true
                "age_over_21" -> true
                "age_over_60" -> false
                "age_over_65" -> false
                "age_over_68" -> false
                "age_in_years" -> 37
                "age_birth_year" -> 1986
                else -> defaultValue(type)
            }

            else -> defaultValue(type)
        }
    }

    fun getSampleValue(namespace: String, identifier: String, type: DocumentAttributeType, index: Int): Any? {
        return when (namespace) {
            MDL_NAMESPACE -> when (identifier) {
                "vehicle_category_code" -> when (index) {
                    0 -> "A"
                    else -> "B"
                }

                "issue_date" -> when (index) {
                    0 -> "2018-08-09"
                    else -> "2017-02-23"
                }

                "expiry_date" -> when (index) {
                    0 -> "2024-10-20"
                    else -> "2024-10-20"
                }

                "code" -> when (index) {
                    0 -> "S01"
                    else -> "S02"
                }

                "sign" -> when (index) {
                    0 -> "<="
                    else -> "="
                }

                "value" -> when (index) {
                    0 -> "2500"
                    else -> "8"
                }

                else -> defaultValue(type)
            }

            AAMVA_NAMESPACE -> when (identifier) {
                "domestic_vehicle_restriction_code" -> when (index) {
                    0 -> "B"
                    else -> "C"
                }

                "domestic_vehicle_restriction_description" -> when (index) {
                    0 -> "Corrective lenses must be worn"
                    else -> "Mechanical Aid (special brakes, hand controls, or other adaptive devices)"
                }

                "domestic_vehicle_endorsement_code" -> when (index) {
                    0 -> "P"
                    else -> "S"
                }

                "domestic_vehicle_endorsement_description" -> when (index) {
                    0 -> "Passenger"
                    else -> "School Bus"
                }

                else -> defaultValue(type)
            }

            else -> defaultValue(type)
        }
    }

    fun getArrayLength(namespace: String, identifier: String): Int {
        return when (namespace) {
            MDL_NAMESPACE -> when (identifier) {
                "driving_privileges" -> 2
                "codes" -> 2
                else -> 2
            }

            AAMVA_NAMESPACE -> when (identifier) {
                "domestic_driving_privileges" -> 1
                "domestic_vehicle_restrictions" -> 2
                "domestic_vehicle_endorsements" -> 2
                else -> 2
            }

            else -> 2
        }
    }

    private fun defaultValue(type: DocumentAttributeType): Any? {
        return when (type) {
            is DocumentAttributeType.String -> "-"
            is DocumentAttributeType.Number -> 0
            is DocumentAttributeType.Date,
            is DocumentAttributeType.DateTime -> "2100-01-01"

            is DocumentAttributeType.Picture -> Bitmap.createBitmap(
                200,
                200,
                Bitmap.Config.ARGB_8888
            )

            is DocumentAttributeType.Boolean -> false
            is DocumentAttributeType.StringOptions,
            is DocumentAttributeType.IntegerOptions,
            is DocumentAttributeType.ComplexType -> null
        }
    }
}