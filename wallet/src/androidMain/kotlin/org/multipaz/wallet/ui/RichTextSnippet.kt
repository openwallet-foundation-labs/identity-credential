package org.multipaz_credential.wallet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.io.bytestring.ByteString

/**
 * Displays rich text either as markdown (see [MarkdownText]) or html snippet
 * (see [HtmlSnippetText]) automatically detecting the formatting.
 */
@Composable
fun RichTextSnippet(
    content: String,
    modifier: Modifier = Modifier,
    verticalScrolling: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    linkColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    assets: Map<String, ByteString>? = mapOf()  // no assets by default
) {
    if (isHtml(content)) {
        HtmlSnippetText(
            content = content,
            modifier = modifier,
            verticalScrolling = verticalScrolling,
            color = color,
            primaryColor = primaryColor,
            linkColor = linkColor,
            backgroundColor = backgroundColor,
            assets = assets)
    } else {
        MarkdownText(
            content = content,
            modifier = modifier,
            verticalScrolling = verticalScrolling,
            color = color,
            primaryColor = primaryColor,
            linkColor = linkColor,
            backgroundColor = backgroundColor,
            assets = assets)
    }
}

private fun isHtml(text: String): Boolean {
    var i = 0;
    while (i < text.length && text[i].isWhitespace()) {
        i++
    }
    return i < text.length && text[i] == '<';
}