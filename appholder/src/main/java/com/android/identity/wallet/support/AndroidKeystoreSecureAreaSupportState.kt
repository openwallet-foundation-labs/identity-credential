package com.android.identity.wallet.support

import com.android.identity.wallet.composables.state.AuthTypeState
import com.android.identity.wallet.composables.state.MdocAuthOption
import com.android.identity.wallet.support.androidkeystore.AndroidAuthKeyCurveState
import kotlinx.parcelize.Parcelize

@Parcelize
data class AndroidKeystoreSecureAreaSupportState(
    override val mDocAuthOption: MdocAuthOption = MdocAuthOption(),
    val userAuthentication: Boolean = true,
    val userAuthenticationTimeoutSeconds: Int = 0,
    val allowLSKFUnlocking: AuthTypeState =
        AuthTypeState(
            isEnabled = true,
            canBeModified = false,
        ),
    val allowBiometricUnlocking: AuthTypeState =
        AuthTypeState(
            isEnabled = true,
            canBeModified = false,
        ),
    val useStrongBox: AuthTypeState =
        AuthTypeState(
            isEnabled = false,
            canBeModified = false,
        ),
    val authKeyCurveState: AndroidAuthKeyCurveState = AndroidAuthKeyCurveState(),
) : SecureAreaSupportState
