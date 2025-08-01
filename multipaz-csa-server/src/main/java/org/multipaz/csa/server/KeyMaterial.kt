package org.multipaz.csa.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.io.bytestring.ByteString
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.buildCborArray
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.getTable
import org.multipaz.server.ServerIdentity
import org.multipaz.server.getServerIdentity
import org.multipaz.storage.StorageTableSpec
import kotlin.random.Random

/**
 * Various keys used by the Cloud Secure Area.
 *
 * This is initialized on the first server start and saved in the database. On subsequent server
 * runs, it is read from the database.
 */
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
        buildCborArray {
            add(serverSecureAreaBoundKey)
            add(attestationKey.toCoseKey().toDataItem())
            add(attestationKeyCertificates.toDataItem())
            add(attestationKeySignatureAlgorithm.coseAlgorithmIdentifier!!)
            add(attestationKeyIssuer)
            add(cloudBindingKey.toCoseKey().toDataItem())
            add(cloudBindingKeyCertificates.toDataItem())
            add(cloudBindingKeySignatureAlgorithm.coseAlgorithmIdentifier!!)
            add(cloudBindingKeyIssuer)
        }
    )

    companion object {
        fun fromCbor(encodedCbor: ByteArray): KeyMaterial {
            val array = Cbor.decode(encodedCbor).asArray
            return KeyMaterial(
                array[0].asBstr,
                array[1].asCoseKey.ecPrivateKey,
                array[2].asX509CertChain,
                Algorithm.fromCoseAlgorithmIdentifier(array[3].asNumber.toInt()),
                array[4].asTstr,
                array[5].asCoseKey.ecPrivateKey,
                array[6].asX509CertChain,
                Algorithm.fromCoseAlgorithmIdentifier(array[7].asNumber.toInt()),
                array[8].asTstr,
            )
        }

        fun create(backendEnvironment: Deferred<BackendEnvironment>): Deferred<KeyMaterial> {
            return CoroutineScope(Dispatchers.Default).async {
                val env = backendEnvironment.await()
                withContext(env) {
                    val storage = env.getTable(cloudRootStateTableSpec)
                    val keyMaterialBlob = storage.get("cloudSecureAreaKeyMaterial")?.toByteArray()
                        ?: let {
                            val blob = createNew().toCbor()
                            storage.insert(
                                key = "cloudSecureAreaKeyMaterial",
                                data = ByteString(blob)
                            )
                            blob
                        }
                    fromCbor(keyMaterialBlob)
                }
            }
        }

        private suspend fun createNew(): KeyMaterial {
            val serverSecureAreaBoundKey = Random.Default.nextBytes(32)

            val now = Clock.System.now()
            val validFrom = now
            val validUntil = now.plus(DateTimePeriod(years = 10), TimeZone.currentSystemDefault())

            val attestationRoot = BackendEnvironment.getServerIdentity(
                name = "csa_attestation_identity"
            ) {
                val rootKey = Crypto.createEcPrivateKey(EcCurve.P384)
                val rootName = X500Name.fromName("CN=csa_dev_root")
                val certificate = X509Cert.Builder(
                    publicKey = rootKey.publicKey,
                    signingKey = rootKey,
                    signatureAlgorithm = EcCurve.P384.defaultSigningAlgorithmFullySpecified,
                    serialNumber = ASN1Integer.fromRandom(128),
                    subject = rootName,
                    issuer = rootName,
                    validFrom = validFrom,
                    validUntil = validUntil
                )
                    .includeSubjectKeyIdentifier()
                    .setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
                    .setBasicConstraints(ca = true, pathLenConstraint = null)
                    .build()
                ServerIdentity(
                    privateKey = rootKey,
                    certificateChain = X509CertChain(listOf(certificate))
                )
            }

            // Create instance-specific intermediate certificate.
            val attestationKey = Crypto.createEcPrivateKey(EcCurve.P256)
            val attestationKeySignatureAlgorithm = Algorithm.ESP256
            val attestationKeySubject = "CN=Cloud Secure Area Attestation Root"
            val rootCertificate = attestationRoot.certificateChain.certificates.first()
            val attestationKeyCertificate = X509Cert.Builder(
                publicKey = attestationKey.publicKey,
                signingKey = attestationRoot.privateKey,
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
            val bindingRoot = BackendEnvironment.getServerIdentity("csa_binding_identity") {
                val cloudBindingKeyAttestationKey = Crypto.createEcPrivateKey(EcCurve.P256)
                val cloudBindingKeySignatureAlgorithm = Algorithm.ESP256
                val cloudBindingKeySubject =
                    "CN=Cloud Secure Area Cloud Binding Key Attestation Root"
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
                ServerIdentity(
                    privateKey = cloudBindingKeyAttestationKey,
                    certificateChain = X509CertChain(listOf(cloudBindingKeyAttestationCertificate))
                )
            }

            val bindingCertificate = bindingRoot.certificateChain.certificates.first()
            return KeyMaterial(
                serverSecureAreaBoundKey,
                attestationKey,
                X509CertChain(listOf(attestationKeyCertificate, rootCertificate)),
                attestationKeySignatureAlgorithm,
                attestationKeySubject,
                bindingRoot.privateKey,
                bindingRoot.certificateChain,
                bindingRoot.privateKey.curve.defaultSigningAlgorithmFullySpecified,
                bindingCertificate.subject.name
            )
        }

        private val cloudRootStateTableSpec = StorageTableSpec(
            name = "CloudSecureAreaRootState",
            supportExpiration = false,
            supportPartitions = false
        )
    }
}

