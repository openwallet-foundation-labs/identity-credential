package com.android.identity.server.openid4vci

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.DiagnosticOption
import com.android.identity.cbor.Simple
import com.android.identity.cbor.Tstr
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import com.android.identity.document.NameSpacedData
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.Resources
import com.android.identity.flow.server.Storage
import com.android.identity.mdoc.response.DeviceResponseParser
import com.android.identity.util.fromBase64Url
import com.android.identity.util.fromHex
import com.android.identity.util.toBase64Url
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URL
import kotlin.time.Duration.Companion.minutes

/**
 * Process the request to run web-based authorization. This is typically the second request
 * (after [ParServlet] request) and it is sent from the browser.
 *
 * Specifics of how the web authorization session is run actually do not matter much for the
 * Wallet App and Wallet Server, as long as the session results in redirecting (or resolving)
 * redirect_uri supplied to [ParServlet] on the previous step.
 */
class AuthorizeServlet : BaseServlet() {
    companion object {
        const val RESOURCE_BASE = "openid4vci"
    }

    /**
     * Create a simple web page for the user to authorize the credential issuance.
     */
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val resources = environment.getInterface(Resources::class)!!
        val requestUri = req.getParameter("request_uri")
        val code = requestUri.substring(requestUri.lastIndexOf(":") + 1)
        val id = codeToId(OpaqueIdType.PAR_CODE, code)
        val authorizationCode = idToCode(OpaqueIdType.AUTHORIZATION_STATE, id, 20.minutes)
        val pidReadingCode = idToCode(OpaqueIdType.PID_READING, id, 20.minutes)
        val authorizeHtml = resources.getStringResource("$RESOURCE_BASE/authorize.html")!!
        resp.contentType = "text/html"
        resp.writer.print(
            authorizeHtml
                .replace("\$authorizationCode", authorizationCode)
                .replace("\$pidReadingCode", pidReadingCode))
    }

    /**
     * Handle user's authorization and redirect to [FinishAuthorizationServlet].
     */
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val code = req.getParameter("authorizationCode")
        val pidData = req.getParameter("pidData")
        val extraInfo = req.getParameter("extraInfo")
        val id = codeToId(OpaqueIdType.AUTHORIZATION_STATE, code)
        val storage = environment.getInterface(Storage::class)!!
        val configuration = environment.getInterface(Configuration::class)!!

        val tokenData = Json.parseToJsonElement(pidData).jsonObject["token"]!!
            .jsonPrimitive.content.fromBase64Url()

        val (cipherText, encapsulatedPublicKey) = parseCredentialDocument(tokenData)

        runBlocking {
            val baseUri = URI(configuration.getValue("base_url")!!)
            val origin = baseUri.scheme + "://" + baseUri.authority
            val state = IssuanceState.fromCbor(storage.get("IssuanceState", "", id)!!.toByteArray())
            val encodedKey = (state.pidReadingKey!!.publicKey as EcPublicKeyDoubleCoordinate).asUncompressedPointEncoding
            val sessionTranscript = generateBrowserSessionTranscript(
                Crypto.digest(Algorithm.SHA256, id.toByteArray()),
                origin,
                Crypto.digest(Algorithm.SHA256, encodedKey)
            )
            val deviceResponseRaw = Crypto.hpkeDecrypt(
                Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
                state.pidReadingKey!!,
                cipherText,
                sessionTranscript,
                encapsulatedPublicKey)
            val parser = DeviceResponseParser(deviceResponseRaw, sessionTranscript)
            val deviceResponse = parser.parse()
            val data = NameSpacedData.Builder()
            for (document in deviceResponse.documents) {
                for (namespaceName in document.issuerNamespaces) {
                    for (dataElementName in document.getIssuerEntryNames(namespaceName)) {
                        val value = document.getIssuerEntryData(namespaceName, dataElementName)
                        data.putEntry(namespaceName, dataElementName, value)
                    }
                }
            }

            data.putEntry("com.android.identity.server.openid4vci", "extraInfo", Cbor.encode(Tstr(extraInfo)))
            state.credentialData = data.build()
            storage.update("IssuanceState", "", id, ByteString(state.toCbor()))
        }
        val issuerState = idToCode(OpaqueIdType.ISSUER_STATE, id, 5.minutes)
        resp.sendRedirect("finish_authorization?issuer_state=$issuerState")
    }
}

// Copied from VerifierServlet.kt as is
// TODO: this code should be removed here and in VerifierServlet.kt and the functionality
// factored into a reusable library.

private const val BROWSER_HANDOVER_V1 = "BrowserHandoverv1"
private const val ANDROID_CREDENTIAL_DOCUMENT_VERSION = "ANDROID-HPKE-v1"

private fun parseCredentialDocument(encodedCredentialDocument: ByteArray
): Pair<ByteArray, EcPublicKey> {
    val map = Cbor.decode(encodedCredentialDocument)
    val version = map["version"].asTstr
    if (!version.equals(ANDROID_CREDENTIAL_DOCUMENT_VERSION)) {
        throw IllegalArgumentException("Unexpected version $version")
    }
    val encryptionParameters = map["encryptionParameters"]
    val pkEm = encryptionParameters["pkEm"].asBstr
    val encapsulatedPublicKey =
        EcPublicKeyDoubleCoordinate.fromUncompressedPointEncoding(EcCurve.P256, pkEm)
    val cipherText = map["cipherText"].asBstr
    return Pair(cipherText, encapsulatedPublicKey)
}

//    SessionTranscript = [
//      null, // DeviceEngagementBytes not available
//      null, // EReaderKeyBytes not available
//      AndroidHandover // defined below
//    ]
//
//    From https://github.com/WICG/mobile-document-request-api
//
//    BrowserHandover = [
//      "BrowserHandoverv1",
//      nonce,
//      OriginInfoBytes, // origin of the request as defined in ISO/IEC 18013-7
//      RequesterIdentity, // ? (omitting)
//      pkRHash
//    ]
private fun generateBrowserSessionTranscript(
    nonce: ByteArray,
    origin: String,
    requesterIdHash: ByteArray
): ByteArray {
    // TODO: Instead of hand-rolling this, we should use OriginInfoDomain which
    //   uses `domain` instead of `baseUrl` which is what the latest version of 18013-7
    //   calls for.
    val originInfoBytes = Cbor.encode(
        CborMap.builder()
            .put("cat", 1)
            .put("type", 1)
            .putMap("details")
            .put("baseUrl", origin)
            .end()
            .end()
            .build()
    )
    return Cbor.encode(
        CborArray.builder()
            .add(Simple.NULL) // DeviceEngagementBytes
            .add(Simple.NULL) // EReaderKeyBytes
            .addArray() // BrowserHandover
            .add(BROWSER_HANDOVER_V1)
            .add(nonce)
            .add(originInfoBytes)
            .add(requesterIdHash)
            .end()
            .end()
            .build()
    )
}