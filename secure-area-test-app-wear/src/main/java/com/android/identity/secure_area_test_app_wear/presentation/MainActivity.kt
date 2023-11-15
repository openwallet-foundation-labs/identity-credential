/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.android.identity.secure_area_test_app_wear.presentation

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.internal.Util
import com.android.identity.secure_area_test_app_wear.R
import com.android.identity.secure_area_test_app_wear.presentation.theme.IdentityCredentialTheme
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.concurrent.Executors

@OptIn(ExperimentalHorologistApi::class, ExperimentalWearFoundationApi::class)
class MainActivity : FragmentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var softwareSecureArea: SoftwareSecureArea

    private lateinit var aksCaps: AndroidKeystoreSecureArea.Capabilities
    private lateinit var androidKeystoreSecureArea: AndroidKeystoreSecureArea
    private lateinit var androidKeystoreStorage: AndroidStorageEngine

    private lateinit var attestation: List<X509Certificate>

    private var keymintVersionTee: Int = 0
    private var keymintVersionStrongBox: Int = 0

    private var executorService = Executors.newSingleThreadExecutor()

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var softwareAttestationKey: PrivateKey
    private lateinit var softwareAttestationKeySignatureAlgorithm: String
    private lateinit var softwareAttestationKeyCertification: List<X509Certificate>

    private val apiLevel = Build.VERSION.SDK_INT
    private val firstApiLevel = getFirstApiLevel()

    // Unfortunately this is API is only available to system apps so we
    // have to use reflection to use it.
    private fun getFirstApiLevel(): Int {
        try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            val firstApiLevelString = get.invoke(c, "ro.product.first_api_level") as String
            return firstApiLevelString.toInt()
        } catch (e: java.lang.Exception) {
            Logger.w(TAG, "Error getting ro.product.first_api_level", e)
            return 0
        }
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
        softwareAttestationKeyCertification =
            secureArea.getKeyInfo("SoftwareAttestationRoot").attestation
    }

    private fun getFeatureVersionKeystore(appContext: Context, useStrongbox: Boolean): Int {
        var feature = PackageManager.FEATURE_HARDWARE_KEYSTORE
        if (useStrongbox) {
            feature = PackageManager.FEATURE_STRONGBOX_KEYSTORE
        }
        val pm = appContext.packageManager
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
            var version = 0
            if (info != null) {
                version = info.version
            }
            // It's entirely possible that the feature exists but the version number hasn't
            // been set. In that case, assume it's at least KeyMaster 4.1.
            if (version < 41) {
                version = 41
            }
            return version
        }
        // It's only a requirement to set PackageManager.FEATURE_HARDWARE_KEYSTORE since
        // Android 12 so for old devices this isn't set. However all devices since Android
        // 8.1 has had HW-backed keystore so in this case we can report KeyMaster 4.1
        if (!useStrongbox) {
            return 41
        }
        return 0
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

        aksCaps = AndroidKeystoreSecureArea.Capabilities(applicationContext)

        softwareSecureArea = SoftwareSecureArea(androidKeystoreStorage)

        keymintVersionTee = getFeatureVersionKeystore(applicationContext, false)
        keymintVersionStrongBox = getFeatureVersionKeystore(applicationContext, true)

        Logger.d(TAG, "keymintVersionTee: ${keymintVersionTee}")
        Logger.d(TAG, "keymintVersionStrongBox: ${keymintVersionStrongBox}")

        sharedPreferences = getSharedPreferences("default", MODE_PRIVATE)

        setContent {
            IdentityCredentialTheme {
                val swipeDismissableNavController = rememberSwipeDismissableNavController()
                SwipeDismissableNavHost(
                    navController = swipeDismissableNavController,
                    startDestination = "Landing",
                    modifier = Modifier.background(MaterialTheme.colors.background)
                ) {
                    composable("Landing") {
                        ListOfSecureAreaTests(swipeDismissableNavController)
                    }
                    composable("VersionsAndCapabilities") {
                        VersionsAndCapabilities(swipeDismissableNavController)
                    }
                    composable("Attestation") {
                        Attestation(swipeDismissableNavController)
                    }
                    composable("Attestation/{id}") {
                        val certificateIndex = it.arguments?.getString("id")!!.toInt()
                        AttestationCertificate(certificateIndex, swipeDismissableNavController)
                    }
                }
            }
        }
    }

    @Composable
    private fun VersionsAndCapabilities(swipeDismissableNavController: NavHostController) {
        val listState = rememberScalingLazyListState()
        val focusRequester = rememberActiveFocusRequester()

        val secureLockScreenText = if (aksCaps.secureLockScreenSetup) "Yes" else "No"

        Scaffold(
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .rotaryWithScroll(listState, focusRequester),
                verticalArrangement = Arrangement.Center,
                autoCentering = AutoCenteringParams(itemIndex = 0),
                state = listState,
            ) {
                item {
                    Text(
                        text = "API: ${apiLevel}",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                item {
                    Text(
                        text = "First API: ${firstApiLevel}",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                item {
                    Text(
                        text = "KeyMint: ${keymintVersionTee}",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                item {
                    Text(
                        text = "KeyMint SB: ${keymintVersionStrongBox}",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                item {
                    Text(
                        text = "Lock Screen: $secureLockScreenText",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                item {
                    Text(
                        text = "Attest Key: ${aksCaps.attestKeySupported}\n" +
                                "Key Agreement: ${aksCaps.keyAgreementSupported}\n" +
                                "Curve 25519: ${aksCaps.curve25519Supported}",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                item {
                    Text(
                        text = "StrongBox: ${aksCaps.strongBoxSupported}\n" +
                                "Attest Key: ${aksCaps.strongBoxAttestKeySupported}\n" +
                                "Key Agreement: ${aksCaps.strongBoxKeyAgreementSupported}\n" +
                                "Curve 25519: ${aksCaps.strongBoxCurve25519Supported}",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        onClick = {
                            swipeDismissableNavController.popBackStack()
                        })
                    {
                        Text(
                            text = "Back",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Attestation(swipeDismissableNavController: NavHostController) {
        val listState = rememberScalingLazyListState()
        val focusRequester = rememberActiveFocusRequester()
        Scaffold(
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .rotaryWithScroll(listState, focusRequester),
                verticalArrangement = Arrangement.Center,
                autoCentering = AutoCenteringParams(itemIndex = 0),
                state = listState,
            ) {
                for ((certificateCount, certificate) in attestation.withIndex()) {
                    val buttonText = when (certificateCount) {
                        0 -> "Leaf\nCertificate"
                        attestation.size - 1 -> "Root\nCertificate"
                        else -> "Certificate ${certificateCount + 1} of ${attestation.size}"
                    }
                    item {
                        Button(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            onClick = {
                                swipeDismissableNavController.navigate("Attestation/$certificateCount")
                            })
                        {
                            Text(
                                text = buttonText,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        onClick = {
                            swipeDismissableNavController.popBackStack()
                        })
                    {
                        Text(
                            text = "Back",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AttestationCertificate(certificateIndex: Int,
                                       swipeDismissableNavController: NavHostController) {
        val certificate = attestation[certificateIndex]
        val listState = rememberScalingLazyListState()
        val focusRequester = rememberActiveFocusRequester()
        Scaffold(
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .rotaryWithScroll(listState, focusRequester),
                verticalArrangement = Arrangement.Center,
                autoCentering = AutoCenteringParams(itemIndex = 0),
                state = listState,
            ) {
                item {
                    Text(
                        text = styledX509CertificateText(certificate.toString()),
                        textAlign = TextAlign.Center,
                        fontSize = 7.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        onClick = {
                            swipeDismissableNavController.popBackStack()
                        })
                    {
                        Text(
                            text = "Back",
                            textAlign = TextAlign.Center
                        )
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
    private fun ListOfSecureAreaTests(swipeDismissableNavController: NavHostController) {
        val listState = rememberScalingLazyListState()
        val focusRequester = rememberActiveFocusRequester()
        Scaffold(
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .rotaryWithScroll(listState, focusRequester),
                verticalArrangement = Arrangement.Center,
                autoCentering = AutoCenteringParams(itemIndex = 0),
                state = listState,
            ) {
                item {
                    Text(
                        text = "Android Keystore Secure Area",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }

                item {
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        onClick = {
                            swipeDismissableNavController.navigate("VersionsAndCapabilities")
                        })
                    {
                        Text(
                            text = "Versions",
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                item {
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        onClick = {
                            androidKeystoreSecureArea.createKey(
                                "testKey",
                                SecureArea.CreateKeySettings("challenge".toByteArray())
                            )
                            attestation =
                                androidKeystoreSecureArea.getKeyInfo("testKey").attestation
                            swipeDismissableNavController.navigate("Attestation")
                        })
                    {
                        Text(
                            text = "Attestation",
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                item {
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        onClick = {
                            try {
                                androidKeystoreSecureArea.createKey(
                                    "testKey",
                                    AndroidKeystoreSecureArea.CreateKeySettings.Builder("challenge".toByteArray())
                                        .setUseStrongBox(true)
                                        .build()
                                )
                                attestation =
                                    androidKeystoreSecureArea.getKeyInfo("testKey").attestation
                                swipeDismissableNavController.navigate("Attestation")
                            } catch (throwable: Throwable) {
                                throwable.printStackTrace()
                                Toast.makeText(
                                    applicationContext, "${throwable.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                    {
                        Text(
                            text = "StrongBox\nAttestation",
                            textAlign = TextAlign.Center,
                        )
                    }
                }


                for ((strongBox, strongBoxDesc) in arrayOf(
                    Pair(false, ""), Pair(true, "SB ")
                )) {
                    for ((keyPurpose, keyPurposeDesc) in arrayOf(
                        Pair(SecureArea.KEY_PURPOSE_SIGN, "Sign"),
                        Pair(SecureArea.KEY_PURPOSE_AGREE_KEY, "ECDH")
                    )) {
                        for ((curve, curveName, purposes) in arrayOf(
                            Triple(
                                SecureArea.EC_CURVE_P256,
                                "P-256",
                                SecureArea.KEY_PURPOSE_SIGN + SecureArea.KEY_PURPOSE_AGREE_KEY
                            ),
                            Triple(
                                SecureArea.EC_CURVE_ED25519,
                                "Ed25519",
                                SecureArea.KEY_PURPOSE_SIGN
                            ),
                            Triple(
                                SecureArea.EC_CURVE_X25519,
                                "X25519",
                                SecureArea.KEY_PURPOSE_AGREE_KEY
                            ),
                        )) {
                            if ((keyPurpose and purposes) == 0) {
                                // No common purpose
                                continue
                            }

                            val AUTH_NONE = 0
                            val AUTH_LSKF_OR_BIOMETRIC =
                                AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_BIOMETRIC +
                                        AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_LSKF
                            for ((userAuthType, authTimeout, authDesc) in arrayOf(
                                Triple(AUTH_NONE, 0L, ""),
                                Triple(AUTH_LSKF_OR_BIOMETRIC, 0L, "w/ Auth"),
                                Triple(AUTH_LSKF_OR_BIOMETRIC, 10 * 1000L, "w/ Auth (10s)"),
                            )) {
                                // For brevity, only do auth for P-256 Sign and Mac
                                if (curve != SecureArea.EC_CURVE_P256 && userAuthType != AUTH_NONE) {
                                    continue
                                }
                                item {
                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        onClick = {
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
                                                    true,
                                                    strongBox
                                                )
                                            })
                                        })
                                    {
                                        Text(
                                            text = "$strongBoxDesc$curveName $keyPurposeDesc $authDesc",
                                            textAlign = TextAlign.Center,
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
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }

                item {
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        onClick = {
                            softwareSecureArea.createKey(
                                "testKey",
                                SoftwareSecureArea.CreateKeySettings.Builder("challenge".toByteArray())
                                    .setAttestationKey(softwareAttestationKey,
                                        softwareAttestationKeySignatureAlgorithm,
                                        softwareAttestationKeyCertification)
                                    .build()
                            )
                            attestation = softwareSecureArea.getKeyInfo("testKey").attestation
                            swipeDismissableNavController.navigate("Attestation")
                        })
                    {
                        Text(
                            text = "Attestation",
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                for ((keyPurpose, keyPurposeDesc) in arrayOf(
                    Pair(SecureArea.KEY_PURPOSE_SIGN, "Sign"),
                    Pair(SecureArea.KEY_PURPOSE_AGREE_KEY, "ECDH")
                )) {
                    for ((curve, curveName, purposes) in arrayOf(
                        Triple(SecureArea.EC_CURVE_P256, "P-256", SecureArea.KEY_PURPOSE_SIGN + SecureArea.KEY_PURPOSE_AGREE_KEY),
                        Triple(SecureArea.EC_CURVE_P384, "P-384", SecureArea.KEY_PURPOSE_SIGN + SecureArea.KEY_PURPOSE_AGREE_KEY),
                        Triple(SecureArea.EC_CURVE_P521, "P-521", SecureArea.KEY_PURPOSE_SIGN + SecureArea.KEY_PURPOSE_AGREE_KEY),
                        Triple(SecureArea.EC_CURVE_BRAINPOOLP256R1, "Brainpool 256", SecureArea.KEY_PURPOSE_SIGN + SecureArea.KEY_PURPOSE_AGREE_KEY),
                        Triple(SecureArea.EC_CURVE_BRAINPOOLP320R1, "Brainpool 320", SecureArea.KEY_PURPOSE_SIGN + SecureArea.KEY_PURPOSE_AGREE_KEY),
                        Triple(SecureArea.EC_CURVE_BRAINPOOLP384R1, "Brainpool 384", SecureArea.KEY_PURPOSE_SIGN + SecureArea.KEY_PURPOSE_AGREE_KEY),
                        Triple(SecureArea.EC_CURVE_BRAINPOOLP512R1, "Brainpool 512", SecureArea.KEY_PURPOSE_SIGN + SecureArea.KEY_PURPOSE_AGREE_KEY),
                        Triple(SecureArea.EC_CURVE_ED25519, "Ed25519", SecureArea.KEY_PURPOSE_SIGN),
                        Triple(
                            SecureArea.EC_CURVE_X25519, "X25519",
                            SecureArea.KEY_PURPOSE_AGREE_KEY
                        ),
                        Triple(SecureArea.EC_CURVE_ED448, "Ed448", SecureArea.KEY_PURPOSE_SIGN),
                        Triple(SecureArea.EC_CURVE_X448, "X448", SecureArea.KEY_PURPOSE_AGREE_KEY),
                    )) {
                        if ((keyPurpose and purposes) == 0) {
                            // No common purpose
                            continue
                        }
                        for ((passphraseRequired, description) in arrayOf(
                            Pair(true, "\nw/ PIN (123456)"),
                            Pair(false, ""),
                        )) {
                            // For brevity, only do passphrase for first item (P-256 Signature)
                            if (!(keyPurpose == SecureArea.KEY_PURPOSE_SIGN && curve == SecureArea.EC_CURVE_P256)) {
                                if (passphraseRequired) {
                                    continue;
                                }
                            }
                            // TODO: ask the user for passphrase/PIN when passphraseRequired is true
                            item {
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    onClick = {
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
                                    })
                                {
                                    Text(
                                        text = "$curveName $keyPurposeDesc $description",
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }

            }
        }
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

    private fun getNaturalAlgorithmForCurve(ecCurve: Int): Int {
        return when (ecCurve) {
            SecureArea.EC_CURVE_P256 -> SecureArea.ALGORITHM_ES256
            SecureArea.EC_CURVE_P384 -> SecureArea.ALGORITHM_ES384
            SecureArea.EC_CURVE_P521 -> SecureArea.ALGORITHM_ES512
            SecureArea.EC_CURVE_BRAINPOOLP256R1 -> SecureArea.ALGORITHM_ES256
            SecureArea.EC_CURVE_BRAINPOOLP320R1 -> SecureArea.ALGORITHM_ES384
            SecureArea.EC_CURVE_BRAINPOOLP384R1 -> SecureArea.ALGORITHM_ES384
            SecureArea.EC_CURVE_BRAINPOOLP512R1 -> SecureArea.ALGORITHM_ES512
            SecureArea.EC_CURVE_ED25519 -> SecureArea.ALGORITHM_EDDSA
            SecureArea.EC_CURVE_ED448 -> SecureArea.ALGORITHM_EDDSA
            else -> {throw IllegalStateException("Unexpected curve " + ecCurve)}
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

        if (keyPurpose == SecureArea.KEY_PURPOSE_SIGN) {
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
                    unlockData.getCryptoObjectForSigning(SecureArea.ALGORITHM_ES256),
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

    private fun doUserAuth(
        title: String,
        cryptoObject: BiometricPrompt.CryptoObject?,
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

        if (keyPurpose == SecureArea.KEY_PURPOSE_SIGN) {
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


    @Composable
    fun WearApp(greetingName: String) {
        IdentityCredentialTheme {
            /* If you have enough items in your list, use [ScalingLazyColumn] which is an optimized
             * version of LazyColumn for wear devices with some added features. For more information,
             * see d.android.com/wear/compose.
             */
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background),
                verticalArrangement = Arrangement.Center
            ) {
                Greeting(greetingName = greetingName)
            }
        }
    }

    @Composable
    fun Greeting(greetingName: String) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.primary,
            text = stringResource(R.string.hello_world, greetingName)
        )
    }

    @Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
    @Composable
    fun DefaultPreview() {
        WearApp("Preview Android")
    }
}