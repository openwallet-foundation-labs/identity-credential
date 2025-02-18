package org.multipaz.compose.screenlock

/**
 * An interface for querying and modifying the screen lock state.
 */
interface ScreenLockState {

    /**
     * Whether the device has a screen lock.
     */
    val hasScreenLock: Boolean

    /**
     * Launches the relevant page in the platform's Settings app for the user to configure the screen lock.
     */
    suspend fun launchSettingsPageWithScreenLock()
}

/**
 * Gets the current screen lock state.
 *
 * @return a [ScreenLockState] with the current state.
 */
expect fun getScreenLockState(): ScreenLockState