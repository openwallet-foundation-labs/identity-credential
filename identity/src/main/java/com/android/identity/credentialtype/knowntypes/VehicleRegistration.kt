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
                CredentialAttributeType.COMPLEX_TYPE,
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
                CredentialAttributeType.COMPLEX_TYPE,
                "registration_holder",
                "Vehicle Registration Holder Information ",
                "This data element identifies the holder of the registration certificate",
                true,
                MVR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.COMPLEX_TYPE,
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
        .build()
    }
}