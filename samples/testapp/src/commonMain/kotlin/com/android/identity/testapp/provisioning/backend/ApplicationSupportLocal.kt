package com.android.identity.testapp.provisioning.backend

import com.android.identity.testapp.provisioning.openid4vci.toJson
import com.android.identity.testapp.provisioning.openid4vci.validateDeviceAssertionBindingKeys
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.asn1.OID
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X509Cert
import org.multipaz.device.AssertionDPoPKey
import org.multipaz.device.DeviceAssertion
import org.multipaz.device.DeviceAttestationAndroid
import org.multipaz.provisioning.ApplicationSupport
import org.multipaz.provisioning.LandingUrlNotification
import org.multipaz.provisioning.LandingUrlUnknownException
import org.multipaz.provisioning.ProvisioningBackendSettings
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Configuration
import org.multipaz.rpc.backend.getTable
import org.multipaz.rpc.handler.RpcAuthInspectorAssertion
import org.multipaz.securearea.KeyAttestation
import org.multipaz.storage.NoRecordStorageException
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import org.multipaz.util.validateAndroidKeyAttestation
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * [ApplicationSupport] implementation suitable for running in-app.
 *
 * NOTE: JWT assertion and attestations created on-device are not secure and suitable for testing
 * or demo purposes only. This is because the private key used to sign them is not secret.
 */
@RpcState
@CborSerializable
class ApplicationSupportLocal(
    val clientId: String
): ApplicationSupport {
    companion object {
        const val TAG = "ApplicationSupportLocal"

        // This must match what is specified in application's AndroidManifest.xml
        // intent filter for app links for Android or in XCode universal link
        // properties for iOS.
        const val APP_LINK_SERVER = "https://apps.multipaz.org"
        const val APP_LINK_BASE_URL = "$APP_LINK_SERVER/landing/"

        private val urlTableSpec = StorageTableSpec(
            name = "LandingUrls",
            supportPartitions = false,
            supportExpiration = true
        )

        private val localClientAssertionCertificate = X509Cert.fromPem("""
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

        private val localClientAssertionPrivateKey = EcPrivateKey.fromPem("""
            -----BEGIN PRIVATE KEY-----
            ME4CAQAwEAYHKoZIzj0CAQYFK4EEACIENzA1AgEBBDBn7jeRC9u9de3kOkrt9lLT
            Pvd1hflNq1FCgs7D+qbbwz1BQa4XXU0SjsV+R1GjnAY=
            -----END PRIVATE KEY-----
            """.trimIndent(),
            localClientAssertionCertificate.ecPublicKey
        )

        // NB: this identifies the test app (not a specific instance of the test app)
        private val localClientId =
            localClientAssertionCertificate.subject.components[OID.COMMON_NAME.oid]?.value
                ?: throw IllegalStateException("No common name (CN) in certificate's subject")
    }

    override suspend fun createLandingUrl(): String {
        val table = BackendEnvironment.getTable(urlTableSpec)
        val landingId = table.insert(
            key = null,
            data = ByteString(),
            expiration = Clock.System.now() + 5.hours
        )
        return APP_LINK_BASE_URL + landingId
    }

    /**
     * Should be called by the app when an app link / universal link is resolved.
     */
    suspend fun onLandingUrlNavigated(url: String) {
        val index = url.indexOf('?')
        if (index < 0) {
            Logger.e(TAG, "Invalid landing url: '$url'")
            return
        }
        val landingUrl = url.substring(0, index)
        if (!landingUrl.startsWith(APP_LINK_BASE_URL)) {
            Logger.e(TAG, "Not a landing url: '$url'")
            return
        }
        val id = landingUrl.substring(APP_LINK_BASE_URL.length)
        val query = url.substring(index + 1)
        val table = BackendEnvironment.getTable(urlTableSpec)
        try {
            table.update(id, query.encodeToByteString())
        } catch (err: NoRecordStorageException) {
            Logger.e(TAG, "No record for landing url: '$url'")
            return
        }
        Logger.e(TAG, "Emitting notification for landing url: '$url'")
        emit(LandingUrlNotification(landingUrl))
    }

    override suspend fun getLandingUrlStatus(landingUrl: String): String? {
        if (!landingUrl.startsWith(APP_LINK_BASE_URL)) {
            throw IllegalArgumentException("Not a landing url: '$landingUrl'")
        }
        val id = landingUrl.substring(APP_LINK_BASE_URL.length)
        val table = BackendEnvironment.getTable(urlTableSpec)
        val record = table.get(id)
            ?: throw LandingUrlUnknownException("Unknown landing url: '$landingUrl'")
        if (record.size == 0) {
            // Exists, but was not resolved
            return null
        }
        return record.decodeToString()
    }

    override suspend fun getClientAssertionId(targetIssuanceUrl: String): String {
        return localClientId
    }

    override suspend fun createJwtClientAssertion(
        keyAttestation: KeyAttestation,
        deviceAssertion: DeviceAssertion
    ): String {
        // Do all the checks locally that we would have to do on the server to avoid surprises.
        val deviceAttestation = RpcAuthInspectorAssertion.getClientDeviceAttestation(clientId)!!
        deviceAttestation.validateAssertion(deviceAssertion)
        val assertion = deviceAssertion.assertion as AssertionDPoPKey
        if (deviceAttestation is DeviceAttestationAndroid) {
            val settings = ProvisioningBackendSettings(BackendEnvironment.getInterface(Configuration::class)!!)
            val certChain = keyAttestation.certChain!!
            check(assertion.publicKey == certChain.certificates.first().ecPublicKey)
            validateAndroidKeyAttestation(
                certChain,
                null,  // no challenge check needed
                settings.androidRequireGmsAttestation,
                settings.androidRequireVerifiedBootGreen,
                settings.androidRequireAppSignatureCertificateDigests
            )
        }
        check(keyAttestation.certChain!!.certificates[0].ecPublicKey == keyAttestation.publicKey)

        // Now, generate the client assertion.
        val alg = localClientAssertionPrivateKey.curve.defaultSigningAlgorithm.joseAlgorithmIdentifier
        val head = buildJsonObject {
            put("typ", "JWT")
            put("alg", alg)
            put("jwk", localClientAssertionCertificate.ecPublicKey.toJson(null))
        }.toString().encodeToByteArray().toBase64Url()

        val now = Clock.System.now()
        val notBefore = now - 1.seconds
        // Expiration here is only for the client assertion to be presented to the issuing server
        // in the given timeframe (which happens without user interaction). It does not imply that
        // the key becomes invalid at that point in time.
        val expiration = now + 5.minutes
        val payload = buildJsonObject {
            put("iss", localClientId)
            put("sub", localClientId) // RFC 7523 Section 3, item 2.B
            put("cnf", buildJsonObject {
                put("jwk", keyAttestation.publicKey.toJson(clientId))
            })
            put("nbf", notBefore.epochSeconds)
            put("exp", expiration.epochSeconds)
            put("iat", now.epochSeconds)
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

    override suspend fun createJwtKeyAttestation(
        keyAttestations: List<KeyAttestation>,
        keysAssertion: DeviceAssertion
    ): String {
        // Do all the checks locally that we would have to do on the server to avoid surprises.
        val deviceAttestation = RpcAuthInspectorAssertion.getClientDeviceAttestation(clientId)!!
        val assertion = validateDeviceAssertionBindingKeys(
            deviceAttestation = deviceAttestation,
            keyAttestations = keyAttestations,
            deviceAssertion = keysAssertion,
            nonce = null,  // no check
        )

        // Generate key attestation
        val nonce = assertion.nonce.decodeToString()
        val keyList = assertion.publicKeys

        val alg = localClientAssertionPrivateKey.curve.defaultSigningAlgorithm.joseAlgorithmIdentifier
        val head = buildJsonObject {
            put("typ", "keyattestation+jwt")
            put("alg", alg)
            put("jwk", localClientAssertionCertificate.ecPublicKey.toJson(null))  // TODO: use x5c instead here?
        }.toString().encodeToByteArray().toBase64Url()

        val now = Clock.System.now()
        val notBefore = now - 1.seconds
        val expiration = now + 5.minutes
        val payload = buildJsonObject {
            put("iss", localClientId)
            put("attested_keys", JsonArray(keyList.map { it.toJson(null) }))
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

    override suspend fun collect(collector: FlowCollector<LandingUrlNotification>) {
        collectImpl(collector)
    }

    override suspend fun dispose() {
        disposeImpl()
    }
}