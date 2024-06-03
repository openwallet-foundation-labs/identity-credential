package com.android.identity.issuance.remote

import com.android.identity.flow.handler.FlowHandlerRemote
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import kotlinx.io.bytestring.ByteString
import java.net.ConnectException

class WalletHttpClient(val baseUrl: String): FlowHandlerRemote.HttpClient {
    companion object {
        // The request timeout.
        //
        // TODO: make it possible to set the requestTimeout for each RPC call so
        //   this timeout can be specified in WalletServerProvider.kt where we do the
        //   waitUntilNotificationAvailable() call.
        //
        private const val REQUEST_TIMEOUT_SECONDS = 5*60
    }

    val client = HttpClient(CIO) {
        install(HttpTimeout)
    }

    override suspend fun get(url: String): FlowHandlerRemote.HttpResponse {
        try {
            val response = client.get("$baseUrl/$url") {
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT_SECONDS.toLong()*1000
                }
            }
            return FlowHandlerRemote.HttpResponse(
                response.status.value,
                response.status.description,
                ByteString(response.readBytes())
            )
        } catch (e: HttpRequestTimeoutException) {
            throw FlowHandlerRemote.TimeoutException("Timed out", e)
        } catch (e: ConnectException) {
            throw FlowHandlerRemote.ConnectionRefusedException("Connection refused", e)
        } catch (e: Throwable) {
            throw FlowHandlerRemote.ConnectionException("Error", e)
        }
    }

    override suspend fun post(
        url: String,
        data: ByteString
    ): FlowHandlerRemote.HttpResponse {
        try {
            val response = client.post("$baseUrl/$url") {
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT_SECONDS.toLong()*1000
                }
                setBody(data.toByteArray())
            }
            return FlowHandlerRemote.HttpResponse(
                response.status.value,
                response.status.description,
                ByteString(response.readBytes())
            )
        } catch (e: HttpRequestTimeoutException) {
            throw FlowHandlerRemote.TimeoutException("Timed out", e)
        } catch (e: ConnectException) {
            throw FlowHandlerRemote.ConnectionRefusedException("Connection refused", e)
        } catch (e: Throwable) {
            throw FlowHandlerRemote.ConnectionException("Error", e)
        }
    }
}