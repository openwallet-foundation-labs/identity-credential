package org.multipaz_credential.wallet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.io.bytestring.ByteString

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

    // Use markdown with attribute and anchor plugins. Only id, class, width, height, and
    // style attributes are allowed (mostly for security reasons, others can be added
    // if they are safe).
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
             {allowedAttributes: ['id', 'class', 'width', 'height', 'style', 'appinfo']}).use(markdownItAnchor);
          document.getElementById('content').innerHTML = md.render(markdownText);
          for (let q of document.querySelectorAll("[appinfo]")) {
            q.textContent = window.Callback?.appinfo(q.getAttribute("appinfo"))
          }
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

/**
 * Displays markdown-formatted content.
 *
 * Use `appinfo` attribute to inject application data, e.g. `_dummytext_[appinfo=version]`.
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    verticalScrolling: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    linkColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    assets: Map<String, ByteString>? = mapOf()  // no assets by default
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
    assets: Map<String, ByteString>? = null  // Android assets by default
) {
    markdownRenderer.Render(asset = asset, modifier = modifier,
        verticalScrolling = verticalScrolling, color = color,
        primaryColor = primaryColor, linkColor = linkColor,
        backgroundColor = backgroundColor, assets = assets)
}