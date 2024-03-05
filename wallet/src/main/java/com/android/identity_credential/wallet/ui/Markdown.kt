package com.android.identity_credential.wallet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val markdownRenderer = object : WebViewContentRenderer() {
    override fun createStyle(
        color: Color,
        primaryColor: Color,
        linkColor: Color,
        backgroundColor: Color
    ) =
        """
        body { color: ${asWebColor(color)}; background-color: ${asWebColor(backgroundColor)} }
        h1, h2, h3, h4, h5, h6 { color: ${asWebColor(primaryColor)} }
        a { color: ${asWebColor(linkColor)} }    
        """.trimIndent()

    // Use markdown with attribute and anchor plugins. Only id and class attributes
    // are allowed (mostly for security reasons, others can be added if they are safe).
    override fun getBootstrapHtml(style: String) =
        """
        <!DOCTYPE html>
        <html>
        <head>
        <style id='style'>$style</style>
        <script src="js/markdown_it_min.js"></script>
        <script src="js/markdown_it_attrs.js"></script>
        <script src="js/markdown_it_anchor_min.js"></script>
        <script>
        function reportHeight() {
            Callback.updateHeight(document.documentElement.offsetHeight);
        }
        function render(markdownText, css) {
          var md = markdownit().use(markdownItAttrs,
             {allowedAttributes: ['id', 'class']}).use(markdownItAnchor);
          document.getElementById('content').innerHTML = md.render(markdownText);
          document.getElementById('style').textContent = css;
          reportHeight();
          for (let image of document.images) {
            image.addEventListener("load", reportHeight);
          }
        }
        </script>
        </head>
        <body id='content'></body>
        <html>
        """.trimIndent()
}

/** Displays markdown-formatted content. */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    verticalScrolling: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    linkColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    assets: Map<String, ByteArray>? = mapOf()  // no assets by default
) {
    markdownRenderer.Render(content = content, modifier = modifier,
        verticalScrolling = verticalScrolling, color = color,
        primaryColor = primaryColor, linkColor = linkColor,
        backgroundColor = backgroundColor, assets = assets)
}

/** Displays markdown-formatted asset with the given name. */
@Composable
fun MarkdownAsset(
    asset: String,
    modifier: Modifier = Modifier,
    verticalScrolling: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    linkColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    assets: Map<String, ByteArray>? = null  // Android assets by default
) {
    markdownRenderer.Render(asset = asset, modifier = modifier,
        verticalScrolling = verticalScrolling, color = color,
        primaryColor = primaryColor, linkColor = linkColor,
        backgroundColor = backgroundColor, assets = assets)
}