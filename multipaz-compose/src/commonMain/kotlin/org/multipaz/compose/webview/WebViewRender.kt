package org.multipaz.compose.webview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.io.bytestring.ByteString

@Composable
internal expect fun WebViewRender(
    renderingContext: WebViewRenderingContext,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: String? = null,
    asset: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    linkColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    assets: Map<String, ByteString> = mapOf(),
    appInfo: Map<String, String> = mapOf()
)