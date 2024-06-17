package com.android.mdl.appreader.home

import com.android.mdl.appreader.R
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertTrue

class RequestCreationTest {

    @Test
    fun defaultRequest() {
        val viewModel = CreateRequestViewModel()
        assertTrue(viewModel.state.value.mdlForUsTransportation.isSelected)
    }

    @Test
    fun selectMdlOver18UnselectsOtherMdlElements() {
        val mdlOver18 = DocumentElementsRequest(R.string.mdl_over_18)
        val viewModel = CreateRequestViewModel().apply {
            onRequestUpdate(DocumentElementsRequest(R.string.mdl_over_21))
        }

        viewModel.onRequestUpdate(mdlOver18)

        assertTrue(viewModel.state.mdlElementsUnselectedExcept(mdlOver18))
    }

    @Test
    fun selectMdlOver21UnselectsOtherMdlElements() {
        val mdlOver21 = DocumentElementsRequest(R.string.mdl_over_21)
        val viewModel = CreateRequestViewModel().apply {
            onRequestUpdate(DocumentElementsRequest(R.string.mdl_over_18))
        }

        viewModel.onRequestUpdate(mdlOver21)

        assertTrue(viewModel.state.mdlElementsUnselectedExcept(mdlOver21))
    }

    @Test
    fun selectMdlMandatoryFieldsUnselectsOtherMdlElements() {
        val mdlMandatory = DocumentElementsRequest(R.string.mdl_mandatory_fields)
        val viewModel = CreateRequestViewModel().apply {
            onRequestUpdate(DocumentElementsRequest(R.string.mdl_full))
        }

        viewModel.onRequestUpdate(mdlMandatory)

        assertTrue(viewModel.state.mdlElementsUnselectedExcept(mdlMandatory))
    }

    @Test
    fun selectFullMdlUnselectsOtherMdlElements() {
        val fullMdl = DocumentElementsRequest(R.string.mdl_full)
        val viewModel = CreateRequestViewModel().apply {
            onRequestUpdate(DocumentElementsRequest(R.string.mdl_mandatory_fields))
        }

        viewModel.onRequestUpdate(fullMdl)

        assertTrue(viewModel.state.mdlElementsUnselectedExcept(fullMdl))
    }

    @Test
    fun selectMdlForUsTransportationUnselectsOtherMdlElements() {
        val mdlUS = DocumentElementsRequest(R.string.mdl_us_transportation)
        val viewModel = CreateRequestViewModel().apply {
            onRequestUpdate(DocumentElementsRequest(R.string.mdl_custom))
        }

        viewModel.onRequestUpdate(mdlUS)

        assertTrue(viewModel.state.mdlElementsUnselectedExcept(mdlUS))
    }

    @Test
    fun selectCustomMdlUnselectsOtherMdlElements() {
        val customMdl = DocumentElementsRequest(R.string.mdl_custom)
        val viewModel = CreateRequestViewModel().apply {
            onRequestUpdate(DocumentElementsRequest(R.string.mdl_over_21))
        }

        viewModel.onRequestUpdate(customMdl)

        assertTrue(viewModel.state.mdlElementsUnselectedExcept(customMdl))
    }

    private val StateFlow<RequestingDocumentState>.mdlElements
        get() = listOf(
            value.olderThan18,
            value.olderThan21,
            value.mandatoryFields,
            value.fullMdl,
            value.mdlForUsTransportation,
            value.custom
        )

    private fun StateFlow<RequestingDocumentState>.mdlElementsUnselectedExcept(
        which: DocumentElementsRequest
    ): Boolean {
        val allExceptGivenAreUnselected = mdlElements
            .filter { it.title != which.title }
            .all { !it.isSelected }
        val givenIsSelected = mdlElements.first { it.title == which.title }.isSelected
        return allExceptGivenAreUnselected && givenIsSelected
    }
}