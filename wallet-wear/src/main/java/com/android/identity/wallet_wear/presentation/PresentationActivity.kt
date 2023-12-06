package com.android.identity.wallet_wear.presentation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.activity.ConfirmationActivity
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.Credential
import com.android.identity.credential.CredentialStore
import com.android.identity.credential.NameSpacedData
import com.android.identity.credentialtype.CredentialType
import com.android.identity.credentialtype.MdocCredentialType
import com.android.identity.credentialtype.MdocDataElement
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.mdoc.request.DeviceRequestParser.DocumentRequest
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.response.DocumentGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity.wallet_wear.R
import com.android.identity.wallet_wear.presentation.theme.IdentityCredentialTheme
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import java.io.File


@OptIn(ExperimentalWearFoundationApi::class, ExperimentalHorologistApi::class)
class PresentationActivity : ComponentActivity() {
    private val TAG = "PresentationActivity"
    private lateinit var storageEngine: StorageEngine
    private lateinit var secureAreaRepository: SecureAreaRepository
    private lateinit var androidKeystoreSecureArea: AndroidKeystoreSecureArea
    private lateinit var credentialStore: CredentialStore
    private lateinit var transferHelper: TransferHelper

    // If set, the credential to present (always set from QR engagement, possibly NFC engagement)
    private var credentialId: String? = null

    // The bitmap and display-name for the credential we're about to send
    private lateinit var credentialBitmap: Bitmap
    private lateinit var credentialName: String

    // The bitmap and display-name representing the verifier to send to
    private lateinit var verifierBitmap: Bitmap
    private lateinit var verifierName: String

    // The DocRequest we received
    private var docRequest: DeviceRequestParser.DocumentRequest? = null

    // The credential we're going to send
    private var credentialToSend: Credential? = null

    // The type of credential, if it matches one of our registered types.
    private var credentialToSendType : CredentialType? = null

    private val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
    private val MDL_NAMESPACE = "org.iso.18013.5.1"

    override fun onPause() {
        Logger.d(TAG, "onPause")
        super.onPause()
        transferHelper.disconnect()
    }

    override fun onResume() {
        Logger.d(TAG, "onResume")
        super.onResume()
    }

    override fun onDestroy() {
        Logger.d(TAG, "onDestroy")
        transferHelper.disconnect()
        super.onDestroy()
    }

    override fun onStop() {
        Logger.d(TAG, "onStop")
        transferHelper.disconnect()
        super.onStop()
    }

    override fun onRestart() {
        Logger.d(TAG, "onRestart")
        super.onRestart()
    }

    private fun findCredentialForDocType(docType : String): Credential? {
        for (credentialId in credentialStore.listCredentials()) {
            val credential = credentialStore.lookupCredential(credentialId)!!
            val credentialDocType = credential.applicationData.getString("docType")
            Logger.d(TAG, "foo ${credentialId} ${docType} ${credentialDocType}")
            if (credentialDocType.equals(docType)) {
                return credential
            }
        }
        return null
    }

