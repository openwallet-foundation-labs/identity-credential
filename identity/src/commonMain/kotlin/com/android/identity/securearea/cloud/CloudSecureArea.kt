package com.android.identity.securearea.cloud

import com.android.identity.asn1.OID
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.EcSignature
import com.android.identity.device.AssertionNonce
import com.android.identity.device.DeviceCheck
import com.android.identity.prompt.requestPassphrase
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyAttestation
import com.android.identity.securearea.KeyLockedException
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.KeyUnlockData
import com.android.identity.securearea.KeyUnlockInteractive
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.CreateKeyRequest0
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.CreateKeyRequest1
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.CreateKeyResponse0
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.CreateKeyResponse1
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.E2EERequest
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.E2EEResponse
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.E2EESetupRequest0
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.E2EESetupRequest1
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.E2EESetupResponse0
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.E2EESetupResponse1
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.KeyAgreementRequest0
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.KeyAgreementRequest1
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.KeyAgreementResponse0
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.KeyAgreementResponse1
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.RegisterRequest0
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.RegisterRequest1
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.RegisterResponse0
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.RegisterResponse1
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.SignRequest0
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.SignRequest1
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.SignResponse0
import com.android.identity.securearea.cloud.CloudSecureAreaProtocol.SignResponse1
import com.android.identity.securearea.fromCbor
import com.android.identity.securearea.toCbor
import com.android.identity.storage.Storage
import com.android.identity.storage.StorageEngine
import com.android.identity.storage.StorageTable
import com.android.identity.storage.StorageTableSpec
import com.android.identity.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Creates a platform-specific [SecureArea] for the local key created for each Cloud Secure Area key.
 *
 * @param storage the [Storage] to use for the platform-specific [SecureArea].
 * @param partitionId the partition id to use for [storage].
 */
internal expect suspend fun cloudSecureAreaGetPlatformSecureArea(
    storage: Storage,
    partitionId: String,
): SecureArea

/**
 * Creates a [CreateKeySettings] for the platform-specific [SecureArea].
 */
internal expect fun cloudSecureAreaGetPlatformSecureAreaCreateKeySettings(
    challenge: ByteString,
    keyPurposes: Set<KeyPurpose>,
    userAuthenticationRequired: Boolean,
    userAuthenticationTypes: Set<CloudUserAuthType>
): CreateKeySettings


/**
 * An implementation of [SecureArea] using a Secure Area managed by an external server.
 */
