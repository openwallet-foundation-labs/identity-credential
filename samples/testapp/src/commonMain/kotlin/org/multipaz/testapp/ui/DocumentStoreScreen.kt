package org.multipaz.testapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import multipazproject.samples.testapp.generated.resources.Res
import multipazproject.samples.testapp.generated.resources.driving_license_card_art
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.toDataItem
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.crypto.JsonWebSignature
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.directaccess.DirectAccessCredential
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.mdoc.issuersigned.buildIssuerNamespaces
import org.multipaz.mdoc.mso.MobileSecurityObjectGenerator
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.secure_area_test_app.ui.CsaConnectDialog
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.PassphraseConstraints
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.cloud.CloudCreateKeySettings
import org.multipaz.securearea.cloud.CloudSecureArea
import org.multipaz.securearea.cloud.CloudUserAuthType
import org.multipaz.securearea.software.SoftwareCreateKeySettings
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.testapp.DocumentModel
import org.multipaz.testapp.TestAppSettingsModel
import org.multipaz.testapp.TestAppUtils
import org.multipaz.testapp.TestAppUtils.CREDENTIAL_DOMAIN_MDOC_NO_USER_AUTH
import org.multipaz.testapp.platformHttpClientEngineFactory
import org.multipaz.testapp.platformSecureAreaHasKeyAgreement
import org.multipaz.util.Logger
import org.multipaz.util.Platform
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val TAG = "DocumentStoreScreen"

enum class DocumentCreationMode {
    NORMAL,
    EUPID_WITH_10_CREDENTIALS,
    EUPID_WITH_10_CREDENTIALS_BATCH,
    DCQL_TEST_DOCUMENTS
}

private val userAuthenticationTimeoutValues = mapOf(
    "0 sec (Auth every use)" to 0.seconds,
    "10 sec" to 10.seconds,
    "60 sec" to 60.seconds
)

