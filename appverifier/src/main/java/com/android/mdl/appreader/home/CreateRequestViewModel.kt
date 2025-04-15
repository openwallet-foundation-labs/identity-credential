package com.android.mdl.appreader.home

import androidx.lifecycle.ViewModel
import org.multipaz.documenttype.MdocDataElement
import com.android.mdl.appreader.VerifierApp
import com.android.mdl.appreader.document.RequestDocument
import com.android.mdl.appreader.document.RequestDocumentList
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
                requestDocumentList.addRequestDocument(
                    getRequestDocument(
                        RequestDocument.MDL_DOCTYPE,
                        intentToRetain,
                        filterElement = { el ->
                            listOf(
                                "sex",
                                "portrait",
                                "given_name",
                                "issue_date",
                                "expiry_date",
                                "family_name",
                                "document_number",
                                "issuing_authority",
                                "DHS_compliance",
                                "EDL_credential"
                            ).contains(el.attribute.identifier)
                        }
                    )
                )
            } else {
                when {
                    uiState.olderThan18.isSelected ->
                        requestDocumentList.addRequestDocument(
                            getRequestDocument(
                                RequestDocument.MDL_DOCTYPE,
                                intentToRetain,
                                filterNamespace = { ns -> ns == RequestDocument.MDL_NAMESPACE },
                                filterElement = { el ->
                                    listOf(
                                        "portrait",
                                        "age_over_18"
                                    ).contains(el.attribute.identifier)
                                }
                            )
                        )

                    uiState.olderThan21.isSelected ->
                        requestDocumentList.addRequestDocument(
                            getRequestDocument(
                                RequestDocument.MDL_DOCTYPE,
                                intentToRetain,
                                filterNamespace = { ns -> ns == RequestDocument.MDL_NAMESPACE },
                                filterElement = { el ->
                                    listOf(
                                        "portrait",
                                        "age_over_21"
                                    ).contains(el.attribute.identifier)
                                }
                            )
                        )

                    uiState.mandatoryFields.isSelected ->
                        requestDocumentList.addRequestDocument(
                            getRequestDocument(
                                RequestDocument.MDL_DOCTYPE,
                                intentToRetain,
                                filterElement = { el -> el.mandatory }
                            )
                        )

                    uiState.fullMdl.isSelected || uiState.isCustomMdlRequest ->
                        requestDocumentList.addRequestDocument(
                            getRequestDocument(
                                RequestDocument.MDL_DOCTYPE,
                                intentToRetain
                            )
                        )
                }
            }
        }

        if (uiState.mVR.isSelected) {
            requestDocumentList.addRequestDocument(
                getRequestDocument(
                    RequestDocument.MVR_DOCTYPE,
                    intentToRetain
                )
            )
        }
        if (uiState.micov.isSelected) {
            requestDocumentList.addRequestDocument(
                getRequestDocument(
                    RequestDocument.MICOV_DOCTYPE,
                    intentToRetain,
                    filterNamespace = { ns -> ns == RequestDocument.MICOV_ATT_NAMESPACE })
            )
            requestDocumentList.addRequestDocument(
                getRequestDocument(
                    RequestDocument.MICOV_DOCTYPE,
                    intentToRetain,
                    filterNamespace = { ns -> ns == RequestDocument.MICOV_VTR_NAMESPACE })
            )
        }
        if (uiState.euPid.isSelected) {
            requestDocumentList.addRequestDocument(
                getRequestDocument(
                    RequestDocument.EU_PID_DOCTYPE,
                    intentToRetain,
                    filterElement = { el ->
                        uiState.euPid.attributes!!.contains(el.attribute.identifier)
                    }
                )
            )
        }
        if (uiState.mdlWithLinkage.isSelected) {
            requestDocumentList.addRequestDocument(
                getRequestDocument(
                    RequestDocument.MDL_DOCTYPE,
                    intentToRetain,
                    filterNamespace = { ns -> ns == RequestDocument.MDL_NAMESPACE },
                    filterElement = { el ->
                        listOf(
                            "portrait",
                            "document_number"
                        ).contains(el.attribute.identifier)
                    }
                )
            )
            requestDocumentList.addRequestDocument(
                getRequestDocument(
                    RequestDocument.MICOV_DOCTYPE,
                    intentToRetain,
                    filterElement = { el ->
                        listOf(
                            "pid_DL",
                            "safeEntry_Leisure"
                        ).contains(el.attribute.identifier)
                    }
                )
            )
        }
        return requestDocumentList
    }


    private fun getRequestDocument(
        docType: String,
        intentToRetain: Boolean,
        filterNamespace: (String) -> Boolean = { _ -> true },
        filterElement: (MdocDataElement) -> Boolean = { _ -> true }
    ): RequestDocument {
        val mdocDocumentType = VerifierApp.documentTypeRepositoryInstance
                .getDocumentTypeForMdoc(docType)!!.mdocDocumentType!!
        return RequestDocument(
            docType,
            mdocDocumentType.namespaces.values.filter { filterNamespace(it.namespace) }
                .map {
                    Pair(
                        it.namespace,
                        it.dataElements.values.filter { el -> filterElement(el) }
                            .map { el -> Pair(el.attribute.identifier, intentToRetain) }
                            .toMap()
                    )
                }.toMap()
        )
    }
}