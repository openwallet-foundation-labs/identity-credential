package org.multipaz.zkp.mdoc

import kotlinx.io.bytestring.ByteString

/**
 * Statements for the zk proof.
 *
 * @property key the key of the attribute.
 * @property value the value of the attribute.
 */
data class Attribute(
    val key: String,
    val value: ByteString
)
