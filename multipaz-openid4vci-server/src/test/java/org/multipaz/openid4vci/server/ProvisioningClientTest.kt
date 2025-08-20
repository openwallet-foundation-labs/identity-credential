package org.multipaz.openid4vci.server

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.parameters
import io.ktor.http.protocolWithAuthority
import io.ktor.http.takeFrom
import io.ktor.server.testing.testApplication
import io.ktor.util.encodeBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.multipaz.asn1.OID
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X509Cert
import org.multipaz.provision.AuthorizationChallenge
import org.multipaz.provision.AuthorizationResponse
import org.multipaz.provision.KeyBindingInfo
import org.multipaz.provision.openid4vci.OpenID4VCI
import org.multipaz.provision.openid4vci.OpenID4VCIBackend
import org.multipaz.provision.openid4vci.OpenID4VCIClientPreferences
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.KeyAttestation
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaProvider
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.server.ServerConfiguration
import org.multipaz.storage.Storage
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.util.toBase64Url
import kotlin.IllegalStateException
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.text.decodeToString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Openid4Vci client-server integration test.
 */
class ProvisioningClientTest {
    lateinit var secureAreaProvider:SecureAreaProvider<SecureArea>

    @Before
    fun setup() {
        val storage = EphemeralStorage()
        secureAreaProvider = SecureAreaProvider<SecureArea>(Dispatchers.Default) {
            SoftwareSecureArea.create(storage)
        }
    }

    @Test
    fun basic() = testApplication {
        val serverArgs = arrayOf(
            "-param", "base_url=http://localhost",
            "-param", "database_engine=ephemeral"
        )
        application {
            configureRouting(ServerConfiguration(serverArgs))
        }
        val httpClient = createClient {
            followRedirects = false
        }
        val env = TestBackendEnvironment(httpClient)
        withContext(env) {
            val provisioningClient = OpenID4VCI.createClientFromOffer(
                offerUri = OFFER,
                clientPreferences = testClientPreferences
            )
            val challenges = provisioningClient.getAuthorizationChallenges()
            val oauthChallenge = (challenges.first() as AuthorizationChallenge.OAuth)
            val authorizationUrl = Url(oauthChallenge.url)

            // ---------------------------------
            // Imitate user interaction, we know the structure of our server's web page, so
            // we can shortcut it.

            // Extract the parameter
            val request = client.get(authorizationUrl) {}
            Assert.assertEquals(HttpStatusCode.OK, request.status)
            val authText = request.readBytes().decodeToString()
            val pattern = "name=\"authorizationCode\" value=\""
            val index = authText.indexOf(pattern)
            Assert.assertNotEquals(-1, index)
            val first = index + pattern.length
            val last = authText.indexOf('"', first)
            val authorizationCode = authText.substring(first, last)

            // Submit the form
            var formRequest = httpClient.submitForm(
                url = authorizationUrl.protocolWithAuthority + authorizationUrl.encodedPath,
                formParameters = parameters {
                    append("authorizationCode", authorizationCode)
                    append("given_name", "Given")
                    append("family_name", "Family")
                    append("birth_date","1998-09-04")
                }
            )

            var location = ""
            while (formRequest.status == HttpStatusCode.Found) {
                location = formRequest.headers["Location"]!!
                if (location.startsWith(testClientPreferences.redirectUrl)) {
                    break
                }
                val newUrl = URLBuilder(authorizationUrl).takeFrom(location).build()
                formRequest = httpClient.get(newUrl)
            }

            // End imitating browser interaction
            //-------------------------------------------------------

            provisioningClient.authorize(
                AuthorizationResponse.OAuth(
                id = oauthChallenge.id,
                parameterizedRedirectUrl = location
            ))

            val secureArea = secureAreaProvider.get()

            provisioningClient.getKeyBindingChallenge()  // Our test backend does not verify key attestation

            val keyInfo = secureArea.createKey(null, CreateKeySettings())

            val credentials = provisioningClient.obtainCredentials(KeyBindingInfo.Attestation(listOf(keyInfo.attestation)))

            Assert.assertEquals(1, credentials.size)
        }
    }

    object TestBackend: OpenID4VCIBackend {
        override suspend fun createJwtClientAssertion(tokenUrl: String): String {
            throw IllegalStateException()
        }

