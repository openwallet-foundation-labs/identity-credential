package com.android.identity.server

import com.android.identity.flow.server.Configuration
import jakarta.servlet.ServletConfig

internal class ServletConfiguration(private val servletConfig: ServletConfig) : Configuration {
    override fun getValue(key: String): String? {
        val value = servletConfig.getInitParameter(key)
        return value
    }
}