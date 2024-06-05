package com.android.identity.wallet.server

import com.android.identity.flow.environment.Storage
import com.android.identity.flow.handler.FlowHandlerLocal
import com.android.identity.issuance.hardcoded.WalletServerState
import com.android.identity.util.Logger
import kotlinx.coroutines.runBlocking
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.io.encoding.ExperimentalEncodingApi
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

        internal val serverEnvironment = ServerEnvironment("environment")

        private val flowHandler = createHandler()

        @OptIn(ExperimentalEncodingApi::class)
        private fun createHandler(): FlowHandlerLocal {

            Security.addProvider(BouncyCastleProvider())

            val flowHandler = FlowHandlerLocal.Builder()
            WalletServerState.registerAll(flowHandler)
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
            return flowHandler.build(messageEncryptionKey, serverEnvironment)
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
        val parts = req.servletPath.split("/")
        if (parts.size < 3) {
            Logger.i(TAG, "$prefix: malformed request")
            throw Exception("Illegal request!")
        }
        val requestLength = req.contentLength
        Logger.i(TAG, "$prefix: POST ${req.servletPath} ($requestLength bytes)")
        val requestData = req.inputStream.readNBytes(requestLength)
        try {
            val bytes = runBlocking {
                flowHandler.handlePost(parts[parts.lastIndex-1],
                    parts.last(), ByteString(requestData))
            }
            Logger.i(TAG, "$prefix: POST response status 200 (${bytes.size} bytes)")
            resp.contentType = "application/cbor"
            resp.outputStream.write(bytes.toByteArray())
        } catch (e: FlowHandlerLocal.NotFoundException) {
            Logger.i(TAG, "$prefix: POST response status 404")
            resp.sendError(404, e.message)
        } catch (e: FlowHandlerLocal.StateTamperedException) {
            Logger.i(TAG, "$prefix: POST response status 405")
            resp.sendError(405, "State tampered")
        } catch (e: Throwable) {
            Logger.i(TAG, "$prefix: POST response status 500: ${e::class.simpleName}: ${e.message}")
            // NotificationTimeoutError happens frequently, don't need a stack trace for this...
            if (e !is WalletServerState.NotificationTimeoutError) {
                e.printStackTrace()
            }
            resp.sendError(500, e.message)
        }
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        println("GET ${req.servletPath}")
        val parts = req.servletPath.split("/")
        if (parts.size < 3) {
            throw Exception("Illegal request!")
        }
        try {
            val bytes = runBlocking {
                flowHandler.handleGet(parts[parts.lastIndex - 1], parts.last())
            }
            resp.contentType = "application/cbor"
            resp.outputStream.write(bytes.toByteArray())
        } catch (ex: FlowHandlerLocal.NotFoundException) {
            resp.sendError(404, ex.message)
        } catch (ex: Exception) {
            ex.printStackTrace()
            resp.sendError(500, ex.message)
        }
    }
}