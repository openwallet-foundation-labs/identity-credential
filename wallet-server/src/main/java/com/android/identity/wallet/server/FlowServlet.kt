package com.android.identity.wallet.server

import com.android.identity.flow.handler.FlowHandlerLocal
import com.android.identity.issuance.hardcoded.IssuingAuthorityState
import com.android.identity.issuance.hardcoded.ProofingState
import com.android.identity.issuance.hardcoded.RegistrationState
import com.android.identity.issuance.hardcoded.RequestCredentialsState
import com.android.identity.issuance.hardcoded.register
import kotlinx.coroutines.runBlocking
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

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
        private val flowHandler = createHandler()

        private fun createHandler(): FlowHandlerLocal {

            Security.addProvider(BouncyCastleProvider())

            val flowHandler = FlowHandlerLocal.Builder()
            IssuingAuthorityState.registerAll(flowHandler)
            val secret = byteArrayOf(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31
            )
            return flowHandler.build(secret)
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