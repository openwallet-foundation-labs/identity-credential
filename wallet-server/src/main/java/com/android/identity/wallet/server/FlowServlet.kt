package com.android.identity.wallet.server

import com.android.identity.flow.environment.Storage
import com.android.identity.flow.handler.FlowHandlerLocal
import com.android.identity.issuance.hardcoded.WalletServerState
import kotlinx.coroutines.runBlocking
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

// To run this servlet for development, use this command:
//
// ./gradlew wallet-server:tomcatRun
//
// This should start the server on http://localhost:8080/wallet-server. To get the Android
// app to connect it, use this command to tunnel 8080 port local connections on the Android
// device to your development machine:
//
// adb reverse tcp:8080 tcp:8080
//
class FlowServlet : HttpServlet() {

    companion object {
        private val serverEnvironment = ServerEnvironment("environment")

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

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        println("POST ${req.servletPath}")
        val parts = req.servletPath.split("/")
        if (parts.size < 3) {
            throw Exception("Illegal request!")
        }
        val requestLength = req.contentLength
        println("Content-length: $requestLength")
        val requestData = req.inputStream.readNBytes(requestLength)
        try {
            val bytes = runBlocking {
                flowHandler.handlePost(parts[parts.lastIndex-1],
                    parts.last(), ByteString(requestData))
            }
            resp.contentType = "application/cbor"
            resp.outputStream.write(bytes.toByteArray())
        } catch (ex: FlowHandlerLocal.NotFoundException) {
            resp.sendError(404, ex.message)
        } catch (ex: FlowHandlerLocal.StateTamperedException) {
            resp.sendError(405, "State tampered")
        } catch (ex: Exception) {
            ex.printStackTrace()
            resp.sendError(500, ex.message)
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