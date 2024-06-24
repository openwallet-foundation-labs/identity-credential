package com.android.identity.secure_area_test_app.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureEnclaveCreateKeySettings
import com.android.identity.securearea.SecureEnclaveKeyUnlockData
import com.android.identity.securearea.SecureEnclaveSecureArea
import com.android.identity.securearea.SecureEnclaveUserAuthType
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity.util.Logger
import com.android.identity.util.toHex
import kotlinx.datetime.Clock

private val TAG = "SecureEnclaveSecureAreaScreen"

private val secureEnclaveStorage = EphemeralStorageEngine()

private val secureEnclaveSecureArea = SecureEnclaveSecureArea(secureEnclaveStorage)

@Composable
actual fun SecureEnclaveSecureAreaScreen(showToast: (message: String) -> Unit) {

    LazyColumn {
        item {
            TextButton(
                onClick = { seTest(KeyPurpose.SIGN,
                    setOf(),
                    showToast) },
                content = { Text("P-256 Signature") }
            )
            TextButton(
                onClick = { seTest(KeyPurpose.SIGN,
                    setOf(SecureEnclaveUserAuthType.DEVICE_PASSCODE),
                    showToast) },
                content = { Text("P-256 Signature - Auth (Passcode)") }
            )
            TextButton(
                onClick = { seTest(KeyPurpose.SIGN,
                    setOf(SecureEnclaveUserAuthType.BIOMETRY_CURRENT_SET),
                    showToast) },
                content = { Text("P-256 Signature - Auth (Biometrics)") }
            )
            TextButton(
                onClick = { seTest(KeyPurpose.SIGN,
                    setOf(SecureEnclaveUserAuthType.DEVICE_PASSCODE, SecureEnclaveUserAuthType.BIOMETRY_CURRENT_SET),
                    showToast) },
                content = { Text("P-256 Signature - Auth (Passcode AND Biometrics)") }
            )
            TextButton(
                onClick = { seTest(KeyPurpose.SIGN,
                    setOf(SecureEnclaveUserAuthType.USER_PRESENCE),
                    showToast) },
                content = { Text("P-256 Signature - Auth (Passcode OR Biometrics)") }
            )
            TextButton(
                onClick = { seTest(KeyPurpose.AGREE_KEY,
                    setOf(),
                    showToast) },
                content = { Text("P-256 Key Agreement") }
            )
            TextButton(
                onClick = { seTest(KeyPurpose.AGREE_KEY,
                    setOf(SecureEnclaveUserAuthType.DEVICE_PASSCODE),
                    showToast) },
                content = { Text("P-256 Key Agreement - Auth (Passcode)") }
            )
            TextButton(
                onClick = { seTest(KeyPurpose.AGREE_KEY,
                    setOf(SecureEnclaveUserAuthType.BIOMETRY_CURRENT_SET),
                    showToast) },
                content = { Text("P-256 Key Agreement - Auth (Biometrics)") }
            )
            TextButton(
                onClick = { seTest(KeyPurpose.AGREE_KEY,
                    setOf(SecureEnclaveUserAuthType.DEVICE_PASSCODE, SecureEnclaveUserAuthType.BIOMETRY_CURRENT_SET),
                    showToast) },
                content = { Text("P-256 Key Agreement - Auth (Passcode AND Biometrics)") }
            )
            TextButton(
                onClick = { seTest(KeyPurpose.AGREE_KEY,
                    setOf(SecureEnclaveUserAuthType.USER_PRESENCE),
                    showToast) },
                content = { Text("P-256 Key Agreement - Auth (Passcode OR Biometrics)") }
            )
        }

    }
}

private fun seTest(
    keyPurpose: KeyPurpose,
    userAuthTypes: Set<SecureEnclaveUserAuthType>,
    showToast: (message: String) -> Unit
) {
    try {
        seTestUnguarded(keyPurpose, userAuthTypes, showToast)
    } catch (e: Throwable) {
        e.printStackTrace();
        showToast("${e.message}")
    }
}

private fun seTestUnguarded(
    keyPurpose: KeyPurpose,
    userAuthTypes: Set<SecureEnclaveUserAuthType>,
    showToast: (message: String) -> Unit
) {
    secureEnclaveSecureArea.createKey(
        "testKey",
        SecureEnclaveCreateKeySettings.Builder()
            .setKeyPurposes(setOf(keyPurpose))
            .setUserAuthenticationRequired(!userAuthTypes.isEmpty(), userAuthTypes)
            .build()
    )

    val laContext = platform.LocalAuthentication.LAContext()
    laContext.localizedReason = "Authenticate to use key"

    val keyUnlockData = SecureEnclaveKeyUnlockData(laContext)

    if (keyPurpose == KeyPurpose.SIGN) {
        val signingAlgorithm = Algorithm.ES256
        val dataToSign = "data".encodeToByteArray()
        val t0 = Clock.System.now()
        val signature = secureEnclaveSecureArea.sign(
            "testKey",
            signingAlgorithm,
            dataToSign,
            keyUnlockData
        )
        val t1 = Clock.System.now()
        Logger.d(
            TAG,
            "Made signature with key " +
                    "r=${signature.r.toHex} s=${signature.s.toHex}",
        )
        showToast("Signed (${t1 - t0})")
    } else {
        val otherKeyPairForEcdh = Crypto.createEcPrivateKey(EcCurve.P256)
        val t0 = Clock.System.now()
        val Zab = secureEnclaveSecureArea.keyAgreement(
            "testKey",
            otherKeyPairForEcdh.publicKey,
            keyUnlockData
        )
        val t1 = Clock.System.now()
        Logger.dHex(
            TAG,
            "Calculated ECDH ",
            Zab)
        showToast("ECDH (${t1 - t0})")
    }
}