package com.android.identity.wallet.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.MdocDataElement
import com.android.identity.wallet.R

object SampleDataProvider {
    const val MDL_NAMESPACE = "org.iso.18013.5.1"
    const val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"
    const val MVR_NAMESPACE = "nl.rdw.mekb.1"
    const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
    const val MICOV_VTR_NAMESPACE = "org.micov.vtr.1"
    const val EUPID_NAMESPACE = "eu.europa.ec.eudiw.pid.1"

    fun getSampleValue(
        context: Context,
        namespace: String,
        dataElement: MdocDataElement,
        dataElementParent: MdocDataElement? = null
    ): Any? {
        return when (namespace) {
            MDL_NAMESPACE -> when (dataElement.attribute.identifier) {
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

                else -> defaultValue(dataElement)
            }

            AAMVA_NAMESPACE -> when (dataElement.attribute.identifier) {
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
                "DomesticVehicleClass.domestic_vehicle_class_code" -> "B"
                "DomesticVehicleClass.domestic_vehicle_class_description" -> "Light vehicles"
                "DomesticVehicleClass.issue_date" -> "2021-04-18"
                "DomesticVehicleClass.expiry_date" -> "2026-04-18"
                else -> defaultValue(dataElement)
            }

            MVR_NAMESPACE -> when (dataElement.attribute.identifier) {
                "issue_date" -> "2021-04-18"
                "vin" -> "1M8GDM9AXKP042788"
                "RegistrationInfo.issuingCountry" -> "NL"
                "RegistrationInfo.competentAuthority" -> "RDW"
                "RegistrationInfo.registrationNumber" -> "E-01-23"
                "RegistrationInfo.validFrom" -> "2021-04-19"
                "RegistrationInfo.validUntil" -> "2023-04-20"
                "RegistrationHolder.ownershipStatus" -> 2
                "PersonalData.name" -> "Erika"
                "Address.streetName" -> "Teststraat"
                "Address.houseNumber" -> "86"
                "Address.houseNumberSuffix" -> "A"
                "Address.postalCode" -> "1234 AA"
                "Address.placeOfResidence" -> "Samplecity"
                "Vehicle.make" -> "Dummymobile"
                else -> defaultValue(dataElement)
            }

            MICOV_ATT_NAMESPACE -> when (dataElement.attribute.identifier) {
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
                "Test.Result" -> "260415000"
                "Test.TypeOfTest" -> "LP6464-4"
                "Test.TimeOfTest" -> "2021-10-12"
                "SafeEntry.SeCondFulfilled" -> true
                "SafeEntry.SeCondType" -> "leisure"
                "SafeEntry.SeCondExpiry" -> "2021-10-13"
                else -> defaultValue(dataElement)
            }

            MICOV_VTR_NAMESPACE -> when (dataElement.attribute.identifier) {
                "fn" -> "Mustermann"
                "gn" -> "Erika"
                "dob" -> "1964-08-12"
                "sex" -> 2
                "Vac.tg" -> "840539006"
                "Vac.vp" -> "1119349007"
                "Vac.mp" -> "EU/1/20/1528"
                "Vac.br" -> "Sample brand"
                "Vac.ma" -> "ORG-100030215"
                "Vac.bn" -> when (dataElementParent != null && dataElementParent.attribute.identifier == "v_RA01_1") {
                    true -> "B12345/67"
                    else -> "B67890/12"
                }

                "Vac.dn" -> when (dataElementParent != null && dataElementParent.attribute.identifier == "v_RA01_1") {
                    true -> 1
                    else -> 2
                }

                "Vac.sd" -> 2
                "Vac.dt" -> when (dataElementParent != null && dataElementParent.attribute.identifier == "v_RA01_1") {
                    true -> "2021-04-08"
                    else -> "2021-05-18"
                }

                "Vac.co" -> "US"
                "Vac.ao" -> "RHI"
                "Vac.ap" -> ""
                "Vac.nx" -> "2021-05-20"
                "Vac.is" -> "SC17"
                "Vac.ci" -> when (dataElementParent != null && dataElementParent.attribute.identifier == "v_RA01_1") {
                    true -> "URN:UVCI:01:UT:187/37512422923"
                    else -> "URN:UVCI:01:UT:187/37512533044"
                }

                "Vac.pd" -> ""
                "Vac.vf" -> ""
                "Vac.vu" -> ""
                "Pid.pty" -> when (dataElementParent != null && dataElementParent.attribute.identifier == "pid_PPN") {
                    true -> "PPN"
                    else -> "DL"
                }

                "Pid.pnr" -> when (dataElementParent != null && dataElementParent.attribute.identifier == "pid_PPN") {
                    true -> "476284728"
                    else -> "987654321"
                }

                "Pid.pic" -> "US"
                "Pid.pia" -> ""
                else -> defaultValue(dataElement)
            }

            EUPID_NAMESPACE -> when (dataElement.attribute.identifier) {
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
                else -> defaultValue(dataElement)
            }

            else -> defaultValue(dataElement)
        }
    }

