package com.android.identity.flow.transport

import kotlinx.io.bytestring.ByteString

/**
 * Simple HTTP client interface used by flow-based RPC. Note that urls passed to this interface
 * are relative and should be resolved relative to some base URL.
 *
 * Note: HTTP codes that indicate errors are represented by exceptions.
 */
interface HttpTransport {
    suspend fun post(url: String, data: ByteString): ByteString

    /**
     * Base class for all exceptions thrown by [HttpTransport]
     */
    open class HttpClientException: Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }

    /**
     * Base class for exceptions related to connection problems in [HttpTransport].
     */
    open class ConnectionException: HttpClientException {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }

    /**
     * Should be thrown by [HttpTransport] methods when the method fails because the connection
     * to the remote server was refused.
     */
    class ConnectionRefusedException: ConnectionException {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }

    /**
     * Should be thrown by [HttpTransport] methods when a connection was established to the remote
     * server but the connection was terminated before the server responded.
     */
    class TimeoutException: ConnectionException {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }

    /**
     * Should be thrown by [HttpTransport] if the the remote server encountered an error during
     * the processing of the request.
     */
    class RemoteException: HttpClientException {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }

    companion object {
        fun processStatus(status: Int, statusText: String) {
            when (status) {
                200 -> {}  // noop
                404 -> throw UnsupportedOperationException(statusText)
                405 -> throw IllegalStateException(statusText)
                else -> throw RemoteException("$status $statusText")
            }
        }
    }
}