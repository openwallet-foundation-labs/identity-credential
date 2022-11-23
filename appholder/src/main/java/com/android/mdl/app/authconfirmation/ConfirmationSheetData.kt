package com.android.mdl.app.authconfirmation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
@Immutable
data class ConfirmationSheetData(
    val documentName: String,
    val elements: List<DocumentElement>
) {

    data class DocumentElement(
        val displayName: String,
        val requestedElement: RequestedElement
    )
}