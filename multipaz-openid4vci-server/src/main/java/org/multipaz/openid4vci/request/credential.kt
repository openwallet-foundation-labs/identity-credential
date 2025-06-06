package org.multipaz.openid4vci.request

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.toDataItem
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources
import org.multipaz.rpc.cache
import org.multipaz.mdoc.mso.MobileSecurityObjectGenerator
import org.multipaz.mdoc.mso.StaticAuthDataGenerator
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.multipaz.documenttype.knowntypes.UtopiaMovieTicket
import org.multipaz.openid4vci.credential.CredentialFactory
import org.multipaz.openid4vci.credential.Openid4VciFormat
import org.multipaz.openid4vci.util.IssuanceState
import org.multipaz.openid4vci.util.OpaqueIdType
import org.multipaz.openid4vci.util.authorizeWithDpop
import org.multipaz.openid4vci.util.checkJwtSignature
import org.multipaz.openid4vci.util.codeToId
import org.multipaz.sdjwt.SdJwt
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

/**
 * Issues a credential based on access token issued by [TokenServlet].
 *
 * TODO: assess token is meant to be a single use (verify that). Add replay protection.
 */
suspend fun credential(call: ApplicationCall) {
    val authorization = call.request.headers["Authorization"]
    if (authorization == null || authorization.substring(0, 5).lowercase() != "dpop ") {
        throw InvalidRequestException("Authorization header invalid or missing")
    }
    val accessToken = authorization.substring(5)
    val id = codeToId(OpaqueIdType.ACCESS_TOKEN, accessToken)
    val state = IssuanceState.getIssuanceState(id)
    authorizeWithDpop(call.request, state.dpopKey, state.dpopNonce!!.toByteArray().toBase64Url(), accessToken)
    val nonce = state.cNonce!!.toByteArray().toBase64Url()  // credential nonce
    state.dpopNonce = null
    state.cNonce = null
    IssuanceState.updateIssuanceState(id, state)
    val requestString = call.receiveText()
    val json = Json.parseToJsonElement(requestString) as JsonObject
    val format = Openid4VciFormat.fromJson(json)
    val factory = CredentialFactory.byOfferId.values.find { factory ->
        factory.format == format && factory.scope == state.scope
    }
    if (factory == null) {
        throw IllegalStateException(
            "No credential can be created for scope '${state.scope}' and the given format")
    }
    if (factory.cryptographicBindingMethods.isEmpty()) {
        // Keyless credential: no need for proof/proofs parameter.
        val credential = factory.makeCredential(state, null)
        call.respondText(
            text = buildJsonObject {
                putJsonArray("credentials") {
                    addJsonObject {
                        put("credential", credential)
                    }
                }
            }.toString(),
            contentType = ContentType.Application.Json
        )
        return
    }
    val proofsObj = json["proofs"]?.jsonObject
    val singleProof = proofsObj == null
    val proofs: JsonArray
    val proofType: String
    if (proofsObj == null) {
        val proof = json["proof"]
            ?: throw InvalidRequestException("neither 'proof' or 'proofs' parameter provided")
        proofType = proof.jsonObject["proof_type"]?.jsonPrimitive?.content!!
        proofs = buildJsonArray { add(proof.jsonObject[proofType]!!) }
    } else {
        proofType = if (proofsObj.containsKey("jwt")) {
            "jwt"
        } else if (proofsObj.containsKey("attestation")) {
            "attestation"
        } else {
            throw InvalidRequestException("Unsupported proof type")
        }
        proofs = proofsObj[proofType]!!.jsonArray
        if (proofs.size == 0) {
            throw InvalidRequestException("'proofs' is empty")
        }
    }

    val authenticationKeys = when (proofType) {
        "attestation" -> {
            val keyAttestationCertificate =
                BackendEnvironment.cache(
                    KeyAttestationCertificate::class,
                    state.clientId
                ) { configuration, resources ->
                    // By default using the same key/certificate as for client attestation
                    val certificateName =
                        configuration.getValue("openid4vci.key-attestation.certificate")
                            ?: "attestation/certificate.pem"
                    val certificate =
                        X509Cert.fromPem(resources.getStringResource(certificateName)!!)
                    KeyAttestationCertificate(certificate)
                }

            proofs.flatMap { proof ->
                val keyAttestation = proof.jsonPrimitive.content
                checkJwtSignature(
                    keyAttestationCertificate.certificate.ecPublicKey,
                    keyAttestation
                )
                val parts = keyAttestation.split(".")

                if (parts.size != 3) {
                    throw InvalidRequestException("invalid value for 'proof(s).attestation' parameter")
                }
                val body = Json.parseToJsonElement(String(parts[1].fromBase64Url())).jsonObject
                if (body["nonce"]?.jsonPrimitive?.content != nonce) {
                    throw InvalidRequestException("invalid nonce in 'proof(s).attestation' parameter")
                }
                body["attested_keys"]!!.jsonArray.map { key ->
                    EcPublicKey.fromJwk(key.jsonObject)
                }
            }
        }
        "jwt" -> {
            if (!isStandaloneProofOfPossessionAccepted()) {
                throw InvalidRequestException("jwt proofs are not accepted by this server")
            }
            proofs.map { proof ->
                val jwt = proof.jsonObject["jwt"]?.jsonPrimitive?.content
                    ?: throw InvalidRequestException("either 'proof.attestation' or 'proof.jwt' parameter is required")
                val parts = jwt.split(".")
                if (parts.size != 3) {
                    throw InvalidRequestException("invalid value for 'proof.jwt' parameter")
                }
                val head = Json.parseToJsonElement(String(parts[0].fromBase64Url())) as JsonObject
                val authenticationKey = EcPublicKey.fromJwk(head)
                checkJwtSignature(authenticationKey, jwt)
                authenticationKey
            }
        }
        else -> {
            throw InvalidRequestException("unsupported proof type")
        }
    }

    val credentials = authenticationKeys.map { key -> factory.makeCredential(state, key) }

    val result =
        buildJsonObject {
            putJsonArray("credentials") {
                for (credential in credentials) {
                    addJsonObject {
                        put("credential", credential)
                    }
                }
            }
        }
    call.respondText(
        text = Json.encodeToString(result),
        contentType = ContentType.Application.Json
    )
}

