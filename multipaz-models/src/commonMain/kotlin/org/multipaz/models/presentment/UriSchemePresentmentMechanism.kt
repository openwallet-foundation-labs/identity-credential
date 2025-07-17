package org.multipaz.models.presentment

import io.ktor.client.engine.HttpClientEngineFactory
import org.multipaz.document.Document
import org.multipaz.models.digitalcredentials.DigitalCredentials

/**
 * A [PresentmentMechanism] to use with [PresentmentModel] when using presentations implemented
 * via URI schemes such as `mdoc://` or `openid4vp://`.
 *
 * @property uri the full URI received from the browser, including the scheme, e.g. `openid4vp://`
 * @property origin the origin of the site which invoked the URI or `null` if unknown.
 * @property httpClientEngineFactory a [HttpClientEngineFactory] that can be used to make network requests.
 */
abstract class UriSchemePresentmentMechanism(
    val uri: String,
    val origin: String?,
    val httpClientEngineFactory: HttpClientEngineFactory<*>
): PresentmentMechanism {

    /**
     * Function to open an URI in the user's default browser.
     *
     * @param uri the URI to open, e.g. https://verifier.multipaz.org/verifier_redirect.html?sessionId=-QP5ykLn4Aymnmxj
     */
    abstract fun openUriInBrowser(uri: String)
}