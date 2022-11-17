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