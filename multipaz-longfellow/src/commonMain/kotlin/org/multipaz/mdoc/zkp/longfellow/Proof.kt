package org.multipaz.mdoc.zkp.longfellow

import kotlinx.io.bytestring.ByteString

/**
 * ZK Proof and timestamp used to generate the proof.
 *
 * @property timestamp the ISO formatted timestamp used to generate the proof.
 * @property proof the bytes of the proof.
 * */
data class Proof (
    val timestamp: String,
    val proof: ByteString
)
