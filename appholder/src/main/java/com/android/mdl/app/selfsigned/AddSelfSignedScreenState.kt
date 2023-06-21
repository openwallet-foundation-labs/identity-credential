package com.android.mdl.app.selfsigned

import android.os.Parcelable
import com.android.mdl.app.document.DocumentColor
import com.android.mdl.app.document.DocumentType
import com.android.mdl.app.document.SecureAreaImplementationState
import kotlinx.parcelize.Parcelize

@Parcelize
data class AddSelfSignedScreenState(
    val documentType: DocumentType = DocumentType.MDL,
    val cardArt: DocumentColor = DocumentColor.Green,
    val documentName: String = "Driving License",
    val secureAreaImplementationState: SecureAreaImplementationState = SecureAreaImplementationState.Android,
    val userAuthentication: Boolean = false,
    val userAuthenticationTimeoutSeconds: Int = 10,
    val passphrase: String = "",
    val numberOfMso: Int = 10,
    val maxUseOfMso: Int = 1
) : Parcelable {

    val isAndroidKeystoreSelected: Boolean
        get() = secureAreaImplementationState == SecureAreaImplementationState.Android
}
