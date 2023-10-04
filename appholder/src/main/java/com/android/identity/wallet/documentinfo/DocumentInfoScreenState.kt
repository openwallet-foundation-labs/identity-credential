package com.android.identity.wallet.documentinfo

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.identity.securearea.SecureArea.EcCurve
import com.android.identity.securearea.SecureArea.KeyPurpose
import com.android.identity.wallet.document.DocumentColor
import com.android.identity.wallet.document.SecureAreaImplementationState

@Immutable
data class DocumentInfoScreenState(
    val isLoading: Boolean = false,
    val documentName: String = "",
    val documentType: String = "",
    val documentColor: DocumentColor = DocumentColor.Green,
    val provisioningDate: String = "",
    val lastTimeUsedDate: String = "",
    val isSelfSigned: Boolean = false,
    val secureAreaImplementationState: SecureAreaImplementationState = SecureAreaImplementationState.Android,
    val authKeys: List<KeyInformation> = emptyList(),
    val isDeletingPromptShown: Boolean = false,
    val isDeleted: Boolean = false
) {

    @Immutable
    data class KeyInformation(
        val alias: String,
        val validFrom: String,
        val validUntil: String,
        val issuerDataBytesCount: Int,
        val usagesCount: Int,
        @KeyPurpose val keyPurposes: Int,
        @EcCurve val ecCurve: Int,
        val isHardwareBacked: Boolean
    )
}