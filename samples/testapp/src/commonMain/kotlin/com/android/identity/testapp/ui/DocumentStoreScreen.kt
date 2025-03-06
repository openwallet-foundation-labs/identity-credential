package com.android.identity.testapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.android.identity.asn1.ASN1Integer
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.X500Name
import com.android.identity.crypto.X509Cert
import com.android.identity.document.DocumentStore
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.secure_area_test_app.ui.CsaConnectDialog
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.cloud.CloudCreateKeySettings
import com.android.identity.securearea.cloud.CloudSecureArea
import com.android.identity.securearea.cloud.CloudUserAuthType
import com.android.identity.securearea.software.SoftwareCreateKeySettings
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.testapp.DocumentModel
import com.android.identity.testapp.TestAppSettingsModel
import com.android.identity.testapp.TestAppUtils
import com.android.identity.testapp.platformCreateKeySettings
import com.android.identity.testapp.platformSecureAreaHasKeyAgreement
import com.android.identity.testapp.platformSecureAreaProvider
import com.android.identity.testapp.platformStorage
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.io.bytestring.ByteString

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
    val documentSigningKeyCurve = remember { mutableStateOf<EcCurve>(EcCurve.P256) }
    val deviceKeyCurve = remember { mutableStateOf<EcCurve>(EcCurve.P256) }
    val deviceKeyPurposeSign = remember { mutableStateOf<Boolean>(true) }
    val deviceKeyPurposeAgreeKey = remember { mutableStateOf<Boolean>(true) }

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
                        url
                    )
                    try {
                        cloudSecureArea.register(
                            walletPin,
                            constraints
                        ) { true }
                        showToast("Registered with CSA")
                        val (dsKey, dsCert) = generateDsKeyAndCert(documentSigningKeyCurve.value, iacaKey, iacaCert)
                        provisionTestDocuments(
                            documentStore = documentStore,
                            secureArea = cloudSecureArea,
                            secureAreaCreateKeySettingsFunc = { challenge, curve, keyPurposes, userAuthenticationRequired,
                                                                validFrom, validUntil ->
                                CloudCreateKeySettings.Builder(challenge.toByteArray())
                                    .setEcCurve(curve)
                                    .setKeyPurposes(keyPurposes)
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
                            deviceKeyPurposeSign = deviceKeyPurposeSign.value,
                            deviceKeyPurposeAgreeKey = deviceKeyPurposeAgreeKey.value,
                            deviceKeyCurve = deviceKeyCurve.value,
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
                    if (deviceKeyPurposeAgreeKey.value && !platformSecureAreaHasKeyAgreement) {
                        showToast("Platform Secure Area does not have Key Agreement support. " +
                                "Either uncheck AGREE_KEY or try another Secure Area.")
                        return@launch
                    }
                    val (dsKey, dsCert) = generateDsKeyAndCert(documentSigningKeyCurve.value, iacaKey, iacaCert)
                    provisionTestDocuments(
                        documentStore = documentStore,
                        secureArea = platformSecureAreaProvider().get(),
                        secureAreaCreateKeySettingsFunc = ::platformCreateKeySettings,
                        dsKey = dsKey,
                        dsCert = dsCert,
                        showToast = showToast,
                        deviceKeyPurposeSign = deviceKeyPurposeSign.value,
                        deviceKeyPurposeAgreeKey = deviceKeyPurposeAgreeKey.value,
                        deviceKeyCurve = deviceKeyCurve.value,
                    )
                }
            }) {
                Text(text = "Create Test Documents in Platform Secure Area")
            }
        }
        item {
            TextButton(onClick = {
                coroutineScope.launch {
                    val (dsKey, dsCert) = generateDsKeyAndCert(documentSigningKeyCurve.value, iacaKey, iacaCert)
                    provisionTestDocuments(
                        documentStore = documentStore,
                        secureArea = softwareSecureArea,
                        secureAreaCreateKeySettingsFunc = { challenge, curve, keyPurposes, userAuthenticationRequired,
                                                            validFrom, validUntil ->
                            SoftwareCreateKeySettings.Builder()
                                .setEcCurve(curve)
                                .setKeyPurposes(keyPurposes)
                                .setPassphraseRequired(true, "1111", PassphraseConstraints.PIN_FOUR_DIGITS)
                                .build()
                        },
                        dsKey = dsKey,
                        dsCert = dsCert,
                        showToast = showToast,
                        deviceKeyPurposeSign = deviceKeyPurposeSign.value,
                        deviceKeyPurposeAgreeKey = deviceKeyPurposeAgreeKey.value,
                        deviceKeyCurve = deviceKeyCurve.value,
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
            SettingToggle(
                title = "Create DeviceKey with purpose SIGN",
                isChecked = deviceKeyPurposeSign.value,
                onCheckedChange = { deviceKeyPurposeSign.value = it },
                enabled = (deviceKeyCurve.value.supportsSigning == true)
            )
        }
        item {
            SettingToggle(
                title = "Create DeviceKey with purpose AGREE_KEY",
                isChecked = deviceKeyPurposeAgreeKey.value,
                onCheckedChange = { deviceKeyPurposeAgreeKey.value = it },
                enabled = (deviceKeyCurve.value.supportsKeyAgreement == true)
            )
        }
        item {
            SettingMultipleChoice(
                title = "DeviceKey Curve",
                choices = EcCurve.entries.map { it.name },
                initialChoice = deviceKeyCurve.value.toString(),
                onChoiceSelected = { choice ->
                    val curve = EcCurve.entries.find { it.name == choice }!!
                    deviceKeyCurve.value = curve
                    deviceKeyPurposeSign.value = curve.supportsSigning
                    deviceKeyPurposeAgreeKey.value = curve.supportsKeyAgreement
                },
            )
        }
        item {
            SettingMultipleChoice(
                title ="Document Signing Key Curve",
                choices = EcCurve.entries.mapNotNull { if (it.supportsSigning) it.name else null },
                initialChoice = documentSigningKeyCurve.value.toString(),
                onChoiceSelected = { choice ->
                    documentSigningKeyCurve.value = EcCurve.entries.find { it.name == choice }!!
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
    curve: EcCurve,
    iacaKey: EcPrivateKey,
    iacaCert: X509Cert,
): Pair<EcPrivateKey, X509Cert> {
    val certsValidFrom = LocalDate.parse("2024-12-01").atStartOfDayIn(TimeZone.UTC)
    val certsValidUntil = LocalDate.parse("2034-12-01").atStartOfDayIn(TimeZone.UTC)
    val dsKey = Crypto.createEcPrivateKey(curve)
    val dsCert = MdocUtil.generateDsCertificate(
        iacaCert = iacaCert,
        iacaKey = iacaKey,
        dsKey = dsKey.publicKey,
        subject = X500Name.fromName("C=ZZ,CN=OWF Identity Credential TEST DS"),
        serial = ASN1Integer(1L),
        validFrom = certsValidFrom,
        validUntil = certsValidUntil,
    )
    return Pair(dsKey, dsCert)
}

private suspend fun provisionTestDocuments(
    documentStore: DocumentStore,
    secureArea: SecureArea,
    secureAreaCreateKeySettingsFunc: (
        challenge: ByteString,
        curve: EcCurve,
        keyPurposes: Set<KeyPurpose>,
        userAuthenticationRequired: Boolean,
        validFrom: Instant,
        validUntil: Instant
    ) -> CreateKeySettings,
    dsKey: EcPrivateKey,
    dsCert: X509Cert,
    deviceKeyPurposeSign: Boolean,
    deviceKeyPurposeAgreeKey: Boolean,
    deviceKeyCurve: EcCurve,
    showToast: (message: String) -> Unit
) {
    val deviceKeyPurposes = mutableSetOf<KeyPurpose>()
    if (deviceKeyPurposeSign) {
        deviceKeyPurposes.add(KeyPurpose.SIGN)
    }
    if (deviceKeyPurposeAgreeKey) {
        deviceKeyPurposes.add(KeyPurpose.AGREE_KEY)
    }
    if (deviceKeyPurposes.isEmpty()) {
        showToast("At least one purpose must be set.")
        return
    }
    // TODO: When SecureArea gains ability to convey which curves it supports (see Issue #850) add check here
    //  and show a Toast explaining if the chosen curve isn't supported.
    try {
        TestAppUtils.provisionTestDocuments(
            documentStore,
            secureArea,
            secureAreaCreateKeySettingsFunc,
            dsKey,
            dsCert,
            deviceKeyPurposes,
            deviceKeyCurve
        )
    } catch (e: Throwable) {
        e.printStackTrace()
        showToast("Error provisioning documents: $e")
    }
}


