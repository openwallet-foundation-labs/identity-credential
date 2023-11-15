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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.model.Map
import com.android.identity.credential.Credential
import com.android.identity.internal.Util
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity.wallet.authconfirmation.AuthConfirmationFragmentDirections
import com.android.identity.wallet.authconfirmation.PassphraseAuthResult
import com.android.identity.wallet.authconfirmation.PassphrasePromptViewModel
import com.android.identity.wallet.composables.AuthenticationKeyCurveSoftware
import com.android.identity.wallet.composables.MdocAuthentication
import com.android.identity.wallet.composables.SoftwareSetupContainer
import com.android.identity.wallet.support.softwarekeystore.SoftwareAuthKeyCurveOption
import com.android.identity.wallet.util.FormatUtil
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.security.PrivateKey
import java.security.cert.X509Certificate

class SoftwareKeystoreSecureAreaSupport : SecureAreaSupport {

    private lateinit var softwareAttestationKey: PrivateKey
    private lateinit var softwareAttestationKeySignatureAlgorithm: String
    private lateinit var softwareAttestationKeyCertification: List<X509Certificate>

    private val screenState = SoftwareKeystoreSecureAreaSupportState()

    override fun Fragment.unlockKey(
        authKey: Credential.AuthenticationKey,
        onKeyUnlocked: (unlockData: SecureArea.KeyUnlockData?) -> Unit,
        onUnlockFailure: (wasCancelled: Boolean) -> Unit
    ) {
        val viewModel: PassphrasePromptViewModel by activityViewModels()
        var didAttemptToUnlock = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.authorizationState.collect { value ->
                    if (value is PassphraseAuthResult.Success) {
                        val keyUnlockData = SoftwareSecureArea.KeyUnlockData(value.userPassphrase)
                        didAttemptToUnlock = true
                        onKeyUnlocked(keyUnlockData)
                        viewModel.reset()
                    }
                }
            }
        }
        val destination = AuthConfirmationFragmentDirections.openPassphrasePrompt(
            showIncorrectPassword = didAttemptToUnlock
        )
        val runnable = { findNavController().navigate(destination) }
        // The system needs a little time to get back to this screen
        Handler(Looper.getMainLooper()).postDelayed(runnable, 500)
    }

    @Composable
    override fun SecureAreaAuthUi(
        onUiStateUpdated: (newState: SecureAreaSupportState) -> Unit
    ) {
        var compositionState by remember { mutableStateOf(screenState) }
        LaunchedEffect(key1 = compositionState) {
            onUiStateUpdated(compositionState)
        }
        SoftwareSetupContainer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            passphrase = compositionState.passphrase,
            onPassphraseChanged = {
                compositionState = compositionState.copy(passphrase = it)
            }
        )
        MdocAuthentication(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = compositionState.mDocAuthOption,
            onMdocAuthOptionChange = {
                val newValue = compositionState.mDocAuthOption.copy(mDocAuthentication = it)
                compositionState = compositionState.copy(
                    mDocAuthOption = newValue,
                    softwareAuthKeyCurveState = compositionState.softwareAuthKeyCurveState.copy(
                        authCurve = SoftwareAuthKeyCurveOption.P256
                    )
                )
            }
        )
        AuthenticationKeyCurveSoftware(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = compositionState.softwareAuthKeyCurveState,
            mDocAuthState = compositionState.mDocAuthOption,
            onSoftwareAuthKeyCurveChanged = {
                val newValue = compositionState.authKeyCurve.copy(authCurve = it)
                compositionState = compositionState.copy(softwareAuthKeyCurveState = newValue)
            }
        )
    }

    override fun getSecureAreaSupportState(): SecureAreaSupportState {
        return screenState
    }

    private fun initSoftwareAttestationKey() {
        val secureArea = SoftwareSecureArea(EphemeralStorageEngine())
        val now = Timestamp.now()
        secureArea.createKey(
            "SoftwareAttestationRoot",
            SoftwareSecureArea.CreateKeySettings.Builder("".toByteArray())
                .setEcCurve(SecureArea.EC_CURVE_P256)
                .setKeyPurposes(SecureArea.KEY_PURPOSE_SIGN)
                .setSubject("CN=Software Attestation Root")
                .setValidityPeriod(
                    now,
                    Timestamp.ofEpochMilli(now.toEpochMilli() + 10L * 86400 * 365 * 1000)
                )
                .build()
        )
        softwareAttestationKey = secureArea.getPrivateKey("SoftwareAttestationRoot", null)
        softwareAttestationKeySignatureAlgorithm = "SHA256withECDSA"
        softwareAttestationKeyCertification = secureArea.getKeyInfo("SoftwareAttestationRoot").attestation
    }

    override fun createAuthKeySettingsConfiguration(secureAreaSupportState: SecureAreaSupportState): ByteArray {
        val state = secureAreaSupportState as SoftwareKeystoreSecureAreaSupportState
        return FormatUtil.cborEncode(
            CborBuilder()
            .addMap()
            .put("curve", state.softwareAuthKeyCurveState.authCurve.toEcCurve().toLong())
            .put("purposes", state.mDocAuthOption.mDocAuthentication.toKeyPurpose().toLong())
            .put("passphraseRequired", state.passphrase.isNotEmpty())
            .put("passphrase", state.passphrase)
            .end()
            .build().get(0))
    }

    override fun createAuthKeySettingsFromConfiguration(
        encodedConfiguration: ByteArray,
        challenge: ByteArray,
        validFrom: Timestamp,
        validUntil: Timestamp
    ): SecureArea.CreateKeySettings {
        if (!this::softwareAttestationKey.isInitialized) {
            initSoftwareAttestationKey()
        }

        val map = CborDecoder(ByteArrayInputStream(encodedConfiguration)).decode().get(0) as Map
        val curve = Util.cborMapExtractNumber(map, "curve").toInt()
        val purposes = Util.cborMapExtractNumber(map, "purposes").toInt()
        val passphraseRequired = Util.cborMapExtractBoolean(map, "passphraseRequired")
        val passphrase = Util.cborMapExtractString(map, "passphrase")
        return SoftwareSecureArea.CreateKeySettings.Builder(challenge)
            .setEcCurve(curve)
            .setKeyPurposes(purposes)
            .setValidityPeriod(validFrom, validUntil)
            .setPassphraseRequired(passphraseRequired, passphrase)
            .setAttestationKey(
                softwareAttestationKey,
                softwareAttestationKeySignatureAlgorithm,
                softwareAttestationKeyCertification
            )
            .build()
    }
}