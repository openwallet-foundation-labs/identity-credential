package org.multipaz.securearea.cloud

import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.multipaz.asn1.ASN1Integer
import org.multipaz.asn1.OID
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.cbor.buildCborArray
import org.multipaz.certext.CloudKeyAttestation
import org.multipaz.certext.MultipazExtension
import org.multipaz.certext.toCbor
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcSignature
import org.multipaz.crypto.SecretKey
import org.multipaz.crypto.Signature
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.crypto.secureZero
import org.multipaz.device.AndroidKeystoreSecurityLevel
import org.multipaz.device.DeviceAttestation
import org.multipaz.device.DeviceAttestationIos
import org.multipaz.device.DeviceAttestationSoftware
import org.multipaz.device.DeviceAttestationValidationData
import org.multipaz.device.toCbor
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.rpc.auth.ClientRegistration
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.backend.getTable
import org.multipaz.rpc.handler.AesGcmCipher
import org.multipaz.rpc.handler.HttpHandler
import org.multipaz.rpc.handler.InvalidRequestException
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthError
import org.multipaz.rpc.handler.RpcAuthException
import org.multipaz.rpc.handler.RpcAuthInspector
import org.multipaz.rpc.handler.RpcAuthInspectorAssertion
import org.multipaz.rpc.handler.RpcDispatcherLocal
import org.multipaz.rpc.handler.RpcExceptionMap
import org.multipaz.rpc.handler.RpcPoll
import org.multipaz.rpc.handler.SimpleCipher
import org.multipaz.rpc.server.ClientCheckImpl
import org.multipaz.rpc.server.register
import org.multipaz.securearea.KeyAttestation
import org.multipaz.securearea.software.SoftwareCreateKeySettings
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.Storage
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.util.AndroidAttestationExtensionParser
import org.multipaz.util.toBase64Url
import org.multipaz.util.validateAndroidKeyAttestation
import org.multipaz.webtoken.buildJwt

/**
 * RPC state endpoint for registering device attestations with [CloudSecureAreaServer].
 */
@RpcState(
    endpoint = "client_registration",
    creatable = true
)
@CborSerializable
class CloudClientRegistrationState(
    var registrationChallenge: ByteString? = null
) : ClientRegistration {
    override suspend fun challenge(): ByteString {
        val server = BackendEnvironment.getInterface(CloudSecureAreaServer::class)!!
        return ByteString(server.random.nextBytes(32)).also {
            registrationChallenge = it
        }
    }

    override suspend fun register(deviceAttestation: DeviceAttestation): String {
        val challenge = registrationChallenge
            ?: throw InvalidRequestException("challenge was not called")
        registrationChallenge = null
        val server = BackendEnvironment.getInterface(CloudSecureAreaServer::class)!!
        try {
            deviceAttestation.validate(
                DeviceAttestationValidationData(
                    attestationChallenge = challenge,
                    softwareAccepted = server.allowSoftwareAttestation,
                    softwareSecrets = emptySet(),
                    iosReleaseBuild = server.iosReleaseBuild,
                    iosAppIdentifiers = server.iosAppIdentifiers.toSet(),
                    androidGmsAttestation = server.androidGmsAttestation,
                    androidVerifiedBootGreen = server.androidVerifiedBootGreen,
                    androidAppSignatureCertificateDigests = server.androidAppSignatureCertificateDigests.toSet(),
                    androidAppPackageNames = server.androidAppPackageNames.toSet(),
                    androidRequiredKeyMintSecurityLevel = server.androidKeystoreSecurityLevel,
                )
            )
        } catch (err: Exception) {
            if (err is CancellationException) throw err
            throw RpcAuthException("Device attestation failed: ${err.message}", RpcAuthError.FAILED)
        }
        val clientTable = BackendEnvironment.getTable(RpcAuthInspectorAssertion.rpcClientTableSpec)
        return clientTable.insert(
            key = null,
            data = ByteString(deviceAttestation.toCbor())
        )
    }

    companion object
}

/**
 * Encrypted per-key context stored by the client and sent back to the server when using a key.
 */
