package org.multipaz.testapp.provisioning.backend

import org.multipaz.rpc.transport.HttpTransport
import org.multipaz.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString

class WalletHttpTransport(private val baseUrl: String): HttpTransport {
    companion object {
        // The request timeout.
        //
        // TODO: make it possible to set the requestTimeout for each RPC call so
        //   this timeout can be specified in WalletServerProvider.kt where we do the
        //   waitUntilNotificationAvailable() call.
        //
        private const val REQUEST_TIMEOUT_SECONDS = 5*60
    }

    private val client = HttpClient(CIO) {
        install(HttpTimeout)
    }

    override suspend fun post(
        url: String,
        data: ByteString
    ): ByteString {
        val response = try {
            client.post("$baseUrl/rpc/$url") {
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT_SECONDS.toLong()*1000
                }
                setBody(data.toByteArray())
            }
        } catch (e: HttpRequestTimeoutException) {
            throw HttpTransport.TimeoutException("Timed out", e)
        } catch (e: CancellationException) {
            // important to propagate this one!
            Logger.i("WalletHttpTransport", "Task cancelled", e)
            throw e
        } catch (e: Throwable) {
            throw HttpTransport.ConnectionException("Error", e)
        }
        HttpTransport.processStatus(url, response.status.value, response.status.description)
        return ByteString(response.readBytes())
    }
}