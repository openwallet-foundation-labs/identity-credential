package com.android.identity_credential.wallet.ui

import android.content.Intent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import kotlin.math.roundToInt

/**
 * An instance of this class defines a WebView-based renderer for a specific content type.
 *
 * Some types of content are natural to render in browser environment by transforming them to
 * HTML. This class simplifies implementing such a renderer in Compose environment.
 */
abstract class WebViewContentRenderer {
    /**
     * Render the content given by [content] or given as an asset name [asset] (one or the other
     * must be given).
     *
     * Links in the content open in an external browser. Resources (such as images) can only be
     * loaded from the app's `assets` folder.
     */
    @Composable
    fun Render(
        modifier: Modifier = Modifier,
        content: String = "",
        asset: String = "",
        verticalScrolling: Boolean = false,
        color: Color = MaterialTheme.colorScheme.onSurface,
        primaryColor: Color = MaterialTheme.colorScheme.primary,
        linkColor: Color = MaterialTheme.colorScheme.secondary,
        backgroundColor: Color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = modifier) {
            val bootstrapHtml = getBootstrapHtml(
                createStyle(
                    color = color,
                    primaryColor = primaryColor,
                    linkColor = linkColor,
                    backgroundColor = backgroundColor
                )
            )
            val client = ClientImpl(bootstrapHtml)
            AndroidView(factory = { context ->
                val webView = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.javaScriptEnabled = true

                    webViewClient = client
                }

                webView.loadUrl("https://local/")

                webView
            }, update = { webView ->
                webView.isHorizontalScrollBarEnabled = false
                webView.isVerticalScrollBarEnabled = verticalScrolling
                if (verticalScrolling) {
                    webView.setOnTouchListener(null)
                } else {
                    webView.setOnTouchListener(scrollPreventionDragListener)
                }

                val cssStr = escape(
                    createStyle(
                        color = color,
                        primaryColor = primaryColor,
                        linkColor = linkColor,
                        backgroundColor = backgroundColor
                    )
                )

                val command = if (content.isNotEmpty()) {
                    "render(\"${escape(content)}\", \"$cssStr\")"
                } else {
                    "renderResource(\"/${escape(asset)}\", \"$cssStr\")"
                }
                if (client.loaded) {
                    webView.evaluateJavascript(command) {}
                } else {
                    client.deferredCommand = command
                }
            })
        }
    }

    /**
     * Creates CSS stylesheet that applies given colors to the rendered content (HTML).
     */
    protected abstract fun createStyle(
        color: Color, primaryColor: Color, linkColor: Color, backgroundColor: Color
    ): String

    /**
     * Returns HTML content that hosts rendered HTML content in its `body` element and
     * loads necessary scripts. Once everything in this HTML content loads, the following
     * JavaScript function must be defined: `render(content, style)` in the global scope.
     * This function should take the given content and style and display it (most likely
     * by converting it to HTML), replacing the content that was displayed before.
     */
    protected abstract fun getBootstrapHtml(style: String): String

    protected fun asWebColor(color: Color): String {
        val srgb = color.convert(ColorSpaces.Srgb)
        return "#" + (srgb.red * 255).roundToInt().toString(16)
            .padStart(2, '0') + (srgb.green * 255).roundToInt().toString(16)
            .padStart(2, '0') + (srgb.blue * 255).roundToInt().toString(16).padStart(2, '0')
    }

    internal class ClientImpl(val bootstrapHtml: String) : WebViewClient() {
        var loaded = false
        var deferredCommand = ""

        override fun onPageFinished(webView: WebView, url: String) {
            webView.evaluateJavascript(renderResourceScript) {}
            if (deferredCommand.isNotEmpty()) {
                webView.evaluateJavascript(deferredCommand) {}
                deferredCommand = ""
            }
            loaded = true
        }

        override fun shouldOverrideUrlLoading(
            webView: WebView, request: WebResourceRequest
        ): Boolean {
            val intent = Intent(Intent.ACTION_VIEW, request.url)
            webView.context.startActivity(intent)
            return true
        }

        override fun shouldInterceptRequest(
            webView: WebView, request: WebResourceRequest
        ): WebResourceResponse {
            val context = webView.context
            val encodedPath = request.url.encodedPath
            return when (encodedPath) {
                "/" -> WebResourceResponse(
                    "text/html", "utf8", ByteArrayInputStream(bootstrapHtml.encodeToByteArray())
                )

                null -> resourceNotFound()
                else -> try {
                    val stream = context.assets.open("webview$encodedPath")
                    val mediaType = mediaTypeFromName(encodedPath)
                    WebResourceResponse(mediaType, "", stream)
                } catch (err: FileNotFoundException) {
                    resourceNotFound()
                }
            }
        }
    }
}

private fun resourceNotFound() = WebResourceResponse(
    "text/plain", "utf8", 404, "Resource not found", mapOf(), ByteArrayInputStream(byteArrayOf())
)

private fun mediaTypeFromName(path: String): String =
    when (path.substring(path.lastIndexOf('.') + 1).lowercase()) {
        "jpg" -> "image/jpeg"
        "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "svg" -> "image/xml+svg"
        "md" -> "text/plain"
        else -> "application/octet-stream"
    }

private fun escape(text: String) =
    text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")

private val scrollPreventionDragListener =
    View.OnTouchListener { v, event -> event.action == MotionEvent.ACTION_MOVE }

private val renderResourceScript = """
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