@CborSerializable
data class CloudKeyContext(
    val clientId: String,
    val passphraseSalt: ByteString,
    val keyConfiguration: CloudKeyConfiguration,
    val cloudKeyStorage: ByteString,
    val localKey: EcPublicKey,
) {
    companion object
}

/**
 * RPC state endpoint implementing [CloudSecureAreaProtocol] on the server.
 */
@RpcState(
    endpoint = "cloud_secure_area",
    creatable = true
)
@CborSerializable
class CloudSecureAreaState(
    var registrationComplete: Boolean = false,
    var clientId: String? = null,
    var clientPassphraseSalt: ByteString = ByteString(),
    var clientSaltedPassphrase: ByteString = ByteString(),
    var pendingKeyConfiguration: CloudKeyConfiguration? = null,
    var pendingCloudChallenge: ByteString? = null,
    var pendingKeyContext: CloudKeyContext? = null,
    var pendingDataToSign: ByteString? = null,
    var pendingOtherPublicKey: EcPublicKey? = null,
    var pendingKemCiphertext: ByteString? = null,
    var pendingCloudNonce: ByteString? = null,
) : CloudSecureAreaProtocol, RpcAuthInspector by RpcAuthBackendDelegate {

    private suspend fun ensureRegistered(): String {
        val currentClientId = RpcAuthContext.getClientId()
        if (!registrationComplete || clientId != currentClientId) {
            throw InvalidRequestException("Client registration is not complete")
        }
        return currentClientId
    }

    private fun clearPendingOperation() {
        pendingKeyConfiguration = null
        pendingCloudChallenge = null
        pendingKeyContext = null
        pendingDataToSign = null
        pendingOtherPublicKey = null
        pendingKemCiphertext = null
        pendingCloudNonce = null
    }

    private fun verifyKeyContextBinding(
        currentClientId: String,
        keyContext: CloudKeyContext
    ) {
        if (keyContext.clientId != currentClientId ||
            keyContext.passphraseSalt != clientPassphraseSalt
        ) {
            throw InvalidRequestException("Key context does not belong to this registration")
        }
    }

    private suspend fun verifyPassphrase(
        currentClientId: String,
        givenPassphrase: String?,
        onWrongPassphrase: () -> Unit = {}
    ) {
        val server = BackendEnvironment.getInterface(CloudSecureAreaServer::class)!!
        val lockedOutDuration = server.passphraseFailureEnforcer.isLockedOut(currentClientId)
        if (lockedOutDuration != null) {
            throw CloudTooManyPassphraseAttemptsException(lockedOutDuration)
        }
        if (givenPassphrase == null) {
            server.passphraseFailureEnforcer.recordFailedPassphraseAttempt(currentClientId)
            onWrongPassphrase()
            throw CloudWrongPassphraseException("Passphrase required but none was supplied")
        }
        val saltedPassphrase = ByteString(
            Crypto.digest(
                Algorithm.SHA256,
                clientPassphraseSalt.toByteArray() + givenPassphrase.encodeToByteArray()
            )
        )
        if (saltedPassphrase != clientSaltedPassphrase) {
            server.passphraseFailureEnforcer.recordFailedPassphraseAttempt(currentClientId)
            onWrongPassphrase()
            throw CloudWrongPassphraseException("Wrong passphrase supplied")
        }
    }

    override suspend fun register(passphrase: String): X509CertChain {
        if (registrationComplete) {
            throw InvalidRequestException("Registration already completed")
        }
        val server = BackendEnvironment.getInterface(CloudSecureAreaServer::class)!!
        val currentClientId = RpcAuthContext.getClientId()
        val salt = server.random.nextBytes(32)
        val saltedPassphrase = Crypto.digest(
            Algorithm.SHA256,
            salt + passphrase.encodeToByteArray()
        )
        registrationComplete = true
        clientId = currentClientId
        clientPassphraseSalt = ByteString(salt)
        clientSaltedPassphrase = ByteString(saltedPassphrase)
        return server.attestationKey.certChain
    }

    override suspend fun createKey0(keyConfiguration: CloudKeyConfiguration): ByteString {
        ensureRegistered()
        clearPendingOperation()
        val server = BackendEnvironment.getInterface(CloudSecureAreaServer::class)!!
        val cloudChallenge = ByteString(server.random.nextBytes(32))
        pendingKeyConfiguration = keyConfiguration
        pendingCloudChallenge = cloudChallenge
        return cloudChallenge
    }

    override suspend fun createKey1(localKeyAttestation: KeyAttestation): CloudCreatedKey {
        val currentClientId = ensureRegistered()
        val config = pendingKeyConfiguration
            ?: throw InvalidRequestException("createKey0 was not called")
        val cloudChallenge = pendingCloudChallenge
            ?: throw InvalidRequestException("createKey0 was not called")
        clearPendingOperation()

        val server = BackendEnvironment.getInterface(CloudSecureAreaServer::class)!!
        server.validateLocalKeyAttestation(
            clientId = currentClientId,
            localKeyAttestation = localKeyAttestation,
            cloudChallenge = cloudChallenge,
            config = config
        )
        val (attestationCert, encryptedKeyContext) = server.createCloudKey(
            clientId = currentClientId,
            passphraseSalt = clientPassphraseSalt,
            config = config,
            localKey = localKeyAttestation.ecPublicKey
        )
        return CloudCreatedKey(
            remoteKeyAttestation = X509CertChain(
                listOf(attestationCert) + server.attestationKey.certChain.certificates
            ),
            serverState = encryptedKeyContext
        )
    }

    override suspend fun batchCreateKey0(keyConfiguration: CloudKeyConfiguration): ByteString {
        return createKey0(keyConfiguration)
    }

    override suspend fun batchCreateKey1(
        localKeyAttestations: List<KeyAttestation>
    ): CloudBatchCreatedKeys {
        val currentClientId = ensureRegistered()
        val config = pendingKeyConfiguration
            ?: throw InvalidRequestException("batchCreateKey0 was not called")
        val cloudChallenge = pendingCloudChallenge
            ?: throw InvalidRequestException("batchCreateKey0 was not called")
        clearPendingOperation()

        val server = BackendEnvironment.getInterface(CloudSecureAreaServer::class)!!
        for (localKeyAttestation in localKeyAttestations) {
            server.validateLocalKeyAttestation(
                clientId = currentClientId,
                localKeyAttestation = localKeyAttestation,
                cloudChallenge = cloudChallenge,
                config = config
            )
        }

        val remoteKeyAttestationLeafs = mutableListOf<X509Cert>()
        val serverStates = mutableListOf<ByteString>()
        val openid4vciAttestedKeysJwks = mutableListOf<JsonElement>()
        for (localKeyAttestation in localKeyAttestations) {
            val (attestationCert, encryptedKeyContext) = server.createCloudKey(
                clientId = currentClientId,
                passphraseSalt = clientPassphraseSalt,
                config = config,
                localKey = localKeyAttestation.ecPublicKey
            )
            remoteKeyAttestationLeafs.add(attestationCert)
            serverStates.add(encryptedKeyContext)
            openid4vciAttestedKeysJwks.add(attestationCert.publicKey.toJwk())
        }
        val openid4vciAttestationJwt = server.buildOpenid4vciKeyAttestationJwt(
            config = config,
            attestedKeysJwks = openid4vciAttestedKeysJwks
        )
        return CloudBatchCreatedKeys(
            commonCertChain = X509CertChain(server.attestationKey.certChain.certificates),
            remoteKeyAttestationLeafs = remoteKeyAttestationLeafs,
            serverStates = serverStates,
            openid4vciKeyAttestationCompactSerialization = openid4vciAttestationJwt
        )
    }

    override suspend fun sign0(
        dataToSign: ByteString,
        keyContext: ByteString
    ): ByteString {
        val currentClientId = ensureRegistered()
        clearPendingOperation()
        val server = BackendEnvironment.getInterface(CloudSecureAreaServer::class)!!
        val decodedKeyContext = server.decryptKeyContext(keyContext)
        verifyKeyContextBinding(currentClientId, decodedKeyContext)
        val cloudNonce = ByteString(server.random.nextBytes(32))
        pendingKeyContext = decodedKeyContext
        pendingDataToSign = dataToSign
        pendingCloudNonce = cloudNonce
        return cloudNonce
    }

    override suspend fun sign1(
        signature: EcSignature,
        passphrase: String?
    ): Signature {
        val currentClientId = ensureRegistered()
        val keyContext = pendingKeyContext
            ?: throw InvalidRequestException("sign0 was not called")
        val dataToSign = pendingDataToSign
            ?: throw InvalidRequestException("sign0 was not called")
        val cloudNonce = pendingCloudNonce
            ?: throw InvalidRequestException("sign0 was not called")

        val dataThatWasSignedLocally = Cbor.encode(
            buildCborArray {
                add(cloudNonce.toByteArray())
            }
        )
        Crypto.checkSignature(
            keyContext.localKey,
            dataThatWasSignedLocally,
            Algorithm.ES256,
            signature
        )

        if (keyContext.keyConfiguration.passphraseRequired) {
            verifyPassphrase(currentClientId, passphrase) {
                clearPendingOperation()
            }
        }

        clearPendingOperation()
        val storage = EphemeralStorage.deserialize(keyContext.cloudKeyStorage)
        val secureArea = SoftwareSecureArea.create(storage)
        return secureArea.sign(
            "CloudKey",
            dataToSign.toByteArray()
        )
    }

    override suspend fun keyAgreement0(
        otherPublicKey: EcPublicKey,
        keyContext: ByteString
    ): ByteString {
        val currentClientId = ensureRegistered()
        clearPendingOperation()
        val server = BackendEnvironment.getInterface(CloudSecureAreaServer::class)!!
        val decodedKeyContext = server.decryptKeyContext(keyContext)
        verifyKeyContextBinding(currentClientId, decodedKeyContext)
        val cloudNonce = ByteString(server.random.nextBytes(32))
        pendingKeyContext = decodedKeyContext
        pendingOtherPublicKey = otherPublicKey
        pendingCloudNonce = cloudNonce
        return cloudNonce
    }

    override suspend fun keyAgreement1(
        signature: EcSignature,
        passphrase: String?
    ): ByteString {
        val currentClientId = ensureRegistered()
        val keyContext = pendingKeyContext
            ?: throw InvalidRequestException("keyAgreement0 was not called")
        val otherPublicKey = pendingOtherPublicKey
            ?: throw InvalidRequestException("keyAgreement0 was not called")
        val cloudNonce = pendingCloudNonce
            ?: throw InvalidRequestException("keyAgreement0 was not called")

        val dataThatWasSignedLocally = Cbor.encode(
            buildCborArray {
                add(cloudNonce.toByteArray())
            }
        )
        Crypto.checkSignature(
            keyContext.localKey,
            dataThatWasSignedLocally,
            Algorithm.ES256,
            signature
        )

        if (keyContext.keyConfiguration.passphraseRequired) {
            verifyPassphrase(currentClientId, passphrase) {
                clearPendingOperation()
            }
        }

        clearPendingOperation()
        val storage = EphemeralStorage.deserialize(keyContext.cloudKeyStorage)
        val secureArea = SoftwareSecureArea.create(storage)
        val zabBytes = secureArea.keyAgreement(
            "CloudKey",
            otherPublicKey
        ).use { it.encoded }
        return try {
            ByteString(zabBytes)
        } finally {
            zabBytes.secureZero()
        }
    }

    override suspend fun kemDecapsulate0(
        ciphertext: ByteString,
        keyContext: ByteString
    ): ByteString {
        val currentClientId = ensureRegistered()
        clearPendingOperation()
        val server = BackendEnvironment.getInterface(CloudSecureAreaServer::class)!!
        val decodedKeyContext = server.decryptKeyContext(keyContext)
        verifyKeyContextBinding(currentClientId, decodedKeyContext)
        val cloudNonce = ByteString(server.random.nextBytes(32))
        pendingKeyContext = decodedKeyContext
        pendingKemCiphertext = ciphertext
        pendingCloudNonce = cloudNonce
        return cloudNonce
    }

    override suspend fun kemDecapsulate1(
        signature: EcSignature,
        passphrase: String?
    ): ByteString {
        val currentClientId = ensureRegistered()
        val keyContext = pendingKeyContext
            ?: throw InvalidRequestException("kemDecapsulate0 was not called")
        val ciphertext = pendingKemCiphertext
            ?: throw InvalidRequestException("kemDecapsulate0 was not called")
        val cloudNonce = pendingCloudNonce
            ?: throw InvalidRequestException("kemDecapsulate0 was not called")

        val dataThatWasSignedLocally = Cbor.encode(
            buildCborArray {
                add(cloudNonce.toByteArray())
            }
        )
        Crypto.checkSignature(
            keyContext.localKey,
            dataThatWasSignedLocally,
            Algorithm.ES256,
            signature
        )

        if (keyContext.keyConfiguration.passphraseRequired) {
            verifyPassphrase(currentClientId, passphrase) {
                clearPendingOperation()
            }
        }

        clearPendingOperation()
        val storage = EphemeralStorage.deserialize(keyContext.cloudKeyStorage)
        val secureArea = SoftwareSecureArea.create(storage)
        val sharedSecretBytes = secureArea.kemDecapsulate(
            "CloudKey",
            ciphertext.toByteArray()
        ).use { it.encoded }
        return try {
            ByteString(sharedSecretBytes)
        } finally {
            sharedSecretBytes.secureZero()
        }
    }

    override suspend fun checkPassphrase(passphrase: String) {
        val currentClientId = ensureRegistered()
        verifyPassphrase(currentClientId, passphrase)
    }

    companion object
}

