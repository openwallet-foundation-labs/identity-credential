package com.android.identity.cloudsa

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.cloud.Server
import com.android.identity.securearea.cloud.SimplePassphraseFailureEnforcer
import com.android.identity.securearea.software.SoftwareCreateKeySettings
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity.util.fromHex
import com.android.identity.util.toHex
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Security
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Simple Servlet-based version of the Cloud Secure Area server.
 *
 * This is driven by a configuration file stored in /tmp/cloudsa-servlet-settings.json which
 * will be generated with defaults if it doesn't exist.
 *
 * Access controls for the Android clients can be controlled via that file.
 */
class CloudSecureAreaServlet : HttpServlet() {
    companion object {
        private const val TAG = "CloudSecureAreaServlet"
        private const val serialVersionUID = 1L

        private const val CONFIG_FILE_PATH = "/tmp/cloudsa-servlet-settings.json"
    }

    private lateinit var server: Server
    private lateinit var settings: Settings

    @Serializable
    data class Settings(
        // User configurable
        val requireGmsAttestation: Boolean,
        val requireVerifiedBootGreen: Boolean,
        val requireAppSignatureCertificateDigests: List<String>,
        val rekeyingIntervalSeconds: Int,
        val lockoutNumFailedAttempts: Int,
        val lockoutDurationSeconds: Int,

        // Hex-encoded string of KeyMaterial.toCbor()
        val keyMaterial: String
    )

    data class KeyMaterial(
        val serverSecureAreaBoundKey: ByteArray,
        val attestationKey: EcPrivateKey,
        val attestationKeyCertificates: CertificateChain,
        val attestationKeySignatureAlgorithm: Algorithm,
        val attestationKeyIssuer: String,
        val cloudBindingKey: EcPrivateKey,
        val cloudBindingKeyCertificates: CertificateChain,
        val cloudBindingKeySignatureAlgorithm: Algorithm,
        val cloudBindingKeyIssuer: String
    ) {
        fun toCbor() = Cbor.encode(
            CborArray.builder()
                .add(serverSecureAreaBoundKey)
                .add(attestationKey.toCoseKey().toDataItem)
                .add(attestationKeyCertificates.dataItem)
                .add(attestationKeySignatureAlgorithm.coseAlgorithmIdentifier)
                .add(attestationKeyIssuer)
                .add(cloudBindingKey.toCoseKey().toDataItem)
                .add(cloudBindingKeyCertificates.dataItem)
                .add(cloudBindingKeySignatureAlgorithm.coseAlgorithmIdentifier)
                .add(cloudBindingKeyIssuer)
                .end().build()
        )

        companion object {
            fun fromCbor(encodedCbor: ByteArray): KeyMaterial {
                val array = Cbor.decode(encodedCbor).asArray
                return KeyMaterial(
                    array[0].asBstr,
                    array[1].asCoseKey.ecPrivateKey,
                    array[2].asCertificateChain,
                    Algorithm.fromInt(array[3].asNumber.toInt()),
                    array[4].asTstr,
                    array[5].asCoseKey.ecPrivateKey,
                    array[6].asCertificateChain,
                    Algorithm.fromInt(array[7].asNumber.toInt()),
                    array[8].asTstr,
                )
            }
        }
    }

    fun createKeyMaterial(): KeyMaterial {
        val serverSecureAreaBoundKey = Random.Default.nextBytes(32)

        val now = Clock.System.now()
        val storageEngine = EphemeralStorageEngine()
        val secureArea = SoftwareSecureArea(storageEngine)
        val validFrom = now
        val validUntil = now.plus(DateTimePeriod(years = 10), TimeZone.currentSystemDefault())

        secureArea.createKey(
            "AttestationRoot",
            SoftwareCreateKeySettings.Builder("".toByteArray())
                .setKeyPurposes(setOf(KeyPurpose.SIGN))
                .setSubject("CN=Cloud Secure Area Attestation Root")
                .setValidityPeriod(
                    Timestamp.ofEpochMilli(validFrom.toEpochMilliseconds()),
                    Timestamp.ofEpochMilli(validUntil.toEpochMilliseconds()),
                )
                .build()
        )
        secureArea.getKeyInfo("AttestationRoot")
        val attestationKey = secureArea.getPrivateKey("AttestationRoot", null)
        val attestationKeyCertificates = secureArea.getKeyInfo("AttestationRoot").attestation
        val attestationKeySignatureAlgorithm = Algorithm.ES256
        val attestationKeyIssuer = "CN=Cloud Secure Area Attestation Root"

        secureArea.createKey(
            "CloudBindingKeyAttestationRoot",
            SoftwareCreateKeySettings.Builder("".toByteArray())
                .setKeyPurposes(setOf(KeyPurpose.SIGN))
                .setSubject("CN=Cloud Secure Area Cloud Binding Key Attestation Root")
                .setValidityPeriod(
                    Timestamp.ofEpochMilli(validFrom.toEpochMilliseconds()),
                    Timestamp.ofEpochMilli(validUntil.toEpochMilliseconds()),
                )
                .build()
        )
        val cloudBindingKeyAttestationKey =
            secureArea.getPrivateKey("CloudBindingKeyAttestationRoot", null)
        val cloudBindingKeyAttestationCertificates =
            secureArea.getKeyInfo("CloudBindingKeyAttestationRoot").attestation
        val cloudBindingKeySignatureAlgorithm = Algorithm.ES256
        val cloudBindingKeyIssuer = "CN=Cloud Secure Area Cloud Binding Key Attestation Root"

        return KeyMaterial(
            serverSecureAreaBoundKey,
            attestationKey,
            attestationKeyCertificates,
            attestationKeySignatureAlgorithm,
            attestationKeyIssuer,
            cloudBindingKeyAttestationKey,
            cloudBindingKeyAttestationCertificates,
            cloudBindingKeySignatureAlgorithm,
            cloudBindingKeyIssuer
        )
    }

