package org.multipaz.openid4vci.util

import io.ktor.http.Url
import io.ktor.http.protocolWithAuthority
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration

suspend fun BackendEnvironment.Companion.getBaseUrl(): String =
    getInterface(Configuration::class)!!.getValue("base_url")!!

suspend fun BackendEnvironment.Companion.getDomain(): String =
    Url(getBaseUrl()).protocolWithAuthority