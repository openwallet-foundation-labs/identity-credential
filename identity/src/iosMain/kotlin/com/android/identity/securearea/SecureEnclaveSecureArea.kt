package com.android.identity.securearea

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.EcSignature
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger

/**
 * An implementation of [SecureArea] using the Apple Secure Enclave.
 *
 * This implementation uses [CryptoKit](https://developer.apple.com/documentation/cryptokit/secureenclave)
 * and only supports [EcCurve.P256]. Keys can optionally be protected by user authentication
 * which can be specified using [SecureEnclaveUserAuthType] and [SecureEnclaveCreateKeySettings].
 *
 * Note that this platform automatically displays authentication dialogs when a key is used (if
 * needed) unlike other [SecureArea] dialogs where the application is expected to show
 * authentication dialogs via catching [KeyUnlockData], preparing a [KeyUnlockData], obtaining
 * authentication, and then retrying the operation.
 *
 * The behavior (for example, which message to show the user) of the platform native authentication
 * dialog can be customized by passing a [SecureEnclaveKeyUnlockData] with a suitable
 * [LAContext](https://developer.apple.com/documentation/LocalAuthentication/LAContext)
 * object when the key is used. Note that the platform native authentication dialog will show
 * even if this is not done.
 *
 * As the Secure Enclave does not current support key attestation, the base [KeyAttestation]
 * object is used.
 */
class SecureEnclaveSecureArea(
    private val storageEngine: StorageEngine
): SecureArea {

    companion object {
        private val TAG = "SecureEnclaveSecureArea"

        // Prefix used for storage items, the key alias follows.
        private const val PREFIX = "IC_SecureEnclave_"
    }

    override val identifier: String
        get() = "SecureEnclaveSecureArea"

    override val displayName: String
        get() = "Secure Enclave Secure Area"

    override fun createKey(alias: String, createKeySettings: CreateKeySettings) {
        val settings = if (createKeySettings is SecureEnclaveCreateKeySettings) {
            createKeySettings
        } else {
            // Use default settings if user passed in a generic SecureArea.CreateKeySettings.
            SecureEnclaveCreateKeySettings.Builder()
                .setKeyPurposes(createKeySettings.keyPurposes)
                .build()
        }

        var accessControlCreateFlags = 0L
        if (settings.userAuthenticationRequired) {
            accessControlCreateFlags = SecureEnclaveUserAuthType.encodeSet(settings.userAuthenticationTypes)
        }
        val (keyBlob, pubKey) = Crypto.secureEnclaveCreateEcPrivateKey(
            settings.keyPurposes,
            accessControlCreateFlags
        )
        Logger.d(TAG, "EC key with alias '$alias' created")
        saveKey(alias, settings, keyBlob, pubKey)
    }

    private fun saveKey(
        alias: String,
        settings: SecureEnclaveCreateKeySettings,
        keyBlob: ByteArray,
        publicKey: EcPublicKey,
    ) {
        val map = CborMap.builder()
        map.put("keyPurposes", KeyPurpose.encodeSet(settings.keyPurposes))
        map.put("userAuthenticationRequired", settings.userAuthenticationRequired)
        map.put("userAuthenticationTypes",
            SecureEnclaveUserAuthType.encodeSet(settings.userAuthenticationTypes))
        map.put("curve", settings.ecCurve.coseCurveIdentifier)
        map.put("publicKey", publicKey.toDataItem)
        map.put("keyBlob", keyBlob)
        storageEngine.put(PREFIX + alias, Cbor.encode(map.end().build()))
    }

    private fun loadKey(alias: String): Pair<ByteArray, SecureEnclaveKeyInfo> {
        val data = storageEngine[PREFIX + alias] ?: throw IllegalArgumentException("No key with given alias")

        val map = Cbor.decode(data)
        val keyPurposes = map["keyPurposes"].asNumber.keyPurposeSet
        val userAuthenticationRequired = map["userAuthenticationRequired"].asBoolean
        val userAuthenticationTypes =
            SecureEnclaveUserAuthType.decodeSet(map["userAuthenticationTypes"].asNumber)
        val publicKey = map["publicKey"].asCoseKey.ecPublicKey
        val keyBlob = map["keyBlob"].asBstr

        val keyInfo = SecureEnclaveKeyInfo(
            publicKey,
            keyPurposes,
            userAuthenticationRequired,
            userAuthenticationTypes)

        return Pair(keyBlob, keyInfo)
    }

    override fun deleteKey(alias: String) {
        storageEngine.delete(PREFIX + alias)
    }

    override fun sign(
        alias: String,
        signatureAlgorithm: Algorithm,
        dataToSign: ByteArray,
        keyUnlockData: KeyUnlockData?
    ): EcSignature {
        val (keyBlob, keyInfo) = loadKey(alias)
        check(signatureAlgorithm == Algorithm.ES256)
        check(keyInfo.keyPurposes.contains(KeyPurpose.SIGN))
        check(keyUnlockData is SecureEnclaveKeyUnlockData?)
        return Crypto.secureEnclaveEcSign(keyBlob, dataToSign, keyUnlockData)
    }

    override fun keyAgreement(
        alias: String,
        otherKey: EcPublicKey,
        keyUnlockData: KeyUnlockData?
    ): ByteArray {
        val (keyBlob, keyInfo) = loadKey(alias)
        check(otherKey.curve == EcCurve.P256)
        check(keyInfo.keyPurposes.contains(KeyPurpose.AGREE_KEY))
        check(keyUnlockData is SecureEnclaveKeyUnlockData?)
        return Crypto.secureEnclaveEcKeyAgreement(keyBlob, otherKey, keyUnlockData)
    }

    override fun getKeyInfo(alias: String): KeyInfo {
        val (_, keyInfo) = loadKey(alias)
        return keyInfo
    }

    override fun getKeyInvalidated(alias: String): Boolean {
        return false
    }

}