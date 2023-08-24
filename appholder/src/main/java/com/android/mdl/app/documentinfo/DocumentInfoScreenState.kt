package com.android.mdl.app.documentinfo

import com.android.mdl.app.document.DocumentColor
import com.android.mdl.app.document.SecureAreaImplementationState

data class DocumentInfoScreenState(
    val isLoading: Boolean = false,
    val documentName: String = "",
    val documentType: String = "",
    val documentColor: DocumentColor = DocumentColor.Green,
    val provisioningDate: String = "",
    val isSelfSigned: Boolean = false,
    val secureAreaImplementationState: SecureAreaImplementationState = SecureAreaImplementationState.Android,
    val authKeys: List<KeyInformation> = emptyList(),
    val isDeletingPromptShown: Boolean = false,
    val isDeleted: Boolean = false
) {

    data class KeyInformation(
        val alias: String,
        val validFrom: String,
        val validUntil: String,
        val issuerDataBytesCount: Int,
        val usagesCount: Int
    )
}