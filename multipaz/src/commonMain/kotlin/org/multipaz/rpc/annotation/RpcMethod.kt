package org.multipaz.rpc.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class RpcMethod(
    /** Path component for the URL used for this method. Must not contain '/'. */
    val endpoint: String = ""
)
