package org.multipaz.models.openid.dcql

/**
 * Thrown when a credential query cannot be satisfied.
 *
 * @param message error message with detail.
 */
class DcqlCredentialQueryException(message: String): Exception(message)
