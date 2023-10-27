package com.android.identity.android.securearea

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Certificate
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.securearea.AttestationExtension
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.cloud.Protocol
import com.android.identity.securearea.cloud.Protocol.CreateKeyRequest0
import com.android.identity.securearea.cloud.Protocol.CreateKeyRequest1
import com.android.identity.securearea.cloud.Protocol.CreateKeyResponse0
import com.android.identity.securearea.cloud.Protocol.CreateKeyResponse1
import com.android.identity.securearea.cloud.Protocol.E2EERequest
import com.android.identity.securearea.cloud.Protocol.E2EEResponse
import com.android.identity.securearea.cloud.Protocol.E2EESetupRequest0
import com.android.identity.securearea.cloud.Protocol.E2EESetupRequest1
import com.android.identity.securearea.cloud.Protocol.E2EESetupResponse0
import com.android.identity.securearea.cloud.Protocol.E2EESetupResponse1
import com.android.identity.securearea.cloud.Protocol.KeyAgreementRequest0
import com.android.identity.securearea.cloud.Protocol.KeyAgreementRequest1
import com.android.identity.securearea.cloud.Protocol.KeyAgreementResponse0
import com.android.identity.securearea.cloud.Protocol.KeyAgreementResponse1
import com.android.identity.securearea.cloud.Protocol.RegisterRequest0
import com.android.identity.securearea.cloud.Protocol.RegisterRequest1
import com.android.identity.securearea.cloud.Protocol.RegisterResponse0
import com.android.identity.securearea.cloud.Protocol.RegisterResponse1
import com.android.identity.securearea.cloud.Protocol.SignRequest0
import com.android.identity.securearea.cloud.Protocol.SignRequest1
import com.android.identity.securearea.cloud.Protocol.SignResponse0
import com.android.identity.securearea.cloud.Protocol.SignResponse1
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity.util.toHex
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1OctetString
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.Signature
import java.security.SignatureException
import java.security.UnrecoverableEntryException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Secure area in the cloud.
 *
 * @param context                 the application context.
 * @param storageEngine           the storage engine to use for storing metadata about keys.
 * @param cloudUri                the cloud endpoint to communicate with.
 * @param onAuthorizeRootOfTrust  called to authorize the Cloud Secure Area's root certificate.
 */
