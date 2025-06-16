package org.multipaz.models.presentment

import org.multipaz.models.digitalcredentials.DigitalCredentials
import org.multipaz.document.Document

/**
 * A [PresentmentMechanism] to use with [PresentmentModel] when using presentations
 * via [DigitalCredentials] and the W3C Digital Credentials API
 *
 * @property appId the id of the application making the request.
 * @property webOrigin the origin of the website if the application is a web browser, or `null` if it's not.
 * @property protocol the W3C Digital Credentials `protocol` field.
 * @property data the W3C Digital Credentials `data` field.
 * @property document the [Document] the request is for or `null` if a document wasn't selected by the user.
 */
abstract class DigitalCredentialsPresentmentMechanism(
    val appId: String,
    val webOrigin: String?,
    val protocol: String,
    val data: String,
    val document: Document?
): PresentmentMechanism {

    /**
     * Sends the response back to the caller.
     *
     * @param response the response.
     */
    abstract fun sendResponse(response: String)
}