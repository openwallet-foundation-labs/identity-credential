package org.multipaz.compose.webview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.io.bytestring.ByteString

/**
 * Displays markdown-formatted snippet.
 *
 * The height of this composable is determined by the amount of the content that it displays.
 *
 * Use `appinfo` attribute to inject application data, e.g. `_placeholder_{appinfo=version}`.
 */
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    linkColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    assets: Map<String, ByteString> = mapOf(),
    appInfo: Map<String, String> = mapOf(),
) {
    WebViewRender(
        renderingContext = MarkdownRenderingContext,
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

private object MarkdownRenderingContext : WebViewRenderingContext() {
    override fun createStyle(
        color: Color,
        primaryColor: Color,
        linkColor: Color,
        backgroundColor: Color
    ): String =
        """
        body { color: ${asWebColor(color)}; background-color: ${asWebColor(backgroundColor)} }
        h1, h2, h3, h4, h5, h6 { color: ${asWebColor(primaryColor)} }
        a { color: ${asWebColor(linkColor)} }    
        """.trimIndent()

    // Use markdown with attribute and anchor plugins. Only id, class, width, height, and
    // style attributes are allowed (mostly for security reasons, others can be added
    // if they are safe).
    override fun getBootstrapHtml(style: String, appInfo: Map<String, String>): String =
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0 maximum-scale=1.0 user-scalable=no"/>
        <style id='style'>$style</style>
        <script src="res/js/markdown_it_min.js"></script>
        <script src="res/js/markdown_it_attrs.js"></script>
        <script src="res/js/markdown_it_anchor_min.js"></script>
        <script>
        ${appInfoMapDef(appInfo)}
        function reportHeight() {
            Callback.updateHeight(document.documentElement.offsetHeight);
        }
        function render(markdownText, css) {
          var md = markdownit().use(markdownItAttrs,
             {allowedAttributes: ['id', 'class', 'width', 'height', 'style', 'appinfo']}).use(markdownItAnchor);
          document.getElementById('content').innerHTML = md.render(markdownText);
          for (let q of document.querySelectorAll("[appinfo]")) {
            q.textContent = appInfoMap[q.getAttribute("appinfo")]
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
