package com.android.identity.issuance.wallet

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.server.Storage
import com.android.identity.issuance.ApplicationSupport
import com.android.identity.issuance.LandingUrlUnknownException
import com.android.identity.issuance.WalletServerSettings
import com.android.identity.issuance.common.cache
import com.android.identity.issuance.funke.FunkeUtil
import com.android.identity.issuance.funke.toJson
import com.android.identity.issuance.validateKeyAttestation
import com.android.identity.securearea.KeyAttestation
import com.android.identity.util.Logger
import com.android.identity.util.toBase64Url
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@FlowState(flowInterface = ApplicationSupport::class)
@CborSerializable
class ApplicationSupportState(
    var clientId: String
) {
    companion object {
        const val URL_PREFIX = "landing/"
        const val TAG = "ApplicationSupportState"

        // This is the ID that was allocated to our app in the context of Funke. Use it as
        // default client id for ease of development.
        const val FUNKE_CLIENT_ID = "60f8c117-b692-4de8-8f7f-636ff852baa6"
    }

    @FlowMethod
    suspend fun createLandingUrl(env: FlowEnvironment): String {
        val storage = env.getInterface(Storage::class)!!
        val id = storage.insert("Landing", "", ByteString(LandingRecord(clientId).toCbor()))
        Logger.i(TAG, "Created landing URL '$id'")
        return URL_PREFIX + id
    }

    @FlowMethod
    suspend fun getLandingUrlStatus(env: FlowEnvironment, baseUrl: String): String? {
        if (!baseUrl.startsWith(URL_PREFIX)) {
            throw IllegalStateException("baseUrl must start with $URL_PREFIX")
        }
        val storage = env.getInterface(Storage::class)!!
        val id = baseUrl.substring(URL_PREFIX.length)
        Logger.i(TAG, "Querying landing URL '$id'")
        val recordData = storage.get("Landing", "", id) ?:
            throw LandingUrlUnknownException("No landing url '$id'")
        val record = LandingRecord.fromCbor(recordData.toByteArray())
        if (record.resolved != null) {
            Logger.i(TAG, "Removed landing URL '$id'")
            storage.delete("Landing", "", id)
        }
        return record.resolved
    }

    @FlowMethod
    fun createJwtClientAssertion(
        env: FlowEnvironment, attestation: KeyAttestation, targetIssuanceUrl: String): String {
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)

        validateKeyAttestation(
            attestation.certChain!!,
            null,  // no challenge check
            settings.androidRequireGmsAttestation,
            settings.androidRequireVerifiedBootGreen,
            settings.androidRequireAppSignatureCertificateDigests
        )

        check(attestation.certChain!!.certificates[0].ecPublicKey == attestation.publicKey)
        return createJwtClientAssertion(env, attestation.publicKey, targetIssuanceUrl)
    }

    // Not exposed as RPC!
    fun createJwtClientAssertion(
        env: FlowEnvironment,
        clientPublicKey: EcPublicKey,
        targetIssuanceUrl: String
    ): String {
        val attestationData = env.cache(
            ClientAttestationData::class,
            targetIssuanceUrl
        ) { configuration, resources ->
            // These are basically our credentials to talk to a particular OpenID4VCI issuance
            // server. So in real life this may need to be parameterized by targetIssuanceUrl
            // if we support many issuance servers. Alternatively, we may have a single public key
            // to be registered with multiple issuers. For development we are OK using
            // a single key for everything.
            val certificateName = configuration.getValue("attestation.certificate")
                ?: "attestation/certificate.pem"
            val clientId = configuration.getValue("attestation.clientId") ?: FUNKE_CLIENT_ID
            val certificate = X509Cert.fromPem(resources.getStringResource(certificateName)!!)

            // NB: default private key is just an arbitrary value, so it can be checked into
            // git and work out of the box for our own OpenID4VCI issuer (in particular, this is
            // NOT the private key registered with Funke server!) It may or may not work for
            // development/prototype servers, but it, of course, should not be used in production!
            // In fact, in production this key may need to come from an HSM, not read from some
            // PEM resource!
            val privateKeyName = configuration.getValue("attestation.privateKey")
                ?: "attestation/private_key.pem"
            val privateKey = EcPrivateKey.fromPem(
                resources.getStringResource(privateKeyName)!!,
                certificate.ecPublicKey
            )
            ClientAttestationData(certificate, privateKey, clientId)
        }
        val publicKey = attestationData.certificate.ecPublicKey
        val privateKey = attestationData.privateKey
        val alg = publicKey.curve.defaultSigningAlgorithm.jwseAlgorithmIdentifier
        val head = buildJsonObject {
            put("typ", JsonPrimitive("JWT"))
            put("alg", JsonPrimitive(alg))
            put("jwk", publicKey.toJson(null))
        }.toString().toByteArray().toBase64Url()

        val now = Clock.System.now()
        val notBefore = now - 1.seconds
        val expiration = now + 5.minutes
        val payload = JsonObject(mapOf(
            "iss" to JsonPrimitive(attestationData.clientId),
            // TODO: should this be clientId or applicationData.clientId? Our server does not care
            // but others might.
            "sub" to JsonPrimitive(attestationData.clientId),
            "cnf" to JsonObject(mapOf(
                "jwk" to clientPublicKey.toJson(clientId)
            )),
            "nbf" to JsonPrimitive(notBefore.epochSeconds),
            "exp" to JsonPrimitive(expiration.epochSeconds),
            "iat" to JsonPrimitive(now.epochSeconds)
        )).toString().toByteArray().toBase64Url()

        val message = "$head.$payload"
        val sig = Crypto.sign(
            privateKey, privateKey.curve.defaultSigningAlgorithm, message.toByteArray())
        val signature = sig.toCoseEncoded().toBase64Url()

        return "$message.$signature"
    }

}

data class ClientAttestationData(
    val certificate: X509Cert,
    val privateKey: EcPrivateKey,
    val clientId: String
)