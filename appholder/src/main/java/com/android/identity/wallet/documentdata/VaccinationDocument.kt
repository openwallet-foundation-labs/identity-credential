package com.android.identity.wallet.documentdata

import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.Options

object VaccinationDocument {
    const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
    const val MICOV_VTR_NAMESPACE = "org.micov.vtr.1"
    fun getMdocComplexTypes() = MdocComplexTypes.Builder("org.micov.1")

        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("RA01_test"),
            false,
            "Result",
            "Test Result",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("RA01_test"),
            false,
            "TypeOfTest",
            "Type of Test",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("RA01_test"),
            false,
            "TimeOfTest",
            "Time of Test",
            CredentialAttributeType.DATE_TIME
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("safeEntry_Leisure"),
            false,
            "SeCondFulfilled",
            "Second Fulfilled",
            CredentialAttributeType.BOOLEAN
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("safeEntry_Leisure"),
            false,
            "SeCondType",
            "Second Type",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("safeEntry_Leisure"),
            false,
            "SeCondExpiry",
            "Second Expiry",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "tg",
            "Disease or Agent Targeted",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "vp",
            "Vaccine or Prophylaxis",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "mp",
            "Vaccine Medicinal Product",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "br",
            "Vaccine Brand",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "ma",
            "Manufacturer",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "bn",
            "Batch/Lot Number of the Vaccine",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "dn",
            "Dose Number",
            CredentialAttributeType.NUMBER
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "sd",
            "Total Series of Doses",
            CredentialAttributeType.NUMBER
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "dt",
            "Date of Vaccination",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "co",
            "Country of Vaccination",
            CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2)
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "ao",
            "Administering Organization",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "ap",
            "Administering Professional",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "nx",
            "Due Date of Next Dose",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "is",
            "Certificate Issuer",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "ci",
            "Unique Certificate Identifier (UVCI)",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "pd",
            "Protection Duration",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "vf",
            "Valid From",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "vu",
            "Valid Until",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("pid_PPN", "pid_DL"),
            false,
            "pty",
            "Type of Person Identifier",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("pid_PPN", "pid_DL"),
            false,
            "pnr",
            "Unique number for the PTY/PIC/(PIA) combination",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("pid_PPN", "pid_DL"),
            false,
            "pic",
            "Issuing Country of the PTY",
            CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2)
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("pid_PPN", "pid_DL"),
            false,
            "pia",
            "Issuing Authority of the PTY",
            CredentialAttributeType.STRING,
        )
        .build()
}