package com.android.identity.wallet.documentinfo

import androidx.compose.runtime.Immutable
import com.android.identity.securearea.SecureArea.EcCurve
import com.android.identity.securearea.SecureArea.KeyPurpose
import com.android.identity.wallet.document.DocumentColor
import com.android.identity.wallet.support.CurrentSecureArea
import com.android.identity.wallet.support.toSecureAreaState
import com.android.identity.wallet.util.ProvisioningUtil

@Immutable
data class DocumentInfoScreenState(
    val isLoading: Boolean = false,
    val documentName: String = "",
    val documentType: String = "",
    val documentColor: DocumentColor = DocumentColor.Green,
    val provisioningDate: String = "",
    val lastTimeUsedDate: String = "",
    val isSelfSigned: Boolean = false,
    val currentSecureArea: CurrentSecureArea = ProvisioningUtil.defaultSecureArea.toSecureAreaState(),
    val authKeys: List<KeyInformation> = emptyList(),
    val isDeletingPromptShown: Boolean = false,
    val isDeleted: Boolean = false
) {

    @Immutable
    data class KeyInformation(
        val counter: Int,
        val validFrom: String,
        val validUntil: String,
        val domain: String,
        val issuerDataBytesCount: Int,
        val usagesCount: Int,
        @KeyPurpose val keyPurposes: Int,
        @EcCurve val ecCurve: Int,
        val isHardwareBacked: Boolean,
        val secureAreaDisplayName: String
    )
}