open class CloudSecureArea protected constructor(
    private val storageTable: StorageTable,
    final override val identifier: String,
    val serverUrl: String,
) : SecureArea {
    private var skDevice: ByteArray? = null
    private var skCloud: ByteArray? = null
    private var e2eeContext: ByteArray? = null
    private var encryptedCounter = 0
    private var decryptedCounter = 0

    internal open suspend fun delayForBruteforceMitigation(duration: Duration) {
        Logger.i(TAG, "delayForBruteforceMitigation: delaying for $duration")
        delay(duration)
        Logger.i(TAG, "delayForBruteforceMitigation: resuming (delayed for $duration)")
    }

    override val displayName: String
        get() = "Cloud Secure Area"

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout)
    }

    private var deviceAttestationId: String? = null
    private var cloudBindingKey: EcPublicKey? = null
    private var registrationContext: ByteString? = null

    protected open suspend fun initialize() {
        require(identifier.startsWith(IDENTIFIER_PREFIX))
        deviceAttestationId = storageTable.get(DEVICE_ATTESTATION_ID, identifier)?.decodeToString()
        storageTable.get(BINDING_KEY, identifier)?.let { bindingKeyData ->
            cloudBindingKey = Cbor.decode(bindingKeyData.toByteArray()).asCoseKey.ecPublicKey
        }
        registrationContext = storageTable.get(REGISTRATION_CONTEXT, identifier)
        platformSecureArea = cloudSecureAreaGetPlatformSecureArea(
            storage = storageTable.storage,
            partitionId = PLATFORM_SECURE_AREA_IDENTIFIER_PREFIX + identifier
        )
    }

    open suspend fun communicateLowlevel(
        endpointUri: String,
        requestData: ByteArray
    ): Pair<Int, ByteArray> {
        try {
            val httpResponse = httpClient.post(endpointUri) {
                contentType(ContentType.Application.Cbor)
                accept(ContentType.Application.Cbor)
                timeout {
                    requestTimeoutMillis = 10000
                }
                setBody(requestData)
            }
            val responseStatus = httpResponse.status.value
            val responseBody: ByteArray = httpResponse.body()
            return Pair(responseStatus, responseBody)
        } catch (e: Exception) {
            throw CloudException("Error communicating with Cloud Secure Area", e)
        }
    }

    private suspend fun communicate(
        endpointUri: String,
        requestData: ByteArray
    ): ByteArray {
        val (status, data) = communicateLowlevel(endpointUri, requestData)
        if (status != HttpStatusCode.OK.value) {
            throw CloudException("Status $status: ${data.decodeToString()}")
        }
        return data
    }

    val isRegistered: Boolean
        get() = registrationContext != null

    lateinit var platformSecureArea: SecureArea

    /**
     * Registers with the cloud secure area.
     *
     * @param passphrase the passphrase to use.
     * @param passphraseConstraints the constraints for the passphrase.
     * @param onAuthorizeRootOfTrust called to authorize the Cloud Secure Area's root certificate.
     * @throws CloudException if an error occurred.
     */
    suspend fun register(
        passphrase: String,
        passphraseConstraints: PassphraseConstraints,
        onAuthorizeRootOfTrust: (cloudSecureAreaRootOfTrust: X509Cert) -> Boolean,
    ) {
        var response: ByteArray
        try {
            val request0 = RegisterRequest0("1.0")
            response = communicate(serverUrl, request0.toCbor())
            val response0 = CloudSecureAreaProtocol.Command.fromCbor(response) as RegisterResponse0
            val deviceBindingKeyAlias = "DeviceBindingKey"

            val deviceBindingKeyInfo = platformSecureArea.createKey(deviceBindingKeyAlias,
                cloudSecureAreaGetPlatformSecureAreaCreateKeySettings(
                    challenge = ByteString(response0.cloudChallenge),
                    keyPurposes = setOf(KeyPurpose.SIGN),
                    userAuthenticationRequired = false,
                    userAuthenticationTypes = setOf()
                )
            )
            val deviceChallenge = Random.Default.nextBytes(32)

            val deviceAttestationResult = DeviceCheck.generateAttestation(
                platformSecureArea,
                response0.attestationChallenge
            )

            val request1 = RegisterRequest1(
                deviceChallenge = deviceChallenge,
                deviceAttestation = deviceAttestationResult.deviceAttestation,
                deviceBindingKey = deviceBindingKeyInfo.publicKey.toCoseKey(),
                deviceBindingKeyAttestation = deviceBindingKeyInfo.attestation.certChain,
                serverState = response0.serverState
            )

            response = communicate(serverUrl, request1.toCbor())
            val response1 = CloudSecureAreaProtocol.Command.fromCbor(response) as RegisterResponse1
            validateCloudBindingKeyAttestation(
                response1.cloudBindingKeyAttestation,
                deviceChallenge,
                onAuthorizeRootOfTrust,
            )

            // TODO: Need deleteAll() to take a partitionId
            storageTable.enumerate(partitionId = identifier).forEach {
                storageTable.delete(
                    key = it,
                    partitionId = identifier
                )
            }

            // Ready to go...
            deviceAttestationId = deviceAttestationResult.deviceAttestationId
            cloudBindingKey = response1.cloudBindingKeyAttestation.certificates[0].ecPublicKey
            registrationContext = ByteString(response1.serverState)

            // ... and save for future use
            storageTable.insert(
                key = DEVICE_ATTESTATION_ID,
                data = deviceAttestationId!!.encodeToByteString(),
                partitionId = identifier,
            )
            storageTable.insert(
                key = BINDING_KEY,
                partitionId = identifier,
                data = ByteString(Cbor.encode(cloudBindingKey!!.toCoseKey().toDataItem()))
            )
            storageTable.insert(
                key = REGISTRATION_CONTEXT,
                partitionId = identifier,
                data = registrationContext!!
            )

            // We need E2EE before entering the second stage of registration - this is because we
            // need the data exchanged in this stage (e.g. the passphrase) to be encrypted so only
            // the Secure Enclave on the server side can see it.
            setupE2EE(false)

            // Note that the second registration phase modifies the registrationContext so we need
            // to update it once the operation completes.
            val stage2Request0 = CloudSecureAreaProtocol.RegisterStage2Request0(passphrase)
            val stage2Response0 =
                CloudSecureAreaProtocol.Command.fromCbor(communicateE2EE(stage2Request0.toCbor()))
                        as CloudSecureAreaProtocol.RegisterStage2Response0
            registrationContext = ByteString(stage2Response0.serverState)
            storageTable.update(
                key = REGISTRATION_CONTEXT,
                partitionId = identifier,
                data = registrationContext!!
            )
            storageTable.insert(
                key = PASSPHRASE_CONSTRAINTS,
                partitionId = identifier,
                data = ByteString(passphraseConstraints.toCbor())
            )
        } catch (e: Throwable) {
            throw CloudException(e)
        }
    }

    /**
     * The [PassphraseConstraints] configured at registration time.
     *
     * @return the [PassphraseConstraints] passed to to the [register] method.
     * @throws IllegalStateException if not registered with a Cloud Secure Area instance.
     */
    suspend fun getPassphraseConstraints(): PassphraseConstraints {
        val encoded = storageTable.get(key = PASSPHRASE_CONSTRAINTS, partitionId = identifier)
            ?: throw IllegalStateException("Not registered with CSA")
        return PassphraseConstraints.fromCbor(encoded.toByteArray())
    }

    suspend fun unregister() {
        // TODO: should we send a RPC to the server to let them know we're bailing?
        storageTable.delete(key = DEVICE_ATTESTATION_ID, partitionId = identifier)
        storageTable.delete(key = BINDING_KEY, partitionId = identifier)
        storageTable.delete(key = REGISTRATION_CONTEXT, partitionId = identifier)
        storageTable.delete(key = PASSPHRASE_CONSTRAINTS, partitionId = identifier)
        deviceAttestationId = null
        cloudBindingKey = null
        registrationContext = null
        skDevice = null
    }

    private fun validateCloudBindingKeyAttestation(
        attestation: X509CertChain,
        expectedDeviceChallenge: ByteArray,
        onAuthorizeRootOfTrust: (cloudSecureAreaRootOfTrust: X509Cert) -> Boolean,
    ) {
        if (!attestation.validate()) {
            throw CloudException("Error verifying attestation chain")
        }

        val rootX509Cert = attestation.certificates.last()
        if (!onAuthorizeRootOfTrust(rootX509Cert)) {
            throw CloudException("Root X509Cert not authorized by app")
        }

        val attestation = CloudAttestationExtension.decode(ByteString(
            attestation.certificates[0]
                .getExtensionValue(OID.X509_EXTENSION_MULTIPAZ_KEY_ATTESTATION.oid)!!
        ))
        check(attestation.challenge == ByteString(expectedDeviceChallenge)) {
            "Challenge in attestation does match what's expected"
        }
    }

    private suspend fun setupE2EE(forceSetup: Boolean) {
        // No need to setup E2EE if it's already up...
        if (!forceSetup && skDevice != null) {
            return
        }
        var response: ByteArray
        try {
            val request0 = E2EESetupRequest0(registrationContext!!.toByteArray())
            response = communicate(serverUrl, request0.toCbor())
            val response0 = CloudSecureAreaProtocol.Command.fromCbor(response) as E2EESetupResponse0
            val deviceNonce = Random.Default.nextBytes(32)
            val eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
            val dataToSign = Cbor.encode(
                CborArray.builder()
                    .add(eDeviceKey.publicKey.toCoseKey().toDataItem())
                    .add(response0.cloudNonce)
                    .add(deviceNonce)
                    .end()
                    .build()
            )
            val signature = platformSecureArea.sign(
                "DeviceBindingKey",
                dataToSign,
                null
            )
            val deviceAssertion = DeviceCheck.generateAssertion(
                secureArea = platformSecureArea,
                deviceAttestationId = deviceAttestationId!!,
                assertion = AssertionNonce(ByteString(response0.cloudNonce))
            )
            val request1 = E2EESetupRequest1(
                eDeviceKey = eDeviceKey.publicKey.toCoseKey(),
                deviceNonce = deviceNonce,
                signature = signature,
                deviceAssertion = deviceAssertion,
                serverState = response0.serverState
            )
            response = communicate(serverUrl, request1.toCbor())
            val response1 = CloudSecureAreaProtocol.Command.fromCbor(response) as E2EESetupResponse1
            val dataSignedByTheCloud = Cbor.encode(
                CborArray.builder()
                    .add(response1.eCloudKey.toDataItem())
                    .add(response0.cloudNonce)
                    .add(deviceNonce)
                    .end()
                    .build()
            )
            if (!Crypto.checkSignature(
                    cloudBindingKey!!,
                    dataSignedByTheCloud,
                    Algorithm.ES256,
                    response1.signature
                )
            ) {
                throw CloudException("Error verifying signature")
            }

            // Now we can derive SKDevice and SKCloud
            val zab = Crypto.keyAgreement(eDeviceKey, response1.eCloudKey.ecPublicKey)
            val salt = Crypto.digest(
                Algorithm.SHA256,
                Cbor.encode(
                    CborArray.builder()
                        .add(deviceNonce)
                        .add(response0.cloudNonce)
                        .end()
                        .build()
                )
            )
            skDevice = Crypto.hkdf(
                Algorithm.HMAC_SHA256,
                zab,
                salt,
                "SKDevice".encodeToByteArray(),
                32
            )
            skCloud = Crypto.hkdf(
                Algorithm.HMAC_SHA256,
                zab,
                salt,
                "SKCloud".encodeToByteArray(),
                32
            )
            e2eeContext = response1.serverState
            encryptedCounter = 1
            decryptedCounter = 1
        } catch (e: Exception) {
            throw CloudException(e)
        }
    }

    private fun encryptToCloud(messagePlaintext: ByteArray): ByteArray {
        // The IV and these constants are specified in ISO/IEC 18013-5:2021 clause 9.1.1.5.
        val iv = Buffer()
        iv.writeInt(0x00000000.toInt())
        val ivIdentifier = 0x00000001
        iv.writeInt(ivIdentifier)
        iv.writeInt(encryptedCounter++)
        return Crypto.encrypt(Algorithm.A128GCM, skDevice!!, iv.readByteArray(), messagePlaintext)
    }

    private fun decryptFromCloud(messageCiphertext: ByteArray): ByteArray {
        val iv = Buffer()
        iv.writeInt(0x00000000.toInt())
        val ivIdentifier = 0x00000000
        iv.writeInt(ivIdentifier)
        iv.writeInt(decryptedCounter++)
        return Crypto.decrypt(Algorithm.A128GCM, skCloud!!, iv.readByteArray(), messageCiphertext)
    }

    // This is internal rather than private b/c it's used in testPassphraseCannotBeChanged()
    internal suspend fun communicateE2EE(requestData: ByteArray): ByteArray {
        return communicateE2EEInternal(requestData, 0)
    }

    private suspend fun communicateE2EEInternal(
        requestData: ByteArray,
        callDepth: Int
    ): ByteArray {
        val requestWrapper = E2EERequest(
            encryptToCloud(requestData),
            e2eeContext!!
        )
        val (httpResponseCode, response) = communicateLowlevel(serverUrl, requestWrapper.toCbor())

        if (httpResponseCode == HttpStatusCode.BadRequest.value) {
            // This status code means that decryption failed
            if (callDepth > 10) {
                throw CloudException("Error reinitializing E2EE after $callDepth attempts")
            }
            Logger.d(TAG, "Received status 400, reinitializing E2EE")
            setupE2EE(true)
            return communicateE2EEInternal(requestData, callDepth + 1)
        } else if (httpResponseCode != HttpStatusCode.OK.value) {
            throw CloudException("Excepted status code 200 or 400, got $httpResponseCode")
        }
        val responseWrapper = CloudSecureAreaProtocol.Command.fromCbor(response) as E2EEResponse
        e2eeContext = responseWrapper.e2eeContext
        return decryptFromCloud(responseWrapper.encryptedResponse)
    }

    override suspend fun createKey(
        alias: String?,
        createKeySettings: CreateKeySettings
    ): CloudKeyInfo {
        if (alias != null) {
            // If the key with the given alias exists, it is silently overwritten.
            storageTable.delete(
                key = alias,
                partitionId = identifier
            )
        }
        val cSettings = if (createKeySettings is CloudCreateKeySettings) {
            createKeySettings
        } else {
            // Use default settings if user passed in a generic SecureArea.CreateKeySettings.
            CloudCreateKeySettings.Builder(byteArrayOf()).build()
        }
        setupE2EE(false)
        try {
            // Default for validFrom and validUntil is Jan 1, 1970 to Feb 7, 2106
            var validFrom: Long = 0
            var validUntil = Int.MAX_VALUE * 1000L * 2
            if (cSettings.validFrom != null) {
                validFrom = cSettings.validFrom.toEpochMilliseconds()
            }
            if (cSettings.validUntil != null) {
                validUntil = cSettings.validUntil.toEpochMilliseconds()
            }
            val signingAlgorithmCose = if (cSettings.signingAlgorithm == Algorithm.UNSET) {
                null
            } else {
                cSettings.signingAlgorithm.coseAlgorithmIdentifier
            }
            val request0 = CreateKeyRequest0(
                cSettings.keyPurposes,
                cSettings.ecCurve,
                signingAlgorithmCose,
                validFrom,
                validUntil,
                cSettings.passphraseRequired,
                cSettings.userAuthenticationRequired,
                CloudUserAuthType.encodeSet(cSettings.userAuthenticationTypes),
                cSettings.attestationChallenge
            )
            val response0 = CloudSecureAreaProtocol.Command.fromCbor(communicateE2EE(request0.toCbor())) as CreateKeyResponse0
            val newKeyAlias = storageTable.insert(
                key = alias,
                partitionId = identifier,
                data = ByteString()
            )
            val localKeyInfo = platformSecureArea.createKey(
                alias = getLocalKeyAlias(newKeyAlias),
                createKeySettings = cloudSecureAreaGetPlatformSecureAreaCreateKeySettings(
                    challenge = ByteString(response0.cloudChallenge),
                    keyPurposes = setOf(KeyPurpose.SIGN),
                    userAuthenticationRequired = cSettings.userAuthenticationRequired,
                    userAuthenticationTypes = cSettings.userAuthenticationTypes
                )
            )
            val request1 = CreateKeyRequest1(
                localKeyInfo.publicKey.toCoseKey(),
                localKeyInfo.attestation.certChain,
                response0.serverState
            )
            val response1 = CloudSecureAreaProtocol.Command.fromCbor(communicateE2EE(request1.toCbor())) as CreateKeyResponse1
            storageTable.update(
                key = newKeyAlias,
                partitionId = identifier,
                data = ByteString(response1.serverState)
            )
            saveKeyMetadata(newKeyAlias, cSettings, response1.remoteKeyAttestation)
            return getKeyInfo(newKeyAlias)
        } catch (e: Exception) {
            throw CloudException(e)
        }
    }

    override suspend fun deleteKey(alias: String) {
        platformSecureArea.deleteKey(getLocalKeyAlias(alias))
        storageTable.delete(
            key = alias,
            partitionId = identifier
        )
        storageTable.delete(
            key = METADATA_PREFIX + alias,
            partitionId = identifier
        )
    }

    private suspend fun<T> interactionHelper(
        alias: String,
        keyUnlockData: KeyUnlockData?,
        op: suspend (unlockData: KeyUnlockData?) -> T,
    ): T {
        if (keyUnlockData !is KeyUnlockInteractive) {
            return op(keyUnlockData)
        }
        val cloudKeyUnlockData = CloudKeyUnlockData(this, alias)
        do {
            try {
                return op(cloudKeyUnlockData)
            } catch (e: CloudKeyLockedException) {
                // TODO: translations
                val defaultSubtitle = if (getPassphraseConstraints().requireNumerical) {
                    "Enter the PIN associated with the document"
                } else {
                    "Enter the passphrase associated with the document"
                }
                when (e.reason) {
                    CloudKeyLockedException.Reason.WRONG_PASSPHRASE -> {
                        cloudKeyUnlockData.passphrase = requestPassphrase(
                            // TODO: translations
                            title = keyUnlockData.title ?: "Verify it's you",
                            subtitle = keyUnlockData.subtitle ?: defaultSubtitle,
                            passphraseConstraints = getPassphraseConstraints(),
                            passphraseEvaluator = { enteredPassphrase: String ->
                                when (val result = checkPassphrase(enteredPassphrase)) {
                                    CloudSecureAreaProtocol.RESULT_WRONG_PASSPHRASE -> {
                                        if (getPassphraseConstraints().requireNumerical) {
                                            "Wrong PIN entered. Try again"
                                        } else {
                                            "Wrong passphrase entered. Try again"
                                        }
                                    }
                                    CloudSecureAreaProtocol.RESULT_TOO_MANY_PASSPHRASE_ATTEMPTS -> {
                                        "Too many attempts. Try again later"
                                    }
                                    CloudSecureAreaProtocol.RESULT_OK -> {
                                        null
                                    }
                                    else -> {
                                        Logger.w(TAG, "Unexpected result $result when checking passphrase")
                                        null
                                    }
                                }
                            }
                        )
                        if (cloudKeyUnlockData.passphrase == null) {
                            throw KeyLockedException("User canceled passphrase prompt")
                        }
                    }
                    CloudKeyLockedException.Reason.USER_NOT_AUTHENTICATED -> {
                        // This should never happen when using KeyUnlockInteractive...
                        throw IllegalStateException("Unexpected reason USER_NOT_AUTHENTICATED")
                    }
                }
            }
        } while (true)
    }

    override suspend fun sign(
        alias: String,
        dataToSign: ByteArray,
        keyUnlockData: KeyUnlockData?
    ): EcSignature {
        return interactionHelper(
            alias,
            keyUnlockData,
            op = { unlockData -> signNonInteractive(alias, dataToSign, unlockData) }
        )
    }

    private suspend fun signNonInteractive(
        alias: String,
        dataToSign: ByteArray,
        keyUnlockData: KeyUnlockData?
    ): EcSignature {
        var resultingSignature: EcSignature? = null
        val keyContext = storageTable.get(
            key = alias,
            partitionId = identifier
        )
        setupE2EE(false)
        var response: ByteArray

        // Throw if passphrase is required and not passed in.
        val keyInfo = getKeyInfo(alias)
        if (keyInfo.isPassphraseRequired) {
            if (keyUnlockData == null || (keyUnlockData as CloudKeyUnlockData).passphrase == null) {
                throw CloudKeyLockedException(
                    CloudKeyLockedException.Reason.WRONG_PASSPHRASE,
                    "No passphrase supplied"
                )
            }
        }

        val request0 = SignRequest0(dataToSign, keyContext!!.toByteArray())
        response = communicateE2EE(request0.toCbor())
        val response0 = CloudSecureAreaProtocol.Command.fromCbor(response) as SignResponse0
        val dataToSignLocally = Cbor.encode(
            CborArray.builder()
                .add(response0.cloudNonce)
                .end()
                .build()
        )
        val signatureLocal = platformSecureArea.sign(
            alias = getLocalKeyAlias(alias),
            dataToSign = dataToSignLocally,
        )
        val request1 = SignRequest1(
            signatureLocal,
            (keyUnlockData as? CloudKeyUnlockData)?.passphrase,
            response0.serverState
        )
        do {
            var tryAgain = false

            response = communicateE2EE(request1.toCbor())
            val response1 = CloudSecureAreaProtocol.Command.fromCbor(response) as SignResponse1
            when (response1.result) {
                CloudSecureAreaProtocol.RESULT_OK -> {
                    resultingSignature = response1.signature
                }

                CloudSecureAreaProtocol.RESULT_WRONG_PASSPHRASE -> {
                    throw CloudKeyLockedException(
                        CloudKeyLockedException.Reason.WRONG_PASSPHRASE,
                        "Wrong passphrase supplied"
                    )
                }

                CloudSecureAreaProtocol.RESULT_TOO_MANY_PASSPHRASE_ATTEMPTS -> {
                    delayForBruteforceMitigation(response1.waitDurationMillis.milliseconds)
                    tryAgain = true
                }

                else -> throw CloudException("Unexpected result ${response1.result}")
            }
        } while (tryAgain)
        return resultingSignature!!
    }

    override suspend fun keyAgreement(
        alias: String,
        otherKey: EcPublicKey,
        keyUnlockData: KeyUnlockData?
    ): ByteArray {
        return interactionHelper(
            alias,
            keyUnlockData,
            op = { unlockData -> keyAgreementNonInteractive(alias, otherKey, unlockData) }
        )
    }

    private suspend fun keyAgreementNonInteractive(
        alias: String,
        otherKey: EcPublicKey,
        keyUnlockData: KeyUnlockData?
    ): ByteArray {
        var zab: ByteArray? = null
        val keyContext = storageTable.get(key = alias, partitionId = identifier)
        setupE2EE(false)
        var response: ByteArray

        // Throw if passphrase is required and not passed in.
        val keyInfo = getKeyInfo(alias)
        if (keyInfo.isPassphraseRequired) {
            if (keyUnlockData == null || (keyUnlockData as CloudKeyUnlockData).passphrase == null) {
                throw CloudKeyLockedException(
                    CloudKeyLockedException.Reason.WRONG_PASSPHRASE,
                    "No passphrase supplied"
                )
            }
        }

        val request0 = KeyAgreementRequest0(otherKey.toCoseKey(), keyContext!!.toByteArray())
        response = communicateE2EE(request0.toCbor())
        val response0 = CloudSecureAreaProtocol.Command.fromCbor(response) as KeyAgreementResponse0
        val dataToSignLocally = Cbor.encode(
            CborArray.builder()
                .add(response0.cloudNonce)
                .end()
                .build()
        )
        val signatureLocal = platformSecureArea.sign(
            alias = getLocalKeyAlias(alias),
            dataToSign = dataToSignLocally,
        )
        val request1 = KeyAgreementRequest1(
            signatureLocal,
            (keyUnlockData as? CloudKeyUnlockData)?.passphrase,
            response0.serverState
        )
        do {
            var tryAgain = false

            response = communicateE2EE(request1.toCbor())
            val response1 = CloudSecureAreaProtocol.Command.fromCbor(response) as KeyAgreementResponse1
            when (response1.result) {
                CloudSecureAreaProtocol.RESULT_OK -> {
                    zab = response1.zab
                }

                CloudSecureAreaProtocol.RESULT_WRONG_PASSPHRASE -> {
                    throw CloudKeyLockedException(
                        CloudKeyLockedException.Reason.WRONG_PASSPHRASE,
                        "Wrong passphrase supplied"
                    )
                }

                CloudSecureAreaProtocol.RESULT_TOO_MANY_PASSPHRASE_ATTEMPTS -> {
                    delayForBruteforceMitigation(response1.waitDurationMillis.milliseconds)
                    tryAgain = true
                }

                else -> throw CloudException("Unexpected result ${response1.result}")
            }
        } while (tryAgain)
        return zab!!
    }

    private suspend fun checkPassphrase(
        passphrase: String,
    ): Int {
        setupE2EE(false)
        val request = CloudSecureAreaProtocol.CheckPassphraseRequest(passphrase)
        val response = communicateE2EE(request.toCbor())
        val commandResponse = CloudSecureAreaProtocol.Command.fromCbor(response) as CloudSecureAreaProtocol.CheckPassphraseResponse
        return commandResponse.result
    }

    override suspend fun getKeyInfo(alias: String): CloudKeyInfo {
        val data = storageTable.get(key = METADATA_PREFIX + alias, partitionId = identifier)
            ?: throw IllegalArgumentException("No key with given alias")
        val map = Cbor.decode(data.toByteArray())
        val keyPurposes = map["keyPurposes"].asNumber
        val userAuthenticationRequired = map["userAuthenticationRequired"].asBoolean
        val userAuthenticationTypes = CloudUserAuthType.decodeSet(map["userAuthenticationTypes"].asNumber)
        val isPassphraseRequired = map["isPassphraseRequired"].asBoolean
        val signatureAlgorithm = if (map.hasKey("signingAlgorithm")) {
            Algorithm.fromInt(map["signingAlgorithm"].asNumber.toInt())
        } else {
            Algorithm.UNSET
        }
        var validFrom: Instant? = null
        var validUntil: Instant? = null
        if (map.hasKey("validFrom")) {
            validFrom = Instant.fromEpochMilliseconds(map["validFrom"].asNumber)
        }
        if (map.hasKey("validUntil")) {
            validUntil = Instant.fromEpochMilliseconds(map["validUntil"].asNumber)
        }
        val attestationCertChain = map["attestationCertChain"].asX509CertChain
        return CloudKeyInfo(
            alias,
            KeyAttestation(attestationCertChain.certificates[0].ecPublicKey, attestationCertChain),
            KeyPurpose.decodeSet(keyPurposes),
            signatureAlgorithm,
            userAuthenticationRequired,
            userAuthenticationTypes,
            validFrom,
            validUntil,
            isPassphraseRequired,
        )
    }

    override suspend fun getKeyInvalidated(alias: String): Boolean {
        return platformSecureArea.getKeyInvalidated(getLocalKeyAlias(alias))
    }

    private suspend fun saveKeyMetadata(
        alias: String,
        settings: CloudCreateKeySettings,
        attestationCertChain: X509CertChain
    ) {
        Logger.d(TAG, "attestation len = ${attestationCertChain.certificates.size}")
        val map = CborMap.builder()
        map.put("keyPurposes", KeyPurpose.encodeSet(settings.keyPurposes))
        map.put("signingAlgorithm", settings.signingAlgorithm.coseAlgorithmIdentifier)
        map.put("userAuthenticationRequired", settings.userAuthenticationRequired)
        map.put("userAuthenticationTypes", CloudUserAuthType.encodeSet(settings.userAuthenticationTypes))
        if (settings.validFrom != null) {
            map.put("validFrom", settings.validFrom.toEpochMilliseconds())
        }
        if (settings.validUntil != null) {
            map.put("validUntil", settings.validUntil.toEpochMilliseconds())
        }
        map.put("isPassphraseRequired", settings.passphraseRequired)
        map.put("attestationCertChain", attestationCertChain.toDataItem())
        // If the key with the given alias exists, it is silently overwritten.
        storageTable.delete(
            key = METADATA_PREFIX + alias,
            partitionId = identifier
        )
        storageTable.insert(
            key = METADATA_PREFIX + alias,
            partitionId = identifier,
            data = ByteString(Cbor.encode(map.end().build()))
        )
    }

    internal fun getLocalKeyAlias(alias: String): String {
        return "${identifier}_alias_${alias}"
    }

    companion object {
        const val IDENTIFIER_PREFIX = "CloudSecureArea"

        private const val TAG = "CloudSecureArea"

        // Special keys in storage
        private const val DEVICE_ATTESTATION_ID = "[DeviceAttestationId]"
        private const val BINDING_KEY = "[BindingKey]"
        private const val REGISTRATION_CONTEXT = "[RegistrationContext]"
        private const val PASSPHRASE_CONSTRAINTS = "[PassphraseConstraints]"

        // Prefix for metadata storage key
        private const val METADATA_PREFIX = "@"

        // Prefix to use for the partition for the platform-specific SecureArea used for local keys.
        //
        private const val PLATFORM_SECURE_AREA_IDENTIFIER_PREFIX = "CloudSecureArea_"

        /**
         * Creates an instance of [CloudSecureArea].
         *
         * The given [identifier] must start with `CloudSecureArea` and if the application is only using
         * a single instance of [CloudSecureArea], just using this string as the identifier is fine.
         *
         * For applications using multiple instances of [CloudSecureArea], a common format for the
         * identifier is `CloudSecureArea?id=1234567890&url=https://csa.example.com/csa-server` where the
         * `id` parameter is a unique identifier for the document and the `url` parameter
         * contains the URL to connect to.
         *
         * The [identifier] is used as a prefix for the Android Keystore alias space and also as a prefix
         * for all keys used in the passed-in [storageTable]. As such, it's safe to use the same
         * [StorageEngine] instance for multiple [CloudSecureArea] instances.
         *
         * @param storage the storage engine to use for storing key material.
         * @param identifier an identifier for the Cloud Secure Area.
         * @param serverUrl the URL the Cloud Secure Area is using.
         */
        suspend fun create(
            storage: Storage,
            identifier: String,
            serverUrl: String
        ): CloudSecureArea {
            val secureArea = CloudSecureArea(
                storage.getTable(tableSpec),
                identifier,
                serverUrl
            )
            secureArea.initialize()
            return secureArea
        }

        private val tableSpec = StorageTableSpec(
            name = "CloudSecureArea",
            supportPartitions = true,
            supportExpiration = false
        )
    }
}