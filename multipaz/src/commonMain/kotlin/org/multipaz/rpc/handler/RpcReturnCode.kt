package org.multipaz.rpc.handler

/** Constants to determine the result kind in Cbor serialization of the method call response. */
enum class RpcReturnCode {
    RESULT,  // Followed by DataItem representing result (either flow or serializable)
    EXCEPTION,  // Followed by exceptionId, then DataItem representing exception
    NONCE_RETRY  // Nothing to follow, request to retry request with refreshed nonce
}