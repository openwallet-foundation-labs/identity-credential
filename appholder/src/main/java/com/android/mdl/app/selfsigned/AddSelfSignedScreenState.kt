package com.android.mdl.app.selfsigned

import android.os.Parcelable
import com.android.identity.securearea.SecureArea
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
    val bouncyCastleAuthKeyCurveState: BouncyCastleAuthKeyCurveState = BouncyCastleAuthKeyCurveState(),
    val passphrase: String = "",
    val numberOfMso: Int = 10,
    val maxUseOfMso: Int = 1,
    val validityInDays: Int = 30,
    val minValidityInDays: Int = 10
) : Parcelable {

    val isAndroidKeystoreSelected: Boolean
        get() = secureAreaImplementationState == SecureAreaImplementationState.Android

    val ecCurve: Int
        get() = if (secureAreaImplementationState == SecureAreaImplementationState.Android) {
            androidAuthKeyCurveState.authCurve.toEcCurve()
        } else {
            bouncyCastleAuthKeyCurveState.authCurve.toEcCurve()
        }

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
    data class BouncyCastleAuthKeyCurveState(
        val isEnabled: Boolean = true,
        val authCurve: BouncyCastleAuthKeyCurveOption = BouncyCastleAuthKeyCurveOption.P256
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
                P_256 -> SecureArea.EC_CURVE_P256
                Ed25519 -> SecureArea.EC_CURVE_ED25519
                X25519 -> SecureArea.EC_CURVE_X25519
            }
        }
    }

    @Parcelize
    enum class BouncyCastleAuthKeyCurveOption : Parcelable {
        P256,
        P384,
        P521,
        BrainPoolP256R1,
        BrainPoolP320R1,
        BrainPoolP384R1,
        BrainPoolP512R1,
        Ed25519,
        Ed448,
        X25519,
        X448;

        fun toEcCurve(): Int {
            return when (this) {
                P256 -> SecureArea.EC_CURVE_P256
                P384 -> SecureArea.EC_CURVE_P384
                P521 -> SecureArea.EC_CURVE_P521
                BrainPoolP256R1 -> SecureArea.EC_CURVE_BRAINPOOLP256R1
                BrainPoolP320R1 -> SecureArea.EC_CURVE_BRAINPOOLP320R1
                BrainPoolP384R1 -> SecureArea.EC_CURVE_BRAINPOOLP384R1
                BrainPoolP512R1 -> SecureArea.EC_CURVE_BRAINPOOLP512R1
                Ed25519 -> SecureArea.EC_CURVE_ED25519
                Ed448 -> SecureArea.EC_CURVE_ED448
                X25519 -> SecureArea.EC_CURVE_X25519
                X448 -> SecureArea.EC_CURVE_X448
            }
        }
    }
}