private suspend fun createCredentialSdJwt(
    state: IssuanceState,
    authenticationKey: EcPublicKey
): String {

    val data = state.credentialData!!
    val identityAttributes = buildJsonObject {
        put("given_name", JsonPrimitive(data.getDataElementString(EUPersonalID.EUPID_NAMESPACE, "given_name")))
        put("family_name", JsonPrimitive(data.getDataElementString(EUPersonalID.EUPID_NAMESPACE, "family_name")))
    }

    val now = Clock.System.now()

    val timeSigned = now
    val validFrom = now
    val validUntil = validFrom + 30.days

    val resources = BackendEnvironment.getInterface(Resources::class)!!
    val documentSigningKeyCert =
        X509Cert.fromPem(resources.getStringResource("ds_certificate.pem")!!)
    val documentSigningKey = EcPrivateKey.fromPem(
        resources.getStringResource("ds_private_key.pem")!!,
        documentSigningKeyCert.ecPublicKey)

    val sdJwt = SdJwt.create(
        issuerKey = documentSigningKey,
        issuerAlgorithm = documentSigningKey.curve.defaultSigningAlgorithmFullySpecified,
        issuerCertChain = X509CertChain(listOf(documentSigningKeyCert)),
        kbKey = authenticationKey,
        claims = identityAttributes,
        nonSdClaims = buildJsonObject {
            put("iss", "https://example-issuer.com")
            put("vct", UtopiaMovieTicket.MOVIE_TICKET_VCT)
            put("iat", timeSigned.epochSeconds)
            put("nbf", validFrom.epochSeconds)
            put("exp", validUntil.epochSeconds)
        }
    )
    return sdJwt.compactSerialization
}

private suspend fun createCredentialMdoc(
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
        Algorithm.SHA256,
        docType,
        authenticationKey
    )
    msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)

    val randomProvider = Random.Default
    val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
        state.credentialData!!,
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

    val resources = BackendEnvironment.getInterface(Resources::class)!!
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
        Algorithm.ES256.coseAlgorithmIdentifier!!.toDataItem()
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

private data class KeyAttestationCertificate(val certificate: X509Cert)

/**
 * Checks if this server accepts proof of possession **without** key attestation.
 *
 * Our code does not support proof of possession with key attestation (only standalone
 * key attestation or standalone proof of possession). Standalone proof of possession
 * is disabled by default, as it does not really guarantee where the private key is
 * stored. Don't enable this unless you are sure you understand the tradeoffs involved!
 *
 * NB: Proof of possession **with** key attestation, if implemented, could be just enabled
 * unconditionally, as it does not have this issue (as long as key attestation is required).
 */
internal suspend fun isStandaloneProofOfPossessionAccepted(): Boolean {
    val allowProofOfPossession = BackendEnvironment.getInterface(Configuration::class)
        ?.getValue("openid4vci.allow-proof-of-possession")
    return allowProofOfPossession == "true"
}
