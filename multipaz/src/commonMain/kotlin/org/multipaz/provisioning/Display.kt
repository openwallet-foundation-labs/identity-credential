package org.multipaz.provisioning

import kotlinx.io.bytestring.ByteString

/**
 * Describes something in the user-facing manner.
 *
 * [logo] image bytes in PNG or JPEG format.
 */
class Display(
    val text: String,
    val logo: ByteString?
)