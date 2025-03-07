package org.multipaz.server.openid4vci

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.flow.handler.InvalidRequestException
import org.multipaz.flow.server.getTable
import org.multipaz.util.toBase64Url
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Generates request for Digital Credential for the browser-based authorization workflow.
 */
class CredentialRequestServlet : BaseServlet() {
    companion object {
        const val EU_PID_DOCTYPE = EUPersonalID.EUPID_DOCTYPE
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestLength = req.contentLength
        val requestData = req.inputStream.readNBytes(requestLength)
        val params = Json.parseToJsonElement(String(requestData)) as JsonObject
        val code = params["code"]?.jsonPrimitive?.content
            ?: throw InvalidRequestException("missing parameter 'code'")
        val id = codeToId(OpaqueIdType.PID_READING, code)
        runBlocking {
            val storage = environment.getTable(IssuanceState.tableSpec)
            val state = IssuanceState.fromCbor(storage.get(id)!!.toByteArray())
            val nonce = Crypto.digest(Algorithm.SHA256, id.toByteArray()).toBase64Url()
            state.pidReadingKey = Crypto.createEcPrivateKey(EcCurve.P256)
            storage.update(id, ByteString(state.toCbor()))
            val pidPublicKey = (state.pidReadingKey!!.publicKey as EcPublicKeyDoubleCoordinate)
                .asUncompressedPointEncoding.toBase64Url()
            val fullPid = EUPersonalID.getDocumentType().cannedRequests.first { it.id == "full" }
            // Request for "preview" protocol
            val previewRequest = buildJsonObject {
                put("selector", buildJsonObject {
                    put("doctype", JsonPrimitive(EU_PID_DOCTYPE))
                    put("format", buildJsonArray {
                        add(JsonPrimitive("mdoc"))
                    })
                    put("fields", buildJsonArray {
                        for (namespace in fullPid.mdocRequest!!.namespacesToRequest) {
                            for ((dataElement, _) in namespace.dataElementsToRequest) {
                                add(buildJsonObject {
                                    put("intentToRetain", JsonPrimitive(false))
                                    put("namespace", JsonPrimitive(namespace.namespace))
                                    put("name", JsonPrimitive(dataElement.attribute.identifier))
                                })
                            }
                        }
                    })
                })
                put("nonce", JsonPrimitive(nonce))
                put("readerPublicKey", JsonPrimitive(pidPublicKey))
            }
            val credentialRequest = buildJsonObject {
                put("providers", buildJsonArray {
                    add(buildJsonObject {
                        put("protocol", JsonPrimitive("preview"))
                        put("request", previewRequest)
                    })
                })
            }
            resp.contentType = "application/json"
            resp.writer.write(credentialRequest.toString())
        }
    }
}