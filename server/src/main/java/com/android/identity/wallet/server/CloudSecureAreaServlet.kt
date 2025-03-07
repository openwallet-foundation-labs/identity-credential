package org.multipaz.wallet.server

import org.multipaz.asn1.ASN1
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.flow.handler.FlowNotifications
import org.multipaz.flow.server.Configuration
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.flow.server.Resources
import org.multipaz.flow.server.getTable
import org.multipaz.issuance.WalletServerSettings
import org.multipaz.securearea.cloud.CloudSecureAreaServer
import org.multipaz.securearea.cloud.SimplePassphraseFailureEnforcer
import org.multipaz.server.BaseHttpServlet
import org.multipaz.storage.StorageTableSpec
import kotlinx.coroutines.runBlocking
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.Boolean
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Simple Servlet-based version of the Cloud Secure Area server.
 *
 * This is using the configuration and storage interfaces from [ServerEnvironment].
 */
class CloudSecureAreaServlet : BaseHttpServlet() {

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

            fun createKeyMaterial(serverEnvironment: FlowEnvironment): KeyMaterial {
                val serverSecureAreaBoundKey = Random.Default.nextBytes(32)

                val now = Clock.System.now()
                val validFrom = now
                val validUntil = now.plus(DateTimePeriod(years = 10), TimeZone.currentSystemDefault())

                // Load attestation root
                val configuration = serverEnvironment.getInterface(Configuration::class)!!
                val resources = serverEnvironment.getInterface(Resources::class)!!
                val certificateName = configuration.getValue("csa.certificate")
                    ?: "cloud_secure_area/certificate.pem"
                val rootCertificate = X509Cert.fromPem(resources.getStringResource(certificateName)!!)
                val privateKeyName = configuration.getValue("csa.privateKey")
                    ?: "cloud_secure_area/private_key.pem"
                val rootPrivateKey = EcPrivateKey.fromPem(
                    resources.getStringResource(privateKeyName)!!,
                    rootCertificate.ecPublicKey
                )

                // Create instance-specific intermediate certificate.
                val attestationKey = Crypto.createEcPrivateKey(EcCurve.P256)
                val attestationKeySignatureAlgorithm = Algorithm.ES256
                val attestationKeySubject = "CN=Cloud Secure Area Attestation Root"
                val attestationKeyCertificate = X509Cert.Builder(
                    publicKey = attestationKey.publicKey,
                    signingKey = rootPrivateKey,
                    signatureAlgorithm = attestationKeySignatureAlgorithm,
                    serialNumber = ASN1Integer(1L),
                    subject = X500Name.fromName(attestationKeySubject),
                    issuer = rootCertificate.subject,
                    validFrom = validFrom,
                    validUntil = validUntil
                )
                    .includeSubjectKeyIdentifier()
                    .setAuthorityKeyIdentifierToCertificate(rootCertificate)
                    .setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
                    .setBasicConstraints(ca = true, pathLenConstraint = null)
                    .build()

                // Create Cloud Binding Key Attestation Root w/ self-signed certificate.
                val cloudBindingKeyAttestationKey = Crypto.createEcPrivateKey(EcCurve.P256)
                val cloudBindingKeySignatureAlgorithm = Algorithm.ES256
                val cloudBindingKeySubject = "CN=Cloud Secure Area Cloud Binding Key Attestation Root"
                val cloudBindingKeyAttestationCertificate = X509Cert.Builder(
                    publicKey = cloudBindingKeyAttestationKey.publicKey,
                    signingKey = cloudBindingKeyAttestationKey,
                    signatureAlgorithm = cloudBindingKeySignatureAlgorithm,
                    serialNumber = ASN1Integer(1L),
                    subject = X500Name.fromName(cloudBindingKeySubject),
                    issuer = X500Name.fromName(cloudBindingKeySubject),
                    validFrom = validFrom,
                    validUntil = validUntil
                )
                    .includeSubjectKeyIdentifier()
                    .setAuthorityKeyIdentifierToCertificate(rootCertificate)
                    .setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
                    .setBasicConstraints(ca = true, pathLenConstraint = null)
                    .build()

                return KeyMaterial(
                    serverSecureAreaBoundKey,
                    attestationKey,
                    X509CertChain(listOf(attestationKeyCertificate, rootCertificate)),
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

        private lateinit var cloudSecureArea: CloudSecureAreaServer
        private lateinit var keyMaterial: KeyMaterial

        private val cloudRootStateTableSpec = StorageTableSpec(
            name = "CloudSecureAreaRootState",
            supportExpiration = false,
            supportPartitions = false
        )

        private fun createKeyMaterial(serverEnvironment: FlowEnvironment): KeyMaterial {
            val keyMaterialBlob = runBlocking {
                val storage = serverEnvironment.getTable(cloudRootStateTableSpec)
                storage.get("cloudSecureAreaKeyMaterial")?.toByteArray()
                    ?: let {
                        val blob = KeyMaterial.createKeyMaterial(serverEnvironment).toCbor()
                        storage.insert(key = "cloudSecureAreaKeyMaterial", data = ByteString(blob))
                        blob
                    }
            }
            return KeyMaterial.fromCbor(keyMaterialBlob)
        }

        private fun createCloudSecureArea(serverEnvironment: FlowEnvironment): CloudSecureAreaServer {
            Security.addProvider(BouncyCastleProvider())
            val settings = WalletServerSettings(serverEnvironment.getInterface(Configuration::class)!!)
            return CloudSecureAreaServer(
                serverSecureAreaBoundKey = keyMaterial.serverSecureAreaBoundKey,
                attestationKey = keyMaterial.attestationKey,
                attestationKeySignatureAlgorithm = keyMaterial.attestationKeySignatureAlgorithm,
                attestationKeyIssuer = keyMaterial.attestationKeyIssuer,
                attestationKeyCertification = keyMaterial.attestationKeyCertificates,
                cloudRootAttestationKey = keyMaterial.cloudBindingKey,
                cloudRootAttestationKeySignatureAlgorithm = keyMaterial.cloudBindingKeySignatureAlgorithm,
                cloudRootAttestationKeyIssuer = keyMaterial.cloudBindingKeyIssuer,
                cloudRootAttestationKeyCertification = keyMaterial.cloudBindingKeyCertificates,
                e2eeKeyLimitSeconds = settings.cloudSecureAreaRekeyingIntervalSeconds,
                iosReleaseBuild = settings.iosReleaseBuild,
                iosAppIdentifier = settings.iosAppIdentifier,
                androidGmsAttestation = settings.androidRequireGmsAttestation,
                androidVerifiedBootGreen = settings.androidRequireVerifiedBootGreen,
                androidAppSignatureCertificateDigests = settings.androidRequireAppSignatureCertificateDigests,
                passphraseFailureEnforcer = SimplePassphraseFailureEnforcer(
                    settings.cloudSecureAreaLockoutNumFailedAttempts,
                    settings.cloudSecureAreaLockoutDurationSeconds.seconds
                )
            )
        }
    }

    override fun initializeEnvironment(env: FlowEnvironment): FlowNotifications? {
        keyMaterial = createKeyMaterial(env)
        cloudSecureArea = createCloudSecureArea(env)
        return null
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val requestLength = req.contentLength
        val requestData = req.inputStream.readNBytes(requestLength)
        val remoteHost = getRemoteHost(req)
        val (first, second) = runBlocking {
            cloudSecureArea.handleCommand(requestData, remoteHost)
        }
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
            sb.append(ASN1.print(ASN1.decode(certificate.tbsCertificate)!!))
            sb.append("</pre>")
        }
        sb.append("<h2>Cloud Binding Key Attestation Root</h2>")
        for (certificate in keyMaterial.cloudBindingKeyCertificates.certificates) {
            sb.append("<h3>Certificate</h3>")
            sb.append("<pre>")
            sb.append(ASN1.print(ASN1.decode(certificate.tbsCertificate)!!))
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