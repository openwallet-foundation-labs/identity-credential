package com.android.identity.wallet.support

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.android.identity.android.securearea.AndroidKeystoreCreateKeySettings
import com.android.identity.android.securearea.AndroidKeystoreKeyInfo
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.android.securearea.userAuthenticationTypeSet
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.document.Credential
import com.android.identity.crypto.Algorithm
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.crypto.EcCurve
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.util.Timestamp
import com.android.identity.wallet.R
import com.android.identity.wallet.authprompt.UserAuthPromptBuilder
import com.android.identity.wallet.composables.AndroidSetupContainer
import com.android.identity.wallet.composables.AuthenticationKeyCurveAndroid
import com.android.identity.wallet.composables.MdocAuthentication
import com.android.identity.wallet.support.androidkeystore.AndroidAuthKeyCurveOption
import com.android.identity.wallet.support.androidkeystore.AndroidAuthKeyCurveState
import com.android.identity.wallet.composables.state.AuthTypeState
import com.android.identity.wallet.composables.state.MdocAuthOption

class AndroidKeystoreSecureAreaSupport(
    private val capabilities: AndroidKeystoreSecureArea.Capabilities
) : SecureAreaSupport {

    private val screenState = AndroidKeystoreSecureAreaSupportState(
        allowLSKFUnlocking = AuthTypeState(true, capabilities.multipleAuthenticationTypesSupported),
        allowBiometricUnlocking = AuthTypeState(true, capabilities.multipleAuthenticationTypesSupported),
        useStrongBox = AuthTypeState(false, capabilities.strongBoxSupported),
        mDocAuthOption = MdocAuthOption(isEnabled = capabilities.keyAgreementSupported),
        authKeyCurveState = AndroidAuthKeyCurveState(isEnabled = capabilities.curve25519Supported)
    )

    override fun Fragment.unlockKey(
        authKey: Credential,
        onKeyUnlocked: (unlockData: KeyUnlockData?) -> Unit,
        onUnlockFailure: (wasCancelled: Boolean) -> Unit
    ) {
        val keyInfo = authKey.secureArea.getKeyInfo(authKey.alias) as AndroidKeystoreKeyInfo
        val unlockData = AndroidKeystoreKeyUnlockData(authKey.alias)

        val allowLskf = keyInfo.userAuthenticationTypes.contains(UserAuthenticationType.LSKF)
        val allowBiometric = keyInfo.userAuthenticationTypes.contains(UserAuthenticationType.BIOMETRIC)
        val allowBoth = keyInfo.userAuthenticationTypes.contains(UserAuthenticationType.LSKF) &&
                keyInfo.userAuthenticationTypes.contains(UserAuthenticationType.BIOMETRIC)
        val allowLskfUnlock = allowLskf || allowBoth
        val allowBiometricUnlock = allowBiometric || allowBoth
        val forceLskf: Boolean = !allowBiometricUnlock

        val userAuthRequest = UserAuthPromptBuilder.requestUserAuth(this)
            .withTitle(getString(R.string.bio_auth_title))
            .withSuccessCallback {
                onKeyUnlocked(unlockData)
            }
            .withCancelledCallback {
                if (allowLskfUnlock) {
                    val runnable = {
                        unlockKey(authKey, onKeyUnlocked, onUnlockFailure)
                    }
                    // Without this delay, the prompt won't reshow
                    Handler(Looper.getMainLooper()).postDelayed(runnable, 100)
                } else {
                    onUnlockFailure(true)
                }
            }
            .withFailureCallback { onUnlockFailure(false) }
            .setForceLskf(forceLskf)
        if (allowLskfUnlock) {
            userAuthRequest.withNegativeButton(getString(R.string.bio_auth_use_pin))
        } else {
            userAuthRequest.withNegativeButton("Cancel")
        }
        val cryptoObject = unlockData.getCryptoObjectForSigning(Algorithm.ES256)
        userAuthRequest.build().authenticate(cryptoObject)
    }

    @Composable
    override fun SecureAreaAuthUi(
        onUiStateUpdated: (newState: SecureAreaSupportState) -> Unit
    ) {
        var compositionState by remember { mutableStateOf(screenState) }
        LaunchedEffect(key1 = compositionState) {
            onUiStateUpdated(compositionState)
        }
        AndroidSetupContainer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            isOn = compositionState.userAuthentication,
            timeoutSeconds = compositionState.userAuthenticationTimeoutSeconds,
            lskfAuthTypeState = compositionState.allowLSKFUnlocking,
            biometricAuthTypeState = compositionState.allowBiometricUnlocking,
            useStrongBox = compositionState.useStrongBox,
            onUserAuthenticationChanged = {
                compositionState = compositionState.copy(userAuthentication = it)
            },
            onAuthTimeoutChanged = { seconds ->
                if (seconds < 0) return@AndroidSetupContainer
                compositionState = compositionState.copy(userAuthenticationTimeoutSeconds = seconds)
            },
            onLskfAuthChanged = {
                val allowLskfUnlock =
                    if (compositionState.allowBiometricUnlocking.isEnabled) it else true
                val newValue = compositionState.allowLSKFUnlocking.copy(isEnabled = allowLskfUnlock)
                compositionState = compositionState.copy(allowLSKFUnlocking = newValue)
            },
            onBiometricAuthChanged = {
                val allowBiometricUnlock =
                    if (compositionState.allowLSKFUnlocking.isEnabled) it else true
                val newValue =
                    compositionState.allowBiometricUnlocking.copy(isEnabled = allowBiometricUnlock)
                compositionState = compositionState.copy(allowBiometricUnlocking = newValue)
            },
            onStrongBoxChanged = { newValue ->
                val update = compositionState.copy(
                    useStrongBox = compositionState.useStrongBox.copy(isEnabled = newValue),
                    mDocAuthOption = MdocAuthOption(
                        isEnabled = if (newValue) capabilities.strongBoxKeyAgreementSupported else capabilities.keyAgreementSupported
                    ),
                    authKeyCurveState = AndroidAuthKeyCurveState(
                        isEnabled = if (newValue) capabilities.strongBoxCurve25519Supported else capabilities.curve25519Supported
                    )
                )
                compositionState = update
            }
        )
        MdocAuthentication(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = compositionState.mDocAuthOption,
            onMdocAuthOptionChange = { newValue ->
                val authState = compositionState.mDocAuthOption.copy(mDocAuthentication = newValue)
                compositionState = compositionState.copy(
                    mDocAuthOption = authState,
                    authKeyCurveState = compositionState.authKeyCurveState.copy(
                        authCurve = AndroidAuthKeyCurveOption.P_256
                    )
                )
            }
        )
        AuthenticationKeyCurveAndroid(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = compositionState.authKeyCurveState,
            mDocAuthState = compositionState.mDocAuthOption,
            onAndroidAuthKeyCurveChanged = {
                val newValue = compositionState.authKeyCurveState.copy(authCurve = it)
                compositionState = compositionState.copy(authKeyCurveState = newValue)
            }
        )
    }

    override fun getSecureAreaSupportState(): SecureAreaSupportState {
        return screenState
    }

    override fun createAuthKeySettingsConfiguration(secureAreaSupportState: SecureAreaSupportState): ByteArray {
        val state = secureAreaSupportState as AndroidKeystoreSecureAreaSupportState

        val userAuthSettings = mutableSetOf<UserAuthenticationType>()
        if (state.allowLSKFUnlocking.isEnabled) {
            userAuthSettings.add(UserAuthenticationType.LSKF)
        }
        if (state.allowBiometricUnlocking.isEnabled) {
            userAuthSettings.add(UserAuthenticationType.BIOMETRIC)
        }

        return Cbor.encode(
            CborMap.builder()
            .put("curve", state.authKeyCurveState.authCurve.toEcCurve().coseCurveIdentifier.toLong())
            .put("purposes", KeyPurpose.encodeSet(setOf(state.mDocAuthOption.mDocAuthentication.toKeyPurpose())))
            .put("userAuthEnabled", state.userAuthentication)
            .put("userAuthTimeoutMillis", state.userAuthenticationTimeoutSeconds.toLong() * 1000L)
            .put("userAuthSettings", UserAuthenticationType.encodeSet(userAuthSettings))
            .put("useStrongBox", state.useStrongBox.isEnabled)
            .end()
            .build()
        )
    }

    override fun createAuthKeySettingsFromConfiguration(
        encodedConfiguration: ByteArray,
        challenge: ByteArray,
        validFrom: Timestamp,
        validUntil: Timestamp
    ): CreateKeySettings {
        val map = Cbor.decode(encodedConfiguration)
        val curve = EcCurve.fromInt(map["curve"].asNumber.toInt())
        val purposes = KeyPurpose.decodeSet(map["purposes"].asNumber)
        val userAuthEnabled = map["userAuthEnabled"].asBoolean
        val userAuthTimeoutMillis = map["userAuthTimeoutMillis"].asNumber
        val userAuthSettings = map["userAuthSettings"].asNumber
        val useStrongBox = map["useStrongBox"].asBoolean
        return AndroidKeystoreCreateKeySettings.Builder(challenge)
            .setEcCurve(curve)
            .setKeyPurposes(purposes)
            .setValidityPeriod(validFrom, validUntil)
            .setUseStrongBox(useStrongBox)
            .setUserAuthenticationRequired(
                userAuthEnabled,
                userAuthTimeoutMillis,
                userAuthSettings.userAuthenticationTypeSet
            )
            .build()
    }
}