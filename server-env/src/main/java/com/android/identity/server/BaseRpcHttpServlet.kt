package org.multipaz.server

import org.multipaz.rpc.handler.AesGcmCipher
import org.multipaz.rpc.handler.RpcDispatcherLocal
import org.multipaz.rpc.handler.RpcExceptionMap
import org.multipaz.rpc.handler.RpcNotifications
import org.multipaz.rpc.handler.RpcNotificationsLocalPoll
import org.multipaz.rpc.handler.HttpHandler
import org.multipaz.rpc.handler.SimpleCipher
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.transport.HttpTransport
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.Logger
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import org.multipaz.storage.Storage
import java.lang.UnsupportedOperationException
import kotlin.random.Random

/**
 * Servlet to handle flow RPC requests.
 *
 * Subclass this servlet registering all flow method handlers and exceptions by overriding
 * buildExceptionMap and buildDispatcher. Typically a single subclass should handle all
 * flow RPC requests on a given server.
 */
abstract class BaseRpcHttpServlet : BaseHttpServlet() {
    companion object {
        const val TAG = "BaseRpcHttpServlet"

        private val flowRootStateTableSpec = StorageTableSpec(
            name = "RpcRootState",
            supportPartitions = false,
            supportExpiration = false
        )
    }

    private lateinit var httpHandler: HttpHandler

    override fun initializeEnvironment(env: BackendEnvironment): RpcNotifications? {
        val exceptionMapBuilder = RpcExceptionMap.Builder()
        buildExceptionMap(exceptionMapBuilder)
        val dispatcherBuilder = RpcDispatcherLocal.Builder()
        buildDispatcher(dispatcherBuilder)
        val messageEncryptionKey = runBlocking {
            val storage = env.getInterface(Storage::class)!!.getTable(flowRootStateTableSpec)
            val key = storage.get("messageEncryptionKey")
            if (key != null) {
                key.toByteArray()
            } else {
                val newKey = Random.nextBytes(16)
                storage.insert(
                    key = "messageEncryptionKey",
                    data = ByteString(newKey),
                )
                newKey
            }
        }
        val cipher = AesGcmCipher(messageEncryptionKey)
        val localPoll = RpcNotificationsLocalPoll(cipher)
        val localDispatcher = dispatcherBuilder.build(
            env,
            cipher,
            exceptionMapBuilder.build()
        )

        httpHandler = HttpHandler(localDispatcher, localPoll)
        return localPoll
    }

    abstract fun buildExceptionMap(exceptionMapBuilder: RpcExceptionMap.Builder)
    abstract fun buildDispatcher(dispatcherBuilder: RpcDispatcherLocal.Builder)

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val path = req.pathInfo.substring(1)
        val threadId = Thread.currentThread().id
        val remoteHost = getRemoteHost(req)
        val prefix = "tid=$threadId host=$remoteHost"
        val requestLength = req.contentLength
        Logger.i(TAG, "$prefix: POST $path ($requestLength bytes)")
        val parts = path.split("/")
        if (parts.size != 2) {
            Logger.i(TAG, "$prefix: malformed request")
            throw Exception("Illegal request!")
        }
        val target = parts[0]
        val action = parts[1]
        val requestData = req.inputStream.readNBytes(requestLength)
        try {
            val bytes = runBlocking {
                httpHandler.post(
                    url = "$target/$action",
                    data = ByteString(requestData)
                )
            }
            Logger.i(TAG, "$prefix: POST response status 200 (${bytes.size} bytes)")
            resp.outputStream.write(bytes.toByteArray())
        } catch (e: UnsupportedOperationException) {
            Logger.e(TAG, "$prefix: POST response status 404", e)
            resp.sendError(404, e.message)
        } catch (e: SimpleCipher.DataTamperedException) {
            Logger.e(TAG, "$prefix: POST response status 405", e)
            resp.sendError(405, "State tampered")
        } catch (e: IllegalStateException) {
            Logger.e(TAG, "$prefix: POST response status 405", e)
            resp.sendError(405, "IllegalStateException")
        } catch (e: Throwable) {
            // NotificationTimeoutError happens frequently, don't need a stack trace for this...
            if (e is HttpTransport.TimeoutException) {
                Logger.e(TAG, "$prefix: POST response status 500 (TimeoutException)")
            } else {
                Logger.e(TAG, "$prefix: POST response status 500", e)
            }
            resp.sendError(500, e.message)
        }
    }

    override val outputFormat: String
        get() = "application/cbor"
}