/**
 * A reference implementation of the Cloud Secure Area server using Multipaz CBOR-RPC.
 *
 * This code is not intended for production use.
 *
 * @param serverSecureAreaBoundKey the secret key used to encrypt/decrypt state externally stored.
 *   Must be 16, 24, or 32 bytes.
 * @param attestationKey the private key used to sign attestations for keys created by clients.
 * @param iosReleaseBuild Whether a release build is required on iOS. When `false`, both debug and release builds
 *   are accepted.
 * @param iosAppIdentifiers A list of iOS App Identifiers (e.g. `9B5CR87588.org.multipaz.testapp`)
 *   to allow. If empty, allow any app.
 * @param androidGmsAttestation whether to require attestations made for local key on clients is using the Google root.
 * @param androidVerifiedBootGreen whether to require clients are in verified boot state green.
 * @param androidAppSignatureCertificateDigests the allowed list of applications that can use the
 *   service. Each element is the bytes of the SHA-256 of a signing certificate, see the
 *   [Signature](https://developer.android.com/reference/android/content/pm/Signature) class in
 *   the Android SDK for details. If empty, allow any app.
 * @param androidAppPackageNames A list of Android package names to allow. If empty, allow any app.
 * @param androidKeystoreSecurityLevel The minimum required Android Keystore security level.
 * @param openid4vciKeyAttestationIssuer The value to use for the `iss` field in OpenID4VCI attestations or `null` to
 *   not include this field.
 * @param openid4vciKeyAttestationKeyStorage The value to use for the `key_storage` field in OpenID4VCI attestations or
 *   `null` to not include this field.
 * @param openid4vciKeyAttestationUserAuthentication The value to use for the `user_authentication` field in OpenID4VCI
 *   attestations for keys that are passphrase protected or `null` to not include this field.
 * @param openid4vciKeyAttestationUserAuthenticationNoPassphrase The value to use for the `user_authentication` field
 *   in OpenID4VCI attestations for keys that are not passphrase protected or `null` to not include this field.
 * @param openid4vciKeyAttestationCertification The value to use for the `certification` field in OpenID4VCI
 *   attestations or `null` to not include this field.
 * @param passphraseFailureEnforcer the [PassphraseFailureEnforcer] to use.
 * @param allowSoftwareAttestation whether to allow software attestation.
 * @param random the [Random] instance to use (defaults to [Crypto.secureRandom]).
 * @param storage the [Storage] instance used to persist registered client attestations and RPC session nonces.
 */
