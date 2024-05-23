package com.android.identity.issuance.remote

import com.android.identity.flow.handler.FlowHandlerRemote
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import kotlinx.io.bytestring.ByteString

class WalletHttpClient(val baseUrl: String): FlowHandlerRemote.HttpClient {
    val client = HttpClient(CIO)

    override suspend fun get(url: String): FlowHandlerRemote.HttpResponse {
        try {
            val response = client.get("$baseUrl/$url")
            return FlowHandlerRemote.HttpResponse(
                response.status.value,
                response.status.description,
                ByteString(response.readBytes())
            )
        } catch (ex: Exception) {
            throw FlowHandlerRemote.ConnectionException(ex.message ?: "")
        }
    }

    override suspend fun post(
        url: String,
        data: ByteString
    ): FlowHandlerRemote.HttpResponse {
        try {
            val response = client.post("$baseUrl/$url") {
                setBody(data.toByteArray())
            }
            return FlowHandlerRemote.HttpResponse(
                response.status.value,
                response.status.description,
                ByteString(response.readBytes())
            )
        } catch (ex: Exception) {
            throw FlowHandlerRemote.ConnectionException(ex.message ?: "")
        }
    }
}