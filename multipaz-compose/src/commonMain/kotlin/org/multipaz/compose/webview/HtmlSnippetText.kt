package org.multipaz.compose.webview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.io.bytestring.ByteString

/**
 * Displays XHTML-formatted snippet.
 *
 * It is expected that text is HTML content, without html and body element.
 * Styling can be applied using style attributes. Only certain elements and
 * attributes are allowed (in particular, no scripting), so this should be
 * generally safe to use with arbitrary content.
 *
 * The height of this composable is determined by the amount of the content that it displays.
 *
 * Use `appinfo` attribute to inject application data, e.g. `<span appinfo='version'/>`.
 */
@Composable
fun HtmlSnippetText(
    content: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    linkColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    assets: Map<String, ByteString> = mapOf(),  // no assets by default
    appInfo: Map<String, String> = mapOf(),
) {
    WebViewRender(
        renderingContext = HtmlRenderingContext,
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

private object HtmlRenderingContext : WebViewRenderingContext() {
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

    // Parse as XHTML to avoid HTML parsing quirks. Only certain elements and attributes
    // are allowed to avoid security issues.
    // TODO: once sanitizer APIs are there in the WebView, consider switching to that?
    override fun getBootstrapHtml(style: String, appInfo: Map<String, String>) =
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0 maximum-scale=1.0 user-scalable=no"/>
        <style id='style'> 
        </style>
        <script>
        ${appInfoMapDef(appInfo)}
        function reportHeight() {
            window.Callback?.updateHeight(document.documentElement.offsetHeight);
        }
        function render(markupText, css) {
          let doc = (new DOMParser()).parseFromString(
              "<div>" + markupText + "</div>", "text/xml");
          for (let q of doc.querySelectorAll("[appinfo]")) {
            q.textContent = appInfoMap[q.getAttribute("appinfo")]
          }
          let destination = document.getElementById('content');
          destination.innerHTML = '';
          injectContent(doc.firstChild, destination);
          document.getElementById('style').textContent = css;
          reportHeight();
          for (let image of document.images) {
            image.addEventListener("load", reportHeight);
          }
        }
        let globalAttributes = {
            style: true,
            lang: true,
            title: true
        };
        let none = {};
        let allowedElements = {
            h1: none,
            h2: none,
            h3: none,
            h4: none,
            h5: none,
            h6: none,
            div: none,
            p: none,
            q: none,
            span: none,
            i: none,
            b: none,
            u: none,
            em: none,
            sup: none,
            sub: none,
            img: {src: true, alt: true},
            a: {href: true},
            hr: none,
            pre: none,
            br: none,
            code: none,
            blockquote: none,
            ol: none,
            ul: none,
            li: none,
            dl: none,
            dd: none,
            dt: none,
            table: none,
            tbody: none,
            tr: none,
            td: {colspan: true, rowspan: true},
            th: {colspan: true, rowspan: true, scope: true},
            thead: none,
            tfoot: none,
            colgroup: none,
            col: none
        };
        function injectContent(source, destination) {
            for (let child = source.firstChild; child; child = child.nextSibling) {
                switch (child.nodeType) {
                    case 1: {
                        let tag = child.localName;
                        let attrs = allowedElements[tag];
                        if (!attrs) {
                            if (tag == 'parsererror') {
                                let rep = destination.ownerDocument.createElement("pre");
                                rep.setAttribute("style", "color: red");
                                rep.textContent = child.textContent;
                                destination.appendChild(rep);
                            } else {
                                console.log(tag + ': element is not allowed');
                            }
                            continue;
                        }
                        let e = destination.ownerDocument.createElement(tag);
                        for (let attr of child.attributes) {
                            if (globalAttributes[attr.name] || attrs[attr.name]) {
                                e.setAttribute(attr.name, attr.value);
                            } else {
                                console.log(attr.name + ': attribute is not allowed');
                            }
                        }
                        injectContent(child, e);
                        destination.appendChild(e);
                        break;
                    }
                    case 3:
                    case 4:
                        destination.appendChild(
                            destination.ownerDocument.createTextNode(child.textContent));
                        break;
                }
            }
        }
        </script>
        </head>
        <body id='content'></body>
        <html>
        """.trimIndent()
}
