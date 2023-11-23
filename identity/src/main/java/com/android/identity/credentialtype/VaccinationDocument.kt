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
 * Object containing the metadata of the Vaccination Document Credential Type
 */
object VaccinationDocument {
    const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
    const val MICOV_VTR_NAMESPACE = "org.micov.vtr.1"

    /**
     * Build the Vaccination Document Credential Type
     */
    fun getCredentialType(): CredentialType {
        return CredentialType.Builder("Vaccination Document")
            .addMdocCredentialType("org.micov.1")
            .addMdocAttribute(
                CredentialAttributeType.BOOLEAN,
                "1D47_vaccinated",
                "Indication of vaccination against Yellow Fever",
                "Attestation that the holder has been fully vaccinated against Yellow Fever",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.BOOLEAN,
                "RA01_vaccinated",
                "Indication of vaccination against COVID-19",
                "Attestation that the holder has been fully vaccinated against COVID-19",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("Test", false),
                "RA01_test",
                "Indication of test event for COVID-19",
                "Attestation that the holder has obtained a negative test for COVID-19",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("SafeEntry", false),
                "safeEntry_Leisure",
                "Safe entry indication",
                "Attest that the holder fulfils certain set requirements for safe entry in a leisure context (without disclosing if it is based on vaccination, recovery, or negative test)",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.PICTURE,
                "fac",
                "Facial Image",
                "Facial Image of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "fni",
                "Family Name Initial",
                "Initial letter of the Family Name of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "gni",
                "Given Name Initial",
                "Initial letter of the Given Name of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.NUMBER,
                "by",
                "Birth Year",
                "Birth Year of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.NUMBER,
                "bm",
                "Birth Month",
                "Birth Month of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.NUMBER,
                "bd",
                "Birth Day",
                "Birth Day of the holder",
                false,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Test.Result",
                "Test result",
                "Test result",
                true,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Test.TypeOfTest",
                "Type of test",
                "Type of test, e.g. PCR test",
                true,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE_TIME,
                "Test.TimeOfTest",
                "Time of test",
                "Time of test",
                true,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.BOOLEAN,
                "SafeEntry.SeCondFulfilled",
                "Second fulfilled",
                "Second fulfilled",
                true,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "SafeEntry.SeCondType",
                "Second type",
                "Second type",
                true,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE,
                "SafeEntry.SeCondExpiry",
                "Second expiry",
                "Second expiry",
                true,
                MICOV_ATT_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "fn",
                "Family Name",
                "Family Name of the holder",
                true,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "gn",
                "Given Name",
                "Given Name of the holder",
                true,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE,
                "dob",
                "Date of Birth",
                "Date of Birth of the holder",
                true,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.IntegerOptions(Options.SEX_ISO_IEC_5218),
                "sex",
                "Sex",
                "Sex",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("Vac", false),
                "v_RA01_1",
                "First vaccination against RA01",
                "COVID-19 – first vaccination data",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("Vac", false),
                "v_RA01_2",
                "Second vaccination against RA01",
                "COVID-19 – second vaccination data",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("Pid", false),
                "pid_PPN",
                "ID with pasport number",
                "Unique set of elements identifying the holder by passport number",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.ComplexType("Pid", false),
                "pid_DL",
                "ID with driver’s license number",
                "Unique set of elements identifying the holder by driver’s license number",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vac.tg",
                "Disease or agent targeted",
                "Disease or agent targeted",
                true,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vac.vp",
                "Vaccine or prophylaxis",
                "Vaccine or prophylaxis",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vac.mp",
                "Vaccine medicinal product",
                "Vaccine medicinal product",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vac.br",
                "Vaccine brand",
                "Vaccine brand",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vac.ma",
                "Marketing authorization holder / Manufacturer",
                "Marketing authorization holder / Manufacturer",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vac.bn",
                "Batch number or lot number of the vaccine",
                "Batch number or lot number of the vaccine",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.NUMBER,
                "Vac.dn",
                "Dose number",
                "Dose number",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.NUMBER,
                "Vac.sd",
                "Total series of doses",
                "Total series of doses",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE,
                "Vac.dt",
                "Date of vaccination",
                "Date of vaccination",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "Vac.co",
                "Country of vaccination",
                "Country of vaccination",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vac.ao",
                "Administering organization",
                "Administering organization",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vac.ap",
                "Administering professional",
                "Administering professional",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE,
                "Vac.nx",
                "Due date of next dose, if required",
                "Due date of next dose, if required",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vac.is",
                "Certificate issuer",
                "Certificate issuer",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vac.ci",
                "Unique certificate identifier (UVCI)",
                "Unique certificate identifier (UVCI)",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Vac.pd",
                "Protection duration",
                "Protection duration",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE,
                "Vac.vf",
                "Valid from",
                "Valid from",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.DATE,
                "Vac.vu",
                "Valid until",
                "Valid until",
                false,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Pid.pty",
                "type of person identifier",
                "type of person identifier",
                true,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Pid.pnr",
                "unique number for the pty/pic or pty/pic/pia combination",
                "unique number for the pty/pic or pty/pic/pia combination",
                true,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "Pid.pic",
                "Issuing country of the pty.",
                "Issuing country of the pty.",
                true,
                MICOV_VTR_NAMESPACE
            )
            .addMdocAttribute(
                CredentialAttributeType.STRING,
                "Pid.pia",
                "Issuing authority of the pty",
                "Issuing authority of the pty",
                false,
                MICOV_VTR_NAMESPACE
            )
            .build()
    }
}