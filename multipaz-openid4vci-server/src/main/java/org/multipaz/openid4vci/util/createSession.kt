package org.multipaz.openid4vci.util

import io.ktor.http.Parameters
import io.ktor.server.request.ApplicationRequest
import org.multipaz.crypto.X509Cert
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.cache
import org.multipaz.sdjwt.util.JsonWebKey
import org.multipaz.util.fromBase64Url
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.openid4vci.credential.CredentialFactory
import org.multipaz.rpc.backend.Configuration

/**
 * Ensures Oauth client attestation attached to the given HTTP request is valid.
 *
 * @throws InvalidRequestException request is syntactically incorrect
 * @throws IllegalArgumentException attestation or attestation proof-of-possession signature is not valid
 */
suspend fun validateClientAttestation(request: ApplicationRequest, clientId: String) {
    val require = BackendEnvironment.getInterface(Configuration::class)!!
        .getValue("requireClientAttestation")
    if (require == "false") {
        return
    }

    val clientAttestationJwt = request.headers["OAuth-Client-Attestation"]
        ?: throw InvalidRequestException("OAuth-Client-Attestation header required")
    val clientAttestationPoPJwt = request.headers["OAuth-Client-Attestation-PoP"]
        ?: throw InvalidRequestException("OAuth-Client-Attestation-PoP header required")

    // Check that client attestation is signed by the trusted client back-end issuer.
    val attestationParts = clientAttestationJwt.split('.')
    if (attestationParts.size != 3) {
        throw InvalidRequestException("Invalid client attestation JWT")
    }
    val attestationBody = Json.parseToJsonElement(
        attestationParts[1].fromBase64Url().decodeToString()
    ).jsonObject
    val attestationIssuer = attestationBody["iss"]?.jsonPrimitive?.content
        ?: throw InvalidRequestException("Client attestation JWT must specify issuer ('iss')")
    val attestationSubject = attestationBody["sub"]?.jsonPrimitive?.content
        ?: throw InvalidRequestException("Client attestation JWT must specify subject ('sub')")
    if (attestationSubject != clientId) {
        throw InvalidRequestException("Client attestation subject and clientId do not match")
    }

    // TODO: replay check, expiration check, etc.

    val attestationIssuerCertificate =
        BackendEnvironment.cache(
            ClientCertificate::class,
            attestationIssuer
        ) { configuration, resources ->
            // So in real life this should be parameterized by attestation issuer, as different
            // clients will have different public keys.
            val certificateName = configuration.getValue("attestation.certificate")
                ?: "attestation/certificate.pem"
            val certificate = X509Cert.fromPem(resources.getStringResource(certificateName)!!)
            ClientCertificate(certificate)
        }
    checkJwtSignature(attestationIssuerCertificate.certificate.ecPublicKey, clientAttestationJwt)
    val attestationKey = JsonWebKey(attestationBody["cnf"]!!.jsonObject).asEcPublicKey

    // Validate client attestation proof-of-possession
    checkJwtSignature(attestationKey, clientAttestationPoPJwt)
}

/**
 * Create issuance session based on the given HTTP request and return a unique id for it.
 */
suspend fun createSession(request: ApplicationRequest, parameters: Parameters): String {
    // Read all parameters
    val clientId = parameters["client_id"]
        ?: throw InvalidRequestException("missing parameter 'client_id'")
    validateClientAttestation(request, clientId)
    val dpopJwt = request.headers["DPoP"]
        ?: throw InvalidRequestException("DPoP header required")
    val scope = parameters["scope"] ?: ""
    if (!CredentialFactory.supportedScopes.contains(scope)) {
        throw InvalidRequestException("invalid parameter 'scope'")
    }
    if (parameters["response_type"] != "code") {
        throw InvalidRequestException("invalid parameter 'response_type'")
    }
    if (parameters["code_challenge_method"] != "S256") {
        throw InvalidRequestException("invalid parameter 'code_challenge_method'")
    }
    val redirectUri = parameters["redirect_uri"]
        ?: throw InvalidRequestException("missing parameter 'redirect_uri'")
    if (!redirectUri.matches(plausibleUrl)) {
        throw InvalidRequestException("invalid parameter value 'redirect_uri'")
    }
    val codeChallenge = try {
        ByteString(parameters["code_challenge"]!!.fromBase64Url())
    } catch (err: Exception) {
        throw InvalidRequestException("invalid parameter 'code_challenge'")
    }

    // Validate DPoP
    val dpopParts = dpopJwt.split('.')
    if (dpopParts.size != 3) {
        throw InvalidRequestException("Invalid client attestation JWT")
    }
    val dpopHeader = Json.parseToJsonElement(
        dpopParts[0].fromBase64Url().decodeToString()
    ).jsonObject
    val dpopKey = JsonWebKey(dpopHeader).asEcPublicKey
    checkJwtSignature(dpopKey, dpopJwt)

    // Create a session
    return IssuanceState.createIssuanceState(
        IssuanceState(clientId, scope, dpopKey, redirectUri, codeChallenge)
    )
}

// We do not allow "#", "&" and "?" characters as they belong to query/fragment part of the
// URL which must not be present
private val plausibleUrl = Regex("^[^\\s'\"#&?]+\$")

private data class ClientCertificate(val certificate: X509Cert)
