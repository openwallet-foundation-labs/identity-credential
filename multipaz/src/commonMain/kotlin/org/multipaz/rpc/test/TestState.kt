package org.multipaz.rpc.test

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthInspector
import org.multipaz.rpc.backend.RpcAuthBackendDelegate

/**
 * Test class, added purely to be able to test against.
 */
@RpcState(
    endpoint = "test",
    creatable = true
)
@CborSerializable
class TestState: TestInterface, RpcAuthInspector by RpcAuthBackendDelegate {
    override suspend fun test(param: String): String {
        return "$param@${RpcAuthContext.getClientId()}.${RpcAuthContext.getSessionId()}"
    }

    companion object
}
