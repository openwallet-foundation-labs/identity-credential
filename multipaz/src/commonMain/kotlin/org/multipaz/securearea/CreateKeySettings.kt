package org.multipaz.securearea

import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.Algorithm
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for key creation settings.
 *
 * This can be used for any conforming [SecureArea] implementations although such implementations
 * will typically supply their own implementations with additional settings to e.g. configure user
 * authentication, passphrase protections, challenges for attestations, and other things.
 *
 * @param algorithm A fully specified [Algorithm], e.g. [Algorithm.ESP256].
 * @param nonce a nonce, to prove freshness of the [KeyAttestation] produced by the [SecureArea]
 *   implementation. Note that not all implementations provide key attestations in which case the
 *   nonce is ignored.
 * @param userAuthenticationRequired true if user authentication is required, false otherwise. Some
 *   [SecureArea] implementations may take options to control more precisely what kind of user
 *   authentication is required, for example timeouts and whether knowledge factors or inherence
 *   factors can be used.
 * @param userAuthenticationTimeout a duration for which the key is authorized after the user
 *   successfully authenticates. If 0, authentication is required on every use. Not all [SecureArea]
 *   implementations supports this.
 * @param validFrom point in time before which the key is not valid, if available.
 * @param validUntil point in time after which the key is not valid, if available.
 */
open class CreateKeySettings(
    val algorithm: Algorithm = Algorithm.ESP256,
    val nonce: ByteString = ByteString(),
    val userAuthenticationRequired: Boolean = false,
    val userAuthenticationTimeout: Duration = 0.seconds,
    val validFrom: Instant? = null,
    val validUntil: Instant? = null
) {
    init {
        require(algorithm.fullySpecified) {
            "Given algorithm $algorithm to use for key creation is not fully specified"
        }
    }
}