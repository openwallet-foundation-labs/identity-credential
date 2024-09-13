package com.android.identity.server

import com.android.identity.flow.handler.FlowNotifications
import com.android.identity.flow.server.FlowEnvironment
import jakarta.servlet.ServletConfig
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.reflect.KClass

open class BaseHttpServlet : HttpServlet() {
    private lateinit var flowEnvironment: FlowEnvironment

    companion object {
        private val environmentMap = mutableMapOf<KClass<*>, ServerEnvironment>()

        @Synchronized
        private fun initializeEnvironment(
            clazz: KClass<*>,
            servletConfig: ServletConfig,
            notificationsFactory: (env: FlowEnvironment) -> FlowNotifications?
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

        fun environmentFor(clazz: KClass<*>): FlowEnvironment? = environmentMap[clazz]

        init {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    val environment get() = flowEnvironment

    override fun init() {
        super.init()
        flowEnvironment = initializeEnvironment(
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
     * Called once per BaseHttpServlet subclass (FlowEnvironment is shared across all instances).
     */
    open fun initializeEnvironment(env: FlowEnvironment): FlowNotifications? {
        return null
    }
}