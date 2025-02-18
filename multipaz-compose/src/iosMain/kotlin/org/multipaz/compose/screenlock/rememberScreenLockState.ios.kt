package org.multipaz.compose.screenlock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.cinterop.ExperimentalForeignApi

import platform.Foundation.NSURL
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

private class IosScreenLockState(): ScreenLockState {

    @OptIn(ExperimentalForeignApi::class)
    override val hasScreenLock = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, null)

    override suspend fun launchSettingsPageWithScreenLock() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        UIApplication.sharedApplication().openURL(url!!)
    }
}

@Composable
actual fun rememberScreenLockState(): ScreenLockState {
    val recomposeCounter = remember { mutableIntStateOf(0) }
    LaunchedEffect(recomposeCounter.value) {}

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            recomposeCounter.value = recomposeCounter.value + 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return IosScreenLockState()
}

actual fun getScreenLockState(): ScreenLockState = IosScreenLockState()
