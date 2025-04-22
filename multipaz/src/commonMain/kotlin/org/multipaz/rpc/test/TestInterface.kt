package org.multipaz.rpc.test

import org.multipaz.rpc.annotation.RpcInterface
import org.multipaz.rpc.annotation.RpcMethod

/**
 * Test interface, added purely to be able to test against.
 */
@RpcInterface
interface TestInterface {
    @RpcMethod
    suspend fun test(param: String): String
}
