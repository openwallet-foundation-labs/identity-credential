package org.multipaz.trustmanagement

/**
 * Thrown if trying to add a [TrustPoint] to a [TrustManager] but there is already another
 * [TrustPoint] with the same Subject Key Identifier.
 *
 * @param message the message
 */
class TrustPointAlreadyExistsException(message: String): Exception(message)