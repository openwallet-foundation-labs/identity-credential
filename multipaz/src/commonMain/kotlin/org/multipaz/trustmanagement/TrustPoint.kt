package org.multipaz.trustmanagement

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.cbor.annotation.CborSerializationImplemented
import org.multipaz.cbor.buildCborMap
import org.multipaz.crypto.X509Cert

/**
 * Base class used for the representation of a trusted entity.
 *
 * This is used to represent both trusted issuers and trusted relying parties.
 *
 * @param metadata a [TrustPointMetadata] with metadata about the trust point.
 */
@CborSerializationImplemented(schemaId = "")
sealed class TrustPoint(
    open val metadata: TrustPointMetadata,
) {

    /**
     * An identifier for the [TrustPoint] which is guaranteed to be unique for the [TrustManager] it belongs to.
     */
    abstract val identifier: String

    /**
     * The [TrustManager] the trust point belongs to.
     */
    abstract val trustManager: TrustManager

    companion object
}

// TODO: Also add AppplicationTrustPoint with a platform-specific way to identify apps for
//   example using android.content.pm.SigningInfo on Android