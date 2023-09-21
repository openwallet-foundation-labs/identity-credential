package com.android.mdl.app.selfsigned

import android.os.Parcelable
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
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
    val userAuthentication: Boolean = true,
    val userAuthenticationTimeoutSeconds: Int = 10,
    val allowLSKFUnlocking: AuthTypeState = AuthTypeState(
        isEnabled = true,
        canBeModified = false
    ),
    val allowBiometricUnlocking: AuthTypeState = AuthTypeState(
        isEnabled = true,
        canBeModified = false
    ),
    val useStrongBox: AuthTypeState = AuthTypeState(
        isEnabled = false,
        canBeModified = false
    ),
    val androidMdocAuthState: MdocAuthOptionState = MdocAuthOptionState(),
    val androidAuthKeyCurveState: AndroidAuthKeyCurveState = AndroidAuthKeyCurveState(),
    val passphrase: String = "",
    val numberOfMso: Int = 10,
    val maxUseOfMso: Int = 1,
    val validityInDays: Int = 30,
    val minValidityInDays: Int = 10
) : Parcelable {

    val isAndroidKeystoreSelected: Boolean
        get() = secureAreaImplementationState == SecureAreaImplementationState.Android

    @Parcelize
    data class AuthTypeState(
        val isEnabled: Boolean = true,
        val canBeModified: Boolean = false
    ) : Parcelable

    @Parcelize
    data class MdocAuthOptionState(
        val isEnabled: Boolean = true,
        val mDocAuthentication: MdocAuthStateOption = MdocAuthStateOption.ECDSA
    ) : Parcelable

    @Parcelize
    data class AndroidAuthKeyCurveState(
        val isEnabled: Boolean = true,
        val authCurve: AndroidAuthKeyCurveOption = AndroidAuthKeyCurveOption.P_256
    ) : Parcelable

    @Parcelize
    enum class MdocAuthStateOption : Parcelable {
        ECDSA, MAC
    }

    @Parcelize
    enum class AndroidAuthKeyCurveOption : Parcelable {
        P_256, Ed25519, X25519;

        fun toEcCurve(): Int {
            return when (this) {
                P_256 -> AndroidKeystoreSecureArea.EC_CURVE_P256
                Ed25519 -> AndroidKeystoreSecureArea.EC_CURVE_ED25519
                X25519 -> AndroidKeystoreSecureArea.EC_CURVE_X25519
            }
        }
    }
}
