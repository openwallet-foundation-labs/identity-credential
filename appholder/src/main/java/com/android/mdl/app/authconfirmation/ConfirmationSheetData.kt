package com.android.mdl.app.authconfirmation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
@Immutable
data class ConfirmationSheetData(
    val documentName: String,
    val namespace: String,
    val properties: List<DocumentProperty>
) {

    data class DocumentProperty(
        val displayName: String,
        val propertyValue: String
    )
}