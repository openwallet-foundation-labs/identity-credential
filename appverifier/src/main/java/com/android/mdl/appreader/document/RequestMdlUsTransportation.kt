package com.android.mdl.appreader.document

import com.android.mdl.appreader.R

object RequestMdlUsTransportation : RequestDocument() {
    override val docType = "org.iso.18013.5.1.mDL"
    override val nameSpace = "org.iso.18013.5.1"
    private val nameSpaceAamva = "org.iso.18013.5.1.aamva"
    override val dataItems = DataItems.values().asList()

    override fun getItemsToRequest(): Map<String, Map<String, Boolean>> {
        return mapOf(
            Pair(nameSpace,
                mapOf(Pair("sex", false),
                      Pair("portrait", false),
                      Pair("given_name", false),
                      Pair("issue_date", false),
                      Pair("expiry_date", false),
                      Pair("family_name", false),
                      Pair("document_number", false),
                      Pair("issuing_authority", false))),
            Pair(nameSpaceAamva,
                mapOf(Pair("DHS_compliance", false),
                      Pair("EDL_credential", false)))
        )
    }

    enum class DataItems(override val identifier: String, override val stringResourceId: Int) :
        RequestDataItem {
        // AAMVA_VERSION("aamva_version", R.string.aamva_version),
        // AKA_FAMILY_NAME_V2("aka_family_name.v2", R.string.aka_family_name_v2),
        // AKA_GIVEN_NAME_V2("aka_given_name.v2", R.string.aka_given_name_v2),
        // AKA_SUFFIX("aka_suffix", R.string.aka_suffix),
        // CDL_INDICATOR("CDL_indicator", R.string.cdl_indicator),
        // DHS_COMPLIANCE("DHS_compliance", R.string.dhs_compliance),
        // DHS_COMPLIANCE_TEXT("DHS_compliance_text", R.string.dhs_compliance_text),
        // DHS_TEMPORARY_LAWFUL_STATUS("DHS_temporary_lawful_status", 
        //     R.string.dhs_temporary_lawful_status),
        // EDL_CREDENTIAL("EDL_credential", R.string.edl_credential),
        // FAMILY_NAME_TRUNCATION("family_name_truncation", R.string.family_name_truncation),
        // GIVEN_NAME_TRUNCATION("given_name_truncation", R.string.given_name_truncation),
        // HAZMAT_ENDORSEMENT_EXPIRATION_DATE("hazmat_endorsement_expiration_date", 
        //     R.string.hazmat_endorsement_expiration_date),
        // NAME_SUFFIX("name_suffix", R.string.name_suffix),
        // ORGAN_DONOR("organ_donor", R.string.organ_donor),
        // RACE_ETHNICITY("race_ethnicity", R.string.race_ethnicity),
        // RESIDENT_COUNTY("resident_county", R.string.resident_county),
        // SEX("", R.string.sex),
        // VETERAN("veteran", R.string.veteran),
        // WEIGHT_RANGE("weight_range", R.string.weight_range),

        SEX("", R.string.sex),
        PORTRAIT("portrait", R.string.portrait),
        GIVEN_NAME("given_name", R.string.given_name),
        BIRTH_DATE("birth_date", R.string.birth_date),
        ISSUE_DATE("issue_date", R.string.issue_date),
        EXPIRY_DATE("expiry_date", R.string.expiry_date),
        FAMILY_NAME("family_name", R.string.family_name),
        DOCUMENT_NUMBER("document_number", R.string.document_number),
        ISSUING_AUTHORITY("issuing_authority", R.string.issuing_authority),
        DHS_COMPLIANCE("DHS_compliance", R.string.dhs_compliance),
    }
}