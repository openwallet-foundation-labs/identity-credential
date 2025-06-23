package org.multipaz.testapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.multipaz.compose.screenlock.rememberScreenLockState

@Composable
fun ScreenLockScreen(
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val screenLockState = rememberScreenLockState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (screenLockState.hasScreenLock) {
            Text("Device has a Screen Lock")
        } else {
            Text("Device does not have a Screen Lock")
            Button(
                onClick = {
                    coroutineScope.launch {
                        screenLockState.launchSettingsPageWithScreenLock()
                    }
                }
            ) {
                Text("Configure Screen Lock")
            }
        }
    }
}