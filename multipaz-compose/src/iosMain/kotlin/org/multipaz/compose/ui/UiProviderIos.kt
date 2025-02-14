package org.multipaz.compose.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner

@Composable
actual fun UiProvider(lifecycleOwner: LifecycleOwner) {
    UiProviderCommon(lifecycleOwner)
}
