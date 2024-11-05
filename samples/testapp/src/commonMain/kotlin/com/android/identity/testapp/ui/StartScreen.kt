package com.android.identity.testapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.identity.testapp.Platform
import com.android.identity.testapp.platform
import identitycredential.samples.testapp.generated.resources.Res
import identitycredential.samples.testapp.generated.resources.about_screen_title
import identitycredential.samples.testapp.generated.resources.android_keystore_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.passphrase_entry_field_screen_title
import identitycredential.samples.testapp.generated.resources.cloud_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.consent_modal_bottom_sheet_list_screen_title
import identitycredential.samples.testapp.generated.resources.iso_mdoc_multi_device_testing_title
import identitycredential.samples.testapp.generated.resources.iso_mdoc_proximity_reading_title
import identitycredential.samples.testapp.generated.resources.iso_mdoc_proximity_sharing_title
import identitycredential.samples.testapp.generated.resources.qr_codes_screen_title
import identitycredential.samples.testapp.generated.resources.secure_enclave_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.software_secure_area_screen_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun StartScreen(
    onClickAbout: () -> Unit = {},
    onClickSoftwareSecureArea: () -> Unit = {},
    onClickAndroidKeystoreSecureArea: () -> Unit = {},
    onClickCloudSecureArea: () -> Unit = {},
    onClickSecureEnclaveSecureArea: () -> Unit = {},
    onClickPassphraseEntryField: () -> Unit = {},
    onClickConsentSheetList: () -> Unit = {},
    onClickQrCodes: () -> Unit = {},
    onClickIsoMdocProximitySharing: () -> Unit = {},
    onClickIsoMdocProximityReading: () -> Unit = {},
    onClickMdocTransportMultiDeviceTesting: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.padding(8.dp)
        ) {
            item {
                TextButton(onClick = onClickAbout) {
                    Text(stringResource(Res.string.about_screen_title))
                }
            }

            item {
                TextButton(onClick = onClickSoftwareSecureArea) {
                    Text(stringResource(Res.string.software_secure_area_screen_title))
                }
            }

            when (platform) {
                Platform.ANDROID -> {
                    item {
                        TextButton(onClick = onClickAndroidKeystoreSecureArea) {
                            Text(stringResource(Res.string.android_keystore_secure_area_screen_title))
                        }

                        // Cloud Secure Area is Android-only for now.
                        TextButton(onClick = onClickCloudSecureArea) {
                            Text(stringResource(Res.string.cloud_secure_area_screen_title))
                        }
                    }
                }

                Platform.IOS -> {
                    item {
                        TextButton(onClick = onClickSecureEnclaveSecureArea) {
                            Text(stringResource(Res.string.secure_enclave_secure_area_screen_title))
                        }
                    }
                }
            }

            item {
                TextButton(onClick = onClickPassphraseEntryField) {
                    Text(stringResource(Res.string.passphrase_entry_field_screen_title))
                }
            }

            item {
                TextButton(onClick = onClickConsentSheetList) {
                    Text(stringResource(Res.string.consent_modal_bottom_sheet_list_screen_title))
                }
            }

            item {
                TextButton(onClick = onClickQrCodes) {
                    Text(stringResource(Res.string.qr_codes_screen_title))
                }
            }

            item {
                TextButton(onClick = onClickIsoMdocProximitySharing) {
                    Text(stringResource(Res.string.iso_mdoc_proximity_sharing_title))
                }
            }

            item {
                TextButton(onClick = onClickIsoMdocProximityReading) {
                    Text(stringResource(Res.string.iso_mdoc_proximity_reading_title))
                }
            }

            item {
                TextButton(onClick = onClickMdocTransportMultiDeviceTesting) {
                    Text(stringResource(Res.string.iso_mdoc_multi_device_testing_title))
                }
            }
        }
    }
}
