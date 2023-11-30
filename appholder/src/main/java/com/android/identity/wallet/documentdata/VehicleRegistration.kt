package com.android.identity.wallet.documentdata

import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.Options

object VehicleRegistration {
    const val MVR_NAMESPACE = "nl.rdw.mekb.1"
    fun getMdocComplexTypes() = MdocComplexTypes.Builder("nl.rdw.mekb.1")
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "issuingCountry",
            "Country code",
            CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2)
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "competentAuthority",
            "Competent authority",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "registrationNumber",
            "UN/EU element A",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "validFrom",
            "Custom EKB element, valid from",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "validUntil",
            "Custom EKB element, valid until",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_holder"),
            false,
            "holderInfo",
            "Personal data",
            CredentialAttributeType.COMPLEXTYPE
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_holder"),
            false,
            "ownershipStatus",
            "Ownership status",
            CredentialAttributeType.NUMBER
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("holderInfo"),
            false,
            "name",
            "Name of the vehicle owner",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("holderInfo"),
            false,
            "address",
            "Address of the vehicle owner",
            CredentialAttributeType.COMPLEXTYPE
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "streetName",
            "Street name",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "houseNumber",
            "House number",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "houseNumberSuffix",
            "House number suffix",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "postalCode",
            "Postal code",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "placeOfResidence",
            "Place of residence",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("basic_vehicle_info"),
            false,
            "vehicle",
            "Vehicle",
            CredentialAttributeType.COMPLEXTYPE
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("vehicle"),
            false,
            "make",
            "Make of the vehicle",
            CredentialAttributeType.STRING,
        )

        .build()
}