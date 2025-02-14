package org.multipaz.compose.webview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.io.bytestring.ByteString

/**
 * Displays rich text either as markdown (see [MarkdownText]) or html snippet
 * (see [HtmlSnippetText]) automatically detecting the formatting.
 *
 * The height of this composable is determined by the amount of the content that it displays.
 */
@Composable
fun RichText(
    content: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    linkColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    assets: Map<String, ByteString> = mapOf(),
    appInfo: Map<String, String> = mapOf()
) {
    if (isHtml(content)) {
        HtmlSnippetText(
            content = content,
            modifier = modifier,
            color = color,
            primaryColor = primaryColor,
            linkColor = linkColor,
            backgroundColor = backgroundColor,
            assets = assets,
            appInfo = appInfo
        )
    } else {
        MarkdownText(
            content = content,
            modifier = modifier,
            color = color,
            primaryColor = primaryColor,
            linkColor = linkColor,
            backgroundColor = backgroundColor,
            assets = assets,
            appInfo = appInfo
        )
    }
}

private fun isHtml(text: String): Boolean {
    var i = 0
    while (i < text.length && text[i].isWhitespace()) {
        i++
    }
    return i < text.length && text[i] == '<';
}