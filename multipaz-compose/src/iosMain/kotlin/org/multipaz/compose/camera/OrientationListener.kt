package org.multipaz.compose.camera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
internal class OrientationListener(
    val orientationChanged: () -> Unit
) : NSObject() {

    private val notificationName = platform.UIKit.UIDeviceOrientationDidChangeNotification

    @OptIn(BetaInteropApi::class)
    @Suppress("UNUSED_PARAMETER")
    @ObjCAction
    fun orientationDidChange(arg: NSNotification) {
        orientationChanged()
    }

    fun register() {
        NSNotificationCenter.defaultCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString(
                OrientationListener::orientationDidChange.name + ":"
            ),
            name = notificationName,
            `object` = null
        )
    }

    fun unregister() {
        NSNotificationCenter.defaultCenter.removeObserver(
            observer = this,
            name = notificationName,
            `object` = null
        )
    }
}