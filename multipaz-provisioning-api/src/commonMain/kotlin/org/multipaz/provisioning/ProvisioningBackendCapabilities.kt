package org.multipaz.provisioning

import org.multipaz.cbor.annotation.CborSerializable
import kotlin.time.Instant

/**
 * Information about the capabilities of the wallet server, for the wallet application.
 *
 * @property generatedAt The point in time this data was generated.
 */
@CborSerializable
data class ProvisioningBackendCapabilities(
    val generatedAt: Instant
) {
    companion object
}
