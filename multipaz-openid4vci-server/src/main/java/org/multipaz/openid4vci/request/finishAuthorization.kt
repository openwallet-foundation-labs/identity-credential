package org.multipaz.openid4vci.request

import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.openid4vci.util.IssuanceState
import org.multipaz.openid4vci.util.OpaqueIdType
import org.multipaz.openid4vci.util.codeToId
import org.multipaz.openid4vci.util.getSystemOfRecordUrl
import org.multipaz.server.getBaseUrl
import org.multipaz.openid4vci.util.idToCode
import org.multipaz.provisioning.SecretCodeRequest
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources
import org.multipaz.rpc.handler.InvalidRequestException
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private const val TAG = "finish_authorization"

/**
 * Finish web-based authorization and hand off the session back to the Wallet App (or
 * Wallet Server).
 */
suspend fun finishAuthorization(call: ApplicationCall) {
    val systemOfRecordUrl = BackendEnvironment.getSystemOfRecordUrl()
    val id = if (systemOfRecordUrl == null) {
        // working without system of record
        val issuerState = call.request.queryParameters["issuer_state"]
            ?: throw InvalidRequestException("missing parameter 'issuer_state'")
        codeToId(OpaqueIdType.ISSUER_STATE, issuerState)
    } else {
        // redirected from the system of record
        val state = call.request.queryParameters["state"]
            ?: throw InvalidRequestException("missing parameter 'state'")
        codeToId(OpaqueIdType.RECORDS_STATE, state)
    }
    val state = IssuanceState.getIssuanceState(id)
    if (systemOfRecordUrl != null) {
        // NB: System-of-Record access code is sensitive. In our simple implementation
        // we store it in the database as plain text. For better security it could be
        // encrypted using hash of an active encrypted code produced by idToCode
        // that we hand over to the client. It is ticky to manage, though (as it would have to be
        // re-encrypted any time a new code is issued) and is not warranted in the sample code.
        state.systemOfRecordAuthCode = call.request.queryParameters["code"]
            ?: throw InvalidRequestException("missing parameter 'code'")
    }
    if (state.clientId != null) {
        // regular authorization flow
        val authCode = idToCode(OpaqueIdType.REDIRECT, id, 2.minutes)
        IssuanceState.updateIssuanceState(id, state)
        processRedirect(call, authCode, state)
    } else {
        // pre-authorized flow
        val preauthCode = idToCode(OpaqueIdType.PRE_AUTHORIZED, id, 100.days)
        val txCode: String? = state.txCodeSpec?.generateRandom()?.also {
            state.txCodeHash = ByteString(Crypto.digest(Algorithm.SHA256, it.encodeToByteArray()))
        }
        IssuanceState.updateIssuanceState(id, state)
        preauthorizedOffer(call, preauthCode, txCode, state)
    }
}

private const val NUMERIC_ALPHABET = "0123456789"
private const val ALPHANUMERIC_ALPHABET = "23456789ABCDEFGHJKLMNPRSTUVWXYZ"  // skip 0/O/Q, 1/I

private fun SecretCodeRequest.generateRandom(): String {
    val alphabet = if (isNumeric) NUMERIC_ALPHABET else ALPHANUMERIC_ALPHABET
    val code = StringBuilder()
    val length = this.length ?: 6
    for (i in 0..<length) {
        code.append(alphabet[Random.Default.nextInt(alphabet.length)])
    }
    return code.toString()
}

private suspend fun processRedirect(call: ApplicationCall, authCode: String, state: IssuanceState) {
    val redirectUri = state.redirectUri ?: throw IllegalStateException("No redirect url")
    val parameterizedUri = buildString {
        append(redirectUri)
        append("?code=")
        append(authCode)
        append("&iss=")
        append(BackendEnvironment.getBaseUrl().encodeURLParameter())
        val clientState = state.clientState
        if (!clientState.isNullOrEmpty()) {
            append("&state=")
            append(clientState.encodeURLParameter())
        }
    }
    if (!redirectUri.startsWith("http://") && !redirectUri.startsWith("https://")) {
        call.respondText(
            text = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>Redirecting...</title>
                    </head>
                    <body>
                    <p>Redirecting to your app...</p>
                    <script>
                        // Automatically trigger the URI redirect
                        window.location.href = "$parameterizedUri";
                    </script>
                    <p>If you are not redirected automatically, <a href="$parameterizedUri">click here</a>.</p>
                    </body>
                    </html>
                """.trimIndent(),
            contentType = ContentType.Text.Html
        )
    } else {
        call.respondRedirect(parameterizedUri)
    }
}

const val OFFER_URL_SCHEMA = "openid-credential-offer:"
private suspend fun preauthorizedOffer(
    call: ApplicationCall,
    preauthCode: String,
    txCode: String?,
    state: IssuanceState
) {
    val offer = "$OFFER_URL_SCHEMA//?credential_offer=" + buildJsonObject {
        put("credential_issuer", BackendEnvironment.getBaseUrl())
        putJsonArray("credential_configuration_ids") {
            add(state.configurationId)
        }
        putJsonObject("grants") {
            putJsonObject("urn:ietf:params:oauth:grant-type:pre-authorized_code") {
                put("pre-authorized_code", preauthCode)
                val txCodeSpec = state.txCodeSpec
                if (txCodeSpec != null) {
                    putJsonObject("tx_code") {
                        put("input_mode", if (txCodeSpec.isNumeric) "numeric" else "text")
                        put("length", txCodeSpec.length)
                        put("description", txCodeSpec.description)
                    }
                }
            }
        }
    }.toString().encodeURLParameter()
    val resources = BackendEnvironment.getInterface(Resources::class)!!
    val preauthorizedHtml = resources.getStringResource("pre-authorized.html")!!
    call.respondText(
        text = preauthorizedHtml
            .replace("\$offer", offer)
            .replace("\$encodedOffer", offer.encodeURLParameter())
            .replace("\$txCode", if (txCode == null) {
                "No verification code is required"
            } else {
                "Verification code: <code>$txCode</code>"
            }),
        contentType = ContentType.Text.Html
    )
}