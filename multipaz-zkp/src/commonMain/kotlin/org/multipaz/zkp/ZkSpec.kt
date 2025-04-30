package org.multipaz.zkp

/**
 * Kotlin representation of the native ZkSpecStruct
 *
 * @property system the ZK system name.
 * @property circuitHash the hash of the circuit.
 * @property numAttributes the number of attributes that the circuit supports.
 * @property version the version of the ZK spec.
 */
data class ZkSpec(
    val system: String,
    val circuitHash: String,
    val numAttributes: Long,
    val version: Long
)