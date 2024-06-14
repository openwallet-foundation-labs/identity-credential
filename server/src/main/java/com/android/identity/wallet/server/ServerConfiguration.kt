package com.android.identity.wallet.server

import com.android.identity.flow.server.Configuration
import jakarta.servlet.ServletConfig

class ServerConfiguration(private val servletConfig: ServletConfig) : Configuration {

    override fun getValue(key: String): String? {
        val value = servletConfig.getInitParameter(key)
        return value
    }

    companion object {
        private const val TAG = "ServerConfiguration"
    }
}