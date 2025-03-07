package org.multipaz.wallet.server

import org.multipaz.flow.handler.FlowDispatcherLocal
import org.multipaz.flow.handler.FlowExceptionMap
import org.multipaz.issuance.wallet.WalletServerState
import org.multipaz.server.BaseFlowHttpServlet

// To run this servlet for development, use this command:
//
// ./gradlew server:tomcatRun
//
// To get the Wallet App to use it, go into Settings and make it point to the machine
// you are running the server on.
//
class FlowServlet : BaseFlowHttpServlet() {
    override fun buildExceptionMap(exceptionMapBuilder: FlowExceptionMap.Builder) {
        WalletServerState.registerExceptions(exceptionMapBuilder)
    }

    override fun buildDispatcher(dispatcherBuilder: FlowDispatcherLocal.Builder) {
        WalletServerState.registerAll(dispatcherBuilder)
    }
}