open class CloudSecureArea(
    private val context: Context,
    private val storageEngine: StorageEngine,
    val cloudUri: String,
    private val onAuthorizeRootOfTrust: (cloudSecureAreaRootOfTrust: X509Certificate) -> Boolean,
) : SecureArea {
    private var skDevice: ByteArray? = null
    private var skCloud: ByteArray? = null
    private var e2eeContext: ByteArray? = null
    private var encryptedCounter = 0
    private var decryptedCounter = 0

    internal open fun delayForBruteforceMitigation(duration: Duration) {
        Logger.i(TAG, "delayForBruteforceMitigation: delaying for $duration")
        runBlocking {
            delay(duration)
        }
        Logger.i(TAG, "delayForBruteforceMitigation: resuming (delayed for $duration)")
    }

    override val identifier: String
        // TODO: support multiple instances, each with a different ID and name
        get() = "CloudSecureArea"

    override val displayName: String
        get() = "Cloud Secure Area"

    // data is non-null if and only if statusCode is 200
    protected open fun communicateLowlevel(requestData: ByteArray): Pair<Int, ByteArray> {
        // TODO: use HTTP library utilizing coroutines
        var con: HttpURLConnection? = null
        return try {
            val url = URL(cloudUri)
            con = url.openConnection() as HttpURLConnection
            con.connectTimeout = 10000
            con.requestMethod = "POST"
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/cbor")
            con.setRequestProperty("Accept", "application/cbor")
            con.setFixedLengthStreamingMode(requestData.size)
            val out = con.outputStream
            out.write(requestData)
            val statusCode = con.responseCode
            val stream = if (statusCode != HttpURLConnection.HTTP_OK) {
                con.errorStream
            } else {
                con.inputStream
            }
            val inputStream = BufferedInputStream(stream, READ_BUF_SIZE)
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(READ_BUF_SIZE)
            while (true) {
                val numRead = inputStream.read(buffer)
                if (numRead == -1) {
                    break
                }
                baos.write(buffer, 0, numRead)
            }
            val responseData = baos.toByteArray()
            Pair(statusCode, responseData)
        } catch (e: IOException) {
            throw CloudException("Error communicating with CSA", e)
        } finally {
            con?.disconnect()
        }
    }

    private fun communicate(requestData: ByteArray): ByteArray {
        val (status, data) = communicateLowlevel(requestData)
        if (status != HttpURLConnection.HTTP_OK) {
            val message = try {
                String(data, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                "Failed parsing as UTF-8: ${data.toHex}"
            }
            throw CloudException("Status $status: $message")
        }
        return data
    }

    val isRegistered: Boolean
        get() = storageEngine["cloudBindingKey"] != null

    /**
     * Registers with the cloud service.
     *
     * @throws CloudException if an error occurred.
     */
    fun register() {
        var response: ByteArray
        try {
            val request0 = RegisterRequest0()
            response = communicate(request0.toCbor())
            val response0 = RegisterResponse0.fromCbor(response)
            val deviceBindingKeyAlias = "DeviceBindingKey"
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            val builder = KeyGenParameterSpec.Builder(deviceBindingKeyAlias, KeyProperties.PURPOSE_SIGN)
            builder.setDigests(KeyProperties.DIGEST_SHA256)
            builder.setAttestationChallenge(response0.cloudChallenge)
            kpg.initialize(builder.build())
            kpg.generateKeyPair()
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val deviceBindingKeyAttestationCerts: MutableList<Certificate> = ArrayList()
            for (deviceBindingCertificate in ks.getCertificateChain(deviceBindingKeyAlias)) {
                deviceBindingKeyAttestationCerts.add(
                    Certificate((deviceBindingCertificate as X509Certificate).encoded)
                )
            }
            val deviceBindingKeyAttestation = CertificateChain(deviceBindingKeyAttestationCerts)
            val deviceChallenge = Random.Default.nextBytes(32)
            val request1 = RegisterRequest1(
                deviceChallenge,
                deviceBindingKeyAttestation,
                response0.serverState
            )
            response = communicate(request1.toCbor())
            val response1 = RegisterResponse1.fromCbor(response)
            validateCloudBindingKeyAttestation(
                response1.cloudBindingKeyAttestation,
                deviceChallenge
            )
            val cloudBindingKey = response1.cloudBindingKeyAttestation.certificates[0].publicKey
            storageEngine.put(
                "cloudBindingKey",
                Cbor.encode(cloudBindingKey.toCoseKey().toDataItem)
            )
            storageEngine.put("registrationContext", response1.serverState)
        } catch (e: Exception) {
            throw CloudException(e)
        }
    }

    private fun validateCloudBindingKeyAttestation(
        attestation: CertificateChain,
        expectedDeviceChallenge: ByteArray
    ) {
        val x509Certs = mutableListOf<X509Certificate>()
        for (cert in attestation.certificates) {
            x509Certs.add(cert.javaX509Certificate)
        }

        for (n in 0 until x509Certs.size - 1) {
            val cert = x509Certs[n]
            val nextCert = x509Certs[n + 1]
            try {
                cert.verify(nextCert.publicKey)
            } catch (e: Exception) {
                throw CloudException("Error verifying attestation chain")
            }
        }

        val rootCertificate = x509Certs.last()
        if (!onAuthorizeRootOfTrust(rootCertificate)) {
            throw CloudException("Root certificate not authorized by app")
        }

        val leafCertificate = x509Certs.first()
        val extensionDerEncodedString = leafCertificate.getExtensionValue(AttestationExtension.ATTESTATION_OID)
            ?: throw CloudException(
                "No attestation extension at OID ${AttestationExtension.ATTESTATION_OID}")

        val attestationExtension = try {
            val asn1InputStream = ASN1InputStream(extensionDerEncodedString);
            (asn1InputStream.readObject() as ASN1OctetString).octets
        } catch (e: Exception) {
            throw CloudException("Error decoding attestation extension", e)
        }

        val challengeInAttestation = AttestationExtension.decode(attestationExtension)
        if (!challengeInAttestation.contentEquals(expectedDeviceChallenge)) {
            throw CloudException("Challenge in attestation does match expected attestation")
        }
    }

    /**
     * Unregisters with the cloud service.
     *
     * @throws CloudException if an error occurred.
     */
    fun unregister() {}
    private fun setupE2EE(forceSetup: Boolean) {
        // No need to setup E2EE if it's already up...
        if (!forceSetup && skDevice != null) {
            return
        }
        var response: ByteArray
        try {
            val registrationContext = storageEngine["registrationContext"]
            val cloudBindingKeyEncoded = storageEngine["cloudBindingKey"]
            val cloudBindingKey = Cbor.decode(cloudBindingKeyEncoded!!).asCoseKey.ecPublicKey
            val request0 = E2EESetupRequest0(registrationContext!!)
            response = communicate(request0.toCbor())
            val response0 = E2EESetupResponse0.fromCbor(response)
            val deviceNonce = Random.Default.nextBytes(32)
            val eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
            val dataToSign = Cbor.encode(
                CborArray.builder()
                    .add(eDeviceKey.publicKey.toCoseKey().toDataItem)
                    .add(response0.cloudNonce)
                    .add(deviceNonce)
                    .end()
                    .build()
            )
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val deviceBindingKeyEntry = ks.getEntry("DeviceBindingKey", null)
                ?: throw IllegalArgumentException("No entry for DeviceBindingKey")
            val deviceBindingKey = (deviceBindingKeyEntry as KeyStore.PrivateKeyEntry).privateKey
            val s = Signature.getInstance("SHA256withECDSA")
            s.initSign(deviceBindingKey)
            s.update(dataToSign)
            val signature = s.sign()
            val request1 = E2EESetupRequest1(
                eDeviceKey.publicKey,
                deviceNonce,
                signature,
                response0.serverState
            )
            response = communicate(request1.toCbor())
            val response1 = E2EESetupResponse1.fromCbor(response)
            val dataSignedByTheCloud = Cbor.encode(
                CborArray.builder()
                    .add(response1.eCloudKey.toCoseKey().toDataItem)
                    .add(response0.cloudNonce)
                    .add(deviceNonce)
                    .end()
                    .build()
            )
            if (!Crypto.checkSignature(
                    cloudBindingKey,
                    dataSignedByTheCloud,
                    Algorithm.ES256,
                    response1.signature
                )
            ) {
                throw CloudException("Error verifying signature")
            }

            // Now we can derive SKDevice and SKCloud
            val Zab = Crypto.keyAgreement(eDeviceKey, response1.eCloudKey)
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
                Zab,
                salt,
                "SKDevice".toByteArray(),
                32
            )
            skCloud = Crypto.hkdf(
                Algorithm.HMAC_SHA256,
                Zab,
                salt,
                "SKCloud".toByteArray(),
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
        val iv = ByteBuffer.allocate(12)
        iv.putInt(0, 0x00000000)
        val ivIdentifier = 0x00000001
        iv.putInt(4, ivIdentifier)
        iv.putInt(8, encryptedCounter++)
        return Crypto.encrypt(Algorithm.A128GCM, skDevice!!, iv.array(), messagePlaintext)
    }

    private fun decryptFromCloud(messageCiphertext: ByteArray): ByteArray {
        val iv = ByteBuffer.allocate(12)
        iv.putInt(0, 0x00000000)
        val ivIdentifier = 0x00000000
        iv.putInt(4, ivIdentifier)
        iv.putInt(8, decryptedCounter++)
        return Crypto.decrypt(Algorithm.A128GCM, skCloud!!, iv.array(), messageCiphertext)
    }

    private fun communicateE2EE(requestData: ByteArray): ByteArray {
        return communicateE2EEInternal(requestData, 0)
    }

    private fun communicateE2EEInternal(
        requestData: ByteArray,
        callDepth: Int
    ): ByteArray {
        val requestWrapper = E2EERequest(
            encryptToCloud(requestData),
            e2eeContext!!
        )
        val (httpResponseCode, response) = communicateLowlevel(requestWrapper.toCbor())
        if (httpResponseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
            // This status code means that decryption failed
            if (callDepth > 10) {
                throw CloudException("Error reinitializing E2EE after $callDepth attempts")
            }
            Logger.d(TAG, "Received status 400, reinitializing E2EE")
            setupE2EE(true)
            return communicateE2EEInternal(requestData, callDepth + 1)
        } else if (httpResponseCode != HttpURLConnection.HTTP_OK) {
            throw CloudException("Excepted status code 200 or 400, got $httpResponseCode")
        }
        val responseWrapper = E2EEResponse.fromCbor(response)
        e2eeContext = responseWrapper.e2eeContext
        return decryptFromCloud(responseWrapper.encryptedResponse)
    }

    override fun createKey(
        alias: String,
        createKeySettings: com.android.identity.securearea.CreateKeySettings
    ) {
        val cSettings = if (createKeySettings is CloudCreateKeySettings) {
            createKeySettings
        } else {
            // Use default settings if user passed in a generic SecureArea.CreateKeySettings.
            CloudCreateKeySettings.Builder(createKeySettings.attestationChallenge).build()
        }
        setupE2EE(false)
        try {
            // Default for validFrom and validUntil is Jan 1, 1970 to Feb 7, 2106
            var validFrom: Long = 0
            var validUntil = Int.MAX_VALUE * 1000L * 2
            if (cSettings.validFrom != null) {
                validFrom = cSettings.validFrom.toEpochMilli()
            }
            if (cSettings.validUntil != null) {
                validUntil = cSettings.validUntil.toEpochMilli()
            }
            val request0 = CreateKeyRequest0(
                cSettings.keyPurposes,
                cSettings.ecCurve,
                validFrom,
                validUntil,
                cSettings.passphrase,
                cSettings.attestationChallenge
            )
            val response0 = CreateKeyResponse0.fromCbor(communicateE2EE(request0.toCbor()))
            val localKeyAlias = ANDROID_KEYSTORE_PREFIX_FOR_ALIAS + alias
            val kpg =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            val builder = KeyGenParameterSpec.Builder(localKeyAlias, KeyProperties.PURPOSE_SIGN)
            builder.setDigests(KeyProperties.DIGEST_SHA256)
            builder.setAttestationChallenge(response0.cloudChallenge)
            if (cSettings.userAuthenticationRequired) {
                builder.setUserAuthenticationRequired(true)
                val timeoutMillis = cSettings.userAuthenticationTimeoutMillis
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val userAuthenticationType = cSettings.userAuthenticationType
                    var type = 0
                    if (userAuthenticationType.contains(UserAuthenticationType.LSKF)) {
                        type = type or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    }
                    if (userAuthenticationType.contains(UserAuthenticationType.BIOMETRIC)) {
                        type = type or KeyProperties.AUTH_BIOMETRIC_STRONG
                    }
                    if (timeoutMillis == 0L) {
                        builder.setUserAuthenticationParameters(0, type)
                    } else {
                        val timeoutSeconds = Math.max(1, timeoutMillis / 1000).toInt()
                        builder.setUserAuthenticationParameters(timeoutSeconds, type)
                    }
                } else {
                    if (timeoutMillis == 0L) {
                        builder.setUserAuthenticationValidityDurationSeconds(-1)
                    } else {
                        val timeoutSeconds = Math.max(1, timeoutMillis / 1000).toInt()
                        builder.setUserAuthenticationValidityDurationSeconds(timeoutSeconds)
                    }
                }
                builder.setInvalidatedByBiometricEnrollment(false)
            }
            kpg.initialize(builder.build())
            kpg.generateKeyPair()
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val localKeyAttestation: MutableList<Certificate> = ArrayList()
            for (certificate in ks.getCertificateChain(localKeyAlias)) {
                localKeyAttestation.add(Certificate(certificate.encoded))
            }
            val request1 = CreateKeyRequest1(
                CertificateChain(localKeyAttestation), response0.serverState
            )
            val response1 = CreateKeyResponse1.fromCbor(communicateE2EE(request1.toCbor()))
            storageEngine.put(
                STORAGE_PREFIX_FOR_PER_KEY_SERVER_STATE + alias,
                response1.serverState
            )
            saveKeyMetadata(alias, cSettings, response1.remoteKeyAttestation)
        } catch (e: Exception) {
            throw CloudException(e)
        }
    }

    override fun deleteKey(alias: String) {
        val localKeyAlias = ANDROID_KEYSTORE_PREFIX_FOR_ALIAS + alias
        val ks: KeyStore
        try {
            ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            if (!ks.containsAlias(localKeyAlias)) {
                Logger.w(TAG, "Key with alias '$localKeyAlias' doesn't exist")
                return
            }
            ks.deleteEntry(localKeyAlias)
            storageEngine.delete(STORAGE_PREFIX_FOR_PER_KEY_SERVER_STATE + alias)
            storageEngine.delete(STORAGE_PREFIX_FOR_PER_KEY_INFO + alias)
        } catch (e: Exception) {
            throw IllegalStateException("Error loading keystore", e)
        }
    }

    @Throws(com.android.identity.securearea.KeyLockedException::class)
    override fun sign(
        alias: String,
        signatureAlgorithm: Algorithm,
        dataToSign: ByteArray,
        keyUnlockData: com.android.identity.securearea.KeyUnlockData?
    ): ByteArray {
        var derSignature: ByteArray? = null
        val keyContext = storageEngine[STORAGE_PREFIX_FOR_PER_KEY_SERVER_STATE + alias]
        setupE2EE(false)
        var response: ByteArray
        try {
            val request0 =
                SignRequest0(signatureAlgorithm.coseAlgorithmIdentifier, dataToSign, keyContext!!)
            response = communicateE2EE(request0.toCbor())
            val response0 = SignResponse0.fromCbor(response)
            val dataToSignLocally = Cbor.encode(
                CborArray.builder()
                    .add(response0.cloudNonce)
                    .end()
                    .build()
            )
            val localKeyAlias = ANDROID_KEYSTORE_PREFIX_FOR_ALIAS + alias
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val deviceBindingKeyEntry = ks.getEntry(localKeyAlias, null)
                ?: throw IllegalArgumentException("No entry for $localKeyAlias")
            val localKey = (deviceBindingKeyEntry as KeyStore.PrivateKeyEntry).privateKey
            var s: Signature? = null
            if (keyUnlockData != null) {
                val csaUnlockData = keyUnlockData as CloudKeyUnlockData
                if (csaUnlockData.cryptoObject_ != null) {
                    s = csaUnlockData.cryptoObject_!!.signature
                }
            }
            if (s == null) {
                s = Signature.getInstance("SHA256withECDSA")
                s.initSign(localKey)
            }
            s!!.update(dataToSignLocally)
            val signatureLocal = s.sign()
            var passphrase: String? = null
            if (keyUnlockData != null && (keyUnlockData as CloudKeyUnlockData).passphrase != null) {
                passphrase = keyUnlockData.passphrase
            }
            val request1 = SignRequest1(signatureLocal, passphrase, response0.serverState)
            do {
                var tryAgain = false

                response = communicateE2EE(request1.toCbor())
                val response1 = SignResponse1.fromCbor(response)
                when (response1.result) {
                    Protocol.RESULT_OK -> {
                        derSignature = response1.signature
                    }

                    Protocol.RESULT_WRONG_PASSPHRASE -> {
                        throw CloudKeyLockedException(
                            CloudKeyLockedException.Reason.WRONG_PASSPHRASE,
                            "Wrong passphrase supplied"
                        )
                    }

                    Protocol.RESULT_TOO_MANY_PASSPHRASE_ATTEMPTS -> {
                        delayForBruteforceMitigation(response1.waitDurationMillis.milliseconds)
                        tryAgain = true
                    }

                    else -> throw CloudException("Unexpected result ${response1.result}")
                }
            } while (tryAgain)

        } catch (e: UserNotAuthenticatedException) {
            throw CloudKeyLockedException(
                CloudKeyLockedException.Reason.USER_NOT_AUTHENTICATED,
                "User not authenticated", e
            )
        } catch (e: SignatureException) {
            // This is a work-around for Android Keystore throwing a SignatureException
            // when it should be throwing UserNotAuthenticatedException instead. b/282174161
            //
            if (e.message!!.startsWith(
                    "android.security.KeyStoreException: Key user not authenticated"
                )
            ) {
                throw CloudKeyLockedException(
                    CloudKeyLockedException.Reason.USER_NOT_AUTHENTICATED,
                    "User not authenticated", e
                )
            }
            throw IllegalStateException("Unexpected exception while signing", e)
        } catch (e: KeyStoreException) {
            throw IllegalStateException("Unexpected exception while signing", e)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Unexpected exception while signing", e)
        } catch (e: UnrecoverableEntryException) {
            throw IllegalStateException("Unexpected exception while signing", e)
        } catch (e: CertificateException) {
            throw IllegalStateException("Unexpected exception while signing", e)
        } catch (e: IOException) {
            throw IllegalStateException("Unexpected exception while signing", e)
        } catch (e: InvalidKeyException) {
            throw IllegalStateException("Unexpected exception while signing", e)
        }
        return derSignature!!
    }

    @Throws(com.android.identity.securearea.KeyLockedException::class)
    override fun keyAgreement(
        alias: String,
        otherKey: EcPublicKey,
        keyUnlockData: com.android.identity.securearea.KeyUnlockData?
    ): ByteArray {
        var Zab: ByteArray? = null
        val keyContext = storageEngine[STORAGE_PREFIX_FOR_PER_KEY_SERVER_STATE + alias]
        setupE2EE(false)
        var response: ByteArray
        try {
            val request0 = KeyAgreementRequest0(otherKey, keyContext!!)
            response = communicateE2EE(request0.toCbor())
            val response0 = KeyAgreementResponse0.fromCbor(response)
            val dataToSignLocally = Cbor.encode(
                CborArray.builder()
                    .add(response0.cloudNonce)
                    .end()
                    .build()
            )
            val localKeyAlias = ANDROID_KEYSTORE_PREFIX_FOR_ALIAS + alias
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val deviceBindingKeyEntry = ks.getEntry(localKeyAlias, null)
                ?: throw IllegalArgumentException("No entry for $localKeyAlias")
            val localKey = (deviceBindingKeyEntry as KeyStore.PrivateKeyEntry).privateKey
            var s: Signature? = null
            if (keyUnlockData != null) {
                val csaUnlockData = keyUnlockData as CloudKeyUnlockData
                if (csaUnlockData.cryptoObject_ != null) {
                    s = csaUnlockData.cryptoObject_!!.signature
                }
            }
            if (s == null) {
                s = Signature.getInstance("SHA256withECDSA")
                s.initSign(localKey)
            }
            s!!.update(dataToSignLocally)
            val signatureLocal = s.sign()
            var passphrase: String? = null
            if (keyUnlockData != null && (keyUnlockData as CloudKeyUnlockData).passphrase != null) {
                passphrase = keyUnlockData.passphrase
            }
            val request1 = KeyAgreementRequest1(signatureLocal, passphrase, response0.serverState)

            do {
                var tryAgain = false

                response = communicateE2EE(request1.toCbor())
                val response1 = KeyAgreementResponse1.fromCbor(response)
                when (response1.result) {
                    Protocol.RESULT_OK -> {
                        Zab = response1.zab
                    }

                    Protocol.RESULT_WRONG_PASSPHRASE -> {
                        throw CloudKeyLockedException(
                            CloudKeyLockedException.Reason.WRONG_PASSPHRASE,
                            "Wrong passphrase supplied"
                        )
                    }

                    Protocol.RESULT_TOO_MANY_PASSPHRASE_ATTEMPTS -> {
                        delayForBruteforceMitigation(response1.waitDurationMillis.milliseconds)
                        tryAgain = true
                    }

                    else -> throw CloudException("Unexpected result ${response1.result}")
                }
            } while (tryAgain)

        } catch (e: UserNotAuthenticatedException) {
            throw CloudKeyLockedException(
                CloudKeyLockedException.Reason.USER_NOT_AUTHENTICATED,
                "User not authenticated", e
            )
        } catch (e: SignatureException) {
            // This is a work-around for Android Keystore throwing a SignatureException
            // when it should be throwing UserNotAuthenticatedException instead. b/282174161
            //
            if (e.message!!.startsWith(
                    "android.security.KeyStoreException: Key user not authenticated"
                )
            ) {
                throw CloudKeyLockedException(
                    CloudKeyLockedException.Reason.USER_NOT_AUTHENTICATED,
                    "User not authenticated", e
                )
            }
            throw IllegalStateException("Unexpected exception while performing Key Agreement", e)
        } catch (e: KeyStoreException) {
            throw IllegalStateException("Unexpected exception while performing Key Agreement", e)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Unexpected exception while performing Key Agreement", e)
        } catch (e: UnrecoverableEntryException) {
            throw IllegalStateException("Unexpected exception while performing Key Agreement", e)
        } catch (e: CertificateException) {
            throw IllegalStateException("Unexpected exception while performing Key Agreement", e)
        } catch (e: IOException) {
            throw IllegalStateException("Unexpected exception while performing Key Agreement", e)
        } catch (e: InvalidKeyException) {
            throw IllegalStateException("Unexpected exception while performing Key Agreement", e)
        }
        return Zab!!
    }

    override fun getKeyInfo(alias: String): CloudKeyInfo {
        val data = storageEngine[STORAGE_PREFIX_FOR_PER_KEY_INFO + alias]
            ?: throw IllegalArgumentException("No key with given alias")
        val map = Cbor.decode(data)
        val keyPurposes = map["keyPurposes"].asNumber
        val userAuthenticationRequired = map["userAuthenticationRequired"].asBoolean
        val userAuthenticationTimeoutMillis = map["userAuthenticationTimeoutMillis"].asNumber
        val isPassphraseRequired = map["isPassphraseRequired"].asBoolean
        val isStrongBoxBacked = map["useStrongBox"].asBoolean
        var validFrom: Timestamp? = null
        var validUntil: Timestamp? = null
        if (map.hasKey("validFrom")) {
            validFrom = Timestamp.ofEpochMilli(map["validFrom"].asNumber)
        }
        if (map.hasKey("validUntil")) {
            validUntil = Timestamp.ofEpochMilli(map["validUntil"].asNumber)
        }
        val attestation = map["attestation"].asCertificateChain
        val userAuthenticationType = map["userAuthenticationType"].asNumber
        return CloudKeyInfo(
            attestation,
            KeyPurpose.decodeSet(keyPurposes),
            userAuthenticationRequired,
            userAuthenticationTimeoutMillis,
            UserAuthenticationType.decodeSet(userAuthenticationType),
            validFrom,
            validUntil,
            isPassphraseRequired,
            isStrongBoxBacked
        )
    }

    private fun saveKeyMetadata(
        alias: String,
        settings: CloudCreateKeySettings,
        attestation: CertificateChain
    ) {
        val map = CborMap.builder()
        map.put("keyPurposes", KeyPurpose.encodeSet(settings.keyPurposes))
        map.put("userAuthenticationType", UserAuthenticationType.encodeSet(settings.userAuthenticationType))
        map.put("userAuthenticationRequired", settings.userAuthenticationRequired)
        map.put("userAuthenticationTimeoutMillis", settings.userAuthenticationTimeoutMillis)
        if (settings.validFrom != null) {
            map.put("validFrom", settings.validFrom.toEpochMilli())
        }
        if (settings.validUntil != null) {
            map.put("validUntil", settings.validUntil.toEpochMilli())
        }
        map.put("isPassphraseRequired", settings.passphraseRequired)
        map.put("useStrongBox", settings.useStrongBox)
        map.put("attestation", attestation.dataItem)
        storageEngine.put(STORAGE_PREFIX_FOR_PER_KEY_INFO + alias, Cbor.encode(map.end().build()))
    }

    companion object {
        private const val TAG = "CloudSecureArea"

        // The prefix used in Android Keystore aliases for local keys
        // to avoid conflicts with regular Android Keystore keys.
        internal const val ANDROID_KEYSTORE_PREFIX_FOR_ALIAS = "IC_CloudKeystore_"

        // The prefix used for storing key-info for a cloud-based key.
        private const val STORAGE_PREFIX_FOR_PER_KEY_INFO = "IC_CloudKeystore_key_info_"

        // The prefix used for storing server state for a cloud-based key.
        private const val STORAGE_PREFIX_FOR_PER_KEY_SERVER_STATE = "IC_CloudKeystore_server_state_"
        private const val READ_BUF_SIZE = 64 * 1024
    }
}