package org.multipaz.rpc.annotation

/**
 * Marks Cbor-serializable object that is used as RPC state and implements an interface exposed
 * through RPC (and marked with [RpcInterface] annotation).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class RpcState(
    /** Path component for the URL used for RPC calls to this class. Must not contain '/'. */
    val endpoint: String = "",
    /**
     * By default [creatable] is `false`, and RPC stub objects for this class are created only
     * when they are returned by RPC methods. Set this to true to allow client to create RPC stub
     * for this RPC state directly.
     */
    val creatable: Boolean = false
)
