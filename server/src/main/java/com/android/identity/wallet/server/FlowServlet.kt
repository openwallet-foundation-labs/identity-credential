package com.android.identity.wallet.server

import com.android.identity.flow.handler.FlowDispatcherLocal
import com.android.identity.flow.handler.FlowExceptionMap
import com.android.identity.issuance.wallet.WalletServerState
import com.android.identity.server.BaseFlowHttpServlet

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