package org.multipaz.device

import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.rpc.handler.RpcAuthInspectorAssertion
import org.multipaz.rpc.handler.RpcAuthIssuerAssertion

/**
 * A subclass of [Assertion] that authorizes a single RPC call.
 *
 * This is primarily for use by [RpcAuthInspectorAssertion] and [RpcAuthIssuerAssertion].
 */
class AssertionRpcAuth(
    val target: String,
    val method: String,
    val nonce: ByteString,
    val timestamp: Instant,
    val clientId: String,
    val payloadHash: ByteString
): Assertion()