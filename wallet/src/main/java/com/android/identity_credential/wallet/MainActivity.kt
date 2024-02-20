/*
 * Copyright (C) 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.android.identity_credential.wallet

import android.Manifest
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.android.identity_credential.wallet.navigation.WalletNavigation
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var application: WalletApplication

    private val provisioningViewModel: ProvisioningViewModel by viewModels()
    private val credentialInformationViewModel: CredentialInformationViewModel by viewModels()
    private lateinit var sharedPreferences: SharedPreferences

    private val permissionTracker: PermissionTracker = if (Build.VERSION.SDK_INT >= 31) {
        PermissionTracker(
            this, mapOf(
                Manifest.permission.CAMERA to "This application requires camera permission to scan",
                Manifest.permission.NFC to "NFC permission is required to operate",
                Manifest.permission.BLUETOOTH_ADVERTISE to "This application requires Bluetooth " +
                        "advertising to send credential data",
                Manifest.permission.BLUETOOTH_SCAN to "This application requires Bluetooth " +
                        "scanning to send credential data",
                Manifest.permission.BLUETOOTH_CONNECT to "This application requires Bluetooth " +
                        "connection to send credential data"
            )
        )
    } else {
        PermissionTracker(
            this, mapOf(
                Manifest.permission.CAMERA to "This application requires camera permission to scan",
                Manifest.permission.NFC to "NFC permission is required to operate",
                Manifest.permission.ACCESS_FINE_LOCATION to "This application requires Bluetooth " +
                        "to send credential data"
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = getApplication() as WalletApplication
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        permissionTracker.updatePermissions()
        val blePermissions: List<String> = if (Build.VERSION.SDK_INT >= 31) {
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            IdentityCredentialTheme {
                permissionTracker.PermissionCheck(permissions = blePermissions) {
                    // A surface container using the 'background' color from the theme
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()

                        WalletNavigation(
                            navController,
                            application = application,
                            provisioningViewModel = provisioningViewModel,
                            credentialInformationViewModel = credentialInformationViewModel,
                            permissionTracker = permissionTracker,
                            sharedPreferences = sharedPreferences
                        )
                    }
                }
            }
        }
    }
}
