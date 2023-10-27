package com.android.identity.securearea.cloud

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.X509v3Extension
import com.android.identity.crypto.javaPublicKey
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.securearea.AttestationExtension
import com.android.identity.securearea.KeyLockedException
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.cloud.Protocol.CreateKeyRequest0
import com.android.identity.securearea.cloud.Protocol.CreateKeyResponse1
import com.android.identity.securearea.cloud.Protocol.E2EEResponse
import com.android.identity.securearea.cloud.Protocol.E2EESetupRequest0
import com.android.identity.securearea.cloud.Protocol.E2EESetupRequest1
import com.android.identity.securearea.cloud.Protocol.E2EESetupResponse0
import com.android.identity.securearea.cloud.Protocol.E2EESetupResponse1
import com.android.identity.securearea.cloud.Protocol.RegisterRequest0
import com.android.identity.securearea.cloud.Protocol.RegisterRequest1
import com.android.identity.securearea.cloud.Protocol.RegisterResponse0
import com.android.identity.securearea.cloud.Protocol.RegisterResponse1
import com.android.identity.securearea.software.SoftwareCreateKeySettings
import com.android.identity.securearea.software.SoftwareKeyUnlockData
import com.android.identity.securearea.software.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity.storage.StorageEngine
import com.android.identity.util.AndroidAttestationExtensionParser
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity.util.fromHex
import com.android.identity.util.toHex
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.SignatureException
import java.security.cert.CertificateException
import java.util.Arrays
import java.util.Locale
import javax.crypto.spec.SecretKeySpec
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
 * @param requireGmsAttestation whether to require attestations made for local key on clients is using the Google root.
 * @param requireVerifiedBootGreen whether to require clients are in verified boot state green
 * @param requireAppSignatureCertificateDigests the allowed list of applications that can use the
 *   service. Each element is the bytes of the SHA-256 of a signing certificate, see the
 *   [Signature](https://developer.android.com/reference/android/content/pm/Signature) class in
 *   the Android SDK for details. If empty, allow any app.
 * @param passphraseFailureEnforcer the [PassphraseFailureEnforcer] to use.
 */
@OptIn(ExperimentalStdlibApi::class)
class Server(
    private val serverSecureAreaBoundKey: ByteArray,
    private val attestationKey: EcPrivateKey,
    private val attestationKeySignatureAlgorithm: Algorithm,
    private val attestationKeyIssuer: String,
    private val attestationKeyCertification: CertificateChain,
    private val cloudRootAttestationKey: EcPrivateKey,
    private val cloudRootAttestationKeySignatureAlgorithm: Algorithm,
    private val cloudRootAttestationKeyIssuer: String,
    private val cloudRootAttestationKeyCertification: CertificateChain,
    private val e2eeKeyLimitSeconds: Int,
    private val requireGmsAttestation: Boolean,
    private val requireVerifiedBootGreen: Boolean,
    private val requireAppSignatureCertificateDigests: List<ByteArray>,
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

    private class RegisterState {
        var cloudChallenge: ByteArray? = null
        var cloudBindingKey: EcPrivateKey? = null
        var deviceBindingKey: EcPublicKey? = null
        fun toCbor(): ByteArray {
            return MessageGenerator(STATE_NAME)
                .add(cloudChallenge)
                .add(cloudBindingKey)
                .add(deviceBindingKey)
                .generate()
        }

        companion object {
            private const val STATE_NAME = "RegisterState"
            fun fromCbor(encodedData: ByteArray?): RegisterState {
                val mp = MessageParser(
                    encodedData!!, STATE_NAME
                )
                val ret = RegisterState()
                ret.cloudChallenge = mp.byteString
                ret.cloudBindingKey = mp.privateKey
                ret.deviceBindingKey = mp.publicKey
                return ret
            }
        }
    }

    private fun encryptRegisterState(state: RegisterState): ByteArray {
        return encryptState(state.toCbor())
    }

    private fun decryptRegisterState(encryptedState: ByteArray): RegisterState {
        return RegisterState.fromCbor(decryptState(encryptedState))
    }

    private fun validateAndroidKeystoreAttestation(
        attestation: CertificateChain,
        cloudChallenge: ByteArray,
        remoteHost: String
    ) {
        // First check that all the certificates sign each other...
        for (n in 0 until attestation.certificates.size - 1) {
            val cert = attestation.certificates[n]
            val nextCert = attestation.certificates[n + 1]
            try {
                cert.verify(nextCert.publicKey)
            } catch (e: NoSuchAlgorithmException) {
                throw IllegalStateException("Error verifying signature", e)
            } catch (e: InvalidKeyException) {
                throw IllegalStateException("Error verifying signature", e)
            } catch (e: SignatureException) {
                throw IllegalStateException("Error verifying signature", e)
            } catch (e: CertificateException) {
                throw IllegalStateException("Error verifying signature", e)
            } catch (e: NoSuchProviderException) {
                throw IllegalStateException("Error verifying signature", e)
            }
        }
        val rootCertificatePublicKey = attestation.certificates[attestation.certificates.size - 1].publicKey
        if (requireGmsAttestation) {
            // Must match the well-known Google root
            // TODO: check X and Y instead
            check(
                Arrays.equals(
                    GOOGLE_ROOT_ATTESTATION_KEY,
                    rootCertificatePublicKey.javaPublicKey.encoded)
            ) { "Unexpected attestation root" }
        }

        // Finally, check the Attestation Extension...
        try {
            val parser = AndroidAttestationExtensionParser(attestation.certificates[0].javaX509Certificate)

            // Challenge must match...
            check(
                Arrays.equals(
                    cloudChallenge,
                    parser.attestationChallenge
                )
            ) { "Challenge didn't match what was expected" }

            if (requireVerifiedBootGreen) {
                // Verified Boot state must VERIFIED
                check(parser.verifiedBootState ==
                        AndroidAttestationExtensionParser.VerifiedBootState.GREEN)
                { "Verified boot state is not GREEN" }
            }

            if (requireAppSignatureCertificateDigests.size > 0) {
                check (parser.applicationSignatureDigests.size == requireAppSignatureCertificateDigests.size)
                { "Number Signing certificates mismatch" }
                for (n in 0..<parser.applicationSignatureDigests.size) {
                    check (Arrays.equals(parser.applicationSignatureDigests[n],
                        requireAppSignatureCertificateDigests[n]))
                    { "Signing certificate $n mismatch" }
                }
            }

            // Log the digests for easy copy-pasting into config file.
            Logger.d(TAG, "$remoteHost: Accepting Android client with ${parser.applicationSignatureDigests.size} " +
                    "signing certificates digests")
            for (n in 0..<parser.applicationSignatureDigests.size) {
                Logger.d(TAG, "$remoteHost: Digest $n: ${parser.applicationSignatureDigests[n].toHex}")
            }

        } catch (e: IOException) {
            throw IllegalStateException("Error parsing Android Attestation Extension", e)
        }
    }

    private fun doRegisterRequest0(requestData: ByteArray,
                                   remoteHost: String): Pair<Int, ByteArray> {
        RegisterRequest0.fromCbor(requestData)
        val state = RegisterState()
        state.cloudChallenge = Random.Default.nextBytes(32)
        val response0 = RegisterResponse0(
            state.cloudChallenge!!,
            encryptRegisterState(state)
        )
        Logger.d(TAG, "$remoteHost: RegisterRequest0: Sending challenge to client")
        return Pair(200, response0.toCbor())
    }

    private fun doRegisterRequest1(requestData: ByteArray,
                                   remoteHost: String): Pair<Int, ByteArray> {
        val request1 = RegisterRequest1.fromCbor(requestData)
        val state = decryptRegisterState(request1.serverState)
        try {
            validateAndroidKeystoreAttestation(
                request1.deviceBindingKeyAttestation,
                state.cloudChallenge!!,
                remoteHost
            )
        } catch (e: IllegalStateException) {
            Logger.w(TAG, "$remoteHost: RegisterRequest1: Android Keystore attestation did not validate", e)
            return Pair(403, e.message!!.toByteArray())
        }
        state.deviceBindingKey = request1.deviceBindingKeyAttestation.certificates[0].publicKey

        // This is used to re-initialize E2EE so it's used quite a bit. Give it a long
        // validity period.
        val cloudBindingKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val cloudBindingKeyValidFrom = Clock.System.now()
        val cloudBindingKeyValidUntil = cloudBindingKeyValidFrom.plus(
            DateTimePeriod(years = 10),
            TimeZone.currentSystemDefault()
        )
        val attestationExtension =
            X509v3Extension(
                AttestationExtension.ATTESTATION_OID,
                false,
                AttestationExtension.encode(request1.deviceChallenge)
            )
        val cloudBindingKeyAttestation = CertificateChain(
            listOf(
                Crypto.createX509v3Certificate(
                    cloudBindingKey.publicKey,
                    cloudRootAttestationKey,
                    null,
                    cloudRootAttestationKeySignatureAlgorithm,
                    "1",
                    "CN=Cloud Secure Area Cloud Binding Key",
                    cloudRootAttestationKeyIssuer,
                    cloudBindingKeyValidFrom,
                    cloudBindingKeyValidUntil,
                    setOf(),
                    listOf(attestationExtension)
                ),
            ) + cloudRootAttestationKeyCertification.certificates
        )
        state.cloudBindingKey = cloudBindingKey
        val response1 = RegisterResponse1(
            cloudBindingKeyAttestation,
            encryptRegisterState(state)
        )
        Logger.d(TAG, "$remoteHost: RegisterRequest1: Client successfully registered")
        return Pair(200, response1.toCbor())
    }

    private class E2EEState {
        var context: RegisterState? = null
        var cloudNonce: ByteArray? = null
        var skDevice: ByteArray? = null
        var skCloud: ByteArray? = null
        var encryptedCounter = 0
        var decryptedCounter = 0
        var derivationTimestamp: Long = 0
        fun toCbor(): ByteArray {
            return MessageGenerator(STATE_NAME)
                .add(context!!.toCbor())
                .add(cloudNonce)
                .add(skDevice)
                .add(skCloud)
                .add(encryptedCounter)
                .add(decryptedCounter)
                .add(derivationTimestamp)
                .generate()
        }

        companion object {
            private const val STATE_NAME = "E2EEState"
            fun fromCbor(encodedData: ByteArray): E2EEState {
                val mp = MessageParser(encodedData, STATE_NAME)
                val ret = E2EEState()
                ret.context = RegisterState.fromCbor(mp.byteString)
                ret.cloudNonce = mp.byteString
                ret.skDevice = mp.byteString
                ret.skCloud = mp.byteString
                ret.encryptedCounter = mp.int
                ret.decryptedCounter = mp.int
                ret.derivationTimestamp = mp.long
                return ret
            }
        }
    }

    private fun encryptE2EEState(state: E2EEState): ByteArray {
        return encryptState(state.toCbor())
    }

    private fun decryptE2EEState(encryptedState: ByteArray): E2EEState {
        return E2EEState.fromCbor(decryptState(encryptedState))
    }

    private fun doE2EESetupRequest0(requestData: ByteArray,
                                    remoteHost: String): Pair<Int, ByteArray> {
        val request0 = E2EESetupRequest0.fromCbor(requestData)
        val state = E2EEState()
        state.context = decryptRegisterState(request0.registrationContext)
        state.cloudNonce = Random.Default.nextBytes(32)
        val response0 = E2EESetupResponse0(
            state.cloudNonce!!,
            encryptE2EEState(state)
        )
        Logger.d(TAG, "$remoteHost: E2EESetupRequest0: Sending nonce to client")
        return Pair(200, response0.toCbor())
    }

    private fun doE2EESetupRequest1(requestData: ByteArray,
                                    remoteHost: String): Pair<Int, ByteArray> {
        val request1 = E2EESetupRequest1.fromCbor(requestData)
        val state = decryptE2EEState(request1.serverState)
        val dataThatWasSigned = Cbor.encode(
            CborArray.builder()
                .add(request1.eDeviceKey.toCoseKey().toDataItem)
                .add(state.cloudNonce!!)
                .add(request1.deviceNonce)
                .end()
                .build()
        )
        check(Crypto.checkSignature(
            state.context!!.deviceBindingKey!!,
            dataThatWasSigned,
            Algorithm.ES256,
            request1.signature
        )) { "Error verifying signature" }

        // OK, the device's EDeviceKey checks out. Time for us to generate ECloudKey,
        // sign over it with CloudBindingKey, and send it to the device for verification
        val eCloudKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val dataToSign = Cbor.encode(
            CborArray.builder()
                .add(eCloudKey.publicKey.toCoseKey().toDataItem)
                .add(state.cloudNonce!!)
                .add(request1.deviceNonce)
                .end()
                .build()
        )
        val signature = Crypto.sign(
            state.context!!.cloudBindingKey!!,
            Algorithm.ES256,
            dataToSign
        )

        // Also derive SKDevice and SKCloud, and stash in state since we're going to need this later
        val zab = Crypto.keyAgreement(eCloudKey, request1.eDeviceKey)
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
            eCloudKey.publicKey,
            signature,
            encryptE2EEState(state)
        )
        Logger.d(TAG, "$remoteHost: E2EESetupRequest1: Encrypted tunnel has been set up")
        return Pair<Int, ByteArray>(200, response1.toCbor())
    }

    private class CreateKeyState {
        var challenge: ByteArray? = null
        var cloudChallenge: ByteArray? = null
        var purposes: Set<KeyPurpose> = emptySet()
        var curve: EcCurve = EcCurve.P256
        var validFromMillis: Long = 0
        var validUntilMillis: Long = 0
        var passphrase: String? = null
        var cloudKeyStorage: ByteArray? = null
        var localKey: EcPublicKey? = null
        var clientId: String? = null
        fun toCbor(): ByteArray {
            return MessageGenerator(STATE_NAME)
                .add(challenge)
                .add(cloudChallenge)
                .add(KeyPurpose.encodeSet(purposes))
                .add(curve.coseCurveIdentifier)
                .add(validFromMillis)
                .add(validUntilMillis)
                .add(passphrase)
                .add(cloudKeyStorage)
                .add(localKey)
                .add(clientId)
                .generate()
        }

        companion object {
            private const val STATE_NAME = "CreateKeyState"
            fun fromCbor(encodedData: ByteArray): CreateKeyState {
                val mp = MessageParser(encodedData, STATE_NAME)
                val ret = CreateKeyState()
                ret.challenge = mp.byteString
                ret.cloudChallenge = mp.byteString
                ret.purposes = KeyPurpose.decodeSet(mp.long)
                ret.curve = EcCurve.fromInt(mp.int)
                ret.validFromMillis = mp.long
                ret.validUntilMillis = mp.long
                ret.passphrase = mp.string
                ret.cloudKeyStorage = mp.byteString
                ret.localKey = mp.publicKey
                ret.clientId = mp.string
                return ret
            }
        }
    }

    private fun encryptCreateKeyState(state: CreateKeyState): ByteArray {
        return encryptState(state.toCbor())
    }

    private fun decryptCreateKeyState(encryptedState: ByteArray): CreateKeyState {
        return CreateKeyState.fromCbor(decryptState(encryptedState))
    }

    private fun doCreateKeyRequest0(
        requestData: ByteArray,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        val request0 = CreateKeyRequest0.fromCbor(requestData)
        val state = CreateKeyState()
        state.challenge = request0.challenge
        state.cloudChallenge = Random.Default.nextBytes(32)
        state.purposes = request0.purposes
        state.curve = request0.curve
        state.validFromMillis = request0.validFromMillis
        state.validUntilMillis = request0.validUntilMillis
        state.passphrase = request0.passphrase
        // This is used for [PassphraseFailureEnforcer].
        state.clientId = Crypto.digest(
            Algorithm.SHA256,
            Cbor.encode(e2eeState.context!!.deviceBindingKey!!.toCoseKey().toDataItem)).toHex
        val response0 = Protocol.CreateKeyResponse0(
            state.cloudChallenge!!,
            encryptCreateKeyState(state)
        )
        val encryptedResponse0 = E2EEResponse(
            encryptToDevice(e2eeState, response0.toCbor()),
            encryptE2EEState(e2eeState)
        )
        Logger.d(TAG, "$remoteHost: CreateKeyRequest0: Sending challenge to client")
        return Pair(200, encryptedResponse0.toCbor())
    }

    private fun doCreateKeyRequest1(
        requestData: ByteArray,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        val request1 = Protocol.CreateKeyRequest1.fromCbor(requestData)
        val state = decryptCreateKeyState(request1.serverState)
        try {
            validateAndroidKeystoreAttestation(
                request1.localKeyAttestation,
                state.cloudChallenge!!,
                remoteHost
            )
        } catch (e: IllegalStateException) {
            throw IllegalStateException("Error verifying signature")
        }
        state.localKey = request1.localKeyAttestation.certificates.get(0).publicKey
        val storageEngine = EphemeralStorageEngine()
        val secureArea = SoftwareSecureArea(storageEngine)
        val builder = SoftwareCreateKeySettings.Builder(state.challenge!!)
            .setSubject("CN=Cloud Secure Area Key")
            .setValidityPeriod(
                Timestamp.ofEpochMilli(state.validFromMillis),
                Timestamp.ofEpochMilli(state.validUntilMillis)
            )
            .setKeyPurposes(state.purposes)
            .setEcCurve(state.curve)
            .setAttestationKey(attestationKey,
                attestationKeySignatureAlgorithm,
                attestationKeyIssuer,
                attestationKeyCertification)
        if (state.passphrase != null && state.passphrase!!.length > 0) {
            builder.setPassphraseRequired(true, state.passphrase)
        }
        secureArea.createKey("CloudKey", builder.build())
        val cloudKeyAttestation = secureArea.getKeyInfo("CloudKey").attestation
        state.cloudKeyStorage = storageEngine.toCbor()
        val response1 = CreateKeyResponse1(
            cloudKeyAttestation,
            encryptCreateKeyState(state)
        )
        val encryptedResponse1 = E2EEResponse(
            encryptToDevice(e2eeState, response1.toCbor()),
            encryptE2EEState(e2eeState)
        )
        Logger.d(TAG, "$remoteHost: CreateKeyRequest1: Created key for client")
        return Pair(200, encryptedResponse1.toCbor())
    }

    private class SignState {
        var keyContext: CreateKeyState? = null
        var dataToSign: ByteArray? = null
        var cloudNonce: ByteArray? = null

        fun toCbor(): ByteArray {
            return MessageGenerator(STATE_NAME)
                .add(keyContext!!.toCbor())
                .add(dataToSign)
                .add(cloudNonce)
                .generate()
        }

        companion object {
            private const val STATE_NAME = "SignState"
            fun fromCbor(encodedData: ByteArray): SignState {
                val mp = MessageParser(encodedData, STATE_NAME)
                val ret = SignState()
                ret.keyContext = CreateKeyState.fromCbor(mp.byteString!!)
                ret.dataToSign = mp.byteString
                ret.cloudNonce = mp.byteString
                return ret
            }
        }
    }

    private fun encryptSignState(state: SignState): ByteArray {
        return encryptState(state.toCbor())
    }

    private fun decryptSignState(encryptedState: ByteArray): SignState {
        return SignState.fromCbor(decryptState(encryptedState))
    }

    private fun doSignRequest0(
        requestData: ByteArray,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        val request0 = Protocol.SignRequest0.fromCbor(requestData)
        val state = SignState()
        state.keyContext = decryptCreateKeyState(request0.keyContext)
        state.dataToSign = request0.dataToSign
        state.cloudNonce = Random.Default.nextBytes(32)
        val response0 = Protocol.SignResponse0(
            state.cloudNonce!!,
            encryptSignState(state)
        )
        val encryptedResponse0 = E2EEResponse(
            encryptToDevice(e2eeState, response0.toCbor()),
            encryptE2EEState(e2eeState)
        )
        Logger.d(TAG, "$remoteHost: SignRequest0: Sending nonce to client")
        return Pair(200, encryptedResponse0.toCbor())
    }

    private fun doSignRequest1(
        requestData: ByteArray,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        try {
            val request1 = Protocol.SignRequest1.fromCbor(requestData)
            val state = decryptSignState(request1.serverState)
            val dataThatWasSignedLocally = Cbor.encode(
                CborArray.builder()
                    .add(state.cloudNonce!!)
                    .end()
                    .build()
            )
            check(Crypto.checkSignature(
                state.keyContext!!.localKey!!,
                dataThatWasSignedLocally,
                Algorithm.ES256,
                request1.signature)) { "Error verifying signature" }

            var result = Protocol.RESULT_OK
            var unlockData: SoftwareKeyUnlockData? = null
            if (request1.passphrase != null) {
                val lockedOutDuration = passphraseFailureEnforcer.isLockedOut(state.keyContext!!.clientId!!)
                if (lockedOutDuration != null) {
                    Logger.i(TAG, "$remoteHost: doSignRequest1: Too many passphrase attempts, " +
                            "locked out for $lockedOutDuration")
                    result = Protocol.RESULT_TOO_MANY_PASSPHRASE_ATTEMPTS
                    val response1 = Protocol.SignResponse1(
                        result,
                        null,
                        lockedOutDuration.inWholeMilliseconds)
                    val encryptedResponse1 = E2EEResponse(
                        encryptToDevice(e2eeState, response1.toCbor()),
                        encryptE2EEState(e2eeState)
                    )
                    return Pair(200, encryptedResponse1.toCbor())
                }
                unlockData = SoftwareKeyUnlockData(request1.passphrase)
            }
            val storageEngine = EphemeralStorageEngine.fromCbor(
                state.keyContext!!.cloudKeyStorage!!
            )

            val secureArea = SoftwareSecureArea(storageEngine)
            var signature: ByteArray? = null
            try {
                signature = secureArea.sign(
                    "CloudKey",
                    Algorithm.ES256,
                    state.dataToSign!!,
                    unlockData
                )
                Logger.d(TAG, "$remoteHost: SignRequest1: Signed data for client")
            } catch (e: KeyLockedException) {
                result = Protocol.RESULT_WRONG_PASSPHRASE
                passphraseFailureEnforcer.recordFailedPassphraseAttempt(
                    state.keyContext!!.clientId!!
                )
                Logger.d(TAG, "$remoteHost: SignRequest1: Wrong passphrase supplied")
            }
            val response1 = Protocol.SignResponse1(result, signature, 0L)
            val encryptedResponse1 = E2EEResponse(
                encryptToDevice(e2eeState, response1.toCbor()),
                encryptE2EEState(e2eeState)
            )
            return Pair(200, encryptedResponse1.toCbor())
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException(e)
        } catch (e: InvalidKeyException) {
            throw IllegalStateException(e)
        } catch (e: SignatureException) {
            throw IllegalStateException(e)
        }
    }

    private class KeyAgreementState {
        var keyContext: CreateKeyState? = null
        var otherPublicKey: EcPublicKey? = null
        var cloudNonce: ByteArray? = null
        fun toCbor(): ByteArray {
            return MessageGenerator(STATE_NAME)
                .add(keyContext!!.toCbor())
                .add(otherPublicKey)
                .add(cloudNonce)
                .generate()
        }

        companion object {
            private const val STATE_NAME = "KeyAgreementState"
            fun fromCbor(encodedData: ByteArray): KeyAgreementState {
                val mp = MessageParser(encodedData, STATE_NAME)
                val ret = KeyAgreementState()
                ret.keyContext = CreateKeyState.fromCbor(mp.byteString!!)
                ret.otherPublicKey = mp.publicKey
                ret.cloudNonce = mp.byteString
                return ret
            }
        }
    }

    private fun encryptKeyAgreementState(state: KeyAgreementState): ByteArray {
        return encryptState(state.toCbor())
    }

    private fun decryptKeyAgreementState(encryptedState: ByteArray): KeyAgreementState {
        return KeyAgreementState.fromCbor(decryptState(encryptedState))
    }

    private fun doKeyAgreementRequest0(
        requestData: ByteArray,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        val request0 = Protocol.KeyAgreementRequest0.fromCbor(requestData)
        val state = KeyAgreementState()
        state.keyContext = decryptCreateKeyState(request0.keyContext)
        state.otherPublicKey = request0.otherPublicKey
        state.cloudNonce = Random.Default.nextBytes(32)
        val response0 = Protocol.KeyAgreementResponse0(
            state.cloudNonce!!,
            encryptKeyAgreementState(state)
        )
        val encryptedResponse0 = E2EEResponse(
            encryptToDevice(e2eeState, response0.toCbor()),
            encryptE2EEState(e2eeState)
        )
        Logger.d(TAG, "$remoteHost: KeyAgreementRequest0: Sending nonce to client")
        return Pair(200, encryptedResponse0.toCbor())
    }

    private fun doKeyAgreementRequest1(
        requestData: ByteArray,
        remoteHost: String,
        e2eeState: E2EEState
    ): Pair<Int, ByteArray> {
        try {
            val request1 = Protocol.KeyAgreementRequest1.fromCbor(requestData)
            val state = decryptKeyAgreementState(request1.serverState)
            val dataThatWasSignedLocally = Cbor.encode(
                CborArray.builder()
                    .add(state.cloudNonce!!)
                    .end()
                    .build()
            )
            check(Crypto.checkSignature(
                state.keyContext!!.localKey!!,
                dataThatWasSignedLocally,
                Algorithm.ES256,
                request1.signature
            )) { "Error verifying signature" }

            var result = Protocol.RESULT_OK
            var unlockData: SoftwareKeyUnlockData? = null
            if (request1.passphrase != null) {
                val lockedOutDuration = passphraseFailureEnforcer.isLockedOut(state.keyContext!!.clientId!!)
                if (lockedOutDuration != null) {
                    Logger.i(TAG, "$remoteHost: doKeyAgreementRequest1: Too many passphrase attempts, " +
                            "locked out for $lockedOutDuration")
                    result = Protocol.RESULT_TOO_MANY_PASSPHRASE_ATTEMPTS
                    val response1 = Protocol.KeyAgreementResponse1(
                        result,
                        null,
                        lockedOutDuration.inWholeMilliseconds)
                    val encryptedResponse1 = E2EEResponse(
                        encryptToDevice(e2eeState, response1.toCbor()),
                        encryptE2EEState(e2eeState)
                    )
                    return Pair(200, encryptedResponse1.toCbor())
                }
                unlockData = SoftwareKeyUnlockData(request1.passphrase)
            }
            val storageEngine = EphemeralStorageEngine.fromCbor(
                state.keyContext!!.cloudKeyStorage!!
            )
            val secureArea = SoftwareSecureArea(storageEngine)
            var Zab: ByteArray? = null
            try {
                Zab = secureArea.keyAgreement(
                    "CloudKey",
                    state.otherPublicKey!!,
                    unlockData
                )
                Logger.d(TAG, "$remoteHost: KeyAgreementRequest1: Calculated Zab")
            } catch (e: KeyLockedException) {
                result = Protocol.RESULT_WRONG_PASSPHRASE
                passphraseFailureEnforcer.recordFailedPassphraseAttempt(
                    state.keyContext!!.clientId!!
                )
                Logger.d(TAG, "$remoteHost: KeyAgreementRequest1: Wrong passphrase supplied")
            }
            val response1 = Protocol.KeyAgreementResponse1(result, Zab, 0L)
            val encryptedResponse1 = E2EEResponse(
                encryptToDevice(e2eeState, response1.toCbor()),
                encryptE2EEState(e2eeState)
            )
            return Pair(200, encryptedResponse1.toCbor())
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException(e)
        } catch (e: InvalidKeyException) {
            throw IllegalStateException(e)
        } catch (e: SignatureException) {
            throw IllegalStateException(e)
        }
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

    private fun doE2EERequest(requestData: ByteArray, remoteHost: String): Pair<Int, ByteArray> {
        val request = Protocol.E2EERequest.fromCbor(requestData)
        val e2eeState = decryptE2EEState(request.e2eeContext)
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

    private fun handleCommandInternal(
        requestData: ByteArray,
        remoteHost: String,
        e2eeState: E2EEState?
    ): Pair<Int, ByteArray> {
        val array = Cbor.decode(requestData)
        return when (val command = array[0].asTstr) {
            RegisterRequest0::class.java.simpleName -> {
                doRegisterRequest0(requestData, remoteHost)
            }
            RegisterRequest1::class.java.simpleName -> {
                doRegisterRequest1(requestData, remoteHost)
            }
            E2EESetupRequest0::class.java.simpleName -> {
                doE2EESetupRequest0(requestData, remoteHost)
            }
            E2EESetupRequest1::class.java.simpleName -> {
                doE2EESetupRequest1(requestData, remoteHost)
            }
            Protocol.E2EERequest::class.java.simpleName -> {
                doE2EERequest(requestData, remoteHost)
            }
            CreateKeyRequest0::class.java.simpleName -> {
                doCreateKeyRequest0(requestData, remoteHost, e2eeState!!)
            }
            Protocol.CreateKeyRequest1::class.java.simpleName -> {
                doCreateKeyRequest1(requestData, remoteHost, e2eeState!!)
            }
            Protocol.SignRequest0::class.java.simpleName -> {
                doSignRequest0(requestData, remoteHost, e2eeState!!)
            }
            Protocol.SignRequest1::class.java.simpleName -> {
                doSignRequest1(requestData, remoteHost, e2eeState!!)
            }
            Protocol.KeyAgreementRequest0::class.java.simpleName -> {
                doKeyAgreementRequest0(requestData, remoteHost, e2eeState!!)
            }
            Protocol.KeyAgreementRequest1::class.java.simpleName -> {
                doKeyAgreementRequest1(requestData, remoteHost, e2eeState!!)
            }
            else -> {
                Logger.w(TAG, "$remoteHost: Unknown command ${command}, returning 404")
                Pair(404, "Unknown command".toByteArray()
                )
            }
        }
    }

    fun handleCommand(requestData: ByteArray, remoteHost: String): Pair<Int, ByteArray> {
        return handleCommandInternal(requestData, remoteHost, null)
    }

    companion object {
        const val TAG = "Server"

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

        // This public key is from https://developer.android.com/training/articles/security-key-attestation
        private val GOOGLE_ROOT_ATTESTATION_KEY =
            "30820222300d06092a864886f70d01010105000382020f003082020a0282020100afb6c7822bb1a701ec2bb42e8bcc541663abef982f32c77f7531030c97524b1b5fe809fbc72aa9451f743cbd9a6f1335744aa55e77f6b6ac3535ee17c25e639517dd9c92e6374a53cbfe258f8ffbb6fd129378a22a4ca99c452d47a59f3201f44197ca1ccd7e762fb2f53151b6feb2fffd2b6fe4fe5bc6bd9ec34bfe08239daafceb8eb5a8ed2b3acd9c5e3a7790e1b51442793159859811ad9eb2a96bbdd7a57c93a91c41fccd27d67fd6f671aa0b815261ad384fa37944864604ddb3d8c4f920a19b1656c2f14ad6d03c56ec060899041c1ed1a5fe6d3440b556bad1d0a152589c53e55d370762f0122eef91861b1b0e6c4c80927499c0e9bec0b83e3bc1f93c72c049604bbd2f1345e62c3f8e26dbec06c94766f3c128239d4f4312fad8123887e06becf567583bf8355a81feeabaf99a83c8df3e2a322afc672bf120b135158b6821ceaf309b6eee77f98833b018daa10e451f06a374d50781f359082966bb778b9308942698e74e0bcd24628a01c2cc03e51f0b3e5b4ac1e4df9eaf9ff6a492a77c1483882885015b422ce67b80b88c9b48e13b607ab545c723ff8c44f8f2d368b9f6520d31145ebf9e862ad71df6a3bfd2450959d653740d97a12f368b13ef66d5d0a54a6e2f5d9a6fef446832bc67844725861f093dd0e6f3405da89643ef0f4d69b6420051fdb93049673e36950580d3cdf4fbd08bc58483952600630203010001"
                .fromHex
    }
}