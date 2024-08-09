package com.android.identity.wallet.server

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.create
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.Storage
import com.android.identity.issuance.WalletServerSettings
import com.android.identity.securearea.cloud.CloudSecureAreaServer
import com.android.identity.securearea.cloud.SimplePassphraseFailureEnforcer
import com.android.identity.util.Logger
import com.android.identity.util.fromHex
import jakarta.servlet.ServletConfig
import kotlinx.coroutines.runBlocking
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Simple Servlet-based version of the Cloud Secure Area server.
 *
 * This is using the configuration and storage interfaces from [ServerEnvironment].
 */
class CloudSecureAreaServlet : HttpServlet() {

    data class KeyMaterial(
        val serverSecureAreaBoundKey: ByteArray,
        val attestationKey: EcPrivateKey,
        val attestationKeyCertificates: X509CertChain,
        val attestationKeySignatureAlgorithm: Algorithm,
        val attestationKeyIssuer: String,
        val cloudBindingKey: EcPrivateKey,
        val cloudBindingKeyCertificates: X509CertChain,
        val cloudBindingKeySignatureAlgorithm: Algorithm,
        val cloudBindingKeyIssuer: String
    ) {
        fun toCbor() = Cbor.encode(
            CborArray.builder()
                .add(serverSecureAreaBoundKey)
                .add(attestationKey.toCoseKey().toDataItem())
                .add(attestationKeyCertificates.toDataItem())
                .add(attestationKeySignatureAlgorithm.coseAlgorithmIdentifier)
                .add(attestationKeyIssuer)
                .add(cloudBindingKey.toCoseKey().toDataItem())
                .add(cloudBindingKeyCertificates.toDataItem())
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
                    array[2].asX509CertChain,
                    Algorithm.fromInt(array[3].asNumber.toInt()),
                    array[4].asTstr,
                    array[5].asCoseKey.ecPrivateKey,
                    array[6].asX509CertChain,
                    Algorithm.fromInt(array[7].asNumber.toInt()),
                    array[8].asTstr,
                )
            }

            fun createKeyMaterial(): KeyMaterial {
                val serverSecureAreaBoundKey = Random.Default.nextBytes(32)

                val now = Clock.System.now()
                val validFrom = now
                val validUntil = now.plus(DateTimePeriod(years = 10), TimeZone.currentSystemDefault())

                // Create Attestation Root w/ self-signed certificate.
                val attestationKey = Crypto.createEcPrivateKey(EcCurve.P256)
                val attestationKeySignatureAlgorithm = Algorithm.ES256
                val attestationKeySubject = "CN=Cloud Secure Area Attestation Root"
                val attestationKeyCertificate = X509Cert.create(
                    attestationKey.publicKey,
                    attestationKey,
                    null,
                    attestationKeySignatureAlgorithm,
                    "1",
                    attestationKeySubject,
                    attestationKeySubject,
                    validFrom,
                    validUntil,
                    setOf(),
                    listOf()
                )

                // Create Cloud Binding Key Attestation Root w/ self-signed certificate.
                val cloudBindingKeyAttestationKey = Crypto.createEcPrivateKey(EcCurve.P256)
                val cloudBindingKeySignatureAlgorithm = Algorithm.ES256
                val cloudBindingKeySubject = "CN=Cloud Secure Area Cloud Binding Key Attestation Root"
                val cloudBindingKeyAttestationCertificate = X509Cert.create(
                    cloudBindingKeyAttestationKey.publicKey,
                    cloudBindingKeyAttestationKey,
                    null,
                    cloudBindingKeySignatureAlgorithm,
                    "1",
                    cloudBindingKeySubject,
                    cloudBindingKeySubject,
                    validFrom,
                    validUntil,
                    setOf(),
                    listOf()
                )

                return KeyMaterial(
                    serverSecureAreaBoundKey,
                    attestationKey,
                    X509CertChain(listOf(attestationKeyCertificate)),
                    attestationKeySignatureAlgorithm,
                    attestationKeySubject,
                    cloudBindingKeyAttestationKey,
                    X509CertChain(listOf(cloudBindingKeyAttestationCertificate)),
                    cloudBindingKeySignatureAlgorithm,
                    cloudBindingKeySubject
                )
            }

        }
    }

    companion object {
        private const val TAG = "CloudSecureAreaServlet"

        private lateinit var serverEnvironment: ServerEnvironment

        @Synchronized
        private fun initialize(servletConfig: ServletConfig) {
            if (this::serverEnvironment.isInitialized) {
                return
            }

            serverEnvironment = ServerEnvironment(servletConfig)


        }

        private val cloudSecureArea: CloudSecureAreaServer by lazy {
            createCloudSecureArea()
        }

        private val keyMaterial: KeyMaterial
            get() {
                val storage = serverEnvironment.getInterface(Storage::class)!!
                val keyMaterialBlob = runBlocking {
                    storage.get("RootState", "", "cloudSecureAreaKeyMaterial")?.toByteArray()
                        ?: let {
                            val blob = KeyMaterial.createKeyMaterial().toCbor()
                            storage.insert(
                                "RootState",
                                "",
                                ByteString(blob),
                                "cloudSecureAreaKeyMaterial")
                            blob
                        }
                }
                return KeyMaterial.fromCbor(keyMaterialBlob)
            }

        private fun createCloudSecureArea(): CloudSecureAreaServer {
            Security.addProvider(BouncyCastleProvider())

            val settings = WalletServerSettings(serverEnvironment.getInterface(Configuration::class)!!)

            return CloudSecureAreaServer(
                keyMaterial.serverSecureAreaBoundKey,
                keyMaterial.attestationKey,
                keyMaterial.attestationKeySignatureAlgorithm,
                keyMaterial.attestationKeyIssuer,
                keyMaterial.attestationKeyCertificates,
                keyMaterial.cloudBindingKey,
                keyMaterial.cloudBindingKeySignatureAlgorithm,
                keyMaterial.cloudBindingKeyIssuer,
                keyMaterial.cloudBindingKeyCertificates,
                settings.cloudSecureAreaRekeyingIntervalSeconds,
                settings.androidRequireGmsAttestation,
                settings.androidRequireVerifiedBootGreen,
                settings.androidRequireAppSignatureCertificateDigests.map { hex -> hex.fromHex() },
                SimplePassphraseFailureEnforcer(
                    settings.cloudSecureAreaLockoutNumFailedAttempts,
                    settings.cloudSecureAreaLockoutDurationSeconds.seconds
                )
            )
        }
    }

    @Override
    override fun init() {
        super.init()

        Security.addProvider(BouncyCastleProvider())

        initialize(servletConfig)
    }

    private fun getRemoteHost(req: HttpServletRequest): String {
        var remoteHost = req.remoteHost
        val forwardedFor = req.getHeader("X-Forwarded-For")
        if (forwardedFor != null) {
            remoteHost = forwardedFor
        }
        return remoteHost
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestLength = req.contentLength
        val requestData = req.inputStream.readNBytes(requestLength)
        val remoteHost = getRemoteHost(req)
        val (first, second) = cloudSecureArea.handleCommand(requestData, remoteHost)
        resp.status = first
        if (first == HttpServletResponse.SC_OK) {
            resp.contentType = "application/cbor"
        }
        resp.outputStream.write(second)
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
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

        sb.append("<h2>Attestation Root</h2>")
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