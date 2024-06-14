package com.android.identity.wallet.server

import com.android.identity.flow.handler.AesGcmCipher
import com.android.identity.flow.handler.FlowDispatcherLocal
import com.android.identity.flow.handler.FlowExceptionMap
import com.android.identity.flow.handler.FlowNotificationsLocalPoll
import com.android.identity.flow.handler.HttpHandler
import com.android.identity.flow.handler.SimpleCipher
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.server.Resources
import com.android.identity.flow.server.Storage
import com.android.identity.flow.transport.HttpTransport
import com.android.identity.issuance.hardcoded.IssuingAuthorityState
import com.android.identity.issuance.hardcoded.WalletServerState
import com.android.identity.util.Logger
import kotlinx.coroutines.runBlocking
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.lang.UnsupportedOperationException
import java.security.Security
import kotlin.random.Random

// To run this servlet for development, use this command:
//
// ./gradlew server:tomcatRun
//
// To get the Wallet App to use it, go into Settings and make it point to the machine
// you are running the server on.
//
class FlowServlet : HttpServlet() {

    companion object {
        private const val TAG = "FlowServlet"

        private val serverEnvironment: FlowEnvironment
        private val httpHandler: HttpHandler

        init {
            Security.addProvider(BouncyCastleProvider())

            serverEnvironment = ServerEnvironment(
                directory = "environment"
            )

            val dispatcherBuilder = FlowDispatcherLocal.Builder()
            WalletServerState.registerAll(dispatcherBuilder)
            val storage = serverEnvironment.getInterface(Storage::class)!!
            val messageEncryptionKey = runBlocking {
                val key = storage.get("RootState", "", "messageEncryptionKey")
                if (key != null) {
                    key.toByteArray()
                } else {
                    val newKey = Random.Default.nextBytes(16)
                    storage.insert(
                        "RootState",
                        "",
                        ByteString(newKey),
                        "messageEncryptionKey")
                    newKey
                }
            }
            val cipher = AesGcmCipher(messageEncryptionKey)
            val localPoll = FlowNotificationsLocalPoll(cipher)
            serverEnvironment.notifications = localPoll
            val localDispatcher = dispatcherBuilder.build(
                serverEnvironment,
                cipher,
                FlowExceptionMap.Builder().build()
            )

            httpHandler = HttpHandler(localDispatcher, localPoll)
        }
    }


    private fun getRemoteHost(req: HttpServletRequest): String {
        var remoteHost = req.remoteHost
        val forwardedFor = req.getHeader("X-Forwarded-For")
        if (forwardedFor != null) {
            remoteHost = forwardedFor
        }
        return remoteHost
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val threadId = Thread.currentThread().id
        val remoteHost = getRemoteHost(req)
        val prefix = "tid=$threadId host=$remoteHost"
        val requestLength = req.contentLength
        Logger.i(TAG, "$prefix: POST ${req.servletPath} ($requestLength bytes)")
        val parts = req.servletPath.split("/")
        if (parts.size < 3) {
            Logger.i(TAG, "$prefix: malformed request")
            throw Exception("Illegal request!")
        }
        val target = parts[parts.lastIndex-1]
        val action = parts.last()
        if (target == "admin") {
            doAdmin(action, req.parameterMap, resp)
            return
        }
        val requestData = req.inputStream.readNBytes(requestLength)
        try {
            val bytes = runBlocking {
                httpHandler.post(
                    url = "$target/$action",
                    data = ByteString(requestData)
                )
            }
            Logger.i(TAG, "$prefix: POST response status 200 (${bytes.size} bytes)")
            resp.contentType = "application/cbor"
            resp.outputStream.write(bytes.toByteArray())
        } catch (e: UnsupportedOperationException) {
            Logger.i(TAG, "$prefix: POST response status 404")
            resp.sendError(404, e.message)
        } catch (e: SimpleCipher.DataTamperedException) {
            Logger.i(TAG, "$prefix: POST response status 405")
            resp.sendError(405, "State tampered")
        } catch (e: IllegalStateException) {
            Logger.i(TAG, "$prefix: POST response status 405")
            resp.sendError(405, "IllegalStateException")
        } catch (e: Throwable) {
            Logger.i(TAG, "$prefix: POST response status 500: ${e::class.simpleName}: ${e.message}")
            // NotificationTimeoutError happens frequently, don't need a stack trace for this...
            if (e !is HttpTransport.TimeoutException) {
                e.printStackTrace()
            }
            resp.sendError(500, e.message)
        }
    }

    private fun doAdmin(action: String, parameters: Map<String, Array<String>>, resp: HttpServletResponse) {
        when (action) {
            "updateDocument" -> {
                val clientId = parameters["clientId"]!![0]
                val issuingAuthorityId = parameters["issuingAuthorityId"]!![0]
                val documentId = parameters["documentId"]!![0]
                val newNumber = parameters["administrativeNumber"]!![0]
                val issuingAuthority = IssuingAuthorityState(clientId, issuingAuthorityId)
                runBlocking {
                    issuingAuthority.administrativeActionUpdateAdministrativeNumber(
                        env = serverEnvironment,
                        documentId = documentId,
                        administrativeNumber = newNumber
                    )
                }
                resp.contentType = "text/plain"
                resp.writer.println("Success")
            }
            else -> {
                resp.sendError(404)
            }
        }
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val threadId = Thread.currentThread().id
        val remoteHost = getRemoteHost(req)
        val prefix = "tid=$threadId host=$remoteHost"
        Logger.i(TAG, "$prefix: GET ${req.servletPath}")
        val parts = req.servletPath.split("/")
        if (req.servletPath.indexOf("..") >= 0) {
            Logger.i(TAG, "$prefix: malformed request")
            throw Exception("Illegal request!")
        }
        val resources = serverEnvironment.getInterface(Resources::class)!!
        val path = parts.last()
        val data = resources.getRawResource("www/$path")
        if (data == null) {
            resp.sendError(404)
        } else {
            val extension = path.substring(path.lastIndexOf(".") + 1)
            resp.contentType = when(extension) {
                "html" -> "text/html"
                "jpeg", "jpg" -> "image/jpeg"
                "png" -> "image/png"
                "js" -> "application/javascript"
                else -> "application/octet-stream"
            }
            resp.outputStream.write(data.toByteArray())
        }
    }
}