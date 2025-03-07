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

package org.multipaz.documenttype.knowntypes

import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentType

/**
 * Object containing the metadata of the Vaccination
 * Document Type.
 */
object VaccinationDocument {
    const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
    const val MICOV_VTR_NAMESPACE = "org.micov.vtr.1"

    /**
     * Build the Vaccination Document Type.
     */
    fun getDocumentType(): DocumentType {
        return DocumentType.Builder("Vaccination Document")
            .addMdocDocumentType("org.micov.1")
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "1D47_vaccinated",
                "Vaccination against Yellow Fever",
                "Attestation that the holder has been fully vaccinated against Yellow Fever",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "RA01_vaccinated",
                "Vaccination against COVID-19",
                "Attestation that the holder has been fully vaccinated against COVID-19",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.ComplexType,
                "RA01_test",
                "Test Event for COVID-19",
                "Attestation that the holder has obtained a negative test for COVID-19",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.ComplexType,
                "safeEntry_Leisure",
                "Safe Entry Indication",
                "Attest that the holder fulfils certain set requirements for safe entry in a leisure context (without disclosing if it is based on vaccination, recovery, or negative test)",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.Picture,
                "fac",
                "Facial Image",
                "Facial Image of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "fni",
                "Family Name Initial",
                "Initial letter of the Family Name of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "gni",
                "Given Name Initial",
                "Initial letter of the Given Name of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.Number,
                "by",
                "Birth Year",
                "Birth Year of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.Number,
                "bm",
                "Birth Month",
                "Birth Month of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.Number,
                "bd",
                "Birth Day",
                "Birth Day of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "fn",
                "Family Name",
                "Family Name of the holder",
                true,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "gn",
                "Given Name",
                "Given Name of the holder",
                true,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.Date,
                "dob",
                "Date of Birth",
                "Date of Birth of the holder",
                true,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.IntegerOptions(Options.SEX_ISO_IEC_5218),
                "sex",
                "Sex",
                "Sex",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.ComplexType,
                "v_RA01_1",
                "RA01 First Vaccination",
                "COVID-19 – first vaccination data",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.ComplexType,
                "v_RA01_2",
                "RA01 Second Vaccination",
                "COVID-19 – second vaccination data",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.ComplexType,
                "pid_PPN",
                "ID with Pasport Number",
                "Unique set of elements identifying the holder by passport number",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                DocumentAttributeType.ComplexType,
                "pid_DL",
                "ID with Driver’s License Number",
                "Unique set of elements identifying the holder by driver’s license number",
                false,
                MICOV_VTR_NAMESPACE
            )
            .build()
    }
}