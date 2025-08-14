package org.multipaz.testapp.provisioning

import io.ktor.http.Url
import io.ktor.util.encodeBase64
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.multipaz.asn1.OID
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X509Cert
import org.multipaz.provision.openid4vci.OpenID4VCIBackend
import org.multipaz.provision.openid4vci.OpenID4VCIClientPreferences
import org.multipaz.securearea.KeyAttestation
import org.multipaz.util.toBase64Url
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Imitate OpenID4VCI wallet back-end for the test app and provide support for the app links.
 *
 * In a real wallet app, the app should call its back-end server to implement [OpenID4VCIBackend]
 * interface, as keys that are used to sign various attestations and assertions must be kept
 * secret. For testing purposes the keys are embedded into the app itself - but such app can be
 * easily impersonated and therefore can never be trusted by a real-life provisioning server.
 */
class ProvisioningSupport: OpenID4VCIBackend {
    companion object Companion {
        const val APP_LINK_SERVER = "https://apps.multipaz.org"
        const val APP_LINK_BASE_URL = "$APP_LINK_SERVER/landing/"

        private val localClientAssertionJwk = Json.parseToJsonElement("""
            {
                "kty": "EC",
                "alg": "ES256",
                "key_ops": [
                    "sign"
                ],
                "kid": "895b72b9-0808-4fcc-bb19-960d14a9e28f",
                "crv": "P-256",
                "x": "nSmAFnZx-SqgTEyqqOSmZyLESdbiSUIYlRlLLoWy5uc",
                "y": "FN1qcif7nyVX1MHN_YSbo7o7RgG2kPJUjg27YX6AKsQ",
                "d": "TdQhxDqbAUpzMJN5XXQqLea7-6LvQu2GFKzj5QmFDCw"
            }            
        """.trimIndent()).jsonObject

        private val localClientAssertionPrivateKey = EcPrivateKey.fromJwk(localClientAssertionJwk)
        private val localClientAssertionKeyId = localClientAssertionJwk["kid"]!!.jsonPrimitive.content

        private val attestationCertificate = X509Cert.fromPem("""
                -----BEGIN CERTIFICATE-----
                MIIBxTCCAUugAwIBAgIJAOQTL9qcQopZMAoGCCqGSM49BAMDMDgxNjA0BgNVBAMT
                LXVybjp1dWlkOjYwZjhjMTE3LWI2OTItNGRlOC04ZjdmLTYzNmZmODUyYmFhNjAe
                Fw0yNDA5MjMyMjUxMzFaFw0zNDA5MjMyMjUxMzFaMDgxNjA0BgNVBAMTLXVybjp1
                dWlkOjYwZjhjMTE3LWI2OTItNGRlOC04ZjdmLTYzNmZmODUyYmFhNjB2MBAGByqG
                SM49AgEGBSuBBAAiA2IABN4D7fpNMAv4EtxyschbITpZ6iNH90rGapa6YEO/uhKn
                C6VpPt5RUrJyhbvwAs0edCPthRfIZwfwl5GSEOS0mKGCXzWdRv4GGX/Y0m7EYypo
                x+tzfnRTmoVX3v6OxQiapKMhMB8wHQYDVR0OBBYEFPqAK5EjiQbxFAeWt//DCaWt
                C57aMAoGCCqGSM49BAMDA2gAMGUCMEO01fJKCy+iOTpaVp9LfO7jiXcXksn2BA22
                reiR9ahDRdGNCrH1E3Q2umQAssSQbQIxAIz1FTHbZPcEbA5uE5lCZlRG/DQxlZhk
                /rZrkPyXFhqEgfMnQ45IJ6f8Utlg+4Wiiw==
                -----END CERTIFICATE-----
            """.trimIndent()
        )

        private val attestationPrivateKey = EcPrivateKey.fromPem("""
            -----BEGIN PRIVATE KEY-----
            ME4CAQAwEAYHKoZIzj0CAQYFK4EEACIENzA1AgEBBDBn7jeRC9u9de3kOkrt9lLT
            Pvd1hflNq1FCgs7D+qbbwz1BQa4XXU0SjsV+R1GjnAY=
            -----END PRIVATE KEY-----
            """.trimIndent(),
            attestationCertificate.ecPublicKey
        )

        const val CLIENT_ID = "urn:uuid:418745b8-78a3-4810-88df-7898aff3ffb4"

        private val attestationId =
            attestationCertificate.subject.components[OID.COMMON_NAME.oid]?.value
                ?: throw IllegalStateException("No common name (CN) in certificate's subject")

        val OPENID4VCI_CLIENT_PREFERENCES = OpenID4VCIClientPreferences(
            clientId = CLIENT_ID,
            redirectUrl = APP_LINK_BASE_URL,
            locales = listOf("en-US"),
            signingAlgorithms = listOf(Algorithm.ESP256, Algorithm.ESP384, Algorithm.ESP512)
        )
    }

    private val lock = Mutex()
    private val pendingLinksByState = mutableMapOf<String, SendChannel<String>>()

    suspend fun processAppLinkInvocation(url: String) {
        val state = Url(url).parameters["state"] ?: ""
        lock.withLock {
            pendingLinksByState.remove(state)?.send(url)
        }
    }

