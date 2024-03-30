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

package com.android.identity_credential.wallet

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.android.identity_credential.wallet.navigation.WalletNavigation
import com.android.identity_credential.wallet.ui.theme.IdentityCredentialTheme

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var application: WalletApplication
    private val qrEngagementViewModel: QrEngagementViewModel by viewModels()
    private val provisioningViewModel: ProvisioningViewModel by viewModels()

    private val permissionTracker: PermissionTracker = if (Build.VERSION.SDK_INT >= 31) {
        PermissionTracker(this, mapOf(
            Manifest.permission.CAMERA to R.string.permission_camera,
            Manifest.permission.NFC to R.string.permission_nfc,
            Manifest.permission.BLUETOOTH_ADVERTISE to R.string.permission_bluetooth_advertise,
            Manifest.permission.BLUETOOTH_SCAN to R.string.permission_bluetooth_scan,
            Manifest.permission.BLUETOOTH_CONNECT to R.string.permission_bluetooth_connect
        ))
    } else {
        PermissionTracker(this, mapOf(
            Manifest.permission.CAMERA to R.string.permission_camera,
            Manifest.permission.NFC to R.string.permission_nfc,
            Manifest.permission.ACCESS_FINE_LOCATION to R.string.permission_bluetooth_connect
        ))
    }

    override fun onStart() {
        super.onStart()
        application.settingsModel.updateScreenLockIsSetup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = getApplication() as WalletApplication

        permissionTracker.updatePermissions()

        setContent {
            IdentityCredentialTheme {
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
                        permissionTracker = permissionTracker,
                        sharedPreferences = application.sharedPreferences,
                        qrEngagementViewModel = qrEngagementViewModel,
                        documentModel = application.documentModel
                    )
                }
            }
        }
    }
}
