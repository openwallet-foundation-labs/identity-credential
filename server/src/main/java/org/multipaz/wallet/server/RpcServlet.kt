package org.multipaz.wallet.server

import org.multipaz.rpc.handler.RpcDispatcherLocal
import org.multipaz.rpc.handler.RpcExceptionMap
import org.multipaz.provisioning.wallet.ProvisioningBackendState
import org.multipaz.server.BaseRpcHttpServlet

// To run this servlet for development, use this command:
//
// ./gradlew server:tomcatRun
//
// To get the Wallet App to use it, go into Settings and make it point to the machine
// you are running the server on.
//
class RpcServlet : BaseRpcHttpServlet() {
    override fun buildExceptionMap(exceptionMapBuilder: RpcExceptionMap.Builder) {
        ProvisioningBackendState.registerExceptions(exceptionMapBuilder)
    }

    override fun buildDispatcher(dispatcherBuilder: RpcDispatcherLocal.Builder) {
        ProvisioningBackendState.registerAll(dispatcherBuilder)
    }
}