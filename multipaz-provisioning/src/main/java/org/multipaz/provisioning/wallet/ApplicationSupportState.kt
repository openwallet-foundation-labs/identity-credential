package org.multipaz.provisioning.wallet

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X509Cert
import org.multipaz.device.DeviceAssertion
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.LandingUrlUnknownException
import org.multipaz.rpc.cache
import org.multipaz.rpc.backend.getTable
import org.multipaz.provisioning.validateDeviceAssertionBindingKeys
import org.multipaz.securearea.KeyAttestation
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import kotlin.time.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthInspector
import org.multipaz.rpc.handler.RpcAuthInspectorAssertion
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RpcState(endpoint = "appsupport")
@CborSerializable
class ApplicationSupportState(
    // NB: since this object is used to emit notifications, it cannot change, as its state
    // serves as notification key. So it generally should not have var members, only val.
    // This is also the reason we keep clientId here (so there is no conflict between different
    // clients)
    val clientId: String
): ApplicationSupport, RpcAuthInspector by RpcAuthBackendDelegate {
    companion object {
        const val URL_PREFIX = "landing/"
        const val TAG = "ApplicationSupportState"

        // Default OpenId4VCI client id. It is NOT used as value for clientId field above!
        // It identifies our wallet ecosystem (app/server) to OpenId4VCI servers, whereas
        // clientId identifies a particular wallet app instance to the wallet server.
        const val MULTIPAZ_CLIENT_ID = "60f8c117-b692-4de8-8f7f-636ff852baa6"

        val landingTableSpec = StorageTableSpec(
            name = "LandingUrls",
            supportPartitions = false,
            supportExpiration = true
        )

        private val EXPIRATION = 1.days
    }

    override suspend fun createLandingUrl(): String {
        checkClientId()
        val storage = BackendEnvironment.getTable(landingTableSpec)
        val id = storage.insert(
            key = null,
            data = ByteString(LandingRecord(clientId).toCbor()),
            expiration = Clock.System.now() + EXPIRATION
        )
        Logger.i(TAG, "Created landing URL '$id'")
        val configuration = BackendEnvironment.getInterface(Configuration::class)!!
        val baseUrl = configuration.getValue("base_url")!!
        return "$baseUrl/$URL_PREFIX$id"
    }

    override suspend fun getLandingUrlStatus(landingUrl: String): String? {
        checkClientId()
        val configuration = BackendEnvironment.getInterface(Configuration::class)!!
        val baseUrl = configuration.getValue("base_url")!!
        val prefix = "$baseUrl/$URL_PREFIX"
        if (!landingUrl.startsWith(prefix)) {
            Logger.e(TAG, "baseUrl must start with $prefix, actual '$landingUrl'")
            throw IllegalStateException("baseUrl must start with $prefix")
        }
        val storage = BackendEnvironment.getTable(landingTableSpec)
        val id = landingUrl.substring(prefix.length)
        Logger.i(TAG, "Querying landing URL '$id'")
        val recordData = storage.get(id)
            ?: throw LandingUrlUnknownException("No landing url '$id'")
        val record = LandingRecord.fromCbor(recordData.toByteArray())
        if (record.resolved != null) {
            Logger.i(TAG, "Removed landing URL '$id'")
            storage.delete(id)
        }
        return record.resolved
    }

    override suspend fun createJwtClientAssertion(
        authorizationServerUrl: String
    ): String {
        return createJwtClientAssertion(authorizationServerUrl)
    }

    override suspend fun getClientAssertionId(authorizationServerUrl: String): String {
        checkClientId()
        return MULTIPAZ_CLIENT_ID
    }

    override suspend fun createJwtKeyAttestation(
        keyAttestations: List<KeyAttestation>,
        keysAssertion: DeviceAssertion // holds AssertionBindingKeys
    ): String {
        checkClientId()
        val deviceAttestation = RpcAuthInspectorAssertion.getClientDeviceAttestation(clientId)!!
        val assertion = validateDeviceAssertionBindingKeys(
            deviceAttestation = deviceAttestation,
            keyAttestations = keyAttestations,
            deviceAssertion = keysAssertion,
            nonce = null,  // no check
        )

        val nonce = String(assertion.nonce.toByteArray())
        val keyList = assertion.publicKeys

        val attestationData = BackendEnvironment.cache(AttestationData::class) { configuration, resources ->
            // The key that we use here is unique for a particular Wallet ecosystem.
            // Use client attestation key as default for development (default is NOT suitable
            // for production, as private key CANNOT be in the source repository).
            val certificateName = configuration.getValue("openid4vci.key-attestation.certificate")
                ?: "attestation/certificate.pem"
            val certificate = X509Cert.fromPem(resources.getStringResource(certificateName)!!)
            val privateKeyName = configuration.getValue("openid4vci.key-attestation.privateKey")
                ?: "attestation/private_key.pem"
            val privateKey = EcPrivateKey.fromPem(
                resources.getStringResource(privateKeyName)!!,
                certificate.ecPublicKey
            )
            val issuer = configuration.getValue("openid4vci.key-attestation.issuer")
                ?: configuration.getValue("base_url")
                ?: "https://github.com/openwallet-foundation-labs/identity-credential"
            AttestationData(certificate, privateKey, issuer)
        }
        val publicKey = attestationData.certificate.ecPublicKey
        val privateKey = attestationData.privateKey
        val alg = publicKey.curve.defaultSigningAlgorithm.joseAlgorithmIdentifier
        val head = buildJsonObject {
            put("typ", JsonPrimitive("keyattestation+jwt"))
            put("alg", JsonPrimitive(alg))
            put("jwk", publicKey.toJwk())  // TODO: use x5c instead here?
        }.toString().toByteArray().toBase64Url()

        val now = Clock.System.now()
        val notBefore = now - 1.seconds
        val expiration = now + 5.minutes
        val payload = buildJsonObject {
            put("iss", attestationData.clientId)
            put("attested_keys", JsonArray(keyList.map { it.toJwk() }))
            put("nonce", nonce)
            put("nbf", notBefore.epochSeconds)
            put("exp", expiration.epochSeconds)
            put("iat", now.epochSeconds)
            if (assertion.userAuthentication.isNotEmpty()) {
                put("user_authentication",
                    JsonArray(assertion.userAuthentication.map { JsonPrimitive(it) })
                )
            }
            if (assertion.keyStorage.isNotEmpty()) {
                put("key_storage",
                    JsonArray(assertion.keyStorage.map { JsonPrimitive(it) })
                )
            }
        }.toString().toByteArray().toBase64Url()

        val message = "$head.$payload"
        val sig = Crypto.sign(
            privateKey, privateKey.curve.defaultSigningAlgorithm, message.toByteArray()
        )
        val signature = sig.toCoseEncoded().toBase64Url()

        return "$message.$signature"
    }

    override suspend fun createJwtClientAttestation(
        keyAttestation: KeyAttestation,
        deviceAssertion: DeviceAssertion
    ): String {
        throw IllegalArgumentException("not implemented")
    }

    // Not exposed as RPC!
    suspend fun createJwtClientAssertion(
        clientPublicKey: EcPublicKey,
        targetIssuanceUrl: String
    ): String {
        val attestationData = BackendEnvironment.cache(
            AttestationData::class,
            targetIssuanceUrl
        ) { configuration, resources ->
            // These are basically our credentials to talk to a particular OpenID4VCI issuance
            // server. So in real life this may need to be parameterized by targetIssuanceUrl
            // if we support many issuance servers. Alternatively, we may have a single public key
            // to be registered with multiple issuers. For development we are OK using
            // a single key for everything.
            val certificateName = configuration.getValue("attestation.certificate")
                ?: "attestation/certificate.pem"
            val clientId = configuration.getValue("attestation.clientId") ?: MULTIPAZ_CLIENT_ID
            val certificate = X509Cert.fromPem(resources.getStringResource(certificateName)!!)

            // NB: default private key is just an arbitrary value, so it can be checked into
            // git and work out of the box for our own OpenID4VCI issuer. It may or may not work for
            // development/prototype servers, but it, of course, should not be used in production!
            // In fact, in production this key may need to come from an HSM, not read from some
            // PEM resource!
            val privateKeyName = configuration.getValue("attestation.privateKey")
                ?: "attestation/private_key.pem"
            val privateKey = EcPrivateKey.fromPem(
                resources.getStringResource(privateKeyName)!!,
                certificate.ecPublicKey
            )
            AttestationData(certificate, privateKey, clientId)
        }
        val publicKey = attestationData.certificate.ecPublicKey
        val privateKey = attestationData.privateKey
        val alg = publicKey.curve.defaultSigningAlgorithm.joseAlgorithmIdentifier
        val head = buildJsonObject {
            put("typ", JsonPrimitive("JWT"))
            put("alg", JsonPrimitive(alg))
            put("jwk", publicKey.toJwk())
        }.toString().toByteArray().toBase64Url()

        val now = Clock.System.now()
        val notBefore = now - 1.seconds
        // Expiration here is only for the client assertion to be presented to the issuing server
        // in the given timeframe (which happens without user interaction). It does not imply that
        // the key becomes invalid at that point in time.
        val expiration = now + 5.minutes
        val payload = JsonObject(
            mapOf(
                "iss" to JsonPrimitive(attestationData.clientId),
                "sub" to JsonPrimitive(attestationData.clientId), // RFC 7523 Section 3, item 2.B
                "cnf" to JsonObject(
                    mapOf(
                        "jwk" to clientPublicKey.toJwk(additionalClaims = buildJsonObject { put("kid", JsonPrimitive(clientId)) })
                    )
                ),
                "nbf" to JsonPrimitive(notBefore.epochSeconds),
                "exp" to JsonPrimitive(expiration.epochSeconds),
                "iat" to JsonPrimitive(now.epochSeconds)
            )
        ).toString().toByteArray().toBase64Url()

        val message = "$head.$payload"
        val sig = Crypto.sign(
            privateKey, privateKey.curve.defaultSigningAlgorithm, message.toByteArray()
        )
        val signature = sig.toCoseEncoded().toBase64Url()

        return "$message.$signature"
    }

    private suspend fun checkClientId() {
        check(clientId == RpcAuthContext.getClientId())
    }

    internal data class AttestationData(
        val certificate: X509Cert,
        val privateKey: EcPrivateKey,
        val clientId: String
    )
}
