package org.multipaz.models.presentment

import kotlinx.serialization.json.JsonObject
import org.multipaz.models.digitalcredentials.DigitalCredentials
import org.multipaz.document.Document

/**
 * A [PresentmentMechanism] to use with [PresentmentModel] when using presentations
 * via [DigitalCredentials] and the
 * [W3C Digital Credentials API](https://w3c-fedid.github.io/digital-credentials/).
 *
 * @property appId the id of the application making the request.
 * @property webOrigin the origin of the website if the application is a web browser, or `null` if it's not.
 * @property protocol the `protocol` field in the `DigitalCredentialGetRequest` dictionary in to the W3C DC API.
 * @property data the `data` field in the `DigitalCredentialGetRequest` dictionary in the W3C DC API.
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
     * @param protocol the `protocol` field in the `DigitalCredential` interface in the W3C DC API.
     * @param data the `data` field in the `DigitalCredential` interface in the W3C DC API.
     */
    abstract fun sendResponse(protocol: String, data: JsonObject)
}