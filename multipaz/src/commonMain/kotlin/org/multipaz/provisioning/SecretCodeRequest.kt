package org.multipaz.provisioning

import org.multipaz.cbor.annotation.CborSerializable

/**
 * Describes secret text that user needs to enter.
 *
 * Designed to be aligned with tx_code credential offer parameter in OpenID4VCI specification.
 */
@CborSerializable
data class SecretCodeRequest(
    /**
     * Description for the requested secret text.
     *
     * This is plain text (i.e. not HTML or Markdown)
     */
    val description: String,
    /**
     * True if required text must only contain ASCII decimal digit characters.
     */
    val isNumeric: Boolean,
    /**
     * Number of required characters that must be entered.
     */
    val length: Int?
)