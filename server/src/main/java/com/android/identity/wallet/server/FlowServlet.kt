package com.android.identity.wallet.server

import com.android.identity.cbor.Cbor
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
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
import com.android.identity.issuance.hardcoded.IssuerDocument
import com.android.identity.issuance.hardcoded.IssuingAuthorityState
import com.android.identity.issuance.wallet.WalletServerState
import com.android.identity.util.Logger
import io.ktor.utils.io.core.toByteArray
import jakarta.servlet.ServletConfig
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServlet
import kotlinx.coroutines.runBlocking
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Base64
import java.lang.UnsupportedOperationException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.Security
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit

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

        private const val PASSWORD_SALT = "1CxCucFhcIzcbMnSrIgB"
        private val AUTH_VALIDITY_DURATION = 7.days
        private val LIST_HEAD = "<!DOCTYPE html><html><head></head><body><ul>"
        private val LIST_TAIL = "</ul></body></html>"
        private val TABLE_HEAD = "<!DOCTYPE html><html><head><link rel='stylesheet' href='table.css'/></head><body><table>"
        private val TABLE_TAIL = "</table></body></html>"

        private lateinit var serverEnvironment: FlowEnvironment
        private lateinit var httpHandler: HttpHandler
        private lateinit var stateCipher: SimpleCipher
        private lateinit var adminPasswordHash: ByteString

        @Synchronized
        private fun initialize(servletConfig: ServletConfig) {
            if (this::serverEnvironment.isInitialized) {
                return
            }

            serverEnvironment = ServerEnvironment(servletConfig)

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
            adminPasswordHash = runBlocking {
                // Don't write initial password hash in the storage
                storage.get("RootState", "", "passwordHash")
                    ?: saltedHash(servletConfig.getInitParameter("initialAdminPassword"))
            }
            val cipher = AesGcmCipher(messageEncryptionKey)
            stateCipher = cipher
            val localPoll = FlowNotificationsLocalPoll(cipher)
            (serverEnvironment as ServerEnvironment).notifications = localPoll
            val localDispatcher = dispatcherBuilder.build(
                serverEnvironment,
                cipher,
                FlowExceptionMap.Builder().build()
            )

            httpHandler = HttpHandler(localDispatcher, localPoll)
        }

        @Synchronized
        private fun updateAdminPasswordHash(hash: ByteString) {
            adminPasswordHash = hash
        }

        private fun saltedHash(password: String): ByteString {
            return ByteString(Crypto.digest(Algorithm.SHA256, "$PASSWORD_SALT$password".toByteArray()))
        }
    }

    @Override
    override fun init() {
        super.init()

        Security.addProvider(BouncyCastleProvider())

        initialize(servletConfig)
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
        val path = req.servletPath.substring(1)
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
        if (target == "admin") {
            doAdmin(req, resp)
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


    private fun getAuthCookie(req: HttpServletRequest): Cookie? {
        if (req.cookies != null) {
            for (cookie in req.cookies) {
                if (cookie.name == "Auth") {
                    return cookie
                }
            }
        }
        return null
    }

    private fun adminAuthCheck(req: HttpServletRequest, resp: HttpServletResponse): Boolean {
        val cookie = getAuthCookie(req)
        if (cookie != null) {
            try {
                val parsedCookie =
                    AdminAuthCookie.fromCbor(stateCipher.decrypt(Base64.decode(cookie.value)))
                if (parsedCookie.expiration >= Clock.System.now()
                        && parsedCookie.passwordHash == adminPasswordHash) {
                    return true
                }
                Logger.e(TAG, "Expired or stale Auth cookie: ${parsedCookie.expiration}")
            } catch (err: Exception) {
                Logger.e(TAG, "Error parsing Auth cookie", err)
            }
        }
        resp.sendRedirect("${req.contextPath}/login.html")
        return false
    }

    private fun doAdmin(req: HttpServletRequest, resp: HttpServletResponse) {
        val action = req.servletPath.split("/").last()
        if (action != "login" && !adminAuthCheck(req, resp)) {
            resp.sendRedirect("${req.contextPath}/login.html")
            return
        }
        val parameters = req.parameterMap
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
            "login" -> {
                val password = parameters["password"]!![0]
                val cookie = getAuthCookie(req)
                if (cookie != null) {
                    cookie.maxAge = 0  // remove existing cookie if present
                }
                if (saltedHash(password) == adminPasswordHash) {
                    val expiration = Clock.System.now() + AUTH_VALIDITY_DURATION
                    val auth = Base64.toBase64String(stateCipher.encrypt(
                        AdminAuthCookie(expiration, adminPasswordHash).toCbor()))
                    val newCookie = Cookie("Auth", auth)
                    newCookie.path = "${req.contextPath}/"
                    newCookie.maxAge = AUTH_VALIDITY_DURATION.toInt(DurationUnit.SECONDS)
                    resp.addCookie(newCookie)
                    Logger.i(TAG, "Successful login")
                    resp.sendRedirect("${req.contextPath}/index.html")
                } else {
                    Logger.e(TAG, "Incorrect password")
                    resp.sendRedirect("${req.contextPath}/login.html")
                }
            }
            "password" -> {
                val oldPassword = parameters["oldPassword"]!![0]
                if (saltedHash(oldPassword) != adminPasswordHash) {
                    resp.contentType = "text/plain"
                    resp.writer.println("Old password is not correct")
                    return
                }
                val password = parameters["newPassword"]!![0]
                if (password != parameters["newPassword1"]!![0]) {
                    resp.contentType = "text/plain"
                    resp.writer.println("Passwords do not match")
                    return
                }
                val hash = saltedHash(password)
                adminPasswordHash = hash
                val storage = serverEnvironment.getInterface(Storage::class)!!
                runBlocking {
                    if (storage.get("RootState", "", "passwordHash") == null) {
                        storage.insert("RootState", "", hash,"passwordHash")
                    } else {
                        storage.update("RootState", "", "passwordHash", hash)
                    }
                }
                updateAdminPasswordHash(hash)
                resp.sendRedirect("${req.contextPath}/login.html")
            }
            else -> {
                resp.sendError(404)
            }
        }
    }

    private fun htmlEscape(text: String?): String {
        return (text ?: "<null>")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val rawPath = req.servletPath.substring(1)
        val path = rawPath.ifEmpty { "index.html" }
        val threadId = Thread.currentThread().id
        val remoteHost = getRemoteHost(req)
        val prefix = "tid=$threadId host=$remoteHost"
        Logger.i(TAG, "$prefix: GET $path")
        if (path != "login.html" && !adminAuthCheck(req, resp)) {
            return
        }
        when (path) {
            "documents.html" -> {
                val clientIds = req.parameterMap["clientId"]
                val clientId = if (clientIds == null) "" else clientIds[0]!!
                resp.contentType = "text/html; charset=utf-8"
                val writer = resp.outputStream.writer(Charset.forName("utf-8"))
                val storage = serverEnvironment.getInterface(Storage::class)!!
                writer.write(TABLE_HEAD)
                writer.write("<tr><th>Id</th><th>Display Name</th></tr>")
                runBlocking {
                    val documentIds = storage.enumerate("IssuerDocument", clientId)
                    for (documentId in documentIds) {
                        writer.write("<tr>")
                        writer.write("<td class='code'>${htmlEscape(documentId)}</td>")
                        val documentData = storage.get("IssuerDocument", clientId, documentId)!!
                        val document = IssuerDocument.fromDataItem(Cbor.decode(documentData.toByteArray()))
                        writer.write("<td>${htmlEscape(document.documentConfiguration?.displayName)}</td>")
                        writer.write("<tr>")
                    }
                }
                writer.write(TABLE_TAIL)
                writer.flush()
            }
            "clients.html" -> {
                resp.contentType = "text/html; charset=utf-8"
                val writer = resp.outputStream.writer(Charset.forName("utf-8"))
                val storage = serverEnvironment.getInterface(Storage::class)!!
                writer.write(LIST_HEAD)
                runBlocking {
                    val clients = storage.enumerate("ClientKeys", "")
                    for (client in clients) {
                        val escaped = htmlEscape(client)
                        val urlenc = URLEncoder.encode(client, "utf-8")
                        writer.write("<li><a href='documents.html?clientId=$urlenc'>$escaped</a></li>")
                    }
                }
                writer.write(LIST_TAIL)
                writer.flush()
            }
            else -> {
                val resources = serverEnvironment.getInterface(Resources::class)!!
                val data = resources.getRawResource("www/$path")
                if (data == null) {
                    resp.sendError(404)
                } else {
                    val extension = path.substring(path.lastIndexOf(".") + 1)
                    resp.contentType = when (extension) {
                        "html" -> "text/html; charset=utf-8"
                        "jpeg", "jpg" -> "image/jpeg"
                        "png" -> "image/png"
                        "js" -> "application/javascript"
                        "css" -> "text/css"
                        else -> "application/octet-stream"
                    }
                    resp.outputStream.write(data.toByteArray())
                }
            }
        }
    }
}