class CloudSecureAreaServer(
    private val serverSecureAreaBoundKey: SecretKey,
    internal val attestationKey: AsymmetricKey.X509Certified,
    internal val iosReleaseBuild: Boolean,
    internal val iosAppIdentifiers: List<String>,
    internal val androidGmsAttestation: Boolean,
    internal val androidVerifiedBootGreen: Boolean,
    internal val androidAppSignatureCertificateDigests: List<ByteString>,
    internal val androidAppPackageNames: List<String>,
    internal val androidKeystoreSecurityLevel: AndroidKeystoreSecurityLevel,
    internal val openid4vciKeyAttestationIssuer: String?,
    internal val openid4vciKeyAttestationKeyStorage: String?,
    internal val openid4vciKeyAttestationUserAuthentication: String?,
    internal val openid4vciKeyAttestationUserAuthenticationNoPassphrase: String?,
    internal val openid4vciKeyAttestationCertification: String?,
    internal val passphraseFailureEnforcer: PassphraseFailureEnforcer,
    internal val allowSoftwareAttestation: Boolean = false,
    internal val random: Random = Crypto.secureRandom,
    private val storage: Storage = EphemeralStorage(),
) : AutoCloseable {
    internal val cipher: SimpleCipher = AesGcmCipher(serverSecureAreaBoundKey)

    val environment: BackendEnvironment = object : BackendEnvironment {
        override fun <T : Any> getInterface(clazz: KClass<T>): T? {
            return when (clazz) {
                CloudSecureAreaServer::class -> clazz.cast(this@CloudSecureAreaServer)
                Storage::class -> clazz.cast(storage)
                SimpleCipher::class -> clazz.cast(cipher)
                RpcAuthInspector::class -> clazz.cast(RpcAuthInspectorAssertion.Default)
                RpcPoll::class -> clazz.cast(RpcPoll.SILENT)
                else -> null
            }
        }
    }

    val exceptionMap: RpcExceptionMap = RpcExceptionMap.Builder().apply {
        CloudWrongPassphraseException.register(this)
        CloudTooManyPassphraseAttemptsException.register(this)
    }.build()

    val dispatcher: RpcDispatcherLocal = RpcDispatcherLocal.Builder().apply {
        CloudClientRegistrationState.register(this)
        ClientCheckImpl.register(this)
        CloudSecureAreaState.register(this)
    }.build(environment, cipher, exceptionMap)

    val httpHandler: HttpHandler = HttpHandler(dispatcher, RpcPoll.SILENT)

    override fun close() {
        serverSecureAreaBoundKey.close()
    }

    internal suspend fun encryptKeyContext(keyContext: CloudKeyContext): ByteString {
        return ByteString(cipher.encrypt(keyContext.toCbor()))
    }

    internal suspend fun decryptKeyContext(encryptedKeyContext: ByteString): CloudKeyContext {
        return CloudKeyContext.fromCbor(cipher.decrypt(encryptedKeyContext.toByteArray()))
    }

    internal suspend fun validateLocalKeyAttestation(
        clientId: String,
        localKeyAttestation: KeyAttestation,
        cloudChallenge: ByteString,
        config: CloudKeyConfiguration
    ) {
        val deviceAttestation = RpcAuthInspectorAssertion.getClientDeviceAttestation(clientId)
            ?: throw InvalidRequestException("Unknown client '$clientId'")

        // iOS devices don't have key attestation for the locally created key but other platforms do, including
        // Android. So we check that the attestation is valid and matches what we requested.
        if (deviceAttestation !is DeviceAttestationIos &&
            !(allowSoftwareAttestation && deviceAttestation is DeviceAttestationSoftware)
        ) {
            try {
                val chain = localKeyAttestation.certChain!!
                validateAndroidKeyAttestation(
                    chain = chain,
                    challenge = cloudChallenge,
                    requireGmsAttestation = androidGmsAttestation,
                    requireVerifiedBootGreen = androidVerifiedBootGreen,
                    requireAppSignatureCertificateDigests = androidAppSignatureCertificateDigests.toSet(),
                    requireAppPackages = androidAppPackageNames.toSet(),
                    requireKeyMintSecurityLevel = androidKeystoreSecurityLevel
                )
                val attestation = AndroidAttestationExtensionParser(chain.certificates[0])
                val expectedUserAuthTypes = CloudUserAuthType.encodeSet(config.userAuthenticationTypes)
                if (config.userAuthenticationRequired) {
                    val attestationExtensionUserAuthType = attestation.getUserAuthenticationType()
                    check(attestationExtensionUserAuthType == expectedUserAuthTypes) {
                        "Requested userAuthType $expectedUserAuthTypes for the local key but the Android " +
                            "attestation extension contains $attestationExtensionUserAuthType"
                    }
                } else {
                    check(attestation.getUserAuthenticationType() == 0L)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw IllegalStateException("Android Keystore attestation did not validate", e)
            }
        }
    }

    internal suspend fun createCloudKey(
        clientId: String,
        passphraseSalt: ByteString,
        config: CloudKeyConfiguration,
        localKey: EcPublicKey
    ): Pair<X509Cert, ByteString> {
        val keyStorage = EphemeralStorage()
        val secureArea = SoftwareSecureArea.create(keyStorage)
        val builder = SoftwareCreateKeySettings.Builder()
            .setValidityPeriod(config.validFrom, config.validUntil)
            .setAlgorithm(config.algorithm)
        secureArea.createKey("CloudKey", builder.build())

        val keyInfo = secureArea.getKeyInfo("CloudKey")

        val attestationCert = X509Cert.Builder(
            publicKey = keyInfo.publicKey,
            signingKey = attestationKey,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Cloud Secure Area Key"),
            issuer = attestationKey.certChain.certificates.first().subject,
            validFrom = config.validFrom,
            validUntil = config.validUntil
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(attestationKey.certChain.certificates[0])
            .setKeyUsage(
                setOf(
                    when {
                        keyInfo.algorithm.isSigning -> X509KeyUsage.DIGITAL_SIGNATURE
                        keyInfo.algorithm.isKeyEncapsulation -> X509KeyUsage.KEY_ENCIPHERMENT
                        else -> X509KeyUsage.KEY_AGREEMENT
                    }
                )
            )
            .addExtension(
                oid = OID.X509_EXTENSION_MULTIPAZ_EXTENSION.oid,
                critical = false,
                value = MultipazExtension(
                    cloudKeyAttestation = CloudKeyAttestation(
                        challenge = config.challenge,
                        passphrase = config.passphraseRequired,
                        userAuthentication = config.userAuthenticationTypes
                    )
                ).toCbor()
            )
            .build()

        val keyContext = CloudKeyContext(
            clientId = clientId,
            passphraseSalt = passphraseSalt,
            keyConfiguration = config,
            cloudKeyStorage = keyStorage.serialize(),
            localKey = localKey
        )
        return Pair(attestationCert, encryptKeyContext(keyContext))
    }

    internal suspend fun buildOpenid4vciKeyAttestationJwt(
        config: CloudKeyConfiguration,
        attestedKeysJwks: List<JsonElement>
    ): String {
        return buildJwt(
            key = attestationKey,
            type = "key-attestation+jwt",
        ) {
            openid4vciKeyAttestationIssuer?.let { put("iss", it) }
            put("nbf", config.validFrom.epochSeconds)
            put("exp", config.validUntil.epochSeconds)
            if (openid4vciKeyAttestationKeyStorage != null) {
                putJsonArray("key_storage") {
                    add(openid4vciKeyAttestationKeyStorage)
                }
            }
            if (config.passphraseRequired) {
                if (openid4vciKeyAttestationUserAuthentication != null) {
                    putJsonArray("user_authentication") {
                        add(openid4vciKeyAttestationUserAuthentication)
                    }
                }
            } else {
                if (openid4vciKeyAttestationUserAuthenticationNoPassphrase != null) {
                    putJsonArray("user_authentication") {
                        add(openid4vciKeyAttestationUserAuthenticationNoPassphrase)
                    }
                }
            }
            openid4vciKeyAttestationCertification?.let { put("certification", it) }
            put("nonce", config.challenge.toByteArray().toBase64Url())
            put("attested_keys", JsonArray(attestedKeysJwks))
        }
    }
}