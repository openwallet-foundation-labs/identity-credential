package com.android.identity.wallet.documentdata

import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.knowntypes.Options

object VehicleRegistration {
    const val MVR_NAMESPACE = "nl.rdw.mekb.1"
    fun getMdocComplexTypes() = MdocComplexTypes.Builder("nl.rdw.mekb.1")
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "issuingCountry",
            "Country Code",
            DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2)
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "competentAuthority",
            "Competent Authority",
            DocumentAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "registrationNumber",
            "UN/EU Element A",
            DocumentAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "validFrom",
            "Custom EKB Element, Valid From",
            DocumentAttributeType.Date
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_info"),
            false,
            "validUntil",
            "Custom EKB Element, Valid Until",
            DocumentAttributeType.Date
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_holder"),
            false,
            "holderInfo",
            "Personal Data",
            DocumentAttributeType.ComplexType
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("registration_holder"),
            false,
            "ownershipStatus",
            "Ownership Status",
            DocumentAttributeType.Number
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("holderInfo"),
            false,
            "name",
            "Name of the Vehicle Owner",
            DocumentAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("holderInfo"),
            false,
            "address",
            "Address of the Vehicle Owner",
            DocumentAttributeType.ComplexType
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "streetName",
            "Street Name",
            DocumentAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "houseNumber",
            "House Number",
            DocumentAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "houseNumberSuffix",
            "House Number Suffix",
            DocumentAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "postalCode",
            "Postal Code",
            DocumentAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("address"),
            false,
            "placeOfResidence",
            "Place of Residence",
            DocumentAttributeType.String
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("basic_vehicle_info"),
            false,
            "vehicle",
            "Vehicle",
            DocumentAttributeType.ComplexType
        )
        .addDefinition(
            MVR_NAMESPACE,
            hashSetOf("vehicle"),
            false,
            "make",
            "Make of the Vehicle",
            DocumentAttributeType.String,
        )

        .build()
}