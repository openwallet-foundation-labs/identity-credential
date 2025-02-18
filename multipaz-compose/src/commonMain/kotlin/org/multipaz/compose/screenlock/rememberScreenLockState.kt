package org.multipaz.compose.screenlock

import androidx.compose.runtime.Composable

/**
 * Creates a [ScreenLockState] that is remembered across compositions.
 *
 * If the screen lock state changes, this will trigger a recomposition with an updated
 * value. This is useful for the case where the user goes into device settings and
 * adds/removes the screen lock.
 */
@Composable
expect fun rememberScreenLockState(): ScreenLockState
