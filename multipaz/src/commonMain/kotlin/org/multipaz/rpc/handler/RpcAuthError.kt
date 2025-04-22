package org.multipaz.rpc.handler

/** RPC authorization error codes. See [RpcAuthException]. */
enum class RpcAuthError {
    /** RPC authorization is required, but was not provided in the RPC call. */
    REQUIRED,
    /** RPC authorization is provided in the RPC call, but is not supported. */
    NOT_SUPPORTED,
    /** Given client id is not known to the back-end. */
    UNKNOWN_CLIENT_ID,
    /** RPC authorization was issued for a different endpoint. */
    REQUEST_URL_MISMATCH,
    /** RPC authorization is expired. */
    STALE,
    /** An attempt to use authorization that was used already. */
    REPLAY,
    /** Authorization validation failed. */
    FAILED,
    /**
     * RPC authorization layer mismatch between the server and the client
     */
    CONFIG,
}