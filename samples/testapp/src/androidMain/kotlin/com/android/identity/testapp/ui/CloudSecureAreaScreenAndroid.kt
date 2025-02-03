package com.android.identity.secure_area_test_app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity.MODE_PRIVATE
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.android.securearea.cloud.CloudCreateKeySettings
import com.android.identity.android.securearea.cloud.CloudSecureArea
import com.android.identity.appsupport.ui.passphrase.PassphrasePromptProvider
import com.android.identity.cbor.Cbor
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.securearea.KeyUnlockInteractive
import com.android.identity.securearea.KeyAttestation
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.util.AndroidContexts
import com.android.identity.storage.ephemeral.EphemeralStorage
import com.android.identity.util.Logger
import com.android.identity.util.toBase64Url
import com.android.identity.util.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

private val TAG = "CloudSecureAreaScreen"

// On the Android Emulator, 10.0.2.2 points to the host so this will work
// nicely if you are running the server on the same machine you are running
// Android Studio on.
//
private val CSA_URL_DEFAULT: String = "http://10.0.2.2:8080/server/csa"

private var cloudSecureArea: CloudSecureArea? = null

private val sharedPreferences = AndroidContexts.applicationContext.getSharedPreferences("default", MODE_PRIVATE)

private data class CsaPassphraseTestConfiguration(
    val keyPurpose: KeyPurpose,
    val curve: EcCurve,
    val authRequired: Boolean,
    val authTimeoutMillis: Long,
    val userAuthType: Set<UserAuthenticationType>,
    val biometricConfirmationRequired: Boolean,
    val passphrase: String?
)

