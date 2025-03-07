package org.multipaz.securearea.cloud

import org.multipaz.asn1.ASN1Integer
import org.multipaz.asn1.OID
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.cose.CoseKey
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.device.DeviceAttestation
import org.multipaz.device.DeviceAttestationIos
import org.multipaz.device.DeviceAttestationValidationData
import org.multipaz.securearea.KeyPurpose
import org.multipaz.securearea.cloud.CloudSecureAreaProtocol.CreateKeyRequest0
import org.multipaz.securearea.cloud.CloudSecureAreaProtocol.CreateKeyResponse1
import org.multipaz.securearea.cloud.CloudSecureAreaProtocol.E2EEResponse
import org.multipaz.securearea.cloud.CloudSecureAreaProtocol.E2EESetupRequest0
import org.multipaz.securearea.cloud.CloudSecureAreaProtocol.E2EESetupRequest1
import org.multipaz.securearea.cloud.CloudSecureAreaProtocol.E2EESetupResponse0
import org.multipaz.securearea.cloud.CloudSecureAreaProtocol.E2EESetupResponse1
import org.multipaz.securearea.cloud.CloudSecureAreaProtocol.RegisterRequest0
import org.multipaz.securearea.cloud.CloudSecureAreaProtocol.RegisterRequest1
import org.multipaz.securearea.cloud.CloudSecureAreaProtocol.RegisterResponse0
import org.multipaz.securearea.cloud.CloudSecureAreaProtocol.RegisterResponse1
import org.multipaz.securearea.software.SoftwareCreateKeySettings
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.util.AndroidAttestationExtensionParser
import org.multipaz.util.Logger
import org.multipaz.util.toHex
import org.multipaz.util.validateAndroidKeyAttestation
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.io.bytestring.ByteString
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.random.Random

/**
 * A reference implementation of the Cloud Secure Area server.
 *
 * This code is not intended for production use.
 *
 * @param serverSecureAreaBoundKey the secret key used to encrypt/decrypt state externally stored.
 * @param attestationKey the private key used to sign attestations for keys created by clients.
 * @param attestationKeyCertification a certification of the attestation key.
 * @param cloudRootAttestationKey the private key used to sign attestations for `CloudBindingKey`.
 * @param cloudRootAttestationKeyCertification a certification of the attestation key for `CloudBindingKey`.
 * @param e2eeKeyLimitSeconds Re-keying interval for end-to-end encryption.
 * @param iosReleaseBuild Whether a release build is required on iOS. When `false`, both debug and release builds
 *   are accepted.
 * @param iosAppIdentifier iOS app identifier that consists of a team id followed by a dot and app bundle name. If
 *   `null`, any app identifier is accepted. It must not be `null` if [iosReleaseBuild] is `true`
 * @param androidGmsAttestation whether to require attestations made for local key on clients is using the Google root.
 * @param androidVerifiedBootGreen whether to require clients are in verified boot state green.
 * @param androidAppSignatureCertificateDigests the allowed list of applications that can use the
 *   service. Each element is the bytes of the SHA-256 of a signing certificate, see the
 *   [Signature](https://developer.android.com/reference/android/content/pm/Signature) class in
 *   the Android SDK for details. If empty, allow any app.
 * @param passphraseFailureEnforcer the [PassphraseFailureEnforcer] to use.
 */
