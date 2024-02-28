package com.android.identity.wallet.documentdata

import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.knowntypes.Options

object VehicleRegistration {
    const val MVR_NAMESPACE = "nl.rdw.mekb.1"
    fun getMdocComplexTypes() = MdocComplexTypes.Builder("nl.rdw.mekb.1")
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "issuingCountry",
            "Country Code",
            CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2)
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "competentAuthority",
            "Competent Authority",
            CredentialAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "registrationNumber",
            "UN/EU Element A",
            CredentialAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "validFrom",
            "Custom EKB Element, Valid From",
            CredentialAttributeType.Date
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "validUntil",
            "Custom EKB Element, Valid Until",
            CredentialAttributeType.Date
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_holder"),
            false,
            "holderInfo",
            "Personal Data",
            CredentialAttributeType.ComplexType
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_holder"),
            false,
            "ownershipStatus",
            "Ownership Status",
            CredentialAttributeType.Number
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("holderInfo"),
            false,
            "name",
            "Name of the Vehicle Owner",
            CredentialAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("holderInfo"),
            false,
            "address",
            "Address of the Vehicle Owner",
            CredentialAttributeType.ComplexType
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "streetName",
            "Street Name",
            CredentialAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "houseNumber",
            "House Number",
            CredentialAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "houseNumberSuffix",
            "House Number Suffix",
            CredentialAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "postalCode",
            "Postal Code",
            CredentialAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "placeOfResidence",
            "Place of Residence",
            CredentialAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("basic_vehicle_info"),
            false,
            "vehicle",
            "Vehicle",
            CredentialAttributeType.ComplexType
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("vehicle"),
            false,
            "make",
            "Make of the Vehicle",
            CredentialAttributeType.String,
        )

        .build()
}