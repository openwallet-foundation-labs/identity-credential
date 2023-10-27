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
import androidx.navigation.NavHostController
import com.android.identity.testapp.Platform
import com.android.identity.testapp.Screen
import com.android.identity.testapp.platform
import identitycredential.samples.testapp.generated.resources.Res
import identitycredential.samples.testapp.generated.resources.about_screen_title
import identitycredential.samples.testapp.generated.resources.android_keystore_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.passphrase_entry_field_screen_title
import identitycredential.samples.testapp.generated.resources.cloud_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.secure_enclave_secure_area_screen_title
import identitycredential.samples.testapp.generated.resources.software_secure_area_screen_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun StartScreen(navController: NavHostController) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.padding(8.dp)
        ) {
            item {
                TextButton(
                    onClick = {
                        navController.navigate(route = Screen.About.name)
                    }
                ) {
                    Text(stringResource(Res.string.about_screen_title))
                }
            }

            item {
                TextButton(
                    onClick = {
                        navController.navigate(route = Screen.SoftwareSecureArea.name)
                    }
                ) {
                    Text(stringResource(Res.string.software_secure_area_screen_title))
                }
            }

            when (platform) {
                Platform.ANDROID -> {
                    item {
                        TextButton(
                            onClick = {
                                navController.navigate(route = Screen.AndroidKeystoreSecureArea.name)
                            }
                        ) {
                            Text(stringResource(Res.string.android_keystore_secure_area_screen_title))
                        }

                        // Cloud Secure Area is Android-only for now.
                        TextButton(
                            onClick = {
                                navController.navigate(route = Screen.CloudSecureArea.name)
                            }
                        ) {
                            Text(stringResource(Res.string.cloud_secure_area_screen_title))
                        }
                    }
                }

                Platform.IOS -> {
                    item {
                        TextButton(
                            onClick = {
                                navController.navigate(route = Screen.SecureEnclaveSecureArea.name)
                            }
                        ) {
                            Text(stringResource(Res.string.secure_enclave_secure_area_screen_title))
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = {
                        navController.navigate(route = Screen.PassphraseEntryField.name)
                    }
                ) {
                    Text(stringResource(Res.string.passphrase_entry_field_screen_title))
                }
            }

        }
    }
}