    private fun identifyReader(docRequest: DocumentRequest) {
        if (docRequest.readerAuth == null) {
            verifierBitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.img_unknown_reader)
            verifierName = "Anonymous Verifier"
            return
        }
        if (!docRequest.readerAuthenticated) {
            val intent = Intent(applicationContext, ConfirmationActivity::class.java).apply {
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION)
                putExtra(ConfirmationActivity.EXTRA_MESSAGE, "Reader Auth invalid")
            }
            startActivity(intent)
        }

        // TODO: this is an awful hack but will suffice until the TrustManager from PR 413
        //  is suitable to be used for reader auth.
        val certChain = docRequest.getReaderCertificateChain()
        if (certChain.size > 0 && certChain[0].issuerX500Principal.name.equals("C=UT,CN=Google TEST Reader CA mDL")) {
            verifierBitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.img_ic_reader)
            verifierName = "IC Reader"
            return
        }

        verifierBitmap = BitmapFactory.decodeResource(applicationContext.resources, R.drawable.img_unknown_reader)
        verifierName = "Unknown Verifier"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val extras = intent.extras
        if (extras != null) {
            credentialId = extras.getString("credentialId")
        }
        Logger.d(TAG, "credentialId: $credentialId")

        val storageDir = File(applicationContext.noBackupFilesDir, "identity")
        storageEngine = AndroidStorageEngine.Builder(applicationContext, storageDir).build()
        secureAreaRepository = SecureAreaRepository();
        androidKeystoreSecureArea = AndroidKeystoreSecureArea(applicationContext, storageEngine);
        secureAreaRepository.addImplementation(androidKeystoreSecureArea);
        transferHelper = TransferHelper.getInstance(applicationContext)

        credentialStore = CredentialStore(storageEngine, secureAreaRepository)

        // For now, just consider the first document request
        val request = DeviceRequestParser()
            .setDeviceRequest(transferHelper.getDeviceRequest())
            .setSessionTranscript(transferHelper.getSessionTranscript())
            .parse()
        docRequest = request.documentRequests[0]

        // Find credential to send
        if (credentialId != null) {
            val credential = credentialStore.lookupCredential(credentialId!!)
            if (credential?.applicationData?.getString("docType").equals(docRequest!!.docType)) {
                credentialToSend = credential
            }
        }
        if (credentialToSend == null) {
            credentialToSend = findCredentialForDocType(docRequest!!.docType)
        }

        if (credentialToSend == null) {
            Logger.w(TAG, "No credential for docType ${docRequest!!.docType}")
            val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_GENERAL_ERROR)
            transferHelper.sendResponse(deviceResponseGenerator.generate())

            val intent = Intent(applicationContext, ConfirmationActivity::class.java).apply {
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION)
                putExtra(ConfirmationActivity.EXTRA_MESSAGE, "No Credential for ${docRequest!!.docType}")
            }
            startActivity(intent)
            return
        }

        Logger.d(TAG, "Picked credential ${credentialToSend!!.name} for docType ${docRequest!!.docType}")

        for (credentialType in transferHelper.credentialTypeRepository.getCredentialTypes()) {
            if (credentialType.mdocCredentialType?.docType.equals(docRequest!!.docType)) {
                credentialToSendType = credentialType
            }
        }

        val encodedArtwork = credentialToSend!!.applicationData.getData("artwork")
        val options = BitmapFactory.Options()
        options.inMutable = true
        credentialBitmap = BitmapFactory.decodeByteArray(encodedArtwork, 0, encodedArtwork.size, options)
        credentialName = credentialToSend!!.applicationData.getString("displayName")

        identifyReader(docRequest!!)

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val vibrationPattern = longArrayOf(0, 500, 50, 300)
        val indexInPatternToRepeat = -1
        vibrator.vibrate(vibrationPattern, indexInPatternToRepeat)

        setContent {
            IdentityCredentialTheme {
                ConsentPage()
            }
        }
    }

    /**
     * Look up a details about a mdoc data element.
     *
     * TODO: move to CredentialType
     *
     * @param namespaceName the mdoc namespace name.
     * @param dataElementName the mdoc data element name.
     * @return a [MdocDataElement] or [null] if not found.
     */
    private fun lookupDataElement(type: MdocCredentialType, namespaceName: String, dataElementName: String): MdocDataElement? {
        // TODO: we probably want to use hashtables instead of lists since this is slow
        for (ns in type.namespaces) {
            if (ns.namespace.equals(namespaceName)) {
                for (de in ns.dataElements) {
                    if (de.attribute.identifier.equals(dataElementName)) {
                        return de
                    }
                }
            }
        }
        return null
    }

    @Composable
    private fun ConsentPage() {
        val listState = rememberScalingLazyListState()
        val focusRequester = rememberActiveFocusRequester()

        // TODO: would be nice to always keep the scrollbar visible since the "Share Data"
        //  button is at the bottom. Doesn't seem like there's any easy way to do that right
        //  now...
        Scaffold(
            positionIndicator = { PositionIndicator(
                scalingLazyListState = listState,
            )},
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
                    Row(
                        modifier = Modifier
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = credentialBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .width(48.dp)
                        )
                        Text(
                            text = "→",
                            modifier = Modifier
                                .padding(end = 8.dp)
                        )
                        Image(
                            bitmap = verifierBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .width(48.dp)
                        )

                    }
                }

                item {
                    Divider()
                }

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

                item {
                    Text(
                        text = "$verifierName is requesting the following data",
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                item {
                    Divider()
                }

                if (docRequest != null && credentialToSend != null) {
                    val nsData = credentialToSend!!.applicationData.getNameSpacedData("credentialData")
                    for (namespaceName in docRequest!!.namespaces) {
                        for (dataElementName in docRequest!!.getEntryNames(namespaceName)) {
                            if (nsData.hasDataElement(namespaceName, dataElementName)) {

                                var dataElementDisplayName = dataElementName
                                if (credentialToSendType != null) {
                                    val mdocDataElement =
                                        lookupDataElement(
                                            credentialToSendType!!.mdocCredentialType!!,
                                            namespaceName,
                                            dataElementName
                                        )
                                    if (mdocDataElement != null) {
                                        dataElementDisplayName = mdocDataElement!!.attribute.displayName
                                    }
                                }

                                // TODO: Use CredentialType machinery from PR #419 to get
                                //  a nice human readable name for [dataElementName]
                                item {
                                    Text(
                                        text = dataElementDisplayName,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        fontSize = MaterialTheme.typography.body2.fontSize
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Divider()
                }

                item {
                    Button(
                        colors = ButtonDefaults.secondaryButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        onClick = {
                            transferHelper.sendResponse(generateEmptyResponse())

                            val intent = Intent(applicationContext, ConfirmationActivity::class.java).apply {
                                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.FAILURE_ANIMATION)
                                putExtra(ConfirmationActivity.EXTRA_MESSAGE, "Sharing Declined")
                            }
                            startActivity(intent)
                        })
                    {
                        Text(
                            text = "Don't Share",
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                item {
                    Button(
                        colors = ButtonDefaults.primaryButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        onClick = {
                            transferHelper.sendResponse(generateResponse())

                            val intent = Intent(applicationContext, ConfirmationActivity::class.java).apply {
                                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
                                putExtra(ConfirmationActivity.EXTRA_MESSAGE, "Credential Shared")
                            }
                            startActivity(intent)
                        })
                    {
                        Text(
                            text = "Share Data",
                            textAlign = TextAlign.Center,
                        )
                    }
                }
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

    private fun generateResponse(): ByteArray {
        val credentialRequest = MdocUtil.generateCredentialRequest(docRequest!!)
        val now = Timestamp.now()
        val authKey = credentialToSend!!.findAuthenticationKey(now)!!

        val staticAuthData = StaticAuthDataParser(authKey.issuerProvidedData).parse()
        val mergedIssuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
            credentialRequest,
            credentialToSend!!.applicationData.getNameSpacedData("credentialData"),
            staticAuthData
        )

        val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
        deviceResponseGenerator.addDocument(
            DocumentGenerator(MDL_DOCTYPE, staticAuthData.issuerAuth, transferHelper.getSessionTranscript())
                .setIssuerNamespaces(mergedIssuerNamespaces)
                .setDeviceNamespacesSignature(
                    NameSpacedData.Builder().build(),
                    authKey.secureArea,
                    authKey.alias,
                    null,
                    SecureArea.ALGORITHM_ES256
                )
                .generate()
        )
        return deviceResponseGenerator.generate()
    }

    private fun generateEmptyResponse(): ByteArray {
        val deviceResponseGenerator = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
        return deviceResponseGenerator.generate()
    }

}