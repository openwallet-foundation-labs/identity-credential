package org.multipaz.server

import org.multipaz.rpc.backend.Configuration
import jakarta.servlet.ServletConfig

internal class ServletConfiguration(private val servletConfig: ServletConfig) : Configuration {
    override fun getValue(key: String): String? {
        val value = servletConfig.getInitParameter(key)
        return value
    }
}