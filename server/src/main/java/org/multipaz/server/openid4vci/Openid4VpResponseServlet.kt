package org.multipaz.server.openid4vci

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.Simple
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.javaPrivateKey
import org.multipaz.crypto.javaPublicKey
import org.multipaz.document.NameSpacedData
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.util.fromBase64Url
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.EncryptedJWT
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.cbor.buildCborArray
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import kotlin.time.Duration.Companion.minutes

/** Servlet class (may trigger warning as unused in the code). */
class Openid4VpResponseServlet: BaseServlet() {
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val stateCode = req.getParameter("state")!!
        val id = codeToId(OpaqueIdType.OPENID4VP_STATE, stateCode)
        val state = blocking { IssuanceState.getIssuanceState(id) }

        val encryptedJWT = EncryptedJWT.parse(req.getParameter("response")!!)

        val encPublic = state.pidReadingKey!!.publicKey.javaPublicKey as ECPublicKey
        val encPrivate = state.pidReadingKey!!.javaPrivateKey as ECPrivateKey

        // TODO: b/393388152: ECKey is deprecated, but might be current library dependency.
        @Suppress("DEPRECATION")
        val encKey = ECKey(
            Curve.P_256,
            encPublic,
            encPrivate,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )

        val decrypter = ECDHDecrypter(encKey)
        encryptedJWT.decrypt(decrypter)

        val responseUri = "$baseUrl/openid4vp-response"

        val sessionTranscript = createSessionTranscriptOpenID4VP(
            clientId = state.clientId,
            responseUri = responseUri,
            authorizationRequestNonce = encryptedJWT.header.agreementPartyVInfo.toString(),
            mdocGeneratedNonce = encryptedJWT.header.agreementPartyUInfo.toString()
        )


        val vpTokenMap = encryptedJWT.jwtClaimsSet.getClaim("vp_token") as Map<*,*>

        // "cred1" is id that we specified in request DCQL.
        val deviceResponse = (vpTokenMap["cred1"] as String).fromBase64Url()

        val parser = DeviceResponseParser(deviceResponse, sessionTranscript)
        val parsedResponse = parser.parse()

        val data = NameSpacedData.Builder()
        for (document in parsedResponse.documents) {
            for (namespaceName in document.issuerNamespaces) {
                for (dataElementName in document.getIssuerEntryNames(namespaceName)) {
                    val value = document.getIssuerEntryData(namespaceName, dataElementName)
                    data.putEntry(namespaceName, dataElementName, value)
                }
            }
        }

        state.credentialData = data.build()
        blocking { IssuanceState.updateIssuanceState(id, state) }

        val presentation = idToCode(OpaqueIdType.OPENID4VP_PRESENTATION, id, 5.minutes)
        resp.writer.write(buildJsonObject {
            put("presentation_during_issuance_session", presentation)
        }.toString())
    }
}

// defined in ISO 18013-7 Annex B
private fun createSessionTranscriptOpenID4VP(
    clientId: String,
    responseUri: String,
    authorizationRequestNonce: String,
    mdocGeneratedNonce: String
): ByteArray {
    val clientIdToHash = Cbor.encode(
        CborArray.builder()
        .add(clientId)
        .add(mdocGeneratedNonce)
        .end()
        .build())
    val clientIdHash = Crypto.digest(Algorithm.SHA256, clientIdToHash)

    val responseUriToHash = Cbor.encode(
        buildCborArray {
            add(responseUri)
            add(mdocGeneratedNonce)
        }
    )
    val responseUriHash = Crypto.digest(Algorithm.SHA256, responseUriToHash)

    val oid4vpHandover = buildCborArray {
        add(clientIdHash)
        add(responseUriHash)
        add(authorizationRequestNonce)
    }

    return Cbor.encode(
        buildCborArray {
            add(Simple.NULL)
            add(Simple.NULL)
            add(oid4vpHandover)
        }
    )
}