        override suspend fun createJwtWalletAttestation(keyAttestation: KeyAttestation): String {
            // Implements this draft:
            // https://datatracker.ietf.org/doc/html/draft-ietf-oauth-attestation-based-client-auth-04

            val signatureAlgorithm = localAttestationPrivateKey.curve.defaultSigningAlgorithmFullySpecified
            val head = buildJsonObject {
                put("typ", "oauth-client-attestation+jwt")
                put("alg", signatureAlgorithm.joseAlgorithmIdentifier)
                put("x5c", buildJsonArray {
                    add(localAttestationCertificate.encodedCertificate.encodeBase64())
                })
            }.toString().encodeToByteArray().toBase64Url()

            val now = Clock.System.now()
            val notBefore = now - 1.seconds
            // Expiration here is only for the wallet assertion to be presented to the issuing server
            // in the given timeframe (which happens without user interaction). It does not imply that
            // the key becomes invalid at that point in time.
            val expiration = now + 5.minutes
            val payload = buildJsonObject {
                put("iss", localClientId)
                put("sub", testClientPreferences.clientId)
                put("exp", expiration.epochSeconds)
                put("cnf", buildJsonObject {
                    put("jwk", keyAttestation.publicKey.toJwk(
                        buildJsonObject {
                            put("kid", JsonPrimitive(testClientPreferences.clientId))
                        }
                    ))
                })
                put("nbf", notBefore.epochSeconds)
                put("iat", now.epochSeconds)
                put("wallet_name", "Multipaz Wallet")
                put("wallet_link", "https://multipaz.org")
            }.toString().encodeToByteArray().toBase64Url()

            val message = "$head.$payload"
            val sig = Crypto.sign(
                key = localAttestationPrivateKey,
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
            // Generate key attestation
            val keyList = keyAttestations.map { it.publicKey }

            val alg = localAttestationPrivateKey.curve.defaultSigningAlgorithm.joseAlgorithmIdentifier
            val head = buildJsonObject {
                put("typ", "keyattestation+jwt")
                put("alg", alg)
                put("x5c", buildJsonArray {
                    add(localAttestationCertificate.encodedCertificate.encodeBase64())
                })
            }.toString().encodeToByteArray().toBase64Url()

            val now = Clock.System.now()
            val notBefore = now - 1.seconds
            val expiration = now + 5.minutes
            val payload = buildJsonObject {
                put("iss", localClientId)
                put("attested_keys", JsonArray(keyList.map { it.toJwk() }))
                put("nonce", challenge)
                put("nbf", notBefore.epochSeconds)
                put("exp", expiration.epochSeconds)
                put("iat", now.epochSeconds)
            }.toString().encodeToByteArray().toBase64Url()

            val message = "$head.$payload"
            val sig = Crypto.sign(
                key = localAttestationPrivateKey,
                signatureAlgorithm = localAttestationPrivateKey.curve.defaultSigningAlgorithm,
                message = message.encodeToByteArray()
            )
            val signature = sig.toCoseEncoded().toBase64Url()

            return "$message.$signature"
        }

        private val localAttestationCertificate = X509Cert.fromPem("""
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

        private val localAttestationPrivateKey = EcPrivateKey.fromPem("""
            -----BEGIN PRIVATE KEY-----
            ME4CAQAwEAYHKoZIzj0CAQYFK4EEACIENzA1AgEBBDBn7jeRC9u9de3kOkrt9lLT
            Pvd1hflNq1FCgs7D+qbbwz1BQa4XXU0SjsV+R1GjnAY=
            -----END PRIVATE KEY-----
            """.trimIndent(),
            localAttestationCertificate.ecPublicKey
        )

        private val localClientId =
            localAttestationCertificate.subject.components[OID.COMMON_NAME.oid]?.value
                ?: throw IllegalStateException("No common name (CN) in certificate's subject")
    }

    inner class TestBackendEnvironment(val httpClient: HttpClient): BackendEnvironment {
        override fun <T : Any> getInterface(clazz: KClass<T>): T {
            return clazz.cast(when (clazz) {
                HttpClient::class -> httpClient
                OpenID4VCIBackend::class -> TestBackend
                SecureAreaProvider::class -> secureAreaProvider
                else -> throw IllegalArgumentException("no such class available: ${clazz.simpleName}")
            })
        }
    }

    companion object {
        const val OFFER = "openid-credential-offer://?credential_offer=%7B%22credential_issuer%22%3A%22http%3A%2F%2Flocalhost%22%2C%22credential_configuration_ids%22%3A%5B%22mDL%22%5D%2C%22grants%22%3A%7B%22authorization_code%22%3A%7B%7D%7D%7D"

        val testClientPreferences = OpenID4VCIClientPreferences(
            clientId = "urn:uuid:418745b8-78a3-4810-88df-7898aff3ffb4",
            redirectUrl = "https://redirect.example.com",
            locales = listOf("en-US"),
            signingAlgorithms = listOf(Algorithm.ESP256)
        )

    }
}