    suspend fun waitForAppLinkInvocation(state: String): String {
        val channel = Channel<String>(Channel.RENDEZVOUS)
        lock.withLock {
            pendingLinksByState[state] = channel
        }
        return channel.receive()
    }

    override suspend fun createJwtClientAssertion(tokenUrl: String): String {
        val alg = localClientAssertionPrivateKey.curve.defaultSigningAlgorithmFullySpecified.joseAlgorithmIdentifier
        val head = buildJsonObject {
            put("typ", "JWT")
            put("alg", alg)
            put("kid", localClientAssertionKeyId)
        }.toString().encodeToByteArray().toBase64Url()

        // TODO: figure out what should be passed as `aud`.
        //  per 'https://datatracker.ietf.org/doc/html/rfc7523#page-5' tokenUrl is appropriate,
        //  but Openid validation suite does not seem to take that.
        val aud = if (tokenUrl.endsWith("/token")) {
            // A hack to get authorization url from token url; would not work in general case.
            tokenUrl.substring(0, tokenUrl.length - 5)
        } else {
            tokenUrl
        }

        val now = Clock.System.now()
        val expiration = now + 5.minutes
        val payload = buildJsonObject {
            put("jti", Random.Default.nextBytes(18).toBase64Url())
            put("iss", CLIENT_ID)
            put("sub", CLIENT_ID) // RFC 7523 Section 3, item 2.B
            put("exp", expiration.epochSeconds)
            put("iat", now.epochSeconds)
            put("aud", aud)
        }.toString().encodeToByteArray().toBase64Url()

        val message = "$head.$payload"
        val sig = Crypto.sign(
            key = localClientAssertionPrivateKey,
            signatureAlgorithm = localClientAssertionPrivateKey.curve.defaultSigningAlgorithm,
            message = message.encodeToByteArray()
        )
        val signature = sig.toCoseEncoded().toBase64Url()

        return "$message.$signature"
    }

    override suspend fun createJwtWalletAttestation(keyAttestation: KeyAttestation): String {
        // Implements this draft:
        // https://datatracker.ietf.org/doc/html/draft-ietf-oauth-attestation-based-client-auth-04

        val signatureAlgorithm = attestationPrivateKey.curve.defaultSigningAlgorithmFullySpecified
        val head = buildJsonObject {
            put("typ", "oauth-client-attestation+jwt")
            put("alg", signatureAlgorithm.joseAlgorithmIdentifier)
            put("x5c", buildJsonArray {
                add(attestationCertificate.encodedCertificate.encodeBase64())
            })
        }.toString().encodeToByteArray().toBase64Url()

        val now = Clock.System.now()
        val notBefore = now - 1.seconds
        // Expiration here is only for the client assertion to be presented to the issuing server
        // in the given timeframe (which happens without user interaction). It does not imply that
        // the key becomes invalid at that point in time.
        val expiration = now + 5.minutes
        val payload = buildJsonObject {
            put("iss", attestationId)
            put("sub", CLIENT_ID)
            put("exp", expiration.epochSeconds)
            put("cnf", buildJsonObject {
                put("jwk", keyAttestation.publicKey.toJwk(buildJsonObject { put("kid", CLIENT_ID) }))
            })
            put("nbf", notBefore.epochSeconds)
            put("iat", now.epochSeconds)
            put("wallet_name", "Multipaz Wallet")
            put("wallet_link", "https://multipaz.org")
        }.toString().encodeToByteArray().toBase64Url()

        val message = "$head.$payload"
        val sig = Crypto.sign(
            key = attestationPrivateKey,
            signatureAlgorithm = signatureAlgorithm,
            message = message.encodeToByteArray()
        )
        val signature = sig.toCoseEncoded().toBase64Url()

        return "$message.$signature"
    }

    override suspend fun createJwtKeyAttestation(
        keyAttestations: List<KeyAttestation>,
        challenge: String
    ): String {
        val keyList = keyAttestations.map { it.publicKey }

        val alg = attestationPrivateKey.curve.defaultSigningAlgorithm.joseAlgorithmIdentifier
        val head = buildJsonObject {
            put("typ", "keyattestation+jwt")
            put("alg", alg)
            put("x5c", buildJsonArray {
                add(attestationCertificate.encodedCertificate.encodeBase64())
            })
        }.toString().encodeToByteArray().toBase64Url()

        val now = Clock.System.now()
        val notBefore = now - 1.seconds
        val expiration = now + 5.minutes
        val payload = buildJsonObject {
            put("iss", attestationId)
            put("attested_keys", JsonArray(keyList.map { it.toJwk() }))
            put("nonce", challenge)
            put("nbf", notBefore.epochSeconds)
            put("exp", expiration.epochSeconds)
            put("iat", now.epochSeconds)
        }.toString().encodeToByteArray().toBase64Url()

        val message = "$head.$payload"
        val sig = Crypto.sign(
            key = attestationPrivateKey,
            signatureAlgorithm = attestationPrivateKey.curve.defaultSigningAlgorithm,
            message = message.encodeToByteArray()
        )
        val signature = sig.toCoseEncoded().toBase64Url()

        return "$message.$signature"
    }
}