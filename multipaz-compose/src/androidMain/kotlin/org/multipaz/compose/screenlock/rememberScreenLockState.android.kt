package org.multipaz.compose.screenlock

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.identity.context.applicationContext
import com.android.identity.securearea.AndroidKeystoreSecureArea

private class AndroidScreenLockState(): ScreenLockState {

    override val hasScreenLock = AndroidKeystoreSecureArea.Capabilities().secureLockScreenSetup

    override suspend fun launchSettingsPageWithScreenLock() {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ContextCompat.startActivity(applicationContext, intent, null)
    }
}

@Composable
actual fun rememberScreenLockState(): ScreenLockState {
    val recomposeCounter = remember { mutableIntStateOf(0) }
    LaunchedEffect(recomposeCounter.intValue) {}

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            recomposeCounter.intValue += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return AndroidScreenLockState()
}

actual fun getScreenLockState(): ScreenLockState = AndroidScreenLockState()
