package org.multipaz.mdoc.zkp

import org.multipaz.cbor.DataItem

/**
 * ZkSystemSpec represents the specifications of a ZK System.
 *
 * @property system the name of the ZK system.
 * @property params parameters for the ZK system.
 */
data class ZkSystemSpec (
    val id: String,
    val system: String,
    val params: Map<String, Any>
)
