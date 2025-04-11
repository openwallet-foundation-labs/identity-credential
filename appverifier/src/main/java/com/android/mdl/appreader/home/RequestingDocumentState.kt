package com.android.mdl.appreader.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.mdl.appreader.R

@Stable
@Immutable
data class RequestingDocumentState(
    val olderThan18: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_over_18),
    val olderThan21: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_over_21),
    val mandatoryFields: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_mandatory_fields),
    val fullMdl: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_full),
    val mdlForUsTransportation: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_us_transportation),
    val custom: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_custom),
    val mVR: DocumentElementsRequest = DocumentElementsRequest(R.string.mvr_full),
    val micov: DocumentElementsRequest = DocumentElementsRequest(R.string.micov_full),
    val euPid: DocumentElementsRequest = DocumentElementsRequest(R.string.eu_pid_full, true),
    val mdlWithLinkage: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_micov_linkage)
) {

    val isCustomMdlRequest: Boolean
        get() = custom.isSelected

    val hasMdlElementsSelected: Boolean
        get() = olderThan18.isSelected
                || olderThan21.isSelected
                || mandatoryFields.isSelected
                || fullMdl.isSelected
                || mdlForUsTransportation.isSelected
                || custom.isSelected

    val currentRequestSelection: String
        get() = buildString {
            if (olderThan18.isSelected) {
                append("Over 18")
                append("; ")
            }
            if (olderThan21.isSelected) {
                append("Over 21")
                append("; ")
            }
            if (mandatoryFields.isSelected) {
                append("mDL Mandatory Fields")
                append("; ")
            }
            if (fullMdl.isSelected) {
                append("Driving Licence")
                append("; ")
            }
            if (mdlForUsTransportation.isSelected) {
                append("mDL for US transportation")
                append("; ")
            }
            if (custom.isSelected) {
                append("mDL Custom")
                append("; ")
            }
            if (mVR.isSelected) {
                append("Vehicle Document")
                append("; ")
            }
            if (micov.isSelected) {
                append("Vaccination Document")
                append("; ")
            }
            if (euPid.isSelected) {
                append("European Personal ID")
                append("; ")
            }
            if (mdlWithLinkage.isSelected) {
                append("Driving Licence + Vaccination with linkage")
                append("; ")
            }
        }

    fun isMdlRequest(request: DocumentElementsRequest): Boolean {
        return request.title == olderThan18.title
                || request.title == olderThan21.title
                || request.title == mandatoryFields.title
                || request.title == fullMdl.title
                || request.title == mdlForUsTransportation.title
                || request.title == custom.title
    }
}