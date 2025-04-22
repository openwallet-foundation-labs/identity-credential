package org.multipaz.rpc.handler

import kotlinx.io.bytestring.ByteString

/**
 * RPC authorization failure with nonce for retry.
 */
class RpcAuthNonceException(
    val nonce: ByteString
) : RuntimeException()