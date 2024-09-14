package com.android.identity.server.openid4vci

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.CoseNumberLabel
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.document.NameSpacedData
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.flow.server.Resources
import com.android.identity.flow.server.Storage
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.sdjwt.Issuer
import com.android.identity.sdjwt.SdJwtVcGenerator
import com.android.identity.sdjwt.util.JsonWebKey
import com.android.identity.util.fromBase64Url
import com.android.identity.util.toBase64Url
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

/**
 * Issues a credential based on access token issued by [TokenServlet].
 *
 * TODO: assess token is meant to a single use (verify that). Add replay protection.
 */
class CredentialServlet : BaseServlet() {
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        println("credential")
        val authorization = req.getHeader("Authorization")
        if (authorization == null || authorization.substring(0, 5).lowercase() != "dpop ") {
            errorResponse(resp, "invalid_request", "Authorization header invalid or missing")
            return
        }
        val accessToken = authorization.substring(5)
        val id = codeToId(OpaqueIdType.ACCESS_TOKEN, accessToken)
        val storage = environment.getInterface(Storage::class)!!
        val state = runBlocking {
            IssuanceState.fromCbor(storage.get("IssuanceState", "", id)!!.toByteArray())
        }
        try {
            authorizeWithDpop(state.dpopKey, req, state.dpopNonce!!.toByteArray().toBase64Url(), accessToken)
        } catch (err: IllegalArgumentException) {
            println("bad DPoP authorization: $err")
            errorResponse(resp, "authorization", err.message ?: "unknown")
            return
        }
        state.dpopNonce = null
        state.cNonce = null
        runBlocking {
            storage.update("IssuanceState", "", id, ByteString(state.toCbor()))
        }
        val requestData = req.inputStream.readNBytes(req.contentLength)
        val json = Json.parseToJsonElement(String(requestData)) as JsonObject
        val proof = json["proof"]?.jsonObject
        if (proof == null) {
            errorResponse(resp, "invalid_request", "'proof.jwt' parameter missing")
            return
        }
        val jwt = proof["jwt"]?.jsonPrimitive?.content
        if (jwt == null) {
            errorResponse(resp, "invalid_request", "'proof.jwt' parameter missing")
            return
        }
        val parts = jwt.split(".")
        if (parts.size != 3) {
            errorResponse(resp, "invalid_request", "invalid value for 'proof.jwt' parameter")
            return
        }
        val head = Json.parseToJsonElement(String(parts[0].fromBase64Url())) as JsonObject
        val authenticationKey = JsonWebKey(head).asEcPublicKey
        val format = json["format"]?.jsonPrimitive?.content
        if (format == "vc+sd-jwt") {
            val vct = json["vct"]?.jsonPrimitive?.content
            if (vct != EUPersonalID.EUPID_VCT) {
                errorResponse(resp, "invalid_request", "invalid value for 'vct' parameter")
                return
            }
            val result = buildJsonObject {
                put("credential", createCredentialSdJwt(state, authenticationKey))
            }
            resp.contentType = "application/json"
            resp.writer.write(Json.encodeToString(result))
            return
        }
        if (format == "mso_mdoc") {
            val vct = json["doctype"]?.jsonPrimitive?.content
            if (vct != EUPersonalID.EUPID_DOCTYPE) {
                errorResponse(resp, "invalid_request", "invalid value for 'doctype' parameter")
                return
            }
            val result = buildJsonObject {
                put("credential", createCredentialMdoc(state, authenticationKey).toBase64Url())
            }
            resp.contentType = "application/json"
            resp.writer.write(Json.encodeToString(result))
            return
        }
        errorResponse(resp, "invalid_request", "invalid value for 'format' parameter")
    }

    private fun createCredentialSdJwt(
        state: IssuanceState,
        authenticationKey: EcPublicKey
    ): String {
        val identityAttributes = buildJsonObject {
            put("given_name", JsonPrimitive(state.credentialData!!.firstName))
            put("family_name", JsonPrimitive(state.credentialData!!.lastName))
        }

        val sdJwtVcGenerator = SdJwtVcGenerator(
            random = Random,
            payload = identityAttributes,
            docType = EUPersonalID.EUPID_VCT,
            issuer = Issuer("https://example-issuer.com", Algorithm.ES256, "key-1")
        )

        val now = Clock.System.now()

        val timeSigned = now
        val validFrom = now
        val validUntil = validFrom + 30.days

        sdJwtVcGenerator.publicKey = JsonWebKey(authenticationKey)
        sdJwtVcGenerator.timeSigned = timeSigned
        sdJwtVcGenerator.timeValidityBegin = validFrom
        sdJwtVcGenerator.timeValidityEnd = validUntil

        val resources = environment.getInterface(Resources::class)!!
        val documentSigningKeyCert =
            X509Cert.fromPem(resources.getStringResource("ds_certificate.pem")!!)
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.ecPublicKey)
        val sdJwt = sdJwtVcGenerator.generateSdJwt(documentSigningKey)

        return sdJwt.toString()
    }

    private fun createCredentialMdoc(
        state: IssuanceState,
        authenticationKey: EcPublicKey
    ): ByteArray {
        val now = Clock.System.now()

        // Create AuthKeys and MSOs, make sure they're valid for 30 days. Also make
        // sure to not use fractional seconds as 18013-5 calls for this (clauses 7.1
        // and 9.1.2.4)
        //
        val timeSigned = Instant.fromEpochSeconds(now.epochSeconds, 0)
        val validFrom = Instant.fromEpochSeconds(now.epochSeconds, 0)
        val validUntil = validFrom + 30.days

        // Generate an MSO and issuer-signed data for this authentication key.
        val docType = EUPersonalID.EUPID_DOCTYPE
        val msoGenerator = MobileSecurityObjectGenerator(
            "SHA-256",
            docType,
            authenticationKey
        )
        msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)

        val credentialData = NameSpacedData.Builder()
            .putEntryString(EUPersonalID.EUPID_NAMESPACE, "family_name", state.credentialData!!.lastName!!)
            .putEntryString(EUPersonalID.EUPID_NAMESPACE, "given_name", state.credentialData!!.firstName!!)
            .build()

        val randomProvider = Random.Default
        val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
            credentialData,
            randomProvider,
            16,
            null
        )
        for (nameSpaceName in issuerNameSpaces.keys) {
            val digests = MdocUtil.calculateDigestsForNameSpace(
                nameSpaceName,
                issuerNameSpaces,
                Algorithm.SHA256
            )
            msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
        }

        val resources = environment.getInterface(Resources::class)!!
        val documentSigningKeyCert = X509Cert.fromPem(
            resources.getStringResource("ds_certificate.pem")!!)
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.ecPublicKey
        )

        val mso = msoGenerator.generate()
        val taggedEncodedMso = Cbor.encode(Tagged(Tagged.ENCODED_CBOR, Bstr(mso)))
        val protectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_ALG),
            Algorithm.ES256.coseAlgorithmIdentifier.toDataItem()
        ))
        val unprotectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
            X509CertChain(listOf(
                X509Cert(documentSigningKeyCert.encodedCertificate))
            ).toDataItem()
        ))
        val encodedIssuerAuth = Cbor.encode(
            Cose.coseSign1Sign(
                documentSigningKey,
                taggedEncodedMso,
                true,
                Algorithm.ES256,
                protectedHeaders,
                unprotectedHeaders
            ).toDataItem()
        )

        val issuerProvidedAuthenticationData = StaticAuthDataGenerator(
            issuerNameSpaces,
            encodedIssuerAuth
        ).generate()

        return issuerProvidedAuthenticationData
    }
}