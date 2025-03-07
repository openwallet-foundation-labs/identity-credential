package org.multipaz.issuance

import org.multipaz.cbor.annotation.CborSerializable

/**
 * The response from the application when registering a new document.
 *
 * @param developerModeEnabled set to `true` if the app is in developer mode, `false` otherwise.
 */
@CborSerializable
data class RegistrationResponse(
    val developerModeEnabled: Boolean,
) {
    companion object
}