@Composable
fun DocumentStoreScreen(
    documentStore: DocumentStore,
    documentModel: DocumentModel,
    softwareSecureArea: SoftwareSecureArea,
    settingsModel: TestAppSettingsModel,
    iacaKey: EcPrivateKey,
    iacaCert: X509Cert,
    showToast: (message: String) -> Unit,
    onViewDocument: (documentId: String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val numCredentialsPerDomain = remember { mutableIntStateOf(2) }
    val deviceKeyAlgorithm = remember { mutableStateOf<Algorithm>(Algorithm.ESP256) }
    val deviceKeyMacAlgorithm = remember { mutableStateOf<Algorithm>(Algorithm.ECDH_P256) }
    val documentSigningAlgorithm = remember { mutableStateOf<Algorithm>(Algorithm.ESP256) }
    val showProvisioningResult = remember { mutableStateOf<AnnotatedString?>(null) }
    val userAuthenticationTimeout = remember { mutableStateOf<Duration>(10.seconds) }

    val showDocumentCreationDialog = remember { mutableStateOf(false) }
    if (showDocumentCreationDialog.value) {
        // TODO: would be nice to show a live-updated string "Creating credential 42 of 80"
        AlertDialog(
            onDismissRequest = {},
            title = { Text(text = "Creating Documents") },
            text = {
                Text(
                    text = "Creating documents and credentials may take a while. " +
                            "This dialog will disappear when the process is complete."
                )
            },
            confirmButton = {
                Button(
                    onClick = {}) {
                    Text("OK")
                }
            }
        )
    }

    showProvisioningResult.value?.let {
        val scrollState = rememberScrollState()
        AlertDialog(
            onDismissRequest = {},
            title = { Text(text = "Provisioning Result") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .fillMaxWidth()
                        .background(color = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = it
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showProvisioningResult.value = null }) {
                    Text("Close")
                }
            }
        )
    }

    val showCsaConnectDialog = remember { mutableStateOf(false) }
    val documentCreationMode = remember { mutableStateOf<DocumentCreationMode>(DocumentCreationMode.NORMAL) }
    if (showCsaConnectDialog.value) {
        CsaConnectDialog(
            settingsModel.cloudSecureAreaUrl.value,
            onDismissRequest = {
                showCsaConnectDialog.value = false
            },
            onConnectButtonClicked = { url: String, walletPin: String, constraints: PassphraseConstraints ->
                showCsaConnectDialog.value = false
                settingsModel.cloudSecureAreaUrl.value = url
                coroutineScope.launch {
                    val cloudSecureArea = CloudSecureArea.create(
                        Platform.nonBackedUpStorage,
                        "CloudSecureArea?url=${url.encodeURLParameter()}",
                        url,
                        platformHttpClientEngineFactory()
                    )
                    try {
                        cloudSecureArea.register(
                            walletPin,
                            constraints
                        ) { true }
                        showToast("Registered with CSA")
                        val (dsKey, dsCert) = generateDsKeyAndCert(documentSigningAlgorithm.value, iacaKey, iacaCert)
                        provisionTestDocuments(
                            documentCreationMode = documentCreationMode.value,
                            showProvisioningResult = showProvisioningResult,
                            documentStore = documentStore,
                            secureArea = cloudSecureArea,
                            secureAreaCreateKeySettingsFunc = { challenge, algorithm, userAuthenticationRequired,
                                                                validFrom, validUntil ->
                                CloudCreateKeySettings.Builder(challenge)
                                    .setAlgorithm(algorithm)
                                    .setPassphraseRequired(true)
                                    .setUserAuthenticationRequired(
                                        userAuthenticationRequired,
                                        setOf(CloudUserAuthType.PASSCODE, CloudUserAuthType.BIOMETRIC)
                                    )
                                    .setValidityPeriod(validFrom, validUntil)
                                    .build()
                            },
                            dsKey = dsKey,
                            dsCert = dsCert,
                            showToast = showToast,
                            deviceKeyAlgorithm = deviceKeyAlgorithm.value,
                            deviceKeyMacAlgorithm = deviceKeyMacAlgorithm.value,
                            numCredentialsPerDomain = numCredentialsPerDomain.value,
                            showDocumentCreationDialog = showDocumentCreationDialog,
                        )
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        showToast("${e.message}")
                    }
                }
            }
        )
    }


    LazyColumn(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            TextButton(onClick = {
                coroutineScope.launch {
                    documentStore.listDocuments().forEach { documentId ->
                        documentStore.deleteDocument(documentId)
                    }
                }
            }) {
                Text(text = "Delete all Documents")
            }
        }
        item {
            TextButton(onClick = {
                coroutineScope.launch {
                    if (deviceKeyMacAlgorithm.value != Algorithm.UNSET && !platformSecureAreaHasKeyAgreement) {
                        showToast(
                            "Platform Secure Area does not have Key Agreement support. " +
                                    "Unset DeviceKey MAC Algorithm or try another Secure Area."
                        )
                        return@launch
                    }
                    val (dsKey, dsCert) = generateDsKeyAndCert(documentSigningAlgorithm.value, iacaKey, iacaCert)
                    provisionTestDocuments(
                        documentCreationMode = DocumentCreationMode.NORMAL,
                        showProvisioningResult = showProvisioningResult,
                        documentStore = documentStore,
                        secureArea = Platform.getSecureArea(),
                        secureAreaCreateKeySettingsFunc = { challenge, algorithm, userAuthenticationRequired,
                                                            validFrom, validUntil ->
                            CreateKeySettings(
                                algorithm = algorithm,
                                nonce = challenge,
                                userAuthenticationRequired = userAuthenticationRequired,
                                userAuthenticationTimeout = userAuthenticationTimeout.value,
                                validFrom = validFrom,
                                validUntil = validUntil
                            )
                        },
                        dsKey = dsKey,
                        dsCert = dsCert,
                        showToast = showToast,
                        deviceKeyAlgorithm = deviceKeyAlgorithm.value,
                        deviceKeyMacAlgorithm = deviceKeyMacAlgorithm.value,
                        numCredentialsPerDomain = numCredentialsPerDomain.value,
                        showDocumentCreationDialog = showDocumentCreationDialog,
                    )
                }
            }) {
                Text(text = "Create Test Documents in Platform Secure Area")
            }
        }
        item {
            TextButton(onClick = {
                coroutineScope.launch {
                    val (dsKey, dsCert) = generateDsKeyAndCert(documentSigningAlgorithm.value, iacaKey, iacaCert)
                    provisionTestDocuments(
                        documentCreationMode = DocumentCreationMode.NORMAL,
                        showProvisioningResult = showProvisioningResult,
                        documentStore = documentStore,
                        secureArea = softwareSecureArea,
                        secureAreaCreateKeySettingsFunc = { challenge, algorithm, userAuthenticationRequired,
                                                            validFrom, validUntil ->
                            SoftwareCreateKeySettings.Builder()
                                .setAlgorithm(algorithm)
                                .setPassphraseRequired(true, "1111", PassphraseConstraints.PIN_FOUR_DIGITS)
                                .build()
                        },
                        dsKey = dsKey,
                        dsCert = dsCert,
                        showToast = showToast,
                        deviceKeyAlgorithm = deviceKeyAlgorithm.value,
                        deviceKeyMacAlgorithm = deviceKeyMacAlgorithm.value,
                        numCredentialsPerDomain = numCredentialsPerDomain.value,
                        showDocumentCreationDialog = showDocumentCreationDialog,
                    )
                }
            }) {
                Text(text = "Create Test Documents in Software Secure Area")
            }
        }
        item {
            TextButton(onClick = {
                documentCreationMode.value = DocumentCreationMode.NORMAL
                showCsaConnectDialog.value = true
            }) {
                Text(text = "Create Test Documents in Cloud Secure Area")
            }
        }
        item {
            TextButton(onClick = {
                documentCreationMode.value = DocumentCreationMode.EUPID_WITH_10_CREDENTIALS
                showCsaConnectDialog.value = true
            }) {
                Text(text = "Create EUPID in CSA w/ 10 creds")
            }
        }
        item {
            TextButton(onClick = {
                documentCreationMode.value = DocumentCreationMode.EUPID_WITH_10_CREDENTIALS_BATCH
                showCsaConnectDialog.value = true
            }) {
                Text(text = "Create EUPID in CSA w/ 10 creds (batch)")
            }
        }
        item {
            TextButton(onClick = {
                coroutineScope.launch {
                    val (dsKey, dsCert) = generateDsKeyAndCert(documentSigningAlgorithm.value, iacaKey, iacaCert)
                    provisionTestDocuments(
                        documentCreationMode = DocumentCreationMode.DCQL_TEST_DOCUMENTS,
                        showProvisioningResult = showProvisioningResult,
                        documentStore = documentStore,
                        secureArea = Platform.getSecureArea(),
                        secureAreaCreateKeySettingsFunc = { challenge, algorithm, userAuthenticationRequired,
                                                            validFrom, validUntil ->
                            CreateKeySettings(
                                algorithm = algorithm,
                                nonce = challenge,
                                userAuthenticationRequired = userAuthenticationRequired,
                                userAuthenticationTimeout = userAuthenticationTimeout.value,
                                validFrom = validFrom,
                                validUntil = validUntil
                            )
                        },
                        dsKey = dsKey,
                        dsCert = dsCert,
                        showToast = showToast,
                        deviceKeyAlgorithm = deviceKeyAlgorithm.value,
                        deviceKeyMacAlgorithm = deviceKeyMacAlgorithm.value,
                        numCredentialsPerDomain = numCredentialsPerDomain.value,
                        showDocumentCreationDialog = showDocumentCreationDialog,
                    )
                }
            }) {
                Text(text = "Create Test Documents for DCQL testing")
            }
        }
        item {
            TextButton(onClick = {
                coroutineScope.launch {
                    val (dsKey, dsCert) = generateDsKeyAndCert(documentSigningAlgorithm.value, iacaKey, iacaCert)
                    provisionDirectAccessDocument(
                        showProvisioningResult = showProvisioningResult,
                        documentStore = documentStore,
                        secureArea = softwareSecureArea,
                        dsKey = dsKey,
                        dsCert = dsCert,
                        showToast = showToast,
                        deviceKeyAlgorithm = deviceKeyAlgorithm.value,
                        deviceKeyMacAlgorithm = deviceKeyMacAlgorithm.value,
                        showDocumentCreationDialog = showDocumentCreationDialog,
                    )
                }
            }) {
                Text(text = "Create Direct Access mDL")
            }
        }
        item {
            TextButton(onClick = {
                coroutineScope.launch {
                    val (dsKey, dsCert) = generateDsKeyAndCert(documentSigningAlgorithm.value, iacaKey, iacaCert)
                    deleteDirectAccessDocument(
                        documentStore = documentStore,
                        showToast = showToast,
                        showDocumentCreationDialog = showDocumentCreationDialog,
                        showProvisioningResult = showProvisioningResult,
                        documentType = DrivingLicense.getDocumentType().mdocDocumentType!!.docType
                    )
                }
            }) {
                Text(text = "Delete Direct Access mDL")
            }
        }
        item {
            SettingHeadline("Settings for new documents")
        }
        item {
            SettingMultipleChoice(
                title = "Credentials per Domain",
                choices = listOf(1, 2, 3, 5, 10, 15, 20).map { it.toString() },
                initialChoice = numCredentialsPerDomain.value.toString(),
                onChoiceSelected = { choice ->
                    numCredentialsPerDomain.value = choice.toInt(10)
                },
            )
        }
        item {
            SettingMultipleChoice(
                title = "DeviceKey Algorithm",
                choices = Algorithm.entries.mapNotNull { if (it.isSigning) it.name else null },
                initialChoice = deviceKeyAlgorithm.value.toString(),
                onChoiceSelected = { choice ->
                    val algorithm = Algorithm.entries.find { it.name == choice }!!
                    deviceKeyAlgorithm.value = algorithm
                },
            )
        }
        item {
            SettingMultipleChoice(
                title = "DeviceKey MAC Algorithm",
                choices = listOf(Algorithm.UNSET.name) +
                        Algorithm.entries.mapNotNull { if (it.isKeyAgreement) it.name else null },
                initialChoice = deviceKeyMacAlgorithm.value.toString(),
                onChoiceSelected = { choice ->
                    val algorithm = Algorithm.entries.find { it.name == choice }!!
                    deviceKeyMacAlgorithm.value = algorithm
                },
            )
        }
        item {
            SettingMultipleChoice(
                title = "Document Signing Algorithm",
                choices = Algorithm.entries.mapNotNull { if (it.fullySpecified && it.isSigning) it.name else null },
                initialChoice = documentSigningAlgorithm.value.toString(),
                onChoiceSelected = { choice ->
                    documentSigningAlgorithm.value = Algorithm.entries.find { it.name == choice }!!
                },
            )
        }
        item {
            SettingMultipleChoice(
                title = "User Auth Timeout",
                choices = userAuthenticationTimeoutValues.keys.toList(),
                initialChoice = userAuthenticationTimeoutValues.keys.toList()[1],
                onChoiceSelected = { choice ->
                    userAuthenticationTimeout.value = userAuthenticationTimeoutValues[choice]!!
                },
            )
        }
        item {
            SettingHeadline("Current Documents in DocumentStore")
        }
        if (documentModel.documentInfos.isEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = "DocumentStore is empty",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            for ((_, documentInfo) in documentModel.documentInfos) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            modifier = Modifier.height(32.dp),
                            contentScale = ContentScale.Fit,
                            bitmap = documentInfo.cardArt,
                            contentDescription = null,
                        )
                        TextButton(onClick = {
                            onViewDocument(documentInfo.document.identifier)
                            // TODO: Go to page showing document details and credentials
                        }) {
                            Text(
                                text = documentInfo.document.metadata.displayName ?: "(displayName not set)"
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun generateDsKeyAndCert(
    algorithm: Algorithm,
    iacaKey: EcPrivateKey,
    iacaCert: X509Cert,
): Pair<EcPrivateKey, X509Cert> {
    // The DS cert must not be valid for more than 457 days.
    //
    // Reference: ISO/IEC 18013-5:2021 Annex B.1.4 Document signer certificate
    //
    val dsCertValidFrom = Clock.System.now() - 1.days
    val dsCertsValidUntil = dsCertValidFrom + 455.days
    val dsKey = Crypto.createEcPrivateKey(algorithm.curve!!)
    val dsCert = MdocUtil.generateDsCertificate(
        iacaCert = iacaCert,
        iacaKey = iacaKey,
        dsKey = dsKey.publicKey,
        subject = X500Name.fromName("C=US,CN=OWF Multipaz TEST DS"),
        serial = ASN1Integer.fromRandom(numBits = 128),
        validFrom = dsCertValidFrom,
        validUntil = dsCertsValidUntil,
    )
    return Pair(dsKey, dsCert)
}

@OptIn(ExperimentalSerializationApi::class)
private suspend fun provisionTestDocuments(
    documentCreationMode: DocumentCreationMode,
    showProvisioningResult: MutableState<AnnotatedString?>,
    documentStore: DocumentStore,
    secureArea: SecureArea,
    secureAreaCreateKeySettingsFunc: (
        challenge: ByteString,
        algorithm: Algorithm,
        userAuthenticationRequired: Boolean,
        validFrom: Instant,
        validUntil: Instant
    ) -> CreateKeySettings,
    dsKey: EcPrivateKey,
    dsCert: X509Cert,
    deviceKeyAlgorithm: Algorithm,
    deviceKeyMacAlgorithm: Algorithm,
    numCredentialsPerDomain: Int,
    showToast: (message: String) -> Unit,
    showDocumentCreationDialog: MutableState<Boolean>
) {
    // This can be slow... so we show a dialog to help convey this to the user.
    showDocumentCreationDialog.value = true

    if (documentCreationMode == DocumentCreationMode.NORMAL && documentStore.listDocuments().size >= 5) {
        // TODO: we need a more granular check once we support provisioning of other kinds of documents
        showToast("Test Documents already provisioned. Delete all documents and try again")
        showDocumentCreationDialog.value = false
        return
    }
    if (secureArea.supportedAlgorithms.find { it == deviceKeyAlgorithm } == null) {
        showToast("Secure Area doesn't support algorithm $deviceKeyAlgorithm for DeviceKey")
        showDocumentCreationDialog.value = false
        return
    }
    if (deviceKeyMacAlgorithm != Algorithm.UNSET &&
        secureArea.supportedAlgorithms.find { it == deviceKeyMacAlgorithm } == null
    ) {
        showToast("Secure Area doesn't support algorithm $deviceKeyMacAlgorithm for DeviceKey for MAC")
        showDocumentCreationDialog.value = false
        return
    }
    try {
        val numDocsBegin = documentStore.listDocuments().size
        val timestampBegin = Clock.System.now()
        val openid4vciAttestationCompactSerialization = TestAppUtils.provisionTestDocuments(
            documentCreationMode,
            documentStore,
            secureArea,
            secureAreaCreateKeySettingsFunc,
            dsKey,
            dsCert,
            deviceKeyAlgorithm,
            deviceKeyMacAlgorithm,
            numCredentialsPerDomain
        )
        val timestampEnd = Clock.System.now()
        val numDocsEnd = documentStore.listDocuments().size

        val provisioningResult = buildAnnotatedString {
            append("Created ${numDocsEnd - numDocsBegin} document(s) in ${timestampEnd - timestampBegin}.")
            if (openid4vciAttestationCompactSerialization != null) {
                val prettyAttestation = Json {
                    prettyPrint = true
                    prettyPrintIndent = "  "
                }.encodeToString(JsonWebSignature.getInfo(openid4vciAttestationCompactSerialization).claimsSet)
                append("\n\nOpenID4VCI attestation:\n")
                append(prettyAttestation)
            }
        }
        showProvisioningResult.value = provisioningResult
    } catch (e: Throwable) {
        e.printStackTrace()
        showToast("Error provisioning documents: $e")
    }
    showDocumentCreationDialog.value = false
}

@OptIn(ExperimentalSerializationApi::class, ExperimentalResourceApi::class)
private suspend fun provisionDirectAccessDocument(
    showProvisioningResult: MutableState<AnnotatedString?>,
    documentStore: DocumentStore,
    secureArea: SecureArea,
    dsKey: EcPrivateKey,
    dsCert: X509Cert,
    deviceKeyAlgorithm: Algorithm,
    deviceKeyMacAlgorithm: Algorithm,
    showToast: (message: String) -> Unit,
    showDocumentCreationDialog: MutableState<Boolean>
) {
    // This can be slow... so we show a dialog to help convey this to the user.
    showDocumentCreationDialog.value = true

    // Dont add if already exists (however, the APDU appears to be tolerant to just overwriting it).
    if (findDirectAccessExampleDocument(documentStore) != null) {
        showToast("DirectAccess mDL document already provisioned. Delete it and try again.")
        showDocumentCreationDialog.value = false
        return
    }

    if (secureArea.supportedAlgorithms.find { it == deviceKeyAlgorithm } == null) {
        showToast("Secure Area doesn't support algorithm $deviceKeyAlgorithm for DeviceKey")
        showDocumentCreationDialog.value = false
        return
    }
    if (deviceKeyMacAlgorithm != Algorithm.UNSET &&
        secureArea.supportedAlgorithms.find { it == deviceKeyMacAlgorithm } == null
    ) {
        showToast("Secure Area doesn't support algorithm $deviceKeyMacAlgorithm for DeviceKey for MAC")
        showDocumentCreationDialog.value = false
        return
    }
    try {
        val numDocsBegin = documentStore.listDocuments().size
        val timestampBegin = Clock.System.now()

        // Provision doc.
        require(deviceKeyAlgorithm.isSigning)
        require(deviceKeyMacAlgorithm == Algorithm.UNSET || deviceKeyMacAlgorithm.isKeyAgreement)

        val cardArt = getDrawableResourceBytes(
            getSystemResourceEnvironment(),
            Res.drawable.driving_license_card_art,
        )

        val displayName = "DirectAccess mDL"
        val documentType = DrivingLicense.getDocumentType()

        val document = documentStore.createDocument(
            displayName = displayName,
            typeDisplayName = documentType.displayName,
            cardArt = ByteString(cardArt),
        )

        val now = Clock.System.now()
        val signedAt = now - 1.hours
        val validFrom = now - 1.hours
        val validUntil = now + 365.days

        // Add Credentials
        if (documentType.mdocDocumentType != null) {
            val issuerNamespaces = buildIssuerNamespaces {
                for ((nsName, ns) in documentType.mdocDocumentType?.namespaces!!) {
                    addNamespace(nsName) {
                        for ((deName, de) in ns.dataElements) {
                            val sampleValue = de.attribute.sampleValueMdoc
                            if (sampleValue != null) {
                                val value = if (deName.startsWith("given_name")) {
                                    Tstr("Erika")
                                } else {
                                    sampleValue
                                }
                                addDataElement(deName, value)
                            } else {
                                Logger.w(TAG, "No sample value for data element $deName")
                            }
                        }
                    }
                }
            }

            // Create authentication keys.
            val algorithm = deviceKeyAlgorithm
            if (algorithm != Algorithm.UNSET) {
                val pemEncodedCert = Res.readBytes("files/owf_identity_credential_reader_cert.pem")
                val trustedReaderCert = X509Cert.fromPem(pemEncodedCert.decodeToString())
                val directAccessCredential = DirectAccessCredential.create(
                    document = document,
                    asReplacementForIdentifier = null,
                    domain = CREDENTIAL_DOMAIN_MDOC_NO_USER_AUTH,
                    documentType = documentType.mdocDocumentType!!.docType
                )

                // Generate an MSO and issuer-signed data for this authentication key.
                val msoGenerator = MobileSecurityObjectGenerator(
                    Algorithm.SHA256,
                    documentType.mdocDocumentType!!.docType,
                    directAccessCredential.attestation.publicKey
                )
                msoGenerator.setValidityInfo(signedAt, validFrom, validUntil, null)
                msoGenerator.addValueDigests(issuerNamespaces)

                val mso = msoGenerator.generate()
                val readerAuth = buildCborArray {
                    add((trustedReaderCert.ecPublicKey as EcPublicKeyDoubleCoordinate).asUncompressedPointEncoding)
                }
                val taggedEncodedMso = Cbor.encode(Tagged(24, Bstr(mso)))

                // IssuerAuth is a COSE_Sign1 where payload is MobileSecurityObjectBytes
                //
                // MobileSecurityObjectBytes = #6.24(bstr .cbor MobileSecurityObject)
                //
                val protectedHeaders = mapOf<CoseLabel, DataItem>(
                    Pair(
                        CoseNumberLabel(Cose.COSE_LABEL_ALG),
                        Algorithm.ES256.coseAlgorithmIdentifier!!.toDataItem()
                    )
                )
                val unprotectedHeaders = mapOf<CoseLabel, DataItem>(
                    Pair(
                        CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
                        X509CertChain(listOf(dsCert)).toDataItem()
                    )
                )
                val encodedIssuerAuth = Cbor.encode(
                    Cose.coseSign1Sign(
                        dsKey,
                        taggedEncodedMso,
                        true,
                        dsKey.publicKey.curve.defaultSigningAlgorithm,
                        protectedHeaders,
                        unprotectedHeaders
                    ).toDataItem()
                )
                val issuerProvidedAuthenticationData = Cbor.encode(
                    buildCborMap {
                        put("issuerNameSpaces", issuerNamespaces.toDataItem())
                        put("issuerAuth", RawCbor(encodedIssuerAuth))
                        put("readerAccess", readerAuth)
                    }
                )

                // Now that we have issuer-provided authentication data we certify the authentication key.
                directAccessCredential.certify(
                    issuerProvidedAuthenticationData,
                    validFrom,
                    validUntil
                )
            }
        }

        val timestampEnd = Clock.System.now()
        val numDocsEnd = documentStore.listDocuments().size

        val provisioningResult = buildAnnotatedString {
            append("Created ${numDocsEnd - numDocsBegin} document(s) in ${timestampEnd - timestampBegin}.")
        }
        showProvisioningResult.value = provisioningResult
    } catch (e: Throwable) {
        e.printStackTrace()
        showToast("Error provisioning documents: $e")
    }
    showDocumentCreationDialog.value = false
}

private suspend fun deleteDirectAccessDocument(
    showProvisioningResult: MutableState<AnnotatedString?>,
    documentStore: DocumentStore,
    documentType: String,
    showToast: (message: String) -> Unit,
    showDocumentCreationDialog: MutableState<Boolean>,
) {
    // This can be slow... so we show a dialog to help convey this to the user.
    showDocumentCreationDialog.value = true

    try {
        val timestampBegin = Clock.System.now()

        // Wipe APDU.
        DirectAccessCredential.delete(documentType)

        // Remove from DocStore.
        val directAccessDocumentId = findDirectAccessExampleDocument(documentStore)
        if (directAccessDocumentId == null) {
            showToast("Test document DirectAccess mDL is not provisioned or already deleted.")
            showDocumentCreationDialog.value = false
            return
        }

        documentStore.deleteDocument(directAccessDocumentId)
        val timestampEnd = Clock.System.now()
        val provisioningResult = buildAnnotatedString {
            append("Direct Access document deleted in ${timestampEnd - timestampBegin}.")
        }
        showProvisioningResult.value = provisioningResult

    } catch (e: Throwable) {
        e.printStackTrace()
        showToast("Error deleting document: $e")
    }
    showDocumentCreationDialog.value = false
}

/** Locate the first Document with DirectAccess credentials. */
private suspend fun findDirectAccessExampleDocument(documentStore: DocumentStore): String? {
    val directAccessDocumentId = documentStore.listDocuments().firstOrNull { documentId ->
        documentStore.lookupDocument(documentId)?.let {
            it.getCredentials().firstOrNull { credential ->
                credential.credentialType == DirectAccessCredential.CREDENTIAL_TYPE
            } != null
        } ?: false
    }
    return directAccessDocumentId
}

