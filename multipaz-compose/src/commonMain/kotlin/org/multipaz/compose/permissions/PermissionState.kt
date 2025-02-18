package org.multipaz.compose.permissions

/**
 * An interface for querying and requesting a permission.
 */
interface PermissionState {

    /**
     * Whether the permission has been granted.
     */
    val isGranted: Boolean

    /**
     * Requests the permission.
     */
    suspend fun launchPermissionRequest()
}
