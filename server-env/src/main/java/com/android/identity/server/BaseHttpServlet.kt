package org.multipaz.server

import org.multipaz.rpc.handler.RpcNotifications
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.util.Logger
import jakarta.servlet.ServletConfig
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.reflect.KClass

open class BaseHttpServlet : HttpServlet() {
    private lateinit var backendEnvironment: BackendEnvironment

    companion object {
        private val environmentMap = mutableMapOf<KClass<*>, ServerEnvironment>()

        const val TAG = "BaseHttpServlet"
        
        @Synchronized
        private fun initializeEnvironment(
            clazz: KClass<*>,
            servletConfig: ServletConfig,
            notificationsFactory: (env: BackendEnvironment) -> RpcNotifications?
        ): ServerEnvironment {
            val existingEnvironment = environmentMap[clazz]
            if (existingEnvironment != null) {
                return existingEnvironment
            }
            val environment = ServerEnvironment(
                ServletConfiguration(servletConfig),
                notificationsFactory
            )
            environmentMap[clazz] = environment
            return environment
        }

        fun environmentFor(clazz: KClass<*>): BackendEnvironment? = environmentMap[clazz]

        init {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    val environment get() = backendEnvironment

    override fun init() {
        super.init()
        backendEnvironment = initializeEnvironment(
            this::class, servletConfig, this::initializeEnvironment
        )
    }

    fun getRemoteHost(req: HttpServletRequest): String {
        var remoteHost = req.remoteHost
        val forwardedFor = req.getHeader("X-Forwarded-For")
        if (forwardedFor != null) {
            remoteHost = forwardedFor
        }
        return remoteHost
    }

    /**
     * Initializes FlowEnvironment, potentially registering exceptions and flow method handlers
     * as well as potentially creating FlowNotifications.
     *
     * Called once per BaseHttpServlet subclass (BackendEnvironment is shared across all instances).
     */
    open fun initializeEnvironment(env: BackendEnvironment): RpcNotifications? {
        return null
    }

    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            resp.contentType = outputFormat
            super.service(req, resp)
        } catch (err: InvalidRequestException) {
            Logger.e(TAG, "Error in ${req.requestURL}", err)
            when (outputFormat) {
                "application/json" -> {
                    val json = buildJsonObject {
                        put("error", JsonPrimitive("invalid_request"))
                        put("error_description", JsonPrimitive(err.message ?: "not provided"))
                    }
                    resp.status = 400
                    resp.writer.write(json.toString())
                }
                "text/html" -> {
                    resp.status = 400
                    val text = (err.message ?: "")
                        .replace("&", "&amp")
                        .replace("<", "&lt")
                        .replace(">", "&gt")
                    resp.writer.write(
                        """
                            <!DOCTYPE html>                          
                            <html>
                            <body>
                            <h1>Invalid Request</h1>
                            <p>$text</p>
                            </body>
                            </html>
                        """.trimIndent())
                }
                else -> {
                    Logger.e(TAG, "Unsupported error format: $outputFormat")
                    resp.status = 400
                }
            }
        }
    }

    protected open val outputFormat: String
        get() = "application/json"
}