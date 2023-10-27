/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity.secure_area_test_app

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ConditionVariable
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.CryptoObject
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.internal.Util
import com.android.identity.secure_area_test_app.ui.theme.IdentityCredentialTheme
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureArea.ALGORITHM_EDDSA
import com.android.identity.securearea.SecureArea.ALGORITHM_ES256
import com.android.identity.securearea.SecureArea.ALGORITHM_ES384
import com.android.identity.securearea.SecureArea.ALGORITHM_ES512
import com.android.identity.securearea.SecureArea.EC_CURVE_BRAINPOOLP256R1
import com.android.identity.securearea.SecureArea.EC_CURVE_BRAINPOOLP320R1
import com.android.identity.securearea.SecureArea.EC_CURVE_BRAINPOOLP384R1
import com.android.identity.securearea.SecureArea.EC_CURVE_BRAINPOOLP512R1
import com.android.identity.securearea.SecureArea.EC_CURVE_ED25519
import com.android.identity.securearea.SecureArea.EC_CURVE_ED448
import com.android.identity.securearea.SecureArea.EC_CURVE_P256
import com.android.identity.securearea.SecureArea.EC_CURVE_P384
import com.android.identity.securearea.SecureArea.EC_CURVE_P521
import com.android.identity.securearea.SecureArea.EC_CURVE_X25519
import com.android.identity.securearea.SecureArea.EC_CURVE_X448
import com.android.identity.securearea.SecureArea.KEY_PURPOSE_AGREE_KEY
import com.android.identity.securearea.SecureArea.KEY_PURPOSE_SIGN
import com.android.identity.securearea.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity :  FragmentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var softwareSecureArea: SoftwareSecureArea

    private lateinit var androidKeystoreCapabilities: AndroidKeystoreSecureArea.Capabilities
    private lateinit var androidKeystoreSecureArea: AndroidKeystoreSecureArea
    private lateinit var androidKeystoreStorage: AndroidStorageEngine

    private var keymintVersionTee: Int = 0
    private var keymintVersionStrongBox: Int = 0

    private var executorService = Executors.newSingleThreadExecutor()

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var softwareAttestationKey: PrivateKey
    private lateinit var softwareAttestationKeySignatureAlgorithm: String
    private lateinit var softwareAttestationKeyCertification: List<X509Certificate>

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

    private fun getFeatureVersionKeystore(appContext: Context, useStrongbox: Boolean): Int {
        var feature = PackageManager.FEATURE_HARDWARE_KEYSTORE
        if (useStrongbox) {
            feature = PackageManager.FEATURE_STRONGBOX_KEYSTORE
        }
        val pm = appContext.packageManager
        var featureVersionFromPm = 0
        if (pm.hasSystemFeature(feature)) {
            var info: FeatureInfo? = null
            val infos = pm.systemAvailableFeatures
            for (n in infos.indices) {
                val i = infos[n]
                if (i.name == feature) {
                    info = i
                    break
                }
            }
            if (info != null) {
                featureVersionFromPm = info.version
            }
        }
        return featureVersionFromPm
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This is needed to prefer BouncyCastle bundled with the app instead of the Conscrypt
        // based implementation included in the OS itself.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())

        androidKeystoreStorage = AndroidStorageEngine.Builder(
            applicationContext,
            File(applicationContext.dataDir, "ic-testing")
        ).build()

        androidKeystoreSecureArea =
            AndroidKeystoreSecureArea(
                applicationContext,
                androidKeystoreStorage
            )
        initSoftwareAttestationKey()

        androidKeystoreCapabilities = AndroidKeystoreSecureArea.Capabilities(applicationContext)

        softwareSecureArea = SoftwareSecureArea(androidKeystoreStorage)

        keymintVersionTee = getFeatureVersionKeystore(applicationContext, false)
        keymintVersionStrongBox = getFeatureVersionKeystore(applicationContext, true)

        sharedPreferences = getSharedPreferences("default", MODE_PRIVATE)

        setContent {
            ListOfSecureAreaTests()
        }
    }

    data class swPassphraseTestConfiguration(
        val keyPurpose: Int,
        val curve: Int,
        val description: String
    )

    @Preview
    @Composable
    private fun ListOfSecureAreaTests() {
        IdentityCredentialTheme {
            // A surface container using the 'background' color from the theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            )
            {
                val showCapabilitiesDialog = remember { mutableStateOf<AndroidKeystoreSecureArea.Capabilities?>(null) }
                val showCertificateDialog = remember { mutableStateOf<List<X509Certificate>>(ArrayList()) }
                val swShowPassphraseDialog = remember { mutableStateOf<swPassphraseTestConfiguration?>(null) }

                if (showCapabilitiesDialog.value != null) {
                    ShowCapabilitiesDialog(
                        showCapabilitiesDialog.value!!,
                        onDismissRequest = {
                            showCapabilitiesDialog.value = null
                        })
                }

                if (showCertificateDialog.value.size > 0) {
                    ShowCertificateDialog(showCertificateDialog.value,
                        onDismissRequest = {
                            showCertificateDialog.value = ArrayList()
                        })
                }

                if (swShowPassphraseDialog.value != null) {
                    ShowPassphraseDialog(
                        onDismissRequest = {
                            swShowPassphraseDialog.value = null;
                        },
                        onContinueButtonClicked = { passphraseEnteredByUser: String ->
                            val configuration = swShowPassphraseDialog.value!!
                            // Does a lot of I/O, cannot run on UI thread
                            executorService.execute(kotlinx.coroutines.Runnable {
                                if (Looper.myLooper() == null) {
                                    Looper.prepare()
                                }
                                swTest(
                                    configuration.keyPurpose,
                                    configuration.curve,
                                    "1111",
                                    passphraseEnteredByUser
                                )
                            })
                            swShowPassphraseDialog.value = null;
                        }
                    )
                }

                LazyColumn {
                    item {
                        Text(
                            text = "Android Keystore Secure Area",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        TextButton(onClick = {
                            // Does a lot of I/O, cannot run on UI thread
                            executorService.execute(kotlinx.coroutines.Runnable {
                                if (Looper.myLooper() == null) {
                                    Looper.prepare()
                                }
                                showCapabilitiesDialog.value = androidKeystoreCapabilities
                            })
                        })
                        {
                            Text(
                                text = "Versions and Capabilities",
                                fontSize = 15.sp
                            )
                        }
                    }

                    item {
                        TextButton(onClick = {
                            // Does a lot of I/O, cannot run on UI thread
                            executorService.execute(kotlinx.coroutines.Runnable {
                                if (Looper.myLooper() == null) {
                                    Looper.prepare()
                                }
                                val attestation = aksAttestation(false)
                                Logger.d(TAG, "attestation: " + attestation)
                                showCertificateDialog.value = attestation
                            })
                        })
                        {
                            Text(
                                text = "Attestation",
                                fontSize = 15.sp
                            )
                        }
                    }

                    item {
                        TextButton(onClick = {
                            // Does a lot of I/O, cannot run on UI thread
                            executorService.execute(kotlinx.coroutines.Runnable {
                                if (Looper.myLooper() == null) {
                                    Looper.prepare()
                                }
                                val attestation = aksAttestation(true)
                                Logger.d(TAG, "attestation: " + attestation)
                                showCertificateDialog.value = attestation
                            })
                        })
                        {
                            Text(
                                text = "StrongBox Attestation",
                                fontSize = 15.sp
                            )
                        }
                    }

                    for ((strongBox, strongBoxDesc) in arrayOf(
                        Pair(false, ""), Pair(true, "StrongBox ")
                    )) {
                        for ((keyPurpose, keyPurposeDesc) in arrayOf(
                            Pair(KEY_PURPOSE_SIGN, "Signature"),
                            Pair(KEY_PURPOSE_AGREE_KEY, "Key Agreement")
                        )) {
                            for ((curve, curveName, purposes) in arrayOf(
                                Triple(EC_CURVE_P256, "P-256", KEY_PURPOSE_SIGN + KEY_PURPOSE_AGREE_KEY),
                                Triple(EC_CURVE_ED25519, "Ed25519", KEY_PURPOSE_SIGN),
                                Triple(EC_CURVE_X25519, "X25519", KEY_PURPOSE_AGREE_KEY),
                            )) {
                                if ((keyPurpose and purposes) == 0) {
                                    // No common purpose
                                    continue
                                }

                                val AUTH_NONE = 0
                                val AUTH_LSKF_OR_BIOMETRIC = AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_BIOMETRIC +
                                        AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_LSKF
                                val AUTH_LSKF_ONLY = AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_LSKF
                                val AUTH_BIOMETRIC_ONLY = AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_BIOMETRIC
                                for ((userAuthType, authTimeout, authDesc) in arrayOf(
                                    Triple(AUTH_NONE, 0L, ""),
                                    Triple(AUTH_LSKF_OR_BIOMETRIC, 0L, "- Auth"),
                                    Triple(AUTH_LSKF_OR_BIOMETRIC, 10 * 1000L, "- Auth (10 sec)"),
                                    Triple(AUTH_LSKF_ONLY, 0L, "- Auth (LSKF Only)"),
                                    Triple(AUTH_BIOMETRIC_ONLY, 0L, "- Auth (Biometric Only)"),
                                    Triple(AUTH_LSKF_OR_BIOMETRIC, -1L, "- Auth (No Confirmation)"),
                                )) {
                                    // For brevity, Only do auth for P-256 Sign and Mac
                                    if (curve != EC_CURVE_P256 && userAuthType != AUTH_NONE) {
                                        continue
                                    }

                                    val biometricConfirmationRequired = (authTimeout >= 0L)
                                    item {
                                        TextButton(onClick = {
                                            // Does a lot of I/O, cannot run on UI thread
                                            executorService.execute(kotlinx.coroutines.Runnable {
                                                if (Looper.myLooper() == null) {
                                                    Looper.prepare()
                                                }
                                                aksTest(
                                                    keyPurpose,
                                                    curve,
                                                    userAuthType != AUTH_NONE,
                                                    if (authTimeout < 0L) 0L else authTimeout,
                                                    userAuthType,
                                                    biometricConfirmationRequired,
                                                    strongBox
                                                )
                                            })
                                        })
                                        {
                                            Text(
                                                text = "$strongBoxDesc$curveName $keyPurposeDesc $authDesc",
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Software Secure Area",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        TextButton(onClick = {
                            // Does a lot of I/O, cannot run on UI thread
                            executorService.execute(kotlinx.coroutines.Runnable {
                                if (Looper.myLooper() == null) {
                                    Looper.prepare()
                                }
                                val attestation = swAttestation()
                                Logger.d(TAG, "attestation: " + attestation)
                                showCertificateDialog.value = attestation
                            })
                        })
                        {
                            Text(
                                text = "Attestation",
                                fontSize = 15.sp
                            )
                        }
                    }

                    for ((keyPurpose, keyPurposeDesc) in arrayOf(
                        Pair(KEY_PURPOSE_SIGN, "Signature"),
                        Pair(KEY_PURPOSE_AGREE_KEY, "Key Agreement")
                    )) {
                        for ((curve, curveName, purposes) in arrayOf(
                            Triple(EC_CURVE_P256, "P-256", KEY_PURPOSE_SIGN + KEY_PURPOSE_AGREE_KEY),
                            Triple(EC_CURVE_P384, "P-384", KEY_PURPOSE_SIGN + KEY_PURPOSE_AGREE_KEY),
                            Triple(EC_CURVE_P521, "P-521", KEY_PURPOSE_SIGN + KEY_PURPOSE_AGREE_KEY),
                            Triple(EC_CURVE_BRAINPOOLP256R1, "Brainpool 256", KEY_PURPOSE_SIGN + KEY_PURPOSE_AGREE_KEY),
                            Triple(EC_CURVE_BRAINPOOLP320R1, "Brainpool 320", KEY_PURPOSE_SIGN + KEY_PURPOSE_AGREE_KEY),
                            Triple(EC_CURVE_BRAINPOOLP384R1, "Brainpool 384", KEY_PURPOSE_SIGN + KEY_PURPOSE_AGREE_KEY),
                            Triple(EC_CURVE_BRAINPOOLP512R1, "Brainpool 512", KEY_PURPOSE_SIGN + KEY_PURPOSE_AGREE_KEY),
                            Triple(EC_CURVE_ED25519, "Ed25519", KEY_PURPOSE_SIGN),
                            Triple(EC_CURVE_X25519, "X25519", KEY_PURPOSE_AGREE_KEY),
                            Triple(EC_CURVE_ED448, "Ed448", KEY_PURPOSE_SIGN),
                            Triple(EC_CURVE_X448, "X448", KEY_PURPOSE_AGREE_KEY),
                        )) {
                            if ((keyPurpose and purposes) == 0) {
                                // No common purpose
                                continue
                            }
                            for ((passphraseRequired, description) in arrayOf(
                                Pair(true, "- Passphrase"),
                                Pair(false, ""),
                            )) {
                                // For brevity, only do passphrase for first item (P-256 Signature)
                                if (!(keyPurpose == KEY_PURPOSE_SIGN && curve == EC_CURVE_P256)) {
                                    if (passphraseRequired) {
                                        continue;
                                    }
                                }

                                item {
                                    TextButton(onClick = {

                                        if (passphraseRequired) {
                                            swShowPassphraseDialog.value =
                                                swPassphraseTestConfiguration(keyPurpose, curve, description)
                                        } else {
                                            // Does a lot of I/O, cannot run on UI thread
                                            executorService.execute(kotlinx.coroutines.Runnable {
                                                if (Looper.myLooper() == null) {
                                                    Looper.prepare()
                                                }
                                                swTest(
                                                    keyPurpose,
                                                    curve,
                                                    null,
                                                    null
                                                )
                                            })
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
        }
    }

    @Composable
    fun ShowCapabilitiesDialog(capabilities: AndroidKeystoreSecureArea.Capabilities,
                               onDismissRequest: () -> Unit) {
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Versions and Capabilities",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Column(
                        modifier = Modifier
                            .size(400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Would be nice to show first API level but that's only available to tests.
                        Text(
                            text = "API Level: ${Build.VERSION.SDK_INT}\n" +
                                    "TEE KeyMint version: ${keymintVersionTee}\n" +
                                    "StrongBox KeyMint version: ${keymintVersionStrongBox}",
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Start,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val userAuthText =
                            if (capabilities.multipleAuthenticationTypesSupported)
                                "LSKF or Bio or LSKF+Bio"
                            else "Only LSKF+Bio"
                        val secureLockScreenText =
                            if (capabilities.secureLockScreenSetup)
                                "Enabled"
                            else
                                "Not Enabled"
                        Text(
                            text = "User Auth: $userAuthText\n" +
                                    "Secure Lock Screen: $secureLockScreenText",
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Start,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Attest Key support (TEE): ${capabilities.attestKeySupported}\n" +
                                    "Key Agreement support (TEE): ${capabilities.keyAgreementSupported}\n" +
                                    "Curve 25519 support (TEE): ${capabilities.curve25519Supported}",
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Start,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "StrongBox Available: ${capabilities.strongBoxSupported}\n" +
                                    "Attest Key support (StrongBox): ${capabilities.strongBoxAttestKeySupported}\n" +
                                    "Key Agreement support (StrongBox): ${capabilities.strongBoxKeyAgreementSupported}\n" +
                                    "Curve 25519 support (StrongBox): ${capabilities.strongBoxCurve25519Supported}",
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
    fun ShowCertificateDialog(attestation: List<X509Certificate>,
                              onDismissRequest: () -> Unit) {
        var certNumber by rememberSaveable() { mutableStateOf(0) }
        if (certNumber < 0 || certNumber >= attestation.size) {
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
                            text = "Certificate ${certNumber + 1} of ${attestation.size}",
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
                            Icon(Icons.Filled.ArrowBack, "Back")
                        }
                        IconButton(
                            enabled = (certNumber < attestation.size - 1),
                            onClick = {
                                certNumber += 1
                            }) {
                            Icon(Icons.Filled.ArrowForward, "Forward")
                        }
                    }
                    Column(
                        modifier = Modifier
                            .size(470.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = styledX509CertificateText(attestation[certNumber].toString()),
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

    @Composable
    fun ShowPassphraseDialog(
        onDismissRequest: () -> Unit,
        onContinueButtonClicked: (passphrase: String) -> Unit,
    ) {
        var passphraseTextField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(""))
        }
        var showPassphrase by remember { mutableStateOf(value = false) }
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(275.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Enter passphrase to use key",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = "The passphrase is '1111'.",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    TextField(
                        value = passphraseTextField,
                        maxLines = 3,
                        onValueChange = { passphraseTextField = it },
                        textStyle = MaterialTheme.typography.bodySmall,
                        visualTransformation = if (showPassphrase) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            if (showPassphrase) {
                                IconButton(onClick = { showPassphrase = false }) {
                                    Icon(
                                        imageVector = Icons.Filled.Visibility,
                                        contentDescription = "hide_password"
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { showPassphrase = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.VisibilityOff,
                                        contentDescription = "hide_password"
                                    )
                                }
                            }
                        }
                    )

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
                            onClick = { onContinueButtonClicked(passphraseTextField.text) },
                        ) {
                            Text("Continue")
                        }
                    }

                }
            }
        }
    }

    private fun getNaturalAlgorithmForCurve(ecCurve: Int): Int {
        return when (ecCurve) {
            EC_CURVE_P256 -> ALGORITHM_ES256
            EC_CURVE_P384 -> ALGORITHM_ES384
            EC_CURVE_P521 -> ALGORITHM_ES512
            EC_CURVE_BRAINPOOLP256R1 -> ALGORITHM_ES256
            EC_CURVE_BRAINPOOLP320R1 -> ALGORITHM_ES384
            EC_CURVE_BRAINPOOLP384R1 -> ALGORITHM_ES384
            EC_CURVE_BRAINPOOLP512R1 -> ALGORITHM_ES512
            EC_CURVE_ED25519 -> ALGORITHM_EDDSA
            EC_CURVE_ED448 -> ALGORITHM_EDDSA
            else -> {throw IllegalStateException("Unexpected curve " + ecCurve)}
        }
    }

    private fun aksAttestation(strongBox: Boolean): List<X509Certificate> {
        val now = Instant.now().toEpochMilli()
        val thirtyDaysFromNow = Instant.now().toEpochMilli() + 30*24*3600*1000L
        androidKeystoreSecureArea.createKey(
            "testKey",
            AndroidKeystoreSecureArea.CreateKeySettings.Builder("Challenge".toByteArray())
                .setUserAuthenticationRequired(
                    true, 10*1000,
                    AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_BIOMETRIC +
                            AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_LSKF
                )
                .setValidityPeriod(Timestamp.ofEpochMilli(now), Timestamp.ofEpochMilli(thirtyDaysFromNow))
                .setUseStrongBox(strongBox)
                .build()
        )
        Logger.dHex(TAG, "encodedLeaf", androidKeystoreSecureArea.getKeyInfo("testKey").attestation[0].encoded)
        return androidKeystoreSecureArea.getKeyInfo("testKey").attestation
    }

    private fun aksTest(
        keyPurpose: Int,
        curve: Int,
        authRequired: Boolean,
        authTimeoutMillis: Long,
        userAuthType: Int,
        biometricConfirmationRequired: Boolean,
        strongBox: Boolean) {
        Logger.d(
            TAG,
            "aksTest keyPurpose:$keyPurpose curve:$curve authRequired:$authRequired authTimeout:$authTimeoutMillis strongBox:$strongBox"
        )
        try {
            aksTestUnguarded(keyPurpose, curve, authRequired, authTimeoutMillis, userAuthType, biometricConfirmationRequired, strongBox)
        } catch (e: Exception) {
            e.printStackTrace();
            Toast.makeText(
                applicationContext, "${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun aksTestUnguarded(
        keyPurpose: Int,
        curve: Int,
        authRequired: Boolean,
        authTimeoutMillis: Long,
        userAuthType: Int,
        biometricConfirmationRequired: Boolean,
        strongBox: Boolean) {

        androidKeystoreSecureArea.createKey(
            "testKey",
            AndroidKeystoreSecureArea.CreateKeySettings.Builder("Challenge".toByteArray())
                .setKeyPurposes(keyPurpose)
                .setUserAuthenticationRequired(
                    authRequired, authTimeoutMillis, userAuthType)
                .setUseStrongBox(strongBox)
                .build()
        )

        val keyInfo = androidKeystoreSecureArea.getKeyInfo("testKey")
        val publicKey = keyInfo.attestation.get(0).publicKey

        if (keyPurpose == KEY_PURPOSE_SIGN) {
            val signingAlgorithm = getNaturalAlgorithmForCurve(curve)
            val dataToSign = "data".toByteArray()
            try {
                val t0 = System.currentTimeMillis()
                val derSignature = androidKeystoreSecureArea.sign(
                    "testKey",
                    signingAlgorithm,
                    dataToSign,
                    null)
                val t1 = System.currentTimeMillis()
                Logger.dHex(
                    TAG,
                    "Made signature with key without authentication",
                    derSignature
                )
                Toast.makeText(
                    applicationContext, "Signed w/o authn (${t1 - t0} msec)",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: SecureArea.KeyLockedException) {
                val unlockData = AndroidKeystoreSecureArea.KeyUnlockData("testKey")
                doUserAuth(
                    "Unlock to sign with key",
                    unlockData.getCryptoObjectForSigning(ALGORITHM_ES256),
                    false,
                    biometricConfirmationRequired,
                    onAuthSuccees = {
                        Logger.d(TAG, "onAuthSuccess")

                        val t0 = System.currentTimeMillis()
                        val derSignature = androidKeystoreSecureArea.sign(
                            "testKey",
                            signingAlgorithm,
                            dataToSign,
                            unlockData)
                        val t1 = System.currentTimeMillis()
                        Logger.dHex(
                            TAG,
                            "Made signature with key after authentication",
                            derSignature
                        )
                        Toast.makeText(
                            applicationContext, "Signed after authn (${t1 - t0} msec)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onAuthFailure = {
                        Logger.d(TAG, "onAuthFailure")
                    },
                    onDismissed = {
                        Logger.d(TAG, "onDismissed")
                    })
            }
        } else {
            val otherKeyPairForEcdh = Util.createEphemeralKeyPair(curve)
            try {
                val t0 = System.currentTimeMillis()
                val Zab = androidKeystoreSecureArea.keyAgreement(
                    "testKey",
                    otherKeyPairForEcdh.public,
                    null)
                val t1 = System.currentTimeMillis()
                Logger.dHex(
                    TAG,
                    "Calculated ECDH",
                    Zab)
                Toast.makeText(applicationContext, "ECDH w/o authn (${t1 - t0} msec)",
                    Toast.LENGTH_SHORT).show()
            } catch (e: SecureArea.KeyLockedException) {
                val unlockData = AndroidKeystoreSecureArea.KeyUnlockData("testKey")
                doUserAuth(
                    "Unlock to ECDH with key",
                    unlockData.cryptoObjectForKeyAgreement,
                    false,
                    biometricConfirmationRequired,
                    onAuthSuccees = {
                        Logger.d(TAG, "onAuthSuccess")
                        val t0 = System.currentTimeMillis()
                        val Zab = androidKeystoreSecureArea.keyAgreement(
                            "testKey",
                            otherKeyPairForEcdh.public,
                            unlockData)
                        val t1 = System.currentTimeMillis()
                        Logger.dHex(
                            TAG,
                            "Calculated ECDH",
                            Zab)
                        Toast.makeText(applicationContext, "ECDH after authn (${t1 - t0} msec)",
                            Toast.LENGTH_SHORT).show()
                    },
                    onAuthFailure = {
                        Logger.d(TAG, "onAuthFailure")
                    },
                    onDismissed = {
                        Logger.d(TAG, "onDismissed")
                    })
            }

        }
    }

    // ----

    private fun swAttestation(): List<X509Certificate> {
        val now = Instant.now().toEpochMilli()
        val thirtyDaysFromNow = Instant.now().toEpochMilli() + 30*24*3600*1000L
        softwareSecureArea.createKey(
            "testKey",
            SoftwareSecureArea.CreateKeySettings.Builder("Challenge".toByteArray())
                .setValidityPeriod(Timestamp.ofEpochMilli(now), Timestamp.ofEpochMilli(thirtyDaysFromNow))
                .setAttestationKey(softwareAttestationKey,
                    softwareAttestationKeySignatureAlgorithm,
                    softwareAttestationKeyCertification)
                .build()
        )
        return softwareSecureArea.getKeyInfo("testKey").attestation
    }

    private fun swTest(
        keyPurpose: Int,
        curve: Int,
        passphrase: String?,
        passphraseEnteredByUser: String?) {
        Logger.d(
            TAG,
            "swTest keyPurpose:$keyPurpose curve:$curve passphrase:$passphrase"
        )
        try {
            swTestUnguarded(keyPurpose, curve, passphrase, passphraseEnteredByUser)
        } catch (e: Exception) {
            e.printStackTrace();
            Toast.makeText(applicationContext, "${e.message}",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun swTestUnguarded(
        keyPurpose: Int,
        curve: Int,
        passphrase: String?,
        passphraseEnteredByUser: String?) {

        val builder = SoftwareSecureArea.CreateKeySettings.Builder("Challenge".toByteArray())
            .setEcCurve(curve)
            .setKeyPurposes(keyPurpose)
            .setAttestationKey(softwareAttestationKey,
                softwareAttestationKeySignatureAlgorithm,
                softwareAttestationKeyCertification)
        if (passphrase != null) {
            builder.setPassphraseRequired(true, passphrase)
        }
        softwareSecureArea.createKey("testKey", builder.build())

        val keyInfo = softwareSecureArea.getKeyInfo("testKey")
        val publicKey = keyInfo.attestation.get(0).publicKey

        var unlockData: SecureArea.KeyUnlockData? = null
        if (passphraseEnteredByUser != null) {
            unlockData = SoftwareSecureArea.KeyUnlockData(passphraseEnteredByUser)
        }

        if (keyPurpose == KEY_PURPOSE_SIGN) {
            val signingAlgorithm = getNaturalAlgorithmForCurve(curve)
            try {
                val t0 = System.currentTimeMillis()
                val derSignature = softwareSecureArea.sign(
                    "testKey",
                    signingAlgorithm,
                    "data".toByteArray(),
                    unlockData)
                val t1 = System.currentTimeMillis()
                Logger.dHex(
                    TAG,
                    "Made signature with key without authentication",
                    derSignature
                )
                Toast.makeText(applicationContext, "Signed w/o authn (${t1 - t0} msec)",
                    Toast.LENGTH_SHORT).show()
            } catch (e: SecureArea.KeyLockedException) {
                e.printStackTrace();
                Toast.makeText(
                    applicationContext, "${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            val otherKeyPairForEcdh = Util.createEphemeralKeyPair(curve)
            try {
                val t0 = System.currentTimeMillis()
                val Zab = softwareSecureArea.keyAgreement(
                    "testKey",
                    otherKeyPairForEcdh.public,
                    unlockData)
                val t1 = System.currentTimeMillis()
                Logger.dHex(
                    TAG,
                    "Calculated ECDH without authentication",
                    Zab)
                Toast.makeText(applicationContext, "ECDH w/o authn (${t1 - t0} msec)",
                    Toast.LENGTH_SHORT).show()
            } catch (e: SecureArea.KeyLockedException) {
                e.printStackTrace();
                Toast.makeText(
                    applicationContext, "${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun doUserAuth(
        title: String,
        cryptoObject: CryptoObject?,
        forceLskf: Boolean,
        biometricConfirmationRequired: Boolean,
        onAuthSuccees: () -> Unit,
        onAuthFailure: () -> Unit,
        onDismissed: () -> Unit
    ) {
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            throw IllegalStateException("Cannot be called from UI thread");
        }
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authentication required")
            .setConfirmationRequired(biometricConfirmationRequired)
            .setSubtitle(title)
        if (forceLskf) {
            // TODO: this works only on Android 11 or later but for now this is fine
            //   as this is just a reference/test app and this path is only hit if
            //   the user actually presses the "Use LSKF" button.  Longer term, we should
            //   fall back to using KeyGuard which will work on all Android versions.
            promptInfoBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            val canUseBiometricAuth = BiometricManager
                .from(applicationContext)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
            if (canUseBiometricAuth) {
                promptInfoBuilder.setNegativeButtonText("Use LSKF")
            } else {
                promptInfoBuilder.setDeviceCredentialAllowed(true)
            }
        }

        var wasSuccess = false
        var wasFailure = false
        var wasDismissed = false
        val cv = ConditionVariable()
        runOnUiThread {
            val biometricPromptInfo = promptInfoBuilder.build()
            val activity = this as FragmentActivity
            val biometricPrompt = BiometricPrompt(activity,
                object : BiometricPrompt.AuthenticationCallback() {

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Logger.d(TAG, "onAuthenticationError $errorCode $errString")
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                            wasDismissed = true
                            cv.open()
                        } else if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            val promptInfoBuilderLskf = BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Authentication required")
                                .setConfirmationRequired(biometricConfirmationRequired)
                                .setSubtitle(title)
                            promptInfoBuilderLskf.setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                            val biometricPromptInfoLskf = promptInfoBuilderLskf.build()
                            val biometricPromptLskf = BiometricPrompt(activity,
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationError(
                                        errorCode: Int,
                                        errString: CharSequence
                                    ) {
                                        super.onAuthenticationError(errorCode, errString)
                                        Logger.d(TAG, "onAuthenticationError LSKF $errorCode $errString")
                                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                                            wasDismissed = true
                                            cv.open()
                                        } else {
                                            wasFailure = true
                                            cv.open()
                                        }
                                    }

                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        super.onAuthenticationSucceeded(result)
                                        Logger.d(TAG, "onAuthenticationSucceeded LSKF $result")
                                        wasSuccess = true
                                        cv.open()
                                    }

                                    override fun onAuthenticationFailed() {
                                        super.onAuthenticationFailed()
                                        Logger.d(TAG, "onAuthenticationFailed LSKF")
                                    }
                                }
                            )
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (cryptoObject != null) {
                                    biometricPromptLskf.authenticate(
                                        biometricPromptInfoLskf, cryptoObject
                                    )
                                } else {
                                    biometricPromptLskf.authenticate(biometricPromptInfoLskf)
                                }
                            }, 100)
                        } else {
                            wasFailure = true
                            cv.open()
                        }
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        Logger.d(TAG, "onAuthenticationSucceeded $result")
                        wasSuccess = true
                        cv.open()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Logger.d(TAG, "onAuthenticationFailed")
                    }
                })
            Logger.d(TAG, "cryptoObject: " + cryptoObject)
            if (cryptoObject != null) {
                biometricPrompt.authenticate(biometricPromptInfo, cryptoObject)
            } else {
                biometricPrompt.authenticate(biometricPromptInfo)
            }
        }
        cv.block()
        if (wasSuccess) {
            Logger.d(TAG, "Reporting success")
            onAuthSuccees()
        }
        else if (wasFailure) {
            Logger.d(TAG, "Reporting failure")
            onAuthFailure()
        }
        else if (wasDismissed) {
            Logger.d(TAG, "Reporting dismissed")
            onDismissed()
        }
    }

}