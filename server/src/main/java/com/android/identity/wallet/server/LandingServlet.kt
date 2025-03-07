package org.multipaz.wallet.server

import org.multipaz.flow.handler.FlowNotifications
import org.multipaz.flow.server.Configuration
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.flow.server.Resources
import org.multipaz.flow.server.getTable
import org.multipaz.issuance.LandingUrlNotification
import org.multipaz.issuance.wallet.ApplicationSupportState
import org.multipaz.issuance.wallet.LandingRecord
import org.multipaz.issuance.wallet.emit
import org.multipaz.issuance.wallet.fromCbor
import org.multipaz.issuance.wallet.toCbor
import org.multipaz.server.BaseHttpServlet
import org.multipaz.util.Logger
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString

/**
 * Servlet to handle landing for a redirect url that happens as a final step in the OpenID-based
 * workflows, when user is authorizing in the browser.
 *
 * A specific landing URL is created using [ApplicationSupport.createLandingUrl] method.
 */
class LandingServlet: BaseHttpServlet() {
    companion object {
        private const val TAG = "LandingServlet"
    }

    override fun initializeEnvironment(env: FlowEnvironment): FlowNotifications? {
        // Use notifications from FlowServlet (it must be initialized before LandingServlet)
        return environmentFor(FlowServlet::class)!!.getInterface(FlowNotifications::class)
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val threadId = Thread.currentThread().id
        val remoteHost = getRemoteHost(req)
        val prefix = "tid=$threadId host=$remoteHost"
        val rawPath = req.pathInfo
        Logger.i(TAG, "$prefix: GET $rawPath")
        if (rawPath == null) {
            resp.sendError(404)
            return
        }
        val id = rawPath.substring(1)
        runBlocking {
            val storage = environment.getTable(ApplicationSupportState.landingTableSpec)
            val recordData = storage.get(id)
            if (recordData == null) {
                resp.sendError(404)
            } else {
                val record = LandingRecord.fromCbor(recordData.toByteArray())
                record.resolved = req.queryString ?: ""
                storage.update(id, ByteString(record.toCbor()))
                val configuration = environment.getInterface(Configuration::class)!!
                val baseUrl = configuration.getValue("base_url")
                val landingUrl = "$baseUrl/${ApplicationSupportState.URL_PREFIX}$id"
                ApplicationSupportState(record.clientId).emit(environment,
                    LandingUrlNotification(landingUrl))
                resp.contentType = "text/html"
                val resources = environment.getInterface(Resources::class)!!
                resp.outputStream.write(
                    resources.getRawResource("landing/index.html")!!.toByteArray())
            }
        }
    }
}