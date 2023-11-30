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
            "Test result",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("RA01_test"),
            false,
            "TypeOfTest",
            "Type of test",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("RA01_test"),
            false,
            "TimeOfTest",
            "Time of test",
            CredentialAttributeType.DATE_TIME
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("safeEntry_Leisure"),
            false,
            "SeCondFulfilled",
            "Second fulfilled",
            CredentialAttributeType.BOOLEAN
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("safeEntry_Leisure"),
            false,
            "SeCondType",
            "Second type",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_ATT_NAMESPACE,
            hashSetOf("safeEntry_Leisure"),
            false,
            "SeCondExpiry",
            "Second expiry",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "tg",
            "Disease or agent targeted",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "vp",
            "Vaccine or prophylaxis",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "mp",
            "Vaccine medicinal product",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "br",
            "Vaccine brand",
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
            "Batch/lot number of the vaccine",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "dn",
            "Dose number",
            CredentialAttributeType.NUMBER
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "sd",
            "Total series of doses",
            CredentialAttributeType.NUMBER
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "dt",
            "Date of vaccination",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "co",
            "Country of vaccination",
            CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2)
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "ao",
            "Administering organization",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "ap",
            "Administering professional",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "nx",
            "Due date of next dose",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "is",
            "Certificate issuer",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "ci",
            "Unique certificate identifier (UVCI)",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "pd",
            "Protection duration",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "vf",
            "Valid from",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("v_RA01_1", "v_RA01_2"),
            false,
            "vu",
            "Valid until",
            CredentialAttributeType.DATE
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("pid_PPN", "pid_DL"),
            false,
            "pty",
            "Type of person identifier",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("pid_PPN", "pid_DL"),
            false,
            "pnr",
            "Unique number for the pty/pic/(pia) combination",
            CredentialAttributeType.STRING
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("pid_PPN", "pid_DL"),
            false,
            "pic",
            "Issuing country of the pty",
            CredentialAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2)
        )
        .addDefinition(
            MICOV_VTR_NAMESPACE,
            hashSetOf("pid_PPN", "pid_DL"),
            false,
            "pia",
            "Issuing authority of the pty",
            CredentialAttributeType.STRING,
        )
        .build()
}