// This is Android-only for now.
@Composable
actual fun CloudSecureAreaScreen(
    showToast: (message: String) -> Unit,
    onViewCertificate: (encodedCertificateData: String) -> Unit
) {
    var connectText by remember { mutableStateOf(
        if (cloudSecureArea == null) {
            "Click to connect to Cloud Secure Area"
        } else {
            "Connected to ${cloudSecureArea!!.serverUrl}"
        }
    ) }
    var connectColor by remember {
        if (cloudSecureArea == null) {
            mutableStateOf(Color.Red)
        } else {
            mutableStateOf(Color.Blue)
        }
    }
    val showConnectDialog = remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    PassphrasePromptProvider()

    if (showConnectDialog.value) {
        CsaConnectDialog(
            sharedPreferences.getString("csaUrl", CSA_URL_DEFAULT)!!,
            onDismissRequest = {
                showConnectDialog.value = false
            },
            onConnectButtonClicked = { url: String, walletPin: String, constraints: PassphraseConstraints ->
                sharedPreferences.edit().putString("csaUrl", url).apply()
                showConnectDialog.value = false
                coroutineScope.launch {
                    cloudSecureArea = CloudSecureArea.create(
                        EphemeralStorage(),
                        "CloudSecureArea",
                        url
                    )
                    try {
                        cloudSecureArea!!.register(
                            walletPin,
                            constraints
                        ) { true }
                        showToast("Registered with CSA")
                        connectText =
                            "Connected to ${cloudSecureArea!!.serverUrl}"
                        connectColor = Color.Blue
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        cloudSecureArea = null
                        showToast("${e.message}")
                    }
                }
            }
        )
    }

    LazyColumn {

        item {
            TextButton(onClick = {
                coroutineScope.launch {
                    if (cloudSecureArea != null) {
                        cloudSecureArea!!.unregister()
                        cloudSecureArea = null
                        connectText = "Click to connect to Cloud Secure Area"
                        connectColor = Color.Red
                    } else {
                        showConnectDialog.value = true
                    }
                }
            })
            {
                Text(
                    text = connectText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = connectColor
                )
            }
        }

        item {
            TextButton(onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    val attestation = csaAttestation(showToast)
                    if (attestation != null) {
                        Logger.d(TAG, "attestation: ${attestation.certChain}")
                        withContext(Dispatchers.Main) {
                            onViewCertificate(Cbor.encode(attestation.certChain!!.toDataItem()).toBase64Url())
                        }
                    }
                }
            })
            {
                Text(
                    text = "CSA Attestation",
                    fontSize = 15.sp
                )
            }
        }

        for ((keyPurpose, keyPurposeDesc) in arrayOf(
            Pair(KeyPurpose.SIGN, "Signature"),
            Pair(KeyPurpose.AGREE_KEY, "Key Agreement")
        )) {
            for ((curve, curveName, purposes) in arrayOf(
                Triple(EcCurve.P256, "P-256", setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)),
                Triple(EcCurve.P384, "P-384", setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)),
                Triple(EcCurve.P521, "P-521", setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)),
                Triple(
                    EcCurve.BRAINPOOLP256R1,
                    "Brainpool 256",
                    setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)
                ),
                Triple(
                    EcCurve.BRAINPOOLP320R1,
                    "Brainpool 320",
                    setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)
                ),
                Triple(
                    EcCurve.BRAINPOOLP384R1,
                    "Brainpool 384",
                    setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)
                ),
                Triple(
                    EcCurve.BRAINPOOLP512R1,
                    "Brainpool 512",
                    setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)
                ),
                Triple(EcCurve.ED25519, "Ed25519", setOf(KeyPurpose.SIGN)),
                Triple(EcCurve.X25519, "X25519", setOf(KeyPurpose.AGREE_KEY)),
                Triple(EcCurve.ED448, "Ed448", setOf(KeyPurpose.SIGN)),
                Triple(EcCurve.X448, "X448", setOf(KeyPurpose.AGREE_KEY)),
            )) {
                if (!purposes.contains(keyPurpose)) {
                    // No common purpose
                    continue
                }

                for ((passphraseRequired, passphraseDescription) in arrayOf(
                    Pair(false, ""),
                    Pair(true, "- Passphrase ")
                )) {
                    for ((userAuthType, authTimeout, authDesc) in arrayOf(
                        Triple(setOf<UserAuthenticationType>(), 0L, ""),
                        Triple(
                            setOf(UserAuthenticationType.LSKF, UserAuthenticationType.BIOMETRIC),
                            0L,
                            "- Auth"
                        ),
                        Triple(
                            setOf(UserAuthenticationType.LSKF, UserAuthenticationType.BIOMETRIC),
                            10 * 1000L,
                            "- Auth (10 sec)"
                        ),
                        Triple(setOf(UserAuthenticationType.LSKF), 0L, "- Auth (LSKF Only)"),
                        Triple(
                            setOf(UserAuthenticationType.BIOMETRIC),
                            0L,
                            "- Auth (Biometric Only)"
                        ),
                        Triple(
                            setOf(UserAuthenticationType.LSKF, UserAuthenticationType.BIOMETRIC),
                            -1L,
                            "- Auth (No Confirmation)"
                        ),
                    )) {
                        // For brevity, only do passphrase and auth for first item (P-256 Signature)
                        if (!(curve == EcCurve.P256 && keyPurpose == KeyPurpose.SIGN)) {
                            if (!userAuthType.isEmpty() || passphraseRequired) {
                                continue
                            }
                        }

                        item {
                            TextButton(onClick = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    csaTest(
                                        keyPurpose = keyPurpose,
                                        curve = curve,
                                        authRequired = !userAuthType.isEmpty(),
                                        authTimeoutMillis = if (authTimeout < 0L) 0L else authTimeout,
                                        userAuthType = userAuthType,
                                        biometricConfirmationRequired = authTimeout >= 0L,
                                        passphraseRequired = passphraseRequired,
                                        showToast = showToast
                                    )
                                }
                            })
                            {
                                Text(
                                    text = "$curveName $keyPurposeDesc $passphraseDescription$authDesc",
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowCertificateDialog(attestation: X509CertChain,
                                  onDismissRequest: () -> Unit) {
    var certNumber by rememberSaveable() { mutableStateOf(0) }
    if (certNumber < 0 || certNumber >= attestation.certificates.size) {
        certNumber = 0
    }
    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(650.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Certificates",
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge
                )
                Row() {
                    Text(
                        text = "Certificate ${certNumber + 1} of ${attestation.certificates.size}",
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        enabled = (certNumber > 0),
                        onClick = {
                            certNumber -= 1
                        }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    IconButton(
                        enabled = (certNumber < attestation.certificates.size - 1),
                        onClick = {
                            certNumber += 1
                        }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
                    }
                }
                Column(
                    modifier = Modifier
                        .size(470.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = styledX509CertificateText(
                            attestation.certificates[certNumber].javaX509Certificate.toString()
                        ),
                        //text = attestation[certNumber].toString(),
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { onDismissRequest() },
                    ) {
                        Text("Close")
                    }
                }

            }
        }
    }
}

@Composable
private fun styledX509CertificateText(certificateText: String): AnnotatedString {
    val lines = certificateText.split("\n")
    return buildAnnotatedString {
        for (line in lines) {
            var colonPos = line.indexOf(':')
            if (colonPos > 0 && line.length > (colonPos + 1) && !line[colonPos + 1].isWhitespace()) {
                colonPos = -1
            }
            if (colonPos > 0) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(line.subSequence(0, colonPos))
                }
                append(line.subSequence(colonPos,line.length))
            } else {
                append(line)
            }
            append("\n")
        }
    }
}

private data class CsaPassphraseChoice(
    val description: String,
    val constraints: PassphraseConstraints,
    val passphrase: String,
)

@Composable
fun CsaConnectDialog(
    url: String,
    onDismissRequest: () -> Unit,
    onConnectButtonClicked: (url: String, walletPin: String, constraints: PassphraseConstraints) -> Unit,
) {
    var urlTextField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(url))
    }

    val walletPinRadioOptions = remember {
        listOf(
            CsaPassphraseChoice("4 Digits (1111)", PassphraseConstraints.PIN_FOUR_DIGITS, "1111"),
            CsaPassphraseChoice("4 Digits (1234)", PassphraseConstraints.PIN_FOUR_DIGITS, "1234"),
            CsaPassphraseChoice("6 Digits (111111)", PassphraseConstraints.PIN_SIX_DIGITS, "111111"),
            CsaPassphraseChoice("4+ Digits (12345)", PassphraseConstraints.PIN_FOUR_DIGITS_OR_LONGER, "12345"),
            CsaPassphraseChoice("4 Chars (abcd)", PassphraseConstraints.PASSPHRASE_FOUR_CHARS, "abcd"),
            CsaPassphraseChoice("4+ Chars (abcde)", PassphraseConstraints.PASSPHRASE_FOUR_CHARS_OR_LONGER, "abcde"),
            CsaPassphraseChoice("No Constraints (multipaz)", PassphraseConstraints.NONE, "multipaz"),
        )
    }

    val (selectedOption, onOptionSelected) = remember { mutableStateOf(walletPinRadioOptions[0]) }

    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Cloud Secure Area",
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "URL for the Cloud Secure Area",
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyMedium
                )

                TextField(
                    value = urlTextField,
                    maxLines = 3,
                    onValueChange = { urlTextField = it },
                    textStyle = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Knowledge-Based Factor to use",
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyMedium
                )

                walletPinRadioOptions.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (entry == selectedOption),
                                onClick = { onOptionSelected(entry) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = (entry == selectedOption),
                            onClick = null
                        )
                        Text(
                            text = entry.description.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { onDismissRequest() },
                    ) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = { onConnectButtonClicked(
                            urlTextField.text,
                            selectedOption.passphrase,
                            selectedOption.constraints
                        ) },
                    ) {
                        Text("Connect")
                    }
                }

            }
        }
    }
}

private suspend fun csaAttestation(
    showToast: (message: String) -> Unit
): KeyAttestation? {
    if (cloudSecureArea == null) {
        showToast("First connect to the CSA")
        return null
    }

    val now = Clock.System.now()
    val thirtyDaysFromNow = now + 30.days
    cloudSecureArea!!.createKey(
        "testKey",
        CloudCreateKeySettings.Builder("Challenge".toByteArray())
            .setUserAuthenticationRequired(
                true,
                10*1000,
                setOf(UserAuthenticationType.LSKF, UserAuthenticationType.BIOMETRIC)
            )
            .setValidityPeriod(now, thirtyDaysFromNow)
            .build()
    )
    return cloudSecureArea!!.getKeyInfo("testKey").attestation
}

private suspend fun csaTest(
    keyPurpose: KeyPurpose,
    curve: EcCurve,
    authRequired: Boolean,
    authTimeoutMillis: Long,
    userAuthType: Set<UserAuthenticationType>,
    biometricConfirmationRequired: Boolean,
    passphraseRequired: Boolean,
    showToast: (message: String) -> Unit
) {
    Logger.d(
        TAG,
        "cksTest keyPurpose:$keyPurpose curve:$curve authRequired:$authRequired authTimeout:$authTimeoutMillis"
    )
    try {
        csaTestUnguarded(
            keyPurpose, curve, authRequired, authTimeoutMillis, userAuthType,
            biometricConfirmationRequired, passphraseRequired, showToast
        )
    } catch (e: Throwable) {
        showToast("${e.message}")
    }
}

private suspend fun csaTestUnguarded(
    keyPurpose: KeyPurpose,
    curve: EcCurve,
    authRequired: Boolean,
    authTimeoutMillis: Long,
    userAuthType: Set<UserAuthenticationType>,
    biometricConfirmationRequired: Boolean,
    passphraseRequired: Boolean,
    showToast: (message: String) -> Unit
) {

    if (cloudSecureArea == null) {
        showToast("First connect to the CSA")
        return
    }

    val builder = CloudCreateKeySettings.Builder("Challenge".toByteArray())
        .setEcCurve(curve)
        .setKeyPurposes(setOf(keyPurpose))
        .setUserAuthenticationRequired(authRequired, authTimeoutMillis, userAuthType)
    if (passphraseRequired) {
        builder.setPassphraseRequired(true)
    }
    cloudSecureArea!!.createKey("testKey", builder.build())

    if (keyPurpose == KeyPurpose.SIGN) {
        val signingAlgorithm = curve.defaultSigningAlgorithm
        val t0 = System.currentTimeMillis()
        val signature = cloudSecureArea!!.sign(
            "testKey",
            signingAlgorithm,
            "data".toByteArray(),
            KeyUnlockInteractive(requireConfirmation = biometricConfirmationRequired))
        val t1 = System.currentTimeMillis()
        Logger.d(
            TAG,
            "Made signature with key " +
                    "r=${signature.r.toHex()} s=${signature.s.toHex()}",
        )
        showToast("EC signature in (${t1 - t0} msec)")
    } else {
        val otherKeyPairForEcdh = Crypto.createEcPrivateKey(curve)
        val t0 = System.currentTimeMillis()
        val Zab = cloudSecureArea!!.keyAgreement(
            "testKey",
            otherKeyPairForEcdh.publicKey,
            KeyUnlockInteractive(requireConfirmation = biometricConfirmationRequired))
        val t1 = System.currentTimeMillis()
        Logger.dHex(
            TAG,
            "Calculated ECDH",
            Zab)
        showToast("ECDH in (${t1 - t0} msec)")
    }
}
