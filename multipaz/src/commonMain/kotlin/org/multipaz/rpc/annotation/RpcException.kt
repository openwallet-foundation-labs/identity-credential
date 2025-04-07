package org.multipaz.rpc.annotation

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class RpcException(
    /** Unique identifier for this exception (simple class name by default). */
    val exceptionId: String = ""
)
