package com.android.identity.wallet.support

import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.credential.Credential
import com.android.identity.securearea.SecureArea
import com.android.identity.util.Timestamp
import com.android.identity.wallet.support.androidkeystore.AndroidAuthKeyCurveState
import com.android.identity.wallet.composables.state.AuthTypeState
import com.android.identity.wallet.composables.state.MdocAuthOption
import com.android.identity.wallet.composables.state.MdocAuthStateOption
import com.android.identity.wallet.util.toTimestampFromNow
import kotlinx.parcelize.Parcelize

@Parcelize
data class AndroidKeystoreSecureAreaSupportState(
    override val mDocAuthOption: MdocAuthOption = MdocAuthOption(),
    val userAuthentication: Boolean = true,
    val userAuthenticationTimeoutSeconds: Int = 0,
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
    val authKeyCurveState: AndroidAuthKeyCurveState = AndroidAuthKeyCurveState(),
) : SecureAreaSupportState {

    override fun createKeystoreSettings(validityInDays: Int): SecureArea.CreateKeySettings {
        return AndroidKeystoreSecureArea.CreateKeySettings.Builder("challenge".toByteArray())
            .setKeyPurposes(mDocAuthOption.mDocAuthentication.toKeyPurpose())
            .setUseStrongBox(useStrongBox.isEnabled)
            .setEcCurve(authKeyCurveState.authCurve.toEcCurve())
            .setValidityPeriod(Timestamp.now(), validityInDays.toTimestampFromNow())
            .setUserAuthenticationRequired(
                userAuthentication,
                userAuthenticationTimeoutSeconds * 1000L,
                userAuthType()
            ).build()
    }

    override fun createKeystoreSettingForCredential(
        mDocAuthOption: String,
        credential: Credential
    ): SecureArea.CreateKeySettings {
        val keyInfo = credential.credentialSecureArea
            .getKeyInfo(credential.credentialKeyAlias) as AndroidKeystoreSecureArea.KeyInfo
        val builder = AndroidKeystoreSecureArea.CreateKeySettings.Builder("challenge".toByteArray())
            .setKeyPurposes(MdocAuthStateOption.valueOf(mDocAuthOption).toKeyPurpose())
            .setUseStrongBox(keyInfo.isStrongBoxBacked)
            .setEcCurve(keyInfo.ecCurve)
            .setValidityPeriod(Timestamp.now(), keyInfo.validUntil ?: Timestamp.now())
            .setUserAuthenticationRequired(
                keyInfo.isUserAuthenticationRequired,
                keyInfo.userAuthenticationTimeoutMillis,
                keyInfo.userAuthenticationType
            )
        return builder.build()
    }

    private fun userAuthType(): Int {
        var userAuthenticationType = 0
        if (allowLSKFUnlocking.isEnabled) {
            userAuthenticationType =
                userAuthenticationType or AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_LSKF
        }
        if (allowBiometricUnlocking.isEnabled) {
            userAuthenticationType =
                userAuthenticationType or AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_BIOMETRIC
        }
        return userAuthenticationType
    }
}