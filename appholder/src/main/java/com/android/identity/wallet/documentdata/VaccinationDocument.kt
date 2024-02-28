package com.android.identity.wallet.documentdata

import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.knowntypes.Options

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
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("RA01_test"),
            false,
            "TypeOfTest",
            "Type of Test",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("RA01_test"),
            false,
            "TimeOfTest",
            "Time of Test",
            CredentialAttributeType.DateTime
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("safeEntry_Leisure"),
            false,
            "SeCondFulfilled",
            "Second Fulfilled",
            CredentialAttributeType.Boolean
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("safeEntry_Leisure"),
            false,
            "SeCondType",
            "Second Type",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("safeEntry_Leisure"),
            false,
            "SeCondExpiry",
            "Second Expiry",
            CredentialAttributeType.Date
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "tg",
            "Disease or Agent Targeted",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "vp",
            "Vaccine or Prophylaxis",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "mp",
            "Vaccine Medicinal Product",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "br",
            "Vaccine Brand",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "ma",
            "Manufacturer",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "bn",
            "Batch/Lot Number of the Vaccine",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "dn",
            "Dose Number",
            CredentialAttributeType.Number
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "sd",
            "Total Series of Doses",
            CredentialAttributeType.Number
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "dt",
            "Date of Vaccination",
            CredentialAttributeType.Date
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
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "ap",
            "Administering Professional",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "nx",
            "Due Date of Next Dose",
            CredentialAttributeType.Date
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "is",
            "Certificate Issuer",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "ci",
            "Unique Certificate Identifier (UVCI)",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "pd",
            "Protection Duration",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "vf",
            "Valid From",
            CredentialAttributeType.Date
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "vu",
            "Valid Until",
            CredentialAttributeType.Date
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("pid_PPN", "pid_DL"),
            false,
            "pty",
            "Type of Person Identifier",
            CredentialAttributeType.String
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("pid_PPN", "pid_DL"),
            false,
            "pnr",
            "Unique number for the PTY/PIC/(PIA) combination",
            CredentialAttributeType.String
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
            CredentialAttributeType.String,
        )
        .build()
}