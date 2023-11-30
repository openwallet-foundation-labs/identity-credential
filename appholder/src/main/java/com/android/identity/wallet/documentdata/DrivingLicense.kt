package com.android.identity.wallet.documentdata

import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.StringOption

object DrivingLicense {
    const val MDL_NAMESPACE = "org.iso.18013.5.1"
    const val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"
    fun getMdocComplexTypes() = MdocComplexTypes.Builder("org.iso.18013.5.1.mDL")
        .addDefinition(
            MDL_NAMESPACE,
            hashSetOf("driving_privileges"),
            true,
            "vehicle_category_code",
            "Vehicle category code",
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
            )
        )
        .addDefinition(
            MDL_NAMESPACE,
            hashSetOf("driving_privileges"),
            true,
            "issue_date",
            "Date of issue",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MDL_NAMESPACE,
            hashSetOf("driving_privileges"),
            true,
            "expiry_date",
            "Date of expiry",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MDL_NAMESPACE,
            hashSetOf("driving_privileges"),
            true,
            "codes",
            "Codes of driving privileges",
            CredentialAttributeType.COMPLEXTYPE,
        )
        // details of DrivingPrivilege.codes
        .addDefinition(
            MDL_NAMESPACE,
            hashSetOf("codes"),
            true,
            "code",
            "Code",
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
            )
        )
        .addDefinition(
            MDL_NAMESPACE,
            hashSetOf("codes"),
            true,
            "sign",
            "Sign",
            CredentialAttributeType.StringOptions(
                listOf(
                    StringOption(null, "(not set)"),
                    StringOption("=", "Equals (=)"),
                    StringOption(">", "Greater than (>)"),
                    StringOption("<", "Less than (<)"),
                    StringOption(">=", "Greater than or equal to (≥)"),
                    StringOption("<=", "Less than or equal to (≤)")
                )
            )
        )
        .addDefinition(
            MDL_NAMESPACE,
            hashSetOf("codes"),
            true,
            "value",
            "Value",
            CredentialAttributeType.STRING
        ).
            // details of domestic_driving_privileges
        addDefinition(
            AAMVA_NAMESPACE,
            hashSetOf("domestic_driving_privileges"),
            true,
            "domestic_vehicle_class",
            "Domestic vehicle class",
            CredentialAttributeType.COMPLEXTYPE,
        )
        .addDefinition(
            AAMVA_NAMESPACE,
            hashSetOf("domestic_driving_privileges"),
            true,
            "domestic_vehicle_restrictions",
            "Domestic vehicle restrictions",
            CredentialAttributeType.COMPLEXTYPE
        )
        .addDefinition(
            AAMVA_NAMESPACE,
            hashSetOf("domestic_driving_privileges"),
            true,
            "domestic_vehicle_endorsements",
            "Domestic vehicle endorsements",
            CredentialAttributeType.COMPLEXTYPE
        )
        // details of DomesticDrivingPrivilege.domestic_vehicle_class
        .addDefinition(
            AAMVA_NAMESPACE,
            hashSetOf("domestic_vehicle_class"),
            false,
            "domestic_vehicle_class_code",
            "Domestic vehicle class code",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            AAMVA_NAMESPACE,
            hashSetOf("domestic_vehicle_class"),
            false,
            "domestic_vehicle_class_description",
            "Domestic vehicle class description",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            AAMVA_NAMESPACE,
            hashSetOf("domestic_vehicle_class"),
            false,
            "issue_date",
            "Date of issue",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            AAMVA_NAMESPACE,
            hashSetOf("domestic_vehicle_class"),
            false,
            "expiry_date",
            "Date of expiry",
            CredentialAttributeType.DATE
        )
        // details of DomesticDrivingPrivilege.domestic_vehicle_restrictions
        .addDefinition(
            AAMVA_NAMESPACE,
            hashSetOf("domestic_vehicle_restrictions"),
            true,
            "domestic_vehicle_restriction_code",
            "Restriction code",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            AAMVA_NAMESPACE,
            hashSetOf("domestic_vehicle_restrictions"),
            true,
            "domestic_vehicle_restriction_description",
            "Vehicle category description",
            CredentialAttributeType.STRING
        )
        // details of DomesticDrivingPrivilege.domestic_vehicle_endorsements
        .addDefinition(
            AAMVA_NAMESPACE,
            hashSetOf("domestic_vehicle_endorsements"),
            true,
            "domestic_vehicle_endorsement_code",
            "Endorsement code",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            AAMVA_NAMESPACE,
            hashSetOf("domestic_vehicle_endorsements"),
            true,
            "domestic_vehicle_endorsement_description",
            "Vehicle endorsement description",
            CredentialAttributeType.STRING
        )
        .build()
}