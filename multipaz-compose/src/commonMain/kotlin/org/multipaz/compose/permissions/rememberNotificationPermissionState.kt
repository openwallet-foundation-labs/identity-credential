package org.multipaz.compose.permissions

import androidx.compose.runtime.Composable

/**
 * Creates a [PermissionState] for Notification permissions.
 *
 * If the permission changes this will trigger a recomposition with an updated
 * value. This is useful for the case where the user goes into the device or
 * app settings screen to manually grant or deny the permission.
 */
@Composable
expect fun rememberNotificationPermissionState(): PermissionState