class CloudSecureAreaServer(
    private val serverSecureAreaBoundKey: ByteArray,
    private val attestationKey: EcPrivateKey,
    private val attestationKeySignatureAlgorithm: Algorithm,
    private val attestationKeyIssuer: String,
    private val attestationKeyCertification: X509CertChain,
    private val cloudRootAttestationKey: EcPrivateKey,
    private val cloudRootAttestationKeySignatureAlgorithm: Algorithm,
    private val cloudRootAttestationKeyIssuer: String,
    private val cloudRootAttestationKeyCertification: X509CertChain,
    private val e2eeKeyLimitSeconds: Int,
    private val iosReleaseBuild: Boolean,
    private val iosAppIdentifier: String?,
    private val androidGmsAttestation: Boolean,
    private val androidVerifiedBootGreen: Boolean,
    private val androidAppSignatureCertificateDigests: List<ByteString>,
    private val passphraseFailureEnforcer: PassphraseFailureEnforcer
) {
    private fun encryptState(plaintext: ByteArray): ByteArray {
        val counter = encryptionGcmCounter
        val iv = ByteBuffer.allocate(12)
        iv.putInt(0, 0x00000000)
        iv.putLong(counter)
        val ciphertext = Crypto.encrypt(Algorithm.A128GCM, serverSecureAreaBoundKey, iv.array(), plaintext)
        return iv.array() + ciphertext
    }

    private fun decryptState(cipherText: ByteArray): ByteArray {
        require(cipherText.size >= 12) { "input too short" }
        val iv = cipherText.copyOfRange(0, 12)
        val encryptedData = cipherText.copyOfRange(12, cipherText.size)
        val plaintext = Crypto.decrypt(Algorithm.A128GCM, serverSecureAreaBoundKey, iv, encryptedData)
        return plaintext
    }

    @CborSerializable
    data class RegisterState(
        var clientVersion: String = "",
        var attestationChallenge: ByteString? = null,
        var cloudChallenge: ByteArray? = null,
        var cloudBindingKey: CoseKey? = null,
        var deviceBindingKey: CoseKey? = null,
        var clientPassphraseSalt: ByteArray = byteArrayOf(),
        var clientSaltedPassphrase: ByteArray = byteArrayOf(),
        var registrationComplete: Boolean = false,
        var clientId: String? = null,
        var deviceAttestation: DeviceAttestation? = null
    ) {
        companion object
    }

    private fun RegisterState.encrypt(): ByteArray = encryptState(toCbor())

    private fun RegisterState.Companion.decrypt(encryptedState: ByteArray) =
        fromCbor(decryptState(encryptedState))

    private fun doRegisterRequest0(request0: RegisterRequest0,
                                   remoteHost: String): Pair<Int, ByteArray> {
        val state = RegisterState()
        state.clientVersion = request0.clientVersion
        state.registrationComplete = false
        state.clientPassphraseSalt = byteArrayOf()
        state.clientSaltedPassphrase = byteArrayOf()
        state.attestationChallenge = ByteString(Random.Default.nextBytes(32))
        state.cloudChallenge = Random.Default.nextBytes(32)
        val response0 = RegisterResponse0(
            attestationChallenge = state.attestationChallenge!!,
            cloudChallenge = state.cloudChallenge!!,
            serverState = state.encrypt()
        )
        Logger.d(TAG, "$remoteHost: RegisterRequest0: Sending challenge to client")
        return Pair(200, response0.toCbor())
    }

    private fun doRegisterRequest1(request1: RegisterRequest1,
                                   remoteHost: String): Pair<Int, ByteArray> {
        val state = RegisterState.decrypt(request1.serverState)

        // Validate device attestation and assertion
        try {
            request1.deviceAttestation.validate(
                DeviceAttestationValidationData(
                    attestationChallenge = state.attestationChallenge!!,
                    iosReleaseBuild = iosReleaseBuild,
                    iosAppIdentifier = iosAppIdentifier,
                    androidGmsAttestation = androidGmsAttestation,
                    androidVerifiedBootGreen = androidVerifiedBootGreen,
                    androidAppSignatureCertificateDigests = androidAppSignatureCertificateDigests
                )
            )
        } catch (e: Throwable) {
            Logger.w(TAG, "$remoteHost: RegisterRequest1: Device Attestation did not validate", e)
            e.printStackTrace()
            return Pair(403, e.message!!.toByteArray())
        }
        state.deviceAttestation = request1.deviceAttestation

        // iOS devices don't have key attestation for the locally created key but other platforms do, including
        // Android. So we check that the attestation is valid and matches what we requested.
        if (state.deviceAttestation !is DeviceAttestationIos) {
            try {
                validateAndroidKeyAttestation(
                    chain = request1.deviceBindingKeyAttestation!!,
                    challenge = ByteString(state.cloudChallenge!!),
                    requireGmsAttestation = androidGmsAttestation,
                    requireVerifiedBootGreen = androidVerifiedBootGreen,
                    requireAppSignatureCertificateDigests = androidAppSignatureCertificateDigests
                )
                // Check that device created the key without user authentication.
                val attestation = AndroidAttestationExtensionParser(request1.deviceBindingKeyAttestation!!.certificates[0])
                check(attestation.getUserAuthenticationType() == 0L)
            } catch (e: Throwable) {
                Logger.w(TAG, "$remoteHost: RegisterRequest1: Android Keystore attestation did not validate", e)
                return Pair(403, e.message!!.toByteArray())
            }
        }
        state.deviceBindingKey = request1.deviceBindingKey

        // This is used to re-initialize E2EE so it's used quite a bit. Give it a long validity period.
        val cloudBindingKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val cloudBindingKeyValidFrom = Clock.System.now()
        val cloudBindingKeyValidUntil = cloudBindingKeyValidFrom.plus(
            DateTimePeriod(years = 10),
            TimeZone.currentSystemDefault()
        )
        val cloudBindingKeyAttestation = X509CertChain(
            listOf(
                X509Cert.Builder(
                    publicKey = cloudBindingKey.publicKey,
                    signingKey = cloudRootAttestationKey,
                    signatureAlgorithm = cloudRootAttestationKeySignatureAlgorithm,
                    serialNumber = ASN1Integer(1L),
                    subject = X500Name.fromName("CN=Cloud Secure Area Cloud Binding Key"),
                    issuer = X500Name.fromName(cloudRootAttestationKeyIssuer),
                    validFrom = cloudBindingKeyValidFrom,
                    validUntil = cloudBindingKeyValidUntil
                )
                    .includeSubjectKeyIdentifier()
                    .setAuthorityKeyIdentifierToCertificate(cloudRootAttestationKeyCertification.certificates[0])
                    .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
                    .addExtension(
                        oid = OID.X509_EXTENSION_MULTIPAZ_KEY_ATTESTATION.oid,
                        critical = false,
                        value = CloudAttestationExtension(
                            challenge = ByteString(request1.deviceChallenge),
                            passphrase = false,
                            userAuthentication = setOf()
                        ).encode().toByteArray()
                    )
                    .build()
            ) + cloudRootAttestationKeyCertification.certificates
        )
        state.cloudBindingKey = cloudBindingKey.toCoseKey()
        val response1 = RegisterResponse1(
            cloudBindingKeyAttestation,
            state.encrypt()
        )
        Logger.d(TAG, "$remoteHost: RegisterRequest1: Client successfully registered")
        return Pair(200, response1.toCbor())
    }

    @CborSerializable
    data class E2EEState(
        var context: RegisterState? = null,
        var cloudNonce: ByteArray? = null,
        var skDevice: ByteArray? = null,
        var skCloud: ByteArray? = null,
        var encryptedCounter: Int = 0,
        var decryptedCounter: Int = 0,
        var derivationTimestamp: Long = 0,
    ) {
        companion object
    }

    private fun E2EEState.encrypt(): ByteArray = encryptState(toCbor())

    private fun E2EEState.Companion.decrypt(encryptedState: ByteArray) =
        fromCbor(decryptState(encryptedState))

    private fun doE2EESetupRequest0(request0: E2EESetupRequest0,
                                    remoteHost: String): Pair<Int, ByteArray> {
        val state = E2EEState()
        state.context = RegisterState.decrypt(request0.registrationContext)
        state.cloudNonce = Random.Default.nextBytes(32)
        val response0 = E2EESetupResponse0(
            state.cloudNonce!!,
            state.encrypt()
        )
        Logger.d(TAG, "$remoteHost: E2EESetupRequest0: Sending nonce to client")
        return Pair(200, response0.toCbor())
    }

    private fun doE2EESetupRequest1(request1: E2EESetupRequest1,
                                    remoteHost: String): Pair<Int, ByteArray> {
        val state = E2EEState.decrypt(request1.serverState)
        val dataThatWasSigned = Cbor.encode(
            CborArray.builder()
                .add(request1.eDeviceKey.toDataItem())
                .add(state.cloudNonce!!)
                .add(request1.deviceNonce)
                .end()
                .build()
        )
        check(Crypto.checkSignature(
            state.context!!.deviceBindingKey!!.ecPublicKey,
            dataThatWasSigned,
            Algorithm.ES256,
            request1.signature
        )) { "Error verifying signature" }

        state.context!!.deviceAttestation!!.validateAssertion(request1.deviceAssertion)

        // OK, the device's EDeviceKey checks out. Time for us to generate ECloudKey,
        // sign over it with CloudBindingKey, and send it to the device for verification
        val eCloudKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val dataToSign = Cbor.encode(
            CborArray.builder()
                .add(eCloudKey.publicKey.toCoseKey().toDataItem())
                .add(state.cloudNonce!!)
                .add(request1.deviceNonce)
                .end()
                .build()
        )
        val signature = Crypto.sign(
            state.context!!.cloudBindingKey!!.ecPrivateKey,
            Algorithm.ES256,
            dataToSign
        )

        // Also derive SKDevice and SKCloud, and stash in state since we're going to need this later
        val zab = Crypto.keyAgreement(eCloudKey, request1.eDeviceKey.ecPublicKey)
        val salt = Crypto.digest(Algorithm.SHA256,
            Cbor.encode(
                CborArray.builder()
                    .add(request1.deviceNonce)
                    .add(state.cloudNonce!!)
                    .end()
                    .build()
            )
        )
        state.skDevice = Crypto.hkdf(
            Algorithm.HMAC_SHA256,
            zab,
            salt,
            "SKDevice".toByteArray(),
            32
        )
        state.skCloud = Crypto.hkdf(
            Algorithm.HMAC_SHA256,
            zab,
            salt,
            "SKCloud".toByteArray(),
            32
        )
        state.encryptedCounter = 1
        state.decryptedCounter = 1
        state.derivationTimestamp = System.currentTimeMillis()
        val response1 = E2EESetupResponse1(
            eCloudKey.publicKey.toCoseKey(),
            signature,
            state.encrypt()
        )
        Logger.d(TAG, "$remoteHost: E2EESetupRequest1: Encrypted tunnel has been set up")
        return Pair<Int, ByteArray>(200, response1.toCbor())
    }

    private fun doRegisterStage2Request0(
        request0: CloudSecureAreaProtocol.RegisterStage2Request0,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        // Protect against an attacker trying to change the passphrase
        if (e2eeState.context!!.registrationComplete) {
            Logger.w(TAG, "$remoteHost: Registration stage 2 already completed")
            return Pair(403, "Registration stage 2 already completed".toByteArray())
        }
        e2eeState.context!!.registrationComplete = true
        e2eeState.context!!.clientPassphraseSalt = Random.Default.nextBytes(32)
        e2eeState.context!!.clientSaltedPassphrase = Crypto.digest(
            Algorithm.SHA256,
            e2eeState.context!!.clientPassphraseSalt + request0.passphrase.encodeToByteArray()
        )
        // This is used for [PassphraseFailureEnforcer].
        e2eeState.context!!.clientId = Crypto.digest(
            Algorithm.SHA256,
            Cbor.encode(e2eeState.context!!.deviceBindingKey!!.toDataItem())
        ).toHex()
        val response0 = CloudSecureAreaProtocol.RegisterStage2Response0(
            e2eeState.context!!.encrypt()
        )
        val encryptedResponse0 = E2EEResponse(
            encryptToDevice(e2eeState, response0.toCbor()),
            e2eeState.encrypt()
        )
        Logger.d(TAG, "$remoteHost: doRegisterStage2Request0: Completed")
        return Pair(200, encryptedResponse0.toCbor())
    }

    @CborSerializable
    data class CreateKeyState(
        var challenge: ByteArray? = null,
        var cloudChallenge: ByteArray? = null,
        var purposes: Set<KeyPurpose> = emptySet(),
        var curve: EcCurve = EcCurve.P256,
        var signingAlgorithm: Int? = null,
        var validFromMillis: Long = 0,
        var validUntilMillis: Long = 0,
        var passphraseRequired: Boolean = false,
        var userAuthenticationRequired: Boolean = false,
        var userAuthenticationTypes: Long = 0,
        var cloudKeyStorage: ByteArray? = null,
        var localKey: CoseKey? = null,
    ) {
        companion object
    }

    private fun encryptCreateKeyState(state: CreateKeyState): ByteArray {
        return encryptState(state.toCbor())
    }

    private fun decryptCreateKeyState(encryptedState: ByteArray): CreateKeyState {
        return CreateKeyState.fromCbor(decryptState(encryptedState))
    }

    private fun doCreateKeyRequest0(
        request0: CreateKeyRequest0,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        val state = CreateKeyState()
        state.challenge = request0.challenge
        state.cloudChallenge = Random.Default.nextBytes(32)
        state.purposes = request0.purposes
        state.curve = request0.curve
        state.signingAlgorithm = request0.signingAlgorithm
        state.validFromMillis = request0.validFromMillis
        state.validUntilMillis = request0.validUntilMillis
        state.passphraseRequired = request0.passphraseRequired
        state.userAuthenticationRequired = request0.userAuthenticationRequired
        state.userAuthenticationTypes = request0.userAuthenticationTypes
        val response0 = CloudSecureAreaProtocol.CreateKeyResponse0(
            state.cloudChallenge!!,
            encryptCreateKeyState(state)
        )
        val encryptedResponse0 = E2EEResponse(
            encryptToDevice(e2eeState, response0.toCbor()),
            e2eeState.encrypt()
        )
        Logger.d(TAG, "$remoteHost: CreateKeyRequest0: Sending challenge to client")
        return Pair(200, encryptedResponse0.toCbor())
    }

    private suspend fun doCreateKeyRequest1(
        request1: CloudSecureAreaProtocol.CreateKeyRequest1,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        val state = decryptCreateKeyState(request1.serverState)

        // iOS devices don't have key attestation for the locally created key but other platforms do, including
        // Android. So we check that the attestation is valid and matches what we requested.
        if (e2eeState.context!!.deviceAttestation !is DeviceAttestationIos) {
            try {
                validateAndroidKeyAttestation(
                    chain = request1.localKeyAttestation!!,
                    challenge = ByteString(state.cloudChallenge!!),
                    requireGmsAttestation = androidGmsAttestation,
                    requireVerifiedBootGreen = androidVerifiedBootGreen,
                    requireAppSignatureCertificateDigests = androidAppSignatureCertificateDigests
                )
                // Check that device created the key with the requested user authentication.
                val attestation = AndroidAttestationExtensionParser(request1.localKeyAttestation!!.certificates[0])
                if (state.userAuthenticationRequired) {
                    val attestationExtensionUserAuthType = attestation.getUserAuthenticationType()
                    check(attestationExtensionUserAuthType == state.userAuthenticationTypes) {
                        "Requested userAuthType ${state.userAuthenticationTypes} for the local key but the Android " +
                        "attestation extension contains ${attestationExtensionUserAuthType}"
                    }
                } else {
                    check(attestation.getUserAuthenticationType() == 0L)
                }
            } catch (e: Throwable) {
                throw IllegalStateException("doCreateKeyRequest1: Android Keystore attestation did not validate", e)
            }
        }

        state.localKey = request1.localKey
        val storage = EphemeralStorage()
        val secureArea = SoftwareSecureArea.create(storage)
        val builder = SoftwareCreateKeySettings.Builder()
            .setValidityPeriod(
                Instant.fromEpochMilliseconds(state.validFromMillis),
                Instant.fromEpochMilliseconds(state.validUntilMillis)
            )
            .setKeyPurposes(state.purposes)
            .setEcCurve(state.curve)
        secureArea.createKey("CloudKey", builder.build())

        val keyInfo = secureArea.getKeyInfo("CloudKey")

        val attestationCert = X509Cert.Builder(
            publicKey = keyInfo.publicKey,
            signingKey = attestationKey,
            signatureAlgorithm = attestationKeySignatureAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Cloud Secure Area Key"),
            issuer = X500Name.fromName(attestationKeyIssuer),
            validFrom = Instant.fromEpochMilliseconds(state.validFromMillis),
            validUntil = Instant.fromEpochMilliseconds(state.validUntilMillis)
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(attestationKeyCertification.certificates[0])
            .setKeyUsage(keyInfo.keyPurposes.map { keyPurpose ->
                when (keyPurpose) {
                    KeyPurpose.AGREE_KEY -> X509KeyUsage.KEY_AGREEMENT
                    KeyPurpose.SIGN -> X509KeyUsage.DIGITAL_SIGNATURE
                }
            }.toSet())
            .addExtension(
                oid = OID.X509_EXTENSION_MULTIPAZ_KEY_ATTESTATION.oid,
                critical = false,
                value = CloudAttestationExtension(
                    challenge = ByteString(state.challenge!!),
                    passphrase = state.passphraseRequired,
                    userAuthentication = CloudUserAuthType.decodeSet(state.userAuthenticationTypes)
                ).encode().toByteArray()
            )
            .build()

        state.cloudKeyStorage = storage.serialize().toByteArray()
        val response1 = CreateKeyResponse1(
            X509CertChain(listOf(attestationCert) + attestationKeyCertification.certificates),
            encryptCreateKeyState(state)
        )
        val encryptedResponse1 = E2EEResponse(
            encryptToDevice(e2eeState, response1.toCbor()),
            e2eeState.encrypt()
        )
        Logger.d(TAG, "$remoteHost: CreateKeyRequest1: Created key for client")
        return Pair(200, encryptedResponse1.toCbor())
    }

    @CborSerializable
    data class SignState(
        var keyContext: CreateKeyState? = null,
        var dataToSign: ByteArray? = null,
        var cloudNonce: ByteArray? = null,
    ) {
        companion object
    }

    private fun encryptSignState(state: SignState): ByteArray {
        return encryptState(state.toCbor())
    }

    private fun decryptSignState(encryptedState: ByteArray): SignState {
        return SignState.fromCbor(decryptState(encryptedState))
    }

    private fun doSignRequest0(
        request0: CloudSecureAreaProtocol.SignRequest0,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        val state = SignState()
        state.keyContext = decryptCreateKeyState(request0.keyContext)
        state.dataToSign = request0.dataToSign
        state.cloudNonce = Random.Default.nextBytes(32)
        val response0 = CloudSecureAreaProtocol.SignResponse0(
            state.cloudNonce!!,
            encryptSignState(state)
        )
        val encryptedResponse0 = E2EEResponse(
            encryptToDevice(e2eeState, response0.toCbor()),
            e2eeState.encrypt()
        )
        Logger.d(TAG, "$remoteHost: SignRequest0: Sending nonce to client")
        return Pair(200, encryptedResponse0.toCbor())
    }

    private suspend fun doSignRequest1(
        request1: CloudSecureAreaProtocol.SignRequest1,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        try {
            val state = decryptSignState(request1.serverState)
            val dataThatWasSignedLocally = Cbor.encode(
                CborArray.builder()
                    .add(state.cloudNonce!!)
                    .end()
                    .build()
            )
            check(Crypto.checkSignature(
                state.keyContext!!.localKey!!.ecPublicKey,
                dataThatWasSignedLocally,
                Algorithm.ES256,
                request1.signature)) { "Error verifying signature" }

            if (state.keyContext!!.passphraseRequired) {
                val lockedOutDuration =
                    passphraseFailureEnforcer.isLockedOut(e2eeState.context!!.clientId!!)
                if (lockedOutDuration != null) {
                    Logger.i(
                        TAG, "$remoteHost: SignRequest1: Too many wrong passphrase attempts, " +
                                "locked out for $lockedOutDuration"
                    )
                    val response1 = CloudSecureAreaProtocol.SignResponse1(
                        CloudSecureAreaProtocol.RESULT_TOO_MANY_PASSPHRASE_ATTEMPTS,
                        null,
                        lockedOutDuration.inWholeMilliseconds
                    )
                    val encryptedResponse1 = E2EEResponse(
                        encryptToDevice(e2eeState, response1.toCbor()),
                        e2eeState.encrypt()
                    )
                    return Pair(200, encryptedResponse1.toCbor())
                }

                if (!checkPassphrase(
                        remoteHost,
                        request1.passphrase,
                        e2eeState.context!!,
                    )
                ) {
                    Logger.d(TAG, "$remoteHost: SignRequest1: Error checking passphrase")
                    val response1 = CloudSecureAreaProtocol.SignResponse1(
                        CloudSecureAreaProtocol.RESULT_WRONG_PASSPHRASE,
                        null,
                        0L
                    )
                    val encryptedResponse1 = E2EEResponse(
                        encryptToDevice(e2eeState, response1.toCbor()),
                        e2eeState.encrypt()
                    )
                    return Pair(200, encryptedResponse1.toCbor())
                }
            }

            val storage = EphemeralStorage.deserialize(ByteString(state.keyContext!!.cloudKeyStorage!!))
            val secureArea = SoftwareSecureArea.create(storage)
            val signature = secureArea.sign(
                "CloudKey",
                state.dataToSign!!,
                null
            )
            Logger.d(TAG, "$remoteHost: SignRequest1: Signed data for client")
            val response1 = CloudSecureAreaProtocol.SignResponse1(
                CloudSecureAreaProtocol.RESULT_OK,
                signature,
                0L)
            val encryptedResponse1 = E2EEResponse(
                encryptToDevice(e2eeState, response1.toCbor()),
                e2eeState.encrypt()
            )
            return Pair(200, encryptedResponse1.toCbor())
        } catch (e: Throwable) {
            throw IllegalStateException(e)
        }
    }

    private fun checkPassphrase(
        remoteHost: String,
        givenPassphrase: String?,
        context: RegisterState,
    ): Boolean {
        if (!context.registrationComplete) {
            Logger.d(TAG, "$remoteHost: checkPassphrase: Stage 2 registration not complete")
            return false
        }
        if (givenPassphrase == null) {
            passphraseFailureEnforcer.recordFailedPassphraseAttempt(context.clientId!!)
            Logger.d(TAG, "$remoteHost: checkPassphrase: Passphrase required but none was supplied")
            return false
        }
        val saltedPassphrase = Crypto.digest(
            Algorithm.SHA256,
            context.clientPassphraseSalt + givenPassphrase.encodeToByteArray()
        )
        if (!saltedPassphrase.contentEquals(context.clientSaltedPassphrase)) {
            passphraseFailureEnforcer.recordFailedPassphraseAttempt(context.clientId!!)
            Logger.d(TAG, "$remoteHost: checkPassphrase: Wrong passphrase supplied")
            return false
        }
        return true
    }

    @CborSerializable
    data class KeyAgreementState(
        var keyContext: CreateKeyState? = null,
        var otherPublicKey: CoseKey? = null,
        var cloudNonce: ByteArray? = null,
    ) {
        companion object
    }

    private fun KeyAgreementState.encrypt(): ByteArray = encryptState(toCbor())

    private fun KeyAgreementState.Companion.decrypt(encryptedState: ByteArray) =
        fromCbor(decryptState(encryptedState))

    private fun doKeyAgreementRequest0(
        request0: CloudSecureAreaProtocol.KeyAgreementRequest0,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        val state = KeyAgreementState()
        state.keyContext = decryptCreateKeyState(request0.keyContext)
        state.otherPublicKey = request0.otherPublicKey
        state.cloudNonce = Random.Default.nextBytes(32)
        val response0 = CloudSecureAreaProtocol.KeyAgreementResponse0(
            state.cloudNonce!!,
            state.encrypt()
        )
        val encryptedResponse0 = E2EEResponse(
            encryptToDevice(e2eeState, response0.toCbor()),
            e2eeState.encrypt()
        )
        Logger.d(TAG, "$remoteHost: KeyAgreementRequest0: Sending nonce to client")
        return Pair(200, encryptedResponse0.toCbor())
    }

    private suspend fun doKeyAgreementRequest1(
        request1: CloudSecureAreaProtocol.KeyAgreementRequest1,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        try {
            val state = KeyAgreementState.decrypt(request1.serverState)
            val dataThatWasSignedLocally = Cbor.encode(
                CborArray.builder()
                    .add(state.cloudNonce!!)
                    .end()
                    .build()
            )
            check(Crypto.checkSignature(
                state.keyContext!!.localKey!!.ecPublicKey,
                dataThatWasSignedLocally,
                Algorithm.ES256,
                request1.signature
            )) { "Error verifying signature" }

            if (state.keyContext!!.passphraseRequired) {
                val lockedOutDuration =
                    passphraseFailureEnforcer.isLockedOut(e2eeState.context!!.clientId!!)
                if (lockedOutDuration != null) {
                    Logger.i(
                        TAG, "$remoteHost: KeyAgreementRequest1: Too many wrong passphrase attempts, " +
                                "locked out for $lockedOutDuration"
                    )
                    val response1 = CloudSecureAreaProtocol.KeyAgreementResponse1(
                        CloudSecureAreaProtocol.RESULT_TOO_MANY_PASSPHRASE_ATTEMPTS,
                        null,
                        lockedOutDuration.inWholeMilliseconds
                    )
                    val encryptedResponse1 = E2EEResponse(
                        encryptToDevice(e2eeState, response1.toCbor()),
                        e2eeState.encrypt()
                    )
                    return Pair(200, encryptedResponse1.toCbor())
                }

                if (!checkPassphrase(
                        remoteHost,
                        request1.passphrase,
                        e2eeState.context!!,
                    )
                ) {
                    Logger.d(TAG, "$remoteHost: KeyAgreementRequest1: Error checking passphrase")
                    val response1 = CloudSecureAreaProtocol.KeyAgreementResponse1(
                        CloudSecureAreaProtocol.RESULT_WRONG_PASSPHRASE,
                        null,
                        0L
                    )
                    val encryptedResponse1 = E2EEResponse(
                        encryptToDevice(e2eeState, response1.toCbor()),
                        e2eeState.encrypt()
                    )
                    return Pair(200, encryptedResponse1.toCbor())
                }
            }


            val storage = EphemeralStorage.deserialize(
                ByteString(state.keyContext!!.cloudKeyStorage!!))
            val secureArea = SoftwareSecureArea.create(storage)
            var Zab = secureArea.keyAgreement(
                    "CloudKey",
                    state.otherPublicKey!!.ecPublicKey,
                    null
                )
            Logger.d(TAG, "$remoteHost: KeyAgreementRequest1: Calculated Zab")
            val response1 = CloudSecureAreaProtocol.KeyAgreementResponse1(
                CloudSecureAreaProtocol.RESULT_OK,
                Zab,
                0L)
            val encryptedResponse1 = E2EEResponse(
                encryptToDevice(e2eeState, response1.toCbor()),
                e2eeState.encrypt()
            )
            return Pair(200, encryptedResponse1.toCbor())
        } catch (e: Throwable) {
            throw IllegalStateException(e)
        }
    }

    private fun doCheckPassphrase(
        remoteHost: String,
        e2eeState: E2EEState,
        passphrase: String,
    ): Int {
        val lockedOutDuration = passphraseFailureEnforcer.isLockedOut(e2eeState.context!!.clientId!!)
        if (lockedOutDuration != null) {
            Logger.i(
                TAG, "$remoteHost: CheckPassphraseRequest: Too many wrong passphrase attempts, " +
                        "locked out for $lockedOutDuration"
            )
            return CloudSecureAreaProtocol.RESULT_TOO_MANY_PASSPHRASE_ATTEMPTS
        }
        if (!checkPassphrase(remoteHost, passphrase, e2eeState.context!!)) {
            Logger.i(TAG, "$remoteHost: CheckPassphraseRequest: Error checking passphrase")
            return CloudSecureAreaProtocol.RESULT_WRONG_PASSPHRASE
        }
        return CloudSecureAreaProtocol.RESULT_OK
    }

    private fun doCheckPassphraseRequest(
        request: CloudSecureAreaProtocol.CheckPassphraseRequest,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        val result = doCheckPassphrase(remoteHost, e2eeState, request.passphrase)
        val response = CloudSecureAreaProtocol.CheckPassphraseResponse(
            result,
        )
        val encryptedResponse = E2EEResponse(
            encryptToDevice(e2eeState, response.toCbor()),
            e2eeState.encrypt()
        )
        Logger.d(TAG, "$remoteHost: CheckPassphraseRequest: Sending result $result to client")
        return Pair(200, encryptedResponse.toCbor())
    }

    private fun encryptToDevice(
        e2eeState: E2EEState,
        messagePlaintext: ByteArray
    ): ByteArray {
        val iv = ByteBuffer.allocate(12)
        iv.putInt(0, 0x00000000)
        val ivIdentifier = 0x00000000
        iv.putInt(4, ivIdentifier)
        iv.putInt(8, e2eeState.encryptedCounter++)
        return Crypto.encrypt(Algorithm.A128GCM, e2eeState.skCloud!!, iv.array(), messagePlaintext)
    }

    private suspend fun doE2EERequest(
        request: CloudSecureAreaProtocol.E2EERequest,
        remoteHost: String
    ): Pair<Int, ByteArray> {
        val e2eeState = E2EEState.decrypt(request.e2eeContext)
        val ageMilliseconds = System.currentTimeMillis() - e2eeState.derivationTimestamp
        if (ageMilliseconds > e2eeKeyLimitSeconds * 1000) {
            Logger.d(
                TAG, String.format(
                    Locale.US,
                    "$remoteHost: E2EE derived keys are expired, age is %.1f seconds and limit is %d seconds",
                    ageMilliseconds / 1000.0, e2eeKeyLimitSeconds
                )
            )
            return Pair(400, "E2EE derived keys expired".toByteArray())
        }
        val iv = ByteBuffer.allocate(12)
        iv.putInt(0, 0x00000000)
        val ivIdentifier = 0x00000001
        iv.putInt(4, ivIdentifier)
        iv.putInt(8, e2eeState.decryptedCounter)
        val plainText = try {
            Crypto.decrypt(
                Algorithm.A128GCM,
                e2eeState.skDevice!!,
                iv.array(),
                request.encryptedRequest)
        } catch (e: IllegalStateException) {
            return Pair(400, "Decryption failed".toByteArray())
        }
        e2eeState.decryptedCounter += 1
        return handleCommandInternal(plainText, remoteHost, e2eeState)
    }

    private suspend fun handleCommandInternal(
        requestData: ByteArray,
        remoteHost: String,
        e2eeState: E2EEState?
    ): Pair<Int, ByteArray> {
        val command = CloudSecureAreaProtocol.Command.fromCbor(requestData)
        // Fail early if stage 2 registration isn't complete.
        if (e2eeState != null && command !is CloudSecureAreaProtocol.RegisterStage2Request0) {
            if (!e2eeState.context!!.registrationComplete) {
                Logger.w(TAG, "$remoteHost: Stage 2 registration not complete")
                return Pair(400, "Stage 2 registration not complete".encodeToByteArray())
            }
        }
        return when (command) {
            is RegisterRequest0 -> doRegisterRequest0(command, remoteHost)
            is RegisterRequest1 -> doRegisterRequest1(command, remoteHost)
            is E2EESetupRequest0 -> doE2EESetupRequest0(command, remoteHost)
            is E2EESetupRequest1 -> doE2EESetupRequest1(command, remoteHost)
            is CloudSecureAreaProtocol.E2EERequest -> doE2EERequest(command, remoteHost)
            is CloudSecureAreaProtocol.RegisterStage2Request0 -> doRegisterStage2Request0(command, remoteHost, e2eeState!!)
            is CreateKeyRequest0 -> doCreateKeyRequest0(command, remoteHost, e2eeState!!)
            is CloudSecureAreaProtocol.CreateKeyRequest1 -> doCreateKeyRequest1(command, remoteHost, e2eeState!!)
            is CloudSecureAreaProtocol.SignRequest0 -> doSignRequest0(command, remoteHost, e2eeState!!)
            is CloudSecureAreaProtocol.SignRequest1 -> doSignRequest1(command, remoteHost, e2eeState!!)
            is CloudSecureAreaProtocol.KeyAgreementRequest0 -> doKeyAgreementRequest0(command, remoteHost, e2eeState!!)
            is CloudSecureAreaProtocol.KeyAgreementRequest1 -> doKeyAgreementRequest1(command, remoteHost, e2eeState!!)
            is CloudSecureAreaProtocol.CheckPassphraseRequest -> doCheckPassphraseRequest(command, remoteHost, e2eeState!!)
            else -> {
                Logger.w(TAG, "$remoteHost: Unknown command ${command}, returning 404")
                Pair(404, "Unknown command".toByteArray())
            }
        }
    }

    suspend fun handleCommand(requestData: ByteArray, remoteHost: String): Pair<Int, ByteArray> {
        return handleCommandInternal(requestData, remoteHost, null)
    }

    companion object {
        const val TAG = "CloudSecureAreaServer"

        // Really important these counters are never reused. We rely on the system
        // clock always going forward to achieve this.
        @get:Synchronized
        val encryptionGcmCounter: Long
            get() {
                var counter = System.currentTimeMillis()
                while (counter < lastCounter) {
                    try {
                        Thread.sleep(1)
                    } catch (e: InterruptedException) {
                    }
                }
                lastCounter = counter
                return counter
            }

        var lastCounter = Long.MIN_VALUE
    }
}