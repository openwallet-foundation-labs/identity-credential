package com.android.identity.testapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.prompt.PromptModel
import com.android.identity.securearea.KeyLockedException
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.KeyUnlockInteractive
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.securearea.SecureAreaProvider
import com.android.identity.securearea.software.SoftwareCreateKeySettings
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.storage.ephemeral.EphemeralStorage
import com.android.identity.util.Logger
import com.android.identity.util.toHex
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.jetbrains.compose.ui.tooling.preview.Preview

private val TAG = "SoftwareSecureAreaScreen"

private val softwareSecureAreaProvider = SecureAreaProvider {
    SoftwareSecureArea.create(EphemeralStorage())
}

@Preview
@Composable
fun SoftwareSecureAreaScreen(
    promptModel: PromptModel,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope { promptModel }

    LazyColumn(
        modifier = Modifier.padding(8.dp)
    ) {
        for ((keyPurpose, keyPurposeDesc) in arrayOf(
            Pair(KeyPurpose.SIGN, "Signature"),
            Pair(KeyPurpose.AGREE_KEY, "Key Agreement")
        )) {
            for ((curve, curveName, purposes) in arrayOf(
                Triple(EcCurve.P256, "P-256", setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)),
                Triple(EcCurve.P384, "P-384", setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)),
                Triple(EcCurve.P521, "P-521", setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)),
                Triple(EcCurve.BRAINPOOLP256R1, "Brainpool 256", setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)),
                Triple(EcCurve.BRAINPOOLP320R1, "Brainpool 320", setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)),
                Triple(EcCurve.BRAINPOOLP384R1, "Brainpool 384", setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)),
                Triple(EcCurve.BRAINPOOLP512R1, "Brainpool 512", setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)),
                Triple(EcCurve.ED25519, "Ed25519", setOf(KeyPurpose.SIGN)),
                Triple(EcCurve.X25519, "X25519", setOf(KeyPurpose.AGREE_KEY)),
                Triple(EcCurve.ED448, "Ed448", setOf(KeyPurpose.SIGN)),
                Triple(EcCurve.X448, "X448", setOf(KeyPurpose.AGREE_KEY)),
            )) {
                if (!purposes.contains(keyPurpose)) {
                    // No common purpose
                    continue
                }
                for ((passphraseRequired, description) in arrayOf(
                    Pair(true, "- Passphrase"),
                    Pair(false, ""),
                )) {
                    // For brevity, only do passphrase for P-256 Signature and P-256 Key Agreement)
                    if (curve != EcCurve.P256) {
                        if (passphraseRequired) {
                            continue;
                        }
                    }

                    item {
                        TextButton(onClick = {

                            coroutineScope.launch {
                                swTest(
                                    keyPurpose = keyPurpose,
                                    curve = curve,
                                    passphrase = if (passphraseRequired) {
                                        "1111"
                                    } else {
                                        null
                                    },
                                    passphraseConstraints = if (passphraseRequired) {
                                        PassphraseConstraints.PIN_FOUR_DIGITS
                                    } else {
                                        null
                                    },
                                    showToast = showToast
                                )
                            }
                        })
                        {
                            Text(
                                text = "$curveName $keyPurposeDesc $description",
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun swTest(
    keyPurpose: KeyPurpose,
    curve: EcCurve,
    passphrase: String?,
    passphraseConstraints: PassphraseConstraints?,
    showToast: (message: String) -> Unit) {
    Logger.d(
        TAG,
        "swTest keyPurpose:$keyPurpose curve:$curve passphrase:$passphrase"
    )
    try {
        swTestUnguarded(keyPurpose, curve, passphrase, passphraseConstraints, showToast)
    } catch (e: Throwable) {
        e.printStackTrace();
        showToast("${e.message}")
    }
}

private suspend fun swTestUnguarded(
    keyPurpose: KeyPurpose,
    curve: EcCurve,
    passphrase: String?,
    passphraseConstraints: PassphraseConstraints?,
    showToast: (message: String) -> Unit) {

    val builder = SoftwareCreateKeySettings.Builder()
        .setEcCurve(curve)
        .setKeyPurposes(setOf(keyPurpose))
    if (passphrase != null) {
        builder.setPassphraseRequired(true, passphrase, passphraseConstraints)
    }

    val softwareSecureArea = softwareSecureAreaProvider.get()

    softwareSecureArea.createKey("testKey", builder.build())

    val interactiveUnlock = KeyUnlockInteractive(
        title = "Enter Knowledge Factor",
        subtitle = "This is used to decrypt the private key material. " +
                "In this sample the knowledge factor is '1111' but try " +
                "entering something else to check out error handling",
    )

    if (keyPurpose == KeyPurpose.SIGN) {
        try {
            val t0 = Clock.System.now()
            val signature = softwareSecureArea.sign(
                "testKey",
                "data".encodeToByteArray(),
                interactiveUnlock,
            )
            val t1 = Clock.System.now()
            Logger.d(
                TAG,
                "Made signature in " +
                        "r=${signature.r.toHex()} s=${signature.s.toHex()}"
            )
            showToast("Signed in (${t1 - t0})")
        } catch (e: KeyLockedException) {
            e.printStackTrace();
            showToast("${e.message}")
        }
    } else {
        val otherKeyPairForEcdh = Crypto.createEcPrivateKey(curve)
        try {
            val t0 = Clock.System.now()
            val Zab = softwareSecureArea.keyAgreement(
                "testKey",
                otherKeyPairForEcdh.publicKey,
                interactiveUnlock,
            )
            val t1 = Clock.System.now()
            Logger.dHex(
                TAG,
                "Calculated ECDH",
                Zab)
            showToast("ECDH in (${t1 - t0})")
        } catch (e: KeyLockedException) {
            e.printStackTrace();
            showToast("${e.message}")
        }
    }
}

