package org.multipaz.testapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import org.multipaz.asn1.ASN1Integer
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.document.DocumentStore
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
import org.multipaz.testapp.platformCreateKeySettings
import org.multipaz.testapp.platformSecureAreaHasKeyAgreement
import org.multipaz.testapp.platformSecureAreaProvider
import org.multipaz.testapp.platformStorage
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.Algorithm
import org.multipaz.testapp.platformHttpClientEngineFactory
import org.multipaz.util.fromHex
import kotlin.time.Duration.Companion.days

private const val TAG = "DocumentStoreScreen"

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

    val showDocumentCreationDialog = remember { mutableStateOf(false) }
    if (showDocumentCreationDialog.value) {
        // TODO: would be nice to show a live-updated string "Creating credential 42 of 80"
        AlertDialog(
            onDismissRequest = {},
            title = { Text(text = "Creating Documents") },
            text = { Text(text = "Creating documents and credentials may take a while. " +
            "This dialog will disappear when the process is complete.") },
            confirmButton = {
                Button(
                    onClick = {}) {
                    Text("OK")
                }
            }
        )
    }

    val showCsaConnectDialog = remember { mutableStateOf(false) }
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
                        platformStorage(),
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
                        showToast("Platform Secure Area does not have Key Agreement support. " +
                                "Unset DeviceKey MAC Algorithm or try another Secure Area.")
                        return@launch
                    }
                    val (dsKey, dsCert) = generateDsKeyAndCert(documentSigningAlgorithm.value, iacaKey, iacaCert)
                    provisionTestDocuments(
                        documentStore = documentStore,
                        secureArea = platformSecureAreaProvider().get(),
                        secureAreaCreateKeySettingsFunc = ::platformCreateKeySettings,
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
                showCsaConnectDialog.value = true
            }) {
                Text(text = "Create Test Documents in Cloud Secure Area")
            }
        }
        item {
            SettingHeadline("Settings for new documents")
        }
        item {
            SettingMultipleChoice(
                title ="Credentials per Domain",
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
                title ="Document Signing Algorithm",
                choices = Algorithm.entries.mapNotNull { if (it.fullySpecified && it.isSigning) it.name else null },
                initialChoice = documentSigningAlgorithm.value.toString(),
                onChoiceSelected = { choice ->
                    documentSigningAlgorithm.value = Algorithm.entries.find { it.name == choice }!!
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

private suspend fun provisionTestDocuments(
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

    if (documentStore.listDocuments().size >= 5) {
        // TODO: we need a more granular check once we support provisioning other kinds of documents
        showToast("Test Documents already provisioned. Delete all documents and try again")
        return
    }
    if (secureArea.supportedAlgorithms.find { it == deviceKeyAlgorithm } == null) {
        showToast("Secure Area doesn't support algorithm $deviceKeyAlgorithm for DeviceKey")
        return
    }
    if (deviceKeyMacAlgorithm != Algorithm.UNSET &&
        secureArea.supportedAlgorithms.find { it == deviceKeyMacAlgorithm } == null) {
        showToast("Secure Area doesn't support algorithm $deviceKeyMacAlgorithm for DeviceKey for MAC")
        return
    }
    try {
        TestAppUtils.provisionTestDocuments(
            documentStore,
            secureArea,
            secureAreaCreateKeySettingsFunc,
            dsKey,
            dsCert,
            deviceKeyAlgorithm,
            deviceKeyMacAlgorithm,
            numCredentialsPerDomain
        )
    } catch (e: Throwable) {
        e.printStackTrace()
        showToast("Error provisioning documents: $e")
    }
    showDocumentCreationDialog.value = false
}