    fun getSampleValue(namespace: String, dataElement: MdocDataElement, index: Int): Any? {
        return when (namespace) {
            MDL_NAMESPACE -> when (dataElement.attribute.identifier) {
                "DrivingPrivilege.vehicle_category_code" -> when (index) {
                    0 -> "A"
                    else -> "B"
                }

                "DrivingPrivilege.issue_date" -> when (index) {
                    0 -> "2018-08-09"
                    else -> "2017-02-23"
                }

                "DrivingPrivilege.expiry_date" -> when (index) {
                    0 -> "2024-10-20"
                    else -> "2024-10-20"
                }

                "Code.code" -> when (index) {
                    0 -> "S01"
                    else -> "S02"
                }

                "Code.sign" -> when (index) {
                    0 -> "<="
                    else -> "="
                }

                "Code.value" -> when (index) {
                    0 -> "2500"
                    else -> "8"
                }

                else -> defaultValue(dataElement)
            }

            AAMVA_NAMESPACE -> when (dataElement.attribute.identifier) {
                "DomesticVehicleRestriction.domestic_vehicle_restriction_code" -> when (index) {
                    0 -> "B"
                    else -> "C"
                }

                "DomesticVehicleRestriction.domestic_vehicle_restriction_description" -> when (index) {
                    0 -> "Corrective lenses must be worn"
                    else -> "Mechanical Aid (special brakes, hand controls, or other adaptive devices)"
                }

                "DomesticVehicleEndorsement.domestic_vehicle_endorsement_code" -> when (index) {
                    0 -> "P"
                    else -> "S"
                }

                "DomesticVehicleEndorsement.domestic_vehicle_endorsement_description" -> when (index) {
                    0 -> "Passenger"
                    else -> "School Bus"
                }

                else -> defaultValue(dataElement)
            }

            else -> defaultValue(dataElement)
        }
    }

    fun getArrayLength(namespace: String, dataElement: MdocDataElement): Int {
        return when (namespace) {
            MDL_NAMESPACE -> when (dataElement.attribute.identifier) {
                "driving_privileges" -> 2
                "DrivingPrivilege.codes" -> 2
                else -> 2
            }

            AAMVA_NAMESPACE -> when (dataElement.attribute.identifier) {
                "domestic_driving_privileges" -> 1
                "DomesticDrivingPrivilege.domestic_vehicle_restrictions" -> 2
                "DomesticDrivingPrivilege.domestic_vehicle_endorsements" -> 2
                else -> 2
            }

            else -> 2
        }
    }

    private fun defaultValue(dataElement: MdocDataElement): Any? {
        return when (dataElement.attribute.type) {
            is CredentialAttributeType.STRING -> "-"
            is CredentialAttributeType.NUMBER -> 0
            is CredentialAttributeType.DATE,
            is CredentialAttributeType.DATE_TIME -> "2100-01-01"

            is CredentialAttributeType.PICTURE -> Bitmap.createBitmap(
                200,
                200,
                Bitmap.Config.ARGB_8888
            )

            is CredentialAttributeType.BOOLEAN -> false
            is CredentialAttributeType.StringOptions,
            is CredentialAttributeType.IntegerOptions,
            is CredentialAttributeType.ComplexType -> null
        }
    }
}