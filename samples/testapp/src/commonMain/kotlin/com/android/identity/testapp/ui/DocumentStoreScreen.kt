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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.X509Cert
import com.android.identity.document.DocumentStore
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
import com.android.identity.testapp.platformSecureAreaProvider
import com.android.identity.testapp.platformStorage
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString

private const val TAG = "DocumentStoreScreen"

@Composable
fun DocumentStoreScreen(
    documentStore: DocumentStore,
    documentModel: DocumentModel,
    softwareSecureArea: SoftwareSecureArea,
    settingsModel: TestAppSettingsModel,
    dsKey: EcPrivateKey,
    dsCert: X509Cert,
    showToast: (message: String) -> Unit,
    onViewDocument: (documentId: String) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

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

                        provisionTestDocuments(
                            documentStore = documentStore,
                            secureArea = cloudSecureArea,
                            secureAreaCreateKeySettingsFunc = { challenge, keyPurposes, userAuthenticationRequired ->
                                CloudCreateKeySettings.Builder(challenge.toByteArray())
                                    .setKeyPurposes(keyPurposes)
                                    .setPassphraseRequired(true)
                                    .setUserAuthenticationRequired(
                                        userAuthenticationRequired,
                                        setOf(CloudUserAuthType.PASSCODE, CloudUserAuthType.BIOMETRIC)
                                    )
                                    .build()
                            },
                            dsKey = dsKey,
                            dsCert = dsCert,
                            showToast = showToast
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
                    provisionTestDocuments(
                        documentStore = documentStore,
                        secureArea = platformSecureAreaProvider().get(),
                        secureAreaCreateKeySettingsFunc = ::platformCreateKeySettings,
                        dsKey = dsKey,
                        dsCert = dsCert,
                        showToast = showToast
                    )
                }
            }) {
                Text(text = "Create Test Documents in Platform Secure Area")
            }
        }
        item {
            TextButton(onClick = {
                coroutineScope.launch {
                    provisionTestDocuments(
                        documentStore = documentStore,
                        secureArea = softwareSecureArea,
                        secureAreaCreateKeySettingsFunc = { challenge, keyPurposes, userAuthenticationRequired ->
                            SoftwareCreateKeySettings.Builder()
                                .setKeyPurposes(keyPurposes)
                                .setPassphraseRequired(true, "1111", PassphraseConstraints.PIN_FOUR_DIGITS)
                                .build()
                        },
                        dsKey = dsKey,
                        dsCert = dsCert,
                        showToast = showToast
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
            Text(text = "Current Documents in DocumentStore")
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

private suspend fun provisionTestDocuments(
    documentStore: DocumentStore,
    secureArea: SecureArea,
    secureAreaCreateKeySettingsFunc: (
        challenge: ByteString,
        keyPurposes: Set<KeyPurpose>,
        userAuthenticationRequired: Boolean
    ) -> CreateKeySettings,
    dsKey: EcPrivateKey,
    dsCert: X509Cert,
    showToast: (message: String) -> Unit
) {
    try {
        TestAppUtils.provisionTestDocuments(
            documentStore,
            secureArea,
            secureAreaCreateKeySettingsFunc,
            dsKey,
            dsCert
        )
    } catch (e: Throwable) {
        e.printStackTrace()
        showToast("Error provisioning documents: $e")
    }
}


