/*
 * Copyright 2024 The Android Open Source Project
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

package com.example.simple_verifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.largeTopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.example.simple_verifier.ui.theme.IdentityCredentialTheme


class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IdentityCredentialTheme {
                VerifierApp(modifier = Modifier.fillMaxSize(), supportFragmentManager = supportFragmentManager) { enableReaderPermissions() }
            }
        }
    }

    private fun enableReaderPermissions() {
        if (!isAllPermissionsGranted()) {
            shouldRequestPermission()
        }
    }

    private val appPermissions:List<String> get() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (android.os.Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        return permissions
    }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("permissions", "permissionsLauncher ${it.key} = ${it.value}")

                // Open settings if user denied any required permission
                if (!it.value && !shouldShowRequestPermissionRationale(it.key)) {
                    openSettings()
                    return@registerForActivityResult
                }
            }
        }

    private fun openSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", this.applicationContext.packageName, null)
        startActivity(intent)
    }

    private fun shouldRequestPermission() {
        val permissionsNeeded = appPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(
                this.applicationContext,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionsLauncher.launch(
                permissionsNeeded.toTypedArray()
            )
        }
    }

    private fun isAllPermissionsGranted(): Boolean {
        // If any permission is not granted return false
        return appPermissions.none { permission ->
            ContextCompat.checkSelfPermission(
                this.applicationContext,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
private fun VerifierApp(
    modifier: Modifier = Modifier,
    supportFragmentManager: FragmentManager,
    enableReaderPermissions: () -> Unit
) {
    Surface(modifier) {
        ToolboxScreen(modifier = modifier, supportFragmentManager = supportFragmentManager, enableReaderPermissions = enableReaderPermissions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolboxScreen(
    modifier: Modifier = Modifier,
    supportFragmentManager: FragmentManager,
    enableReaderPermissions: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text(
                        style = MaterialTheme.typography.headlineMedium,
                        text = "mDL Age Check Example",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->

        Toolbox(
            onOver21BtnClicked = {
                enableReaderPermissions()
                val mdocReaderPrompt = MdocReaderPrompt(MdocReaderSettings.Builder()
                    .setAgeVerificationType(AgeVerificationType.Over21)
                    .build())
                mdocReaderPrompt.show(supportFragmentManager, null)
            },
            onOver18BtnClicked = {
                enableReaderPermissions()
                val mdocReaderPrompt = MdocReaderPrompt(MdocReaderSettings.Builder()
                    .setAgeVerificationType(AgeVerificationType.Over18)
                    .build())
                mdocReaderPrompt.show(supportFragmentManager, null)
            },
            modifier = Modifier
                .padding(innerPadding)
                .padding(vertical = 20.dp)
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun Toolbox(
    onOver21BtnClicked: () -> Unit,
    onOver18BtnClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.secondary)
    ) {
        Text(
            modifier = Modifier
                .padding(vertical = 20.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = "Options",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSecondary
        )
        FilledTonalButton(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth(),
            onClick = onOver21BtnClicked
        ) {
            Text("Over 21?")
        }
        FilledTonalButton(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth(),
            onClick = onOver18BtnClicked
        ) {
            Text("Over 18?")
        }
    }
}


@Preview
@Composable
private fun VerifierAppPreview() {
    IdentityCredentialTheme {
        VerifierApp(Modifier.fillMaxSize(), MainActivity().supportFragmentManager) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun ToolboxPreview() {
    IdentityCredentialTheme {
        Toolbox(onOver21BtnClicked = {}, onOver18BtnClicked = {})
    }
}