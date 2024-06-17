package com.android.mdl.appreader.home

import com.android.mdl.appreader.R
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestingDocumentStateTest {

    private val requestingDocumentState = RequestingDocumentState()
    private val selected = DocumentElementsRequest(0, true)

    @Test
    fun detectNonCustomMdlRequest() {
        assertFalse(requestingDocumentState.isCustomMdlRequest)
    }

    @Test
    fun detectCustomMdlRequest() {
        val state = requestingDocumentState.copy(custom = selected)
        assertTrue(state.isCustomMdlRequest)
    }

    @Test
    fun detectMdlElementsWhenOlderThan18Selected() {
        val state = requestingDocumentState.copy(olderThan18 = selected)
        assertTrue(state.hasMdlElementsSelected)
    }

    @Test
    fun detectMdlElementsWhenOlderThan21Selected() {
        val state = requestingDocumentState.copy(olderThan21 = selected)
        assertTrue(state.hasMdlElementsSelected)
    }

    @Test
    fun detectMdlElementsWhenMandatoryFieldsSelected() {
        val state = requestingDocumentState.copy(mandatoryFields = selected)
        assertTrue(state.hasMdlElementsSelected)
    }

    @Test
    fun detectMdlElementsWhenFullMdlSelected() {
        val state = requestingDocumentState.copy(fullMdl = selected)
        assertTrue(state.hasMdlElementsSelected)
    }

    @Test
    fun detectMdlElementsWhenUsTransportationSelected() {
        val state = requestingDocumentState.copy(mdlForUsTransportation = selected)
        assertTrue(state.hasMdlElementsSelected)
    }

    @Test
    fun detectMdlElementsWhenCustomMdlSelected() {
        val state = requestingDocumentState.copy(custom = selected)
        assertTrue(state.hasMdlElementsSelected)
    }

    @Test
    fun detectMdlRequestForOlderThan18Request() {
        val mdlOver18 = DocumentElementsRequest(R.string.mdl_over_18)
        assertTrue(requestingDocumentState.isMdlRequest(mdlOver18))
    }

    @Test
    fun detectMdlRequestForOlderThan21Request() {
        val mdlOver21 = DocumentElementsRequest(R.string.mdl_over_21)
        assertTrue(requestingDocumentState.isMdlRequest(mdlOver21))
    }

    @Test
    fun detectMdlRequestForMdlWithMandatoryFieldsRequest() {
        val mldMandatoryFields = DocumentElementsRequest(R.string.mdl_mandatory_fields)
        assertTrue(requestingDocumentState.isMdlRequest(mldMandatoryFields))
    }

    @Test
    fun detectMdlRequestForFullMdl() {
        val fullMdlFields = DocumentElementsRequest(R.string.mdl_full)
        assertTrue(requestingDocumentState.isMdlRequest(fullMdlFields))
    }

    @Test
    fun detectMdlRequestForMdlForUsTransportation() {
        val mdlForUsFields = DocumentElementsRequest(R.string.mdl_us_transportation)
        assertTrue(requestingDocumentState.isMdlRequest(mdlForUsFields))
    }

    @Test
    fun detectMdlRequestForCustomMdl() {
        val customMdlRequest = DocumentElementsRequest(R.string.mdl_custom)
        assertTrue(requestingDocumentState.isMdlRequest(customMdlRequest))
    }
}