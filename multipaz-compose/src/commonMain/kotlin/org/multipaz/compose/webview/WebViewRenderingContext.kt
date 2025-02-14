package org.multipaz.compose.webview

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.roundToInt

/**
 * A class that helps to set up a browser-based renderer for a particular content type.
 */
internal abstract class WebViewRenderingContext {
    /**
     * Creates CSS stylesheet that applies given colors to the rendered content (HTML).
     */
    abstract fun createStyle(
        color: Color, primaryColor: Color, linkColor: Color, backgroundColor: Color
    ): String

    /**
     * Returns HTML content that hosts rendered HTML content in its `body` element and
     * loads necessary scripts. Once everything in this HTML content loads, the following
     * JavaScript function must be defined: `render(content, style)` in the global scope.
     * This function should take the given content and style and display it (most likely
     * by converting it to HTML), replacing the content that was displayed before.
     */
    abstract fun getBootstrapHtml(style: String, appInfo: Map<String, String>): String

    companion object {
        internal fun asWebColor(color: Color): String {
            val srgb = color.convert(ColorSpaces.Srgb)
            return "#" +
                    (srgb.red * 255).roundToInt().toString(16).padStart(2, '0') +
                    (srgb.green * 255).roundToInt().toString(16).padStart(2, '0') +
                    (srgb.blue * 255).roundToInt().toString(16).padStart(2, '0')
        }

        internal fun mediaTypeFromName(path: String): String =
            when (path.substring(path.lastIndexOf('.') + 1).lowercase()) {
                "jpg" -> "image/jpeg"
                "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "svg" -> "image/svg+xml"
                "md" -> "text/plain"
                else -> "application/octet-stream"
            }

        internal fun escape(text: String) =
            text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")

        internal val renderResourceScript = """
            var req = null;
            function renderResource(path, style) {
                if (req) {
                    req.abort();
                }
                req = new XMLHttpRequest();
                req.onload = function() {
                    if (req.status == 200) {
                        var text = req.responseText;
                        req = null;
                        render(text, style);
                    }
                };
                req.open('GET', path);
                req.send();
            }
            """.trimIndent()

        internal fun appInfoMapDef(appInfo: Map<String, String>) = StringBuilder().apply {
                append("const appInfoMap = {")
                for (entry in appInfo) {
                    append("\"")
                    append(escape(entry.key))
                    append("\": \"")
                    append(escape(entry.value))
                    append("\",")
                }
                append("};")
            }.toString()
    }
}