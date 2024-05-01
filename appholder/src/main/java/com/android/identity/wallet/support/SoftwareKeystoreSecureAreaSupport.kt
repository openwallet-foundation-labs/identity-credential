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
import com.android.identity.cbor.Cbor
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.securearea.software.SoftwareCreateKeySettings
import com.android.identity.securearea.software.SoftwareKeyUnlockData
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
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
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

class SoftwareKeystoreSecureAreaSupport : SecureAreaSupport {
    private lateinit var softwareAttestationKey: EcPrivateKey
    private lateinit var softwareAttestationKeySignatureAlgorithm: Algorithm
    private lateinit var softwareAttestationKeyIssuer: String
    private lateinit var softwareAttestationKeyCertification: CertificateChain

    private val screenState = SoftwareKeystoreSecureAreaSupportState()

    override fun Fragment.unlockKey(
        credential: MdocCredential,
        onKeyUnlocked: (unlockData: KeyUnlockData?) -> Unit,
        onUnlockFailure: (wasCancelled: Boolean) -> Unit,
    ) {
        val viewModel: PassphrasePromptViewModel by activityViewModels()
        var didAttemptToUnlock = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.authorizationState.collect { value ->
                    if (value is PassphraseAuthResult.Success) {
                        val keyUnlockData = SoftwareKeyUnlockData(value.userPassphrase)
                        didAttemptToUnlock = true
                        onKeyUnlocked(keyUnlockData)
                        viewModel.reset()
                    }
                }
            }
        }
        val destination =
            AuthConfirmationFragmentDirections.openPassphrasePrompt(
                showIncorrectPassword = didAttemptToUnlock,
            )
        val runnable = { findNavController().navigate(destination) }
        // The system needs a little time to get back to this screen
        Handler(Looper.getMainLooper()).postDelayed(runnable, 500)
    }

    @Composable
    override fun SecureAreaAuthUi(onUiStateUpdated: (newState: SecureAreaSupportState) -> Unit) {
        var compositionState by remember { mutableStateOf(screenState) }
        LaunchedEffect(key1 = compositionState) {
            onUiStateUpdated(compositionState)
        }
        SoftwareSetupContainer(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            passphrase = compositionState.passphrase,
            onPassphraseChanged = {
                compositionState = compositionState.copy(passphrase = it)
            },
        )
        MdocAuthentication(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            state = compositionState.mDocAuthOption,
            onMdocAuthOptionChange = {
                val newValue = compositionState.mDocAuthOption.copy(mDocAuthentication = it)
                compositionState =
                    compositionState.copy(
                        mDocAuthOption = newValue,
                        softwareAuthKeyCurveState =
                            compositionState.softwareAuthKeyCurveState.copy(
                                authCurve = SoftwareAuthKeyCurveOption.P256,
                            ),
                    )
            },
        )
        AuthenticationKeyCurveSoftware(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            state = compositionState.softwareAuthKeyCurveState,
            mDocAuthState = compositionState.mDocAuthOption,
            onSoftwareAuthKeyCurveChanged = {
                val newValue = compositionState.authKeyCurve.copy(authCurve = it)
                compositionState = compositionState.copy(softwareAuthKeyCurveState = newValue)
            },
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
            SoftwareCreateKeySettings.Builder("".toByteArray())
                .setEcCurve(EcCurve.P256)
                .setKeyPurposes(setOf(KeyPurpose.SIGN))
                .setSubject("CN=Software Attestation Root")
                .setValidityPeriod(
                    now,
                    Timestamp.ofEpochMilli(now.toEpochMilli() + 10L * 86400 * 365 * 1000),
                )
                .build(),
        )
        val validFrom = Clock.System.now()
        val validUntil = validFrom + 100.days
        softwareAttestationKey = Crypto.createEcPrivateKey(EcCurve.P256)
        softwareAttestationKeySignatureAlgorithm = Algorithm.ES256
        softwareAttestationKeyIssuer = "CN=Software Attestation Root"
        softwareAttestationKeyCertification =
            CertificateChain(
                listOf(
                    Crypto.createX509v3Certificate(
                        softwareAttestationKey.publicKey,
                        softwareAttestationKey,
                        null,
                        Algorithm.ES256,
                        "1",
                        "CN=Software Attestation Root",
                        "CN=Software Attestation Root",
                        validFrom,
                        validUntil,
                        setOf(),
                        listOf(),
                    ),
                ),
            )
    }

    override fun createAuthKeySettingsConfiguration(secureAreaSupportState: SecureAreaSupportState): ByteArray {
        val state = secureAreaSupportState as SoftwareKeystoreSecureAreaSupportState
        return FormatUtil.cborEncode(
            CborBuilder()
                .addMap()
                .put("curve", state.softwareAuthKeyCurveState.authCurve.toEcCurve().coseCurveIdentifier.toLong())
                .put(
                    "purposes",
                    KeyPurpose.encodeSet(
                        setOf(state.mDocAuthOption.mDocAuthentication.toKeyPurpose()),
                    ).toLong(),
                )
                .put("passphraseRequired", state.passphrase.isNotEmpty())
                .put("passphrase", state.passphrase)
                .end()
                .build().get(0),
        )
    }

    override fun createAuthKeySettingsFromConfiguration(
        encodedConfiguration: ByteArray,
        challenge: ByteArray,
        validFrom: Timestamp,
        validUntil: Timestamp,
    ): CreateKeySettings {
        if (!this::softwareAttestationKey.isInitialized) {
            initSoftwareAttestationKey()
        }

        val map = Cbor.decode(encodedConfiguration)
        val curve = EcCurve.fromInt(map["curve"].asNumber.toInt())
        val purposes = KeyPurpose.decodeSet(map["purposes"].asNumber)
        val passphraseRequired = map["passphraseRequired"].asBoolean
        val passphrase = map["passphrase"].asTstr
        return SoftwareCreateKeySettings.Builder(challenge)
            .setEcCurve(curve)
            .setKeyPurposes(purposes)
            .setValidityPeriod(validFrom, validUntil)
            .setPassphraseRequired(passphraseRequired, passphrase, null)
            .setAttestationKey(
                softwareAttestationKey,
                softwareAttestationKeySignatureAlgorithm,
                softwareAttestationKeyIssuer,
                softwareAttestationKeyCertification,
            )
            .build()
    }
}
