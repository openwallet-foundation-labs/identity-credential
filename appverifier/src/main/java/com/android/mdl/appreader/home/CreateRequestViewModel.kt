package com.android.mdl.appreader.home

import androidx.lifecycle.ViewModel
import com.android.mdl.appreader.document.RequestDocument
import com.android.mdl.appreader.document.RequestDocumentList
import com.android.mdl.appreader.document.RequestDocumentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class CreateRequestViewModel : ViewModel() {

    private val mutableState = MutableStateFlow(RequestingDocumentState())
    val state: StateFlow<RequestingDocumentState> = mutableState

    fun onRequestUpdate(fieldsRequest: DocumentElementsRequest) {
        if (mutableState.value.isMdlRequest(fieldsRequest)) {
            mutableState.update { state -> resetMdlSelection(state) }
        }
        val updated = fieldsRequest.copy(isSelected = !fieldsRequest.isSelected)
        when (updated.title) {
            state.value.olderThan18.title -> mutableState.update { it.copy(olderThan18 = updated) }
            state.value.olderThan21.title -> mutableState.update { it.copy(olderThan21 = updated) }
            state.value.mandatoryFields.title -> mutableState.update { it.copy(mandatoryFields = updated) }
            state.value.fullMdl.title -> mutableState.update { it.copy(fullMdl = updated) }
            state.value.mdlForUsTransportation.title -> mutableState.update {
                it.copy(
                    mdlForUsTransportation = updated
                )
            }

            state.value.custom.title -> mutableState.update { it.copy(custom = updated) }
            state.value.mVR.title -> mutableState.update { it.copy(mVR = updated) }
            state.value.micov.title -> mutableState.update { it.copy(micov = updated) }
            state.value.euPid.title -> mutableState.update { it.copy(euPid = updated) }
            state.value.mdlWithLinkage.title -> mutableState.update { it.copy(mdlWithLinkage = updated) }
        }
    }

    private fun resetMdlSelection(state: RequestingDocumentState) = state.copy(
        olderThan18 = state.olderThan18.copy(isSelected = false),
        olderThan21 = state.olderThan21.copy(isSelected = false),
        mandatoryFields = state.mandatoryFields.copy(isSelected = false),
        fullMdl = state.fullMdl.copy(isSelected = false),
        mdlForUsTransportation = state.mdlForUsTransportation.copy(isSelected = false),
        custom = state.custom.copy(isSelected = false),
    )

    fun calculateRequestDocumentList(intentToRetain: Boolean): RequestDocumentList {
        val requestDocumentList = RequestDocumentList()
        val uiState = state.value

        if (uiState.hasMdlElementsSelected) {
            if (uiState.mdlForUsTransportation.isSelected) {
                val mdlUsTransportation = RequestDocument(RequestDocumentType.MLD_US_TRANSPORTATION)
                mdlUsTransportation.setSelectedDataElements(mdlUsTransportation.elements,false)
                requestDocumentList.addRequestDocument(mdlUsTransportation)
            } else {
                val mdl: RequestDocument =
                    when {
                        uiState.olderThan18.isSelected ->
                            RequestDocument(RequestDocumentType.MDL_OLDER_THAN_18)
                        uiState.olderThan21.isSelected ->
                            RequestDocument(RequestDocumentType.MDL_OLDER_THAN_21)
                        uiState.mandatoryFields.isSelected ->
                            RequestDocument(RequestDocumentType.MDL_MANDATORY_FIELDS)
                        else ->
                            RequestDocument(RequestDocumentType.MDL)
                    }
                mdl.setSelectedDataElements(mdl.elements, intentToRetain)
                requestDocumentList.addRequestDocument(mdl)
            }
        }

        if (uiState.mVR.isSelected) {
            val doc = RequestDocument(RequestDocumentType.MVR)
            doc.setSelectedDataElements(doc.elements, intentToRetain)
            requestDocumentList.addRequestDocument(doc)
        }
        if (uiState.micov.isSelected) {
            val doc = RequestDocument(RequestDocumentType.MICOV_VTR)
            doc.setSelectedDataElements(doc.elements, intentToRetain)
            requestDocumentList.addRequestDocument(doc)
            val doc2 = RequestDocument(RequestDocumentType.MICOV_ATT)
            doc2.setSelectedDataElements(doc2.elements, intentToRetain)
            requestDocumentList.addRequestDocument(doc2)
        }
        if (uiState.euPid.isSelected) {
            val doc = RequestDocument(RequestDocumentType.EUPID)
            doc.setSelectedDataElements(doc.elements, intentToRetain)
            requestDocumentList.addRequestDocument(doc)
        }
        if (uiState.mdlWithLinkage.isSelected) {
            val doc = RequestDocument(RequestDocumentType.MDL_WITH_LINKAGE)
            doc.setSelectedDataElements(doc.elements, false)
            requestDocumentList.addRequestDocument(doc)
            val doc2 = RequestDocument(RequestDocumentType.MULTI003)
            doc2.setSelectedDataElements(doc2.elements, false)
            requestDocumentList.addRequestDocument(doc2)
        }
        return requestDocumentList
    }
}