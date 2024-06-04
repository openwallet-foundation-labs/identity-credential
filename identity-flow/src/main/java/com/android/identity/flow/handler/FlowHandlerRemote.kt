package com.android.identity.flow.handler

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.MajorType
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder

/**
 * [FlowHandler] implementation that dispatches flow getters and methods
 * to a remote HTTP server.
 */
class FlowHandlerRemote(private val client: HttpClient) : FlowHandler {

    override suspend fun get(flow: String, method: String): DataItem {
        val response = client.get("$flow/$method")
        return decodeResponse(response)
    }

    override suspend fun post(flow: String, method: String, args: List<DataItem>): List<DataItem> {
        val builder = CborArray.builder()
        args.forEach { builder.add(it) }
        val response = client.post("$flow/$method", ByteString(Cbor.encode(builder.end().build())))
        return decodeResponse(response).asArray
    }

    /**
     * Simple HTTP client interface. Note that urls passed to this interface
     * are relative and should be resolved relative to some base URL.
     */
    interface HttpClient {
        suspend fun get(url: String): HttpResponse
        suspend fun post(url: String, data: ByteString): HttpResponse
    }

    /**
     * Should be thrown by [HttpClient] methods when connecting to the
     * server failed.
     */
    class ConnectionException(message: String) : RuntimeException(message)

    class RemoteException(message: String) : RuntimeException(message)

    data class HttpResponse(val status: Int, val statusText: String, val body: ByteString)

    private fun decodeResponse(response: HttpResponse): DataItem {
        when (response.status) {
            200 -> return Cbor.decode(response.body.toByteArray())
            404 -> throw UnsupportedOperationException(response.statusText)
            405 -> throw IllegalStateException(response.statusText)
            else -> throw RemoteException("${response.status} ${response.statusText}")
        }
    }
}