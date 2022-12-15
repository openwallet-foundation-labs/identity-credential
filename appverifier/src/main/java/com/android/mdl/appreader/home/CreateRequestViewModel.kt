package com.android.mdl.appreader.home

import androidx.lifecycle.ViewModel
import com.android.mdl.appreader.document.RequestDocument
import com.android.mdl.appreader.document.RequestDocumentList
import com.android.mdl.appreader.document.RequestEuPid
import com.android.mdl.appreader.document.RequestMdl
import com.android.mdl.appreader.document.RequestMdlUsTransportation
import com.android.mdl.appreader.document.RequestMicovAtt
import com.android.mdl.appreader.document.RequestMicovVtr
import com.android.mdl.appreader.document.RequestMulti003
import com.android.mdl.appreader.document.RequestMvr
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
                requestDocumentList.addRequestDocument(RequestMdlUsTransportation)
            } else {
                val mdl = RequestMdl
                when {
                    uiState.olderThan18.isSelected ->
                        mdl.setSelectedDataItems(getSelectRequestMdlOlder18(intentToRetain))

                    uiState.olderThan21.isSelected ->
                        mdl.setSelectedDataItems(getSelectRequestMdlOlder21(intentToRetain))

                    uiState.mandatoryFields.isSelected ->
                        mdl.setSelectedDataItems(getSelectRequestMdlMandatory(intentToRetain))

                    uiState.fullMdl.isSelected ->
                        mdl.setSelectedDataItems(getSelectRequestFull(mdl, intentToRetain))
                }
                requestDocumentList.addRequestDocument(mdl)
            }
        }

        if (uiState.mVR.isSelected) {
            val doc = RequestMvr
            doc.setSelectedDataItems(getSelectRequestFull(doc, intentToRetain))
            requestDocumentList.addRequestDocument(doc)
        }
        if (uiState.micov.isSelected) {
            val doc = RequestMicovAtt
            doc.setSelectedDataItems(getSelectRequestFull(doc, intentToRetain))
            requestDocumentList.addRequestDocument(doc)
            val doc2 = RequestMicovVtr
            doc2.setSelectedDataItems(getSelectRequestFull(doc2, intentToRetain))
            requestDocumentList.addRequestDocument(doc2)
        }
        if (uiState.euPid.isSelected) {
            val doc = RequestEuPid
            doc.setSelectedDataItems(getSelectRequestFull(doc, intentToRetain))
            requestDocumentList.addRequestDocument(doc)
        }
        if (uiState.mdlWithLinkage.isSelected) {
            val doc = RequestMdl
            val selectMdl = mapOf(Pair("portrait", false), Pair("document_number", false))
            doc.setSelectedDataItems(selectMdl)
            requestDocumentList.addRequestDocument(doc)
            val doc2 = RequestMulti003()
            requestDocumentList.addRequestDocument(doc2)
        }
        return requestDocumentList
    }

    private fun getSelectRequestMdlMandatory(intentToRetain: Boolean): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        map[RequestMdl.DataItems.FAMILY_NAME.identifier] = intentToRetain
        map[RequestMdl.DataItems.GIVEN_NAMES.identifier] = intentToRetain
        map[RequestMdl.DataItems.BIRTH_DATE.identifier] = intentToRetain
        map[RequestMdl.DataItems.ISSUE_DATE.identifier] = intentToRetain
        map[RequestMdl.DataItems.EXPIRY_DATE.identifier] = intentToRetain
        map[RequestMdl.DataItems.ISSUING_COUNTRY.identifier] = intentToRetain
        map[RequestMdl.DataItems.ISSUING_AUTHORITY.identifier] = intentToRetain
        map[RequestMdl.DataItems.DOCUMENT_NUMBER.identifier] = intentToRetain
        map[RequestMdl.DataItems.PORTRAIT.identifier] = intentToRetain
        map[RequestMdl.DataItems.DRIVING_PRIVILEGES.identifier] = intentToRetain
        map[RequestMdl.DataItems.UN_DISTINGUISHING_SIGN.identifier] = intentToRetain
        return map
    }

    private fun getSelectRequestMdlOlder21(intentToRetain: Boolean): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        map[RequestMdl.DataItems.PORTRAIT.identifier] = intentToRetain
        map[RequestMdl.DataItems.AGE_OVER_21.identifier] = intentToRetain
        return map
    }

    private fun getSelectRequestMdlOlder18(intentToRetain: Boolean): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        map[RequestMdl.DataItems.PORTRAIT.identifier] = intentToRetain
        map[RequestMdl.DataItems.AGE_OVER_18.identifier] = intentToRetain
        return map
    }

    private fun getSelectRequestFull(
        requestDocument: RequestDocument,
        intentToRetain: Boolean
    ): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        requestDocument.dataItems.forEach {
            map[it.identifier] = intentToRetain
        }
        return map
    }
}