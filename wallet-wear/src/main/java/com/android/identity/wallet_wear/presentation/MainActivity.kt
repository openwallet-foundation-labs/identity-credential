/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

@file:OptIn(ExperimentalHorologistApi::class, ExperimentalWearFoundationApi::class)

package com.android.identity.wallet_wear.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.OutlinedChip
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.ProvideTextStyle
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.curvedText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.CredentialStore
import com.android.identity.credential.NameSpacedData
import com.android.identity.internal.Util
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.storage.StorageEngine
import com.android.identity.util.CborUtil
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity.wallet_wear.R
import com.android.identity.wallet_wear.presentation.theme.IdentityCredentialTheme
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.google.android.horologist.compose.rotaryinput.rotaryWithSnap
import com.google.android.horologist.compose.rotaryinput.toRotaryScrollAdapter
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.Random
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private lateinit var storageEngine: StorageEngine
    private lateinit var secureAreaRepository: SecureAreaRepository
    private lateinit var androidKeystoreSecureArea: AndroidKeystoreSecureArea
    private lateinit var credentialStore: CredentialStore
    private lateinit var transferHelper: TransferHelper

    companion object {
        var focusedCredentialId: String? = null
    }

    private val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
    private val MDL_NAMESPACE = "org.iso.18013.5.1"
    private val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"

    private val addNewCredentialBitmap: Bitmap = createAddNewCredentialBitmap()

    private fun createAddNewCredentialBitmap(): Bitmap {
        val width = 800
        val height = ceil(width.toFloat() * 2.125 / 3.375).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val strokePaint = Paint()
        strokePaint.setStyle(Paint.Style.STROKE)
        strokePaint.setColor(android.graphics.Color.WHITE)
        strokePaint.setStrokeWidth(10f)
        val round = bitmap.width / 25f
        val padding = bitmap.width/25f
        canvas.drawRoundRect(
            padding,
            padding,
            bitmap.width.toFloat() - padding*2f,
            bitmap.height.toFloat() - padding*2f,
            round,
            round,
            strokePaint
        )

        val addText = "Add New"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.setColor(android.graphics.Color.WHITE)
        paint.textSize = bitmap.width / 10f
        val bounds = Rect()
        paint.getTextBounds(addText, 0, addText.length, bounds)
        val x: Float = (bitmap.width - bounds.width())/2f
        val y: Float = (bitmap.height - bounds.height())/2f + paint.textSize/2
        canvas.drawText(addText, x, y, paint)
        return bitmap
    }

    private fun createArtwork(color1: Int,
                              color2: Int,
                              artworkText: String): ByteArray {
        val width = 800
        val height = ceil(width.toFloat() * 2.125 / 3.375).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint()
        bgPaint.setShader(
            RadialGradient(
                width / 2f, height / 2f,
                height / 0.5f, color1, color2, Shader.TileMode.MIRROR
            )
        )
        val round = bitmap.width / 25f
        canvas.drawRoundRect(
            0f,
            0f,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            round,
            round,
            bgPaint
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.setColor(android.graphics.Color.WHITE)
        paint.textSize = bitmap.width / 10.0f
        paint.setShadowLayer(2.0f, 1.0f, 1.0f, android.graphics.Color.BLACK)
        val bounds = Rect()
        paint.getTextBounds(artworkText, 0, artworkText.length, bounds)
        val textPadding = bitmap.width/25f
        val x: Float = textPadding
        val y: Float = bitmap.height - bounds.height() - textPadding + paint.textSize/2
        canvas.drawText(artworkText, x, y, paint)

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, baos)
        return baos.toByteArray()
    }

    private fun provisionCredentials() {
        if (credentialStore.lookupCredential("mDL_Erika") == null) {
            provisionCredential(
                "mDL_Erika",
                "Erika's Driving License",
                android.graphics.Color.rgb(64, 255, 64),
                android.graphics.Color.rgb(0, 96, 0),
                "E MUS",
                "Erika",
                "Mustermann",
                R.drawable.img_erika_portrait
            )
            provisionCredential(
                "mDL_Max",
                "Max's Driving License",
                android.graphics.Color.rgb(64, 64, 255),
                android.graphics.Color.rgb(0, 0, 96),
                "M EXA",
                "Max",
                "Example-Person",
                R.drawable.img_davidz_portrait
            )
        }
    }

    private fun provisionCredential(
        credentialId: String,
        displayName: String,
        color1: Int,
        color2: Int,
        artworkText: String,
        givenName: String,
        familyName: String,
        portrait_id: Int
    ) {
        val credential = credentialStore.createCredential(
            credentialId,
            androidKeystoreSecureArea,
            AndroidKeystoreSecureArea.CreateKeySettings.Builder("challenge".toByteArray()).build()
        )

        credential.applicationData.setData("artwork", createArtwork(color1, color2, artworkText))
        credential.applicationData.setString("displayName", displayName)
        credential.applicationData.setString("docType", "org.iso.18013.5.1.mDL")

        val baos = ByteArrayOutputStream()
        BitmapFactory.decodeResource(
            applicationContext.resources,
            portrait_id
        ).compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val portrait: ByteArray = baos.toByteArray()

        val now = Timestamp.now()
        val issueDate = now
        val expiryDate = Timestamp.ofEpochMilli(issueDate.toEpochMilli() + 5*365*24*3600*1000L)

        val credentialData = NameSpacedData.Builder()
            .putEntryString(MDL_NAMESPACE, "given_name", givenName)
            .putEntryString(MDL_NAMESPACE, "family_name", familyName)
            .putEntryByteString(MDL_NAMESPACE, "portrait", portrait)
            .putEntryNumber(MDL_NAMESPACE, "sex", 2)
            .putEntry(MDL_NAMESPACE, "issue_date", Util.cborEncodeDateTime(issueDate))
            .putEntry(MDL_NAMESPACE, "expiry_date", Util.cborEncodeDateTime(expiryDate))
            .putEntryString(MDL_NAMESPACE, "document_number", "1234567890")
            .putEntryString(MDL_NAMESPACE, "issuing_authority", "State of Utopia")
            .putEntryString(AAMVA_NAMESPACE, "DHS_compliance", "F")
            .putEntryNumber(AAMVA_NAMESPACE, "EDL_credential", 1)
            .putEntryBoolean(MDL_NAMESPACE, "age_over_18", true)
            .putEntryBoolean(MDL_NAMESPACE, "age_over_21", true)
            .build()
        credential.applicationData.setNameSpacedData("credentialData", credentialData)
        credential.applicationData.setString("docType", MDL_DOCTYPE)

        // Create AuthKeys and MSOs, make sure they're valid for a long time
        val timeSigned = now
        val validFrom = now
        val validUntil = Timestamp.ofEpochMilli(validFrom.toEpochMilli() + 365*24*3600*1000L)

        // Create three authentication keys and certify them
        for (n in 0..2) {
            val pendingAuthKey = credential.createPendingAuthenticationKey(
                "mdoc",
                androidKeystoreSecureArea,
                SecureArea.CreateKeySettings("".toByteArray()),
                null
            )

            // Generate an MSO and issuer-signed data for this authentication key.
            val msoGenerator = MobileSecurityObjectGenerator(
                "SHA-256",
                MDL_DOCTYPE,
                pendingAuthKey.attestation[0].publicKey
            )
            msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)
            val deterministicRandomProvider = Random(42)
            val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                credentialData,
                deterministicRandomProvider,
                16,
                null
            )
            for (nameSpaceName in issuerNameSpaces.keys) {
                val digests = MdocUtil.calculateDigestsForNameSpace(
                    nameSpaceName,
                    issuerNameSpaces,
                    "SHA-256"
                )
                msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
            }
            val issuerKeyPair: KeyPair = generateIssuingAuthorityKeyPair()
            val issuerCert = getSelfSignedIssuerAuthorityCertificate(issuerKeyPair)

            val mso = msoGenerator.generate()
            val taggedEncodedMso = Util.cborEncode(Util.cborBuildTaggedByteString(mso))
            val issuerCertChain = listOf(issuerCert)
            val encodedIssuerAuth = Util.cborEncode(
                Util.coseSign1Sign(
                    issuerKeyPair.private,
                    "SHA256withECDSA", taggedEncodedMso,
                    null,
                    issuerCertChain
                )
            )

            val issuerProvidedAuthenticationData = StaticAuthDataGenerator(
                MdocUtil.stripIssuerNameSpaces(issuerNameSpaces, null),
                encodedIssuerAuth
            ).generate()

            pendingAuthKey.certify(issuerProvidedAuthenticationData, validFrom, validUntil)
        }
        Logger.d(TAG, "Created credential with name ${credential.name}")
    }

    private fun generateIssuingAuthorityKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        kpg.initialize(ecSpec)
        return kpg.generateKeyPair()
    }

    private fun getSelfSignedIssuerAuthorityCertificate(
        issuerAuthorityKeyPair: KeyPair
    ): X509Certificate {
        val issuer: X500Name = X500Name("CN=State Of Utopia")
        val subject: X500Name = X500Name("CN=State Of Utopia Issuing Authority Signing Key")

        // Valid from now to five years from now.
        val now = Date()
        val kMilliSecsInOneYear = 365L * 24 * 60 * 60 * 1000
        val expirationDate = Date(now.time + 5 * kMilliSecsInOneYear)
        val serial = BigInteger("42")
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            now,
            expirationDate,
            subject,
            issuerAuthorityKeyPair.public
        )
        val signer: ContentSigner = JcaContentSignerBuilder("SHA256withECDSA")
            .build(issuerAuthorityKeyPair.private)
        val certHolder: X509CertificateHolder = builder.build(signer)
        return JcaX509CertificateConverter().getCertificate(certHolder)
    }

    override fun onPause() {
        Logger.d(TAG, "onPause")
        super.onPause()
    }

    override fun onResume() {
        Logger.d(TAG, "onResume")
        super.onResume()
    }

    override fun onDestroy() {
        Logger.d(TAG, "onDestroy")
        focusedCredentialId = null
        super.onDestroy()
    }

    override fun onStop() {
        Logger.d(TAG, "onStop")
        super.onStop()
    }

    override fun onRestart() {
        Logger.d(TAG, "onRestart")
        super.onRestart()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val storageDir = File(applicationContext.noBackupFilesDir, "identity")
        storageEngine = AndroidStorageEngine.Builder(applicationContext, storageDir).build()
        secureAreaRepository = SecureAreaRepository();
        androidKeystoreSecureArea = AndroidKeystoreSecureArea(applicationContext, storageEngine);
        secureAreaRepository.addImplementation(androidKeystoreSecureArea);
        transferHelper = TransferHelper.getInstance(applicationContext)

        credentialStore = CredentialStore(storageEngine, secureAreaRepository)
        provisionCredentials()

        // focusedCredentialId is used in NfcEngagementHandler to present the currently focused
        // credential.
        val credentialIds = credentialStore.listCredentials()
        if (credentialIds.size > 0) {
            focusedCredentialId = credentialIds[0]
            Logger.d(TAG, "Focused credentialId $focusedCredentialId")
        }

        setContent {
            IdentityCredentialTheme {
                val swipeDismissableNavController = rememberSwipeDismissableNavController()

                SwipeDismissableNavHost(
                    navController = swipeDismissableNavController,
                    startDestination = "CredentialList",
                    modifier = Modifier.background(MaterialTheme.colors.background)
                ) {
                    composable("CredentialList") {
                        CredentialList(swipeDismissableNavController)
                    }
                    composable("CredentialDetails/{id}") {
                        val credentialId = it.arguments?.getString("id")!!
                        CredentialDetails(swipeDismissableNavController, credentialId)
                    }
                    composable("AddNewCredential") {
                        AddNewCredential(swipeDismissableNavController)
                    }
                }
            }
        }

        val permissionsNeeded = appPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(
                applicationContext,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionsLauncher.launch(
                permissionsNeeded.toTypedArray()
            )
        }
    }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Logger.d(TAG, "permissionsLauncher ${it.key} = ${it.value}")
                if (!it.value) {
                    Toast.makeText(
                        this,
                        "The ${it.key} permission is required for BLE",
                        Toast.LENGTH_LONG
                    ).show()
                    return@registerForActivityResult
                }
            }
        }

    private val appPermissions: Array<String> =
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }

    @Composable
    private fun CredentialList(swipeDismissableNavController: NavHostController) {
        val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
        val focusRequester = rememberActiveFocusRequester()

        // This is for updating focusedCredentialId which is used in NfcEngagementHandler.
        // TODO: currently this is not called when using rotary scrolling.
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    val focusedCredentialNumber = listState.centerItemIndex
                    val credentialIds = credentialStore.listCredentials()
                    if (focusedCredentialNumber < credentialIds.size) {
                        focusedCredentialId = credentialIds[focusedCredentialNumber]
                    } else {
                        focusedCredentialId = null
                    }
                    Logger.d(TAG, "Focused credentialId $focusedCredentialId")
                    return super.onPostFling(consumed, available)
                }
            }
        }

        Scaffold(
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .nestedScroll(nestedScrollConnection)
                    .rotaryWithSnap(listState.toRotaryScrollAdapter(), focusRequester),
                verticalArrangement = Arrangement.Center,
                autoCentering = AutoCenteringParams(itemIndex = 0),
                state = listState,
                anchorType = ScalingLazyListAnchorType.ItemCenter,
                flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = listState)
            ) {
                for (credentialId in credentialStore.listCredentials()) {
                    val credential = credentialStore.lookupCredential(credentialId)!!
                    val encodedArtwork = credential.applicationData.getData("artwork")
                    val options = BitmapFactory.Options()
                    options.inMutable = true
                    val artworkBitmap = BitmapFactory.decodeByteArray(encodedArtwork, 0, encodedArtwork.size, options)

                    item {
                        Box(
                            Modifier.padding(bottom = 8.dp)
                        ) {
                            Image(
                                bitmap = artworkBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .clickable {
                                        swipeDismissableNavController.navigate("CredentialDetails/$credentialId")
                                    }
                            )
                        }
                    }
                }

                item {
                    Box(
                        Modifier.padding(bottom = 8.dp)
                    ) {
                        Image(
                            bitmap = addNewCredentialBitmap.asImageBitmap(),
                            contentDescription = "Add New Credential",
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .clickable {
                                    swipeDismissableNavController.navigate("AddNewCredential")
                                }
                        )
                    }
                }

            }

            if (resources.configuration.isScreenRound) {
                ProvideTextStyle(
                    value = TextStyle(
                        background = Color.Black
                    )
                ) {
                    CurvedLayout(
                        anchor = -90f,
                        modifier = Modifier.shadow(2.dp),
                    ) {
                        curvedText("Hold to Reader")
                    }
                }
            } else {
                Text(
                    text = "Hold to Reader",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black)
                        .fillMaxWidth()
                )
            }

        }
    }

    @Composable
    private fun CredentialDetails(
        swipeDismissableNavController: NavHostController,
        credentialId: String
    ) {
        val credential = credentialStore.lookupCredential(credentialId)!!
        val credentialName = credential.applicationData.getString("displayName")
        val credentialData = credential.applicationData.getNameSpacedData("credentialData")

        var portraitBitmap: Bitmap? = null
        if (credentialData.hasDataElement(MDL_NAMESPACE, "portrait")) {
            val data = credentialData.getDataElementByteString(MDL_NAMESPACE, "portrait")
            portraitBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        }

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
                        text = credentialName,
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                item {
                    Divider()
                }

                if (portraitBitmap != null) {
                    item {
                        Image(
                            bitmap = portraitBitmap.asImageBitmap(),
                            contentDescription = "Photo of credential holder",
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(bottom = 8.dp)
                        )
                    }

                    item {
                        Divider()
                    }
                }

                for (nsName in credentialData.nameSpaceNames) {
                    for (deName in credentialData.getDataElementNames(nsName)) {
                        if (deName.equals("portrait")) {
                            continue
                        }
                        // TODO: Use CredentialType machinery from PR #419 to get
                        //  a nice human readable name for [dataElementName]
                        val dataElementDisplayValue = deName
                        val valueEncodedCbor = credentialData.getDataElement(nsName, deName)
                        val displayValue = CborUtil.toString(valueEncodedCbor)

                        item {
                            Chip(
                                //colors = ChipDefaults.secondaryChipColors(),
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = Color.Transparent
                                ),
                                onClick = {},
                                enabled = true,
                                label = {
                                    Text(
                                        text = dataElementDisplayValue,
                                    )
                                },
                                secondaryLabel = {
                                    Text(
                                        text = displayValue,
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    Divider()
                }

                item {
                    Chip(
                        onClick = {
                            val launchAppIntent = Intent(applicationContext, ShowQrCodeActivity::class.java)
                            launchAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                            launchAppIntent.putExtra("credentialId", credentialId)
                            applicationContext.startActivity(launchAppIntent)
                        },
                        enabled = true,
                        label = {
                            Text(
                                text = "Share",
                            )},
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.QrCode,
                                contentDescription = null,
                                modifier = Modifier.padding(8.dp)
                            )},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        )
                }

                /*
                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        onClick = {
                            val launchAppIntent = Intent(applicationContext, ShowQrCodeActivity::class.java)
                            launchAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                            launchAppIntent.putExtra("credentialId", credentialId)
                            applicationContext.startActivity(launchAppIntent)
                        })
                    {
                    }
                }
                 */

            }
        }
    }

    @Composable
    private fun Divider() {
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(color = Color.DarkGray)
        )
    }

    @Composable
    private fun AddNewCredential(swipeDismissableNavController: NavHostController) {
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
                        text = "Not Yet Implemented",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
 }
