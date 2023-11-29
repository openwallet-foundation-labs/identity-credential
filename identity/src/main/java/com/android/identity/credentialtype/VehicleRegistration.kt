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
 * Object containing the metadata of the Vehicle Registration Credential Type
 */

object VehicleRegistration {
    const val MVR_NAMESPACE = "nl.rdw.mekb.1"

    /**
     * Build the Vehicle Registration Credential Type
     */
    fun getCredentialType(): CredentialType {
        return CredentialType.Builder("Vehicle Registration")
            .addMdocCredentialType("nl.rdw.mekb.1")
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("RegistrationInfo", false),
                "registration_info",
                "Vehicle Registration Information",
                "This data element contains the common vehicle registration information, including UN/EU elements, A and H.",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE,
                "issue_date",
                "Issue Date",
                "Date when document was issued",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("RegistrationHolder", false),
                "registration_holder",
                "Vehicle Registration Holder Information ",
                "This data element identifies the holder of the registration certificate",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("BasicVehicleInfo", false),
                "basic_vehicle_info",
                "Basic Vehicle Information",
                "This data element contains the basic vehicle information",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "vin",
                "Vehicle Identification Number",
                "Vehicle Identification Number defined by the vehicle manufacture",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "RegistrationInfo.issuingCountry",
                "Country code",
                "country code",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "RegistrationInfo.competentAuthority",
                "Competent authority",
                "name of the competent authority",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "RegistrationInfo.registrationNumber",
                "UN/EU element A",
                "UN/EU element A",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE,
                "RegistrationInfo.validFrom",
                "Custom EKB element, valid from",
                "Custom EKB element, valid from",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE,
                "RegistrationInfo.validUntil",
                "Custom EKB element, valid until",
                "Custom EKB element, valid until",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("PersonalData", false),
                "RegistrationHolder.holderInfo",
                "Personal data",
                "Personal data",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.NUMBER,
                "RegistrationHolder.ownershipStatus",
                "Ownership status",
                "Ownership status",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "PersonalData.name",
                "Name of the vehicle owner",
                "Name of the vehicle owner",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("Address", false),
                "PersonalData.address",
                "Address of the vehicle owner",
                "Address of the vehicle owner",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Address.streetName",
                "Street name",
                "Street name",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Address.houseNumber",
                "House number",
                "House number",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Address.houseNumberSuffix",
                "House number suffix",
                "House number suffix",
                false,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Address.postalCode",
                "Postal code",
                "Postal code",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Address.placeOfResidence",
                "Place of residence",
                "Place of residence",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("Vehicle", false),
                "BasicVehicleInfo.vehicle",
                "Vehicle",
                "Vehicle",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vehicle.make",
                "Make of the vehicle",
                "Make of the vehicle",
                true,
                MVR_NAMESPACE
            )
            .build()
    }
}