package com.android.identity.secure_area_test_app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.android.identity.secure_area_test_app.Platform
import com.android.identity.secure_area_test_app.Screen
import com.android.identity.secure_area_test_app.platform
import identitycredential.samples.secure_area_test_app.generated.resources.Res
import identitycredential.samples.secure_area_test_app.generated.resources.android_keystore_secure_area_screen_title
import identitycredential.samples.secure_area_test_app.generated.resources.secure_enclave_secure_area_screen_title
import identitycredential.samples.secure_area_test_app.generated.resources.software_secure_area_screen_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun StartScreen(navController: NavHostController) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        LazyColumn(
            modifier = Modifier.padding(8.dp)
        ) {
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
        }
    }
}
