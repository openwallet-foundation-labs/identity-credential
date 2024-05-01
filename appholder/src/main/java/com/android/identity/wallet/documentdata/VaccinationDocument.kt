package com.android.identity.wallet.documentdata

import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.knowntypes.Options

object VaccinationDocument {
    const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
    const val MICOV_VTR_NAMESPACE = "org.micov.vtr.1"

    fun getMdocComplexTypes() =
        MdocComplexTypes.Builder("org.micov.1")
            .addDefinition(
                MICOV_ATT_NAMESPACE,
                hashSetOf("RA01_test"),
                false,
                "Result",
                "Test Result",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_ATT_NAMESPACE,
                hashSetOf("RA01_test"),
                false,
                "TypeOfTest",
                "Type of Test",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_ATT_NAMESPACE,
                hashSetOf("RA01_test"),
                false,
                "TimeOfTest",
                "Time of Test",
                DocumentAttributeType.DateTime,
            )
            .addDefinition(
                MICOV_ATT_NAMESPACE,
                hashSetOf("safeEntry_Leisure"),
                false,
                "SeCondFulfilled",
                "Second Fulfilled",
                DocumentAttributeType.Boolean,
            )
            .addDefinition(
                MICOV_ATT_NAMESPACE,
                hashSetOf("safeEntry_Leisure"),
                false,
                "SeCondType",
                "Second Type",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_ATT_NAMESPACE,
                hashSetOf("safeEntry_Leisure"),
                false,
                "SeCondExpiry",
                "Second Expiry",
                DocumentAttributeType.Date,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "tg",
                "Disease or Agent Targeted",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "vp",
                "Vaccine or Prophylaxis",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "mp",
                "Vaccine Medicinal Product",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "br",
                "Vaccine Brand",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "ma",
                "Manufacturer",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "bn",
                "Batch/Lot Number of the Vaccine",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "dn",
                "Dose Number",
                DocumentAttributeType.Number,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "sd",
                "Total Series of Doses",
                DocumentAttributeType.Number,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "dt",
                "Date of Vaccination",
                DocumentAttributeType.Date,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "co",
                "Country of Vaccination",
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "ao",
                "Administering Organization",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "ap",
                "Administering Professional",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "nx",
                "Due Date of Next Dose",
                DocumentAttributeType.Date,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "is",
                "Certificate Issuer",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "ci",
                "Unique Certificate Identifier (UVCI)",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "pd",
                "Protection Duration",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "vf",
                "Valid From",
                DocumentAttributeType.Date,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("v_RA01_1", "v_RA01_2"),
                false,
                "vu",
                "Valid Until",
                DocumentAttributeType.Date,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("pid_PPN", "pid_DL"),
                false,
                "pty",
                "Type of Person Identifier",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("pid_PPN", "pid_DL"),
                false,
                "pnr",
                "Unique number for the PTY/PIC/(PIA) combination",
                DocumentAttributeType.String,
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("pid_PPN", "pid_DL"),
                false,
                "pic",
                "Issuing Country of the PTY",
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
            )
            .addDefinition(
                MICOV_VTR_NAMESPACE,
                hashSetOf("pid_PPN", "pid_DL"),
                false,
                "pia",
                "Issuing Authority of the PTY",
                DocumentAttributeType.String,
            )
            .build()
}
