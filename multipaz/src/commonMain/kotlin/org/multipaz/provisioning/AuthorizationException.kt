package org.multipaz.provisioning

/**
 * Thrown when authorization failed permanently.
 */
class AuthorizationException(
    val code: String,
    val description: String?
): Exception(if (description != null) "$code: $description" else code)