    fun createSettings(): Settings {
        return Settings(
            requireGmsAttestation = true,
            requireVerifiedBootGreen = true,
            requireAppSignatureCertificateDigests = listOf(),
            rekeyingIntervalSeconds = 600,
            lockoutNumFailedAttempts = 3,
            lockoutDurationSeconds = 60,
            keyMaterial = createKeyMaterial().toCbor().toHex
        )
    }

    override fun init() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        val settingsFile = File(CONFIG_FILE_PATH)
        if (!settingsFile.exists()) {
            val defaultSettings = createSettings()
            settingsFile.writeText(
                Json { prettyPrint = true }.encodeToString(defaultSettings)
            )
            Logger.i(TAG, "Saving config file to ${settingsFile.path} with defaults")
        }
        settings = Json.decodeFromString(File(CONFIG_FILE_PATH).readText())
        Logger.i(TAG, "Read config file at ${settingsFile.path}")
        val keyMaterial = KeyMaterial.fromCbor(settings.keyMaterial.fromHex)

        server = Server(
            keyMaterial.serverSecureAreaBoundKey,
            keyMaterial.attestationKey,
            keyMaterial.attestationKeySignatureAlgorithm,
            keyMaterial.attestationKeyIssuer,
            keyMaterial.attestationKeyCertificates,
            keyMaterial.cloudBindingKey,
            keyMaterial.cloudBindingKeySignatureAlgorithm,
            keyMaterial.cloudBindingKeyIssuer,
            keyMaterial.cloudBindingKeyCertificates,
            settings.rekeyingIntervalSeconds,
            settings.requireGmsAttestation,
            settings.requireVerifiedBootGreen,
            settings.requireAppSignatureCertificateDigests.map { hex -> hex.fromHex },
            SimplePassphraseFailureEnforcer(
                lockoutNumFailedAttempts = settings.lockoutNumFailedAttempts,
                lockoutDuration = settings.lockoutDurationSeconds.seconds
            )
        )
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestLength = req.contentLength
        val requestData = req.inputStream.readNBytes(requestLength)
        val remoteHost = getRemoteHost(req)
        val server = server
        val (first, second) = server.handleCommand(requestData, remoteHost)
        resp.status = first
        if (first == HttpServletResponse.SC_OK) {
            resp.contentType = "application/cbor"
        }
        resp.outputStream.write(second)
    }

    private fun getRemoteHost(req: HttpServletRequest): String {
        var remoteHost = req.remoteHost
        val forwardedFor = req.getHeader("X-Forwarded-For")
        if (forwardedFor != null) {
            remoteHost = forwardedFor
        }
        return remoteHost
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        Logger.d(TAG, "GET from " + getRemoteHost(req))
        val sb = StringBuilder()
        sb.append(
            """<!DOCTYPE html>
<html>
<head>
  <title>Cloud Secure Area - Server Reference Implementation</title>
"""
        )
        sb.append(
            """
    </head>
    <body>
    <h1>Cloud Secure Area - Server Reference Implementation</h1>
    <p><b>Note: This reference implementation is not production quality. Use at your own risk.</b></p>
    
    """.trimIndent()
        )
        sb.append("<h2>Settings</h2>")
        sb.append("<p><b>E2EE re-keying interval:</b> ${settings.rekeyingIntervalSeconds} seconds")
        sb.append("<p><b>Require Verified Boot Green:</b> ${settings.requireVerifiedBootGreen}")
        sb.append("<p><b>Require GMS attestation keys:</b> ${settings.requireGmsAttestation}")
        sb.append("<p><b>Passphrase failure lockout attempts:</b> ${settings.lockoutNumFailedAttempts}")
        sb.append("<p><b>Passphrase failure lockout duration:</b> ${settings.lockoutDurationSeconds} seconds")
        sb.append("<p><b>Android App pinning:</b> ")
        if (settings.requireAppSignatureCertificateDigests.isEmpty()) {
            sb.append("None")
        } else {
            sb.append("Pinned to app with certificate digests: ")
            for (hexDigest in settings.requireAppSignatureCertificateDigests) {
                sb.append("<i>$hexDigest</i> ")
            }
        }
        sb.append("<p><i>These settings can be edited in $CONFIG_FILE_PATH</i>")
        sb.append("<h2>Attestation Root</h2>")
        val keyMaterial = KeyMaterial.fromCbor(settings.keyMaterial.fromHex)
        for (certificate in keyMaterial.attestationKeyCertificates.certificates) {
            sb.append("<h3>Certificate</h3>")
            sb.append("<pre>")
            sb.append(certificate.javaX509Certificate)
            sb.append("</pre>")
        }
        sb.append("<h2>Cloud Binding Key Attestation Root</h2>")
        for (certificate in keyMaterial.cloudBindingKeyCertificates.certificates) {
            sb.append("<h3>Certificate</h3>")
            sb.append("<pre>")
            sb.append(certificate.javaX509Certificate)
            sb.append("</pre>")
        }
        sb.append(
            """
    </body>
    </html>
    """.trimIndent()
        )
        resp.contentType = "text/html"
        resp.outputStream.write(sb.toString().toByteArray())
    }
}