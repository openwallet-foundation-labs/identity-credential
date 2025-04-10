package org.multipaz.rpc.annotation

/**
 * Marks interface that defines flow getters and methods.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class RpcInterface(
    /** Fully qualified name for generated RPC stub class for this interface. */
    val stubName: String = ""
)
