package com.android.mdl.appreader.home

import com.android.mdl.appreader.R
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RequestingDocumentStateTest {
    private val requestingDocumentState = RequestingDocumentState()
    private val selected = DocumentElementsRequest(0, true)

    @Test
    fun detectNonCustomMdlRequest() {
        assertThat(requestingDocumentState.isCustomMdlRequest).isFalse()
    }

    @Test
    fun detectCustomMdlRequest() {
        val state = requestingDocumentState.copy(custom = selected)
        assertThat(state.isCustomMdlRequest).isTrue()
    }

    @Test
    fun detectMdlElementsWhenOlderThan18Selected() {
        val state = requestingDocumentState.copy(olderThan18 = selected)
        assertThat(state.hasMdlElementsSelected).isTrue()
    }

    @Test
    fun detectMdlElementsWhenOlderThan21Selected() {
        val state = requestingDocumentState.copy(olderThan21 = selected)
        assertThat(state.hasMdlElementsSelected).isTrue()
    }

    @Test
    fun detectMdlElementsWhenMandatoryFieldsSelected() {
        val state = requestingDocumentState.copy(mandatoryFields = selected)
        assertThat(state.hasMdlElementsSelected).isTrue()
    }

    @Test
    fun detectMdlElementsWhenFullMdlSelected() {
        val state = requestingDocumentState.copy(fullMdl = selected)
        assertThat(state.hasMdlElementsSelected).isTrue()
    }

    @Test
    fun detectMdlElementsWhenUsTransportationSelected() {
        val state = requestingDocumentState.copy(mdlForUsTransportation = selected)
        assertThat(state.hasMdlElementsSelected).isTrue()
    }

    @Test
    fun detectMdlElementsWhenCustomMdlSelected() {
        val state = requestingDocumentState.copy(custom = selected)
        assertThat(state.hasMdlElementsSelected).isTrue()
    }

    @Test
    fun detectMdlRequestForOlderThan18Request() {
        val mdlOver18 = DocumentElementsRequest(R.string.mdl_over_18)
        assertThat(requestingDocumentState.isMdlRequest(mdlOver18)).isTrue()
    }

    @Test
    fun detectMdlRequestForOlderThan21Request() {
        val mdlOver21 = DocumentElementsRequest(R.string.mdl_over_21)
        assertThat(requestingDocumentState.isMdlRequest(mdlOver21)).isTrue()
    }

    @Test
    fun detectMdlRequestForMdlWithMandatoryFieldsRequest() {
        val mldMandatoryFields = DocumentElementsRequest(R.string.mdl_mandatory_fields)
        assertThat(requestingDocumentState.isMdlRequest(mldMandatoryFields)).isTrue()
    }

    @Test
    fun detectMdlRequestForFullMdl() {
        val fullMdlFields = DocumentElementsRequest(R.string.mdl_full)
        assertThat(requestingDocumentState.isMdlRequest(fullMdlFields)).isTrue()
    }

    @Test
    fun detectMdlRequestForMdlForUsTransportation() {
        val mdlForUsFields = DocumentElementsRequest(R.string.mdl_us_transportation)
        assertThat(requestingDocumentState.isMdlRequest(mdlForUsFields)).isTrue()
    }

    @Test
    fun detectMdlRequestForCustomMdl() {
        val customMdlRequest = DocumentElementsRequest(R.string.mdl_custom)
        assertThat(requestingDocumentState.isMdlRequest(customMdlRequest)).isTrue()
    }
}
