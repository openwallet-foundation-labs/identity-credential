package org.multipaz.securearea

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborMap
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcSignature
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.util.Logger
import kotlinx.io.bytestring.ByteString

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
class SecureEnclaveSecureArea private constructor(
    private val storageTable: StorageTable,
    private val partitionId: String
): SecureArea {

    companion object {
        private val TAG = "SecureEnclaveSecureArea"

        /**
         * Creates an instance of [SecureEnclaveSecureArea].
         *
         * @param storage the storage engine to use for storing key metadata.
         * @param partitionId the partitionId to use for [storage].
         */
        suspend fun create(
            storage: Storage,
            partitionId: String = "default"
        ): SecureEnclaveSecureArea {
            return SecureEnclaveSecureArea(storage.getTable(tableSpec), partitionId)
        }

        private val tableSpec = StorageTableSpec(
            name = "SecureEnclaveSecureArea",
            supportPartitions = true,
            supportExpiration = false
        )
    }

    override val identifier: String
        get() = "SecureEnclaveSecureArea"

    override val displayName: String
        get() = "Secure Enclave Secure Area"

    override suspend fun createKey(alias: String?, createKeySettings: CreateKeySettings): KeyInfo {
        if (alias != null) {
            // If the key with the given alias exists, it is silently overwritten.
            storageTable.delete(alias, partitionId)
        }

        // If key is used to sign, Algorithm.ES256 is the only option
        check(!createKeySettings.keyPurposes.contains(KeyPurpose.SIGN) ||
                createKeySettings.signingAlgorithm == Algorithm.ES256)

        val settings = if (createKeySettings is SecureEnclaveCreateKeySettings) {
            createKeySettings
        } else {
            // If user passed in a generic SecureArea.CreateKeySettings, honor that (although
            // only key settings can really be honored).
            check(createKeySettings.ecCurve == EcCurve.P256)
            check(!createKeySettings.keyPurposes.contains(KeyPurpose.SIGN) ||
                createKeySettings.signingAlgorithm == Algorithm.ES256)
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
        val newAlias = insertKey(alias, settings, keyBlob, pubKey)
        return getKeyInfo(newAlias)
    }

    private suspend fun insertKey(
        alias: String?,
        settings: SecureEnclaveCreateKeySettings,
        keyBlob: ByteArray,
        publicKey: EcPublicKey,
    ): String {
        val map = CborMap.builder()
        map.put("keyPurposes", KeyPurpose.encodeSet(settings.keyPurposes))
        map.put("userAuthenticationRequired", settings.userAuthenticationRequired)
        map.put("userAuthenticationTypes",
            SecureEnclaveUserAuthType.encodeSet(settings.userAuthenticationTypes))
        map.put("curve", settings.ecCurve.coseCurveIdentifier)
        map.put("publicKey", publicKey.toDataItem())
        map.put("keyBlob", keyBlob)
        return storageTable.insert(alias, ByteString(Cbor.encode(map.end().build())), partitionId)
    }

    private suspend fun loadKey(alias: String): Pair<ByteArray, SecureEnclaveKeyInfo> {
        val data = storageTable.get(alias, partitionId)
            ?: throw IllegalArgumentException("No key with given alias")

        val map = Cbor.decode(data.toByteArray())
        val keyPurposes = map["keyPurposes"].asNumber.keyPurposeSet
        val userAuthenticationRequired = map["userAuthenticationRequired"].asBoolean
        val userAuthenticationTypes =
            SecureEnclaveUserAuthType.decodeSet(map["userAuthenticationTypes"].asNumber)
        val publicKey = map["publicKey"].asCoseKey.ecPublicKey
        val keyBlob = map["keyBlob"].asBstr

        val keyInfo = SecureEnclaveKeyInfo(
            alias,
            publicKey,
            keyPurposes,
            Algorithm.ES256,
            userAuthenticationRequired,
            userAuthenticationTypes)

        return Pair(keyBlob, keyInfo)
    }

    override suspend fun deleteKey(alias: String) {
        storageTable.delete(alias, partitionId)
    }

    override suspend fun sign(
        alias: String,
        dataToSign: ByteArray,
        keyUnlockData: KeyUnlockData?
    ): EcSignature {
        val (keyBlob, keyInfo) = loadKey(alias)
        check(keyInfo.keyPurposes.contains(KeyPurpose.SIGN))
        val unlockData = if (keyUnlockData is KeyUnlockInteractive) {
            // TODO: create LAContext with title/subtitle from KeyUnlockInteractive
            null
        } else {
            keyUnlockData
        }
        check(unlockData is SecureEnclaveKeyUnlockData?)
        return Crypto.secureEnclaveEcSign(keyBlob, dataToSign, unlockData)
    }

    override suspend fun keyAgreement(
        alias: String,
        otherKey: EcPublicKey,
        keyUnlockData: KeyUnlockData?
    ): ByteArray {
        val (keyBlob, keyInfo) = loadKey(alias)
        check(otherKey.curve == EcCurve.P256)
        check(keyInfo.keyPurposes.contains(KeyPurpose.AGREE_KEY))
        val unlockData = if (keyUnlockData is KeyUnlockInteractive) {
            // TODO: create LAContext with title/subtitle from KeyUnlockInteractive
            null
        } else {
            keyUnlockData
        }
        check(unlockData is SecureEnclaveKeyUnlockData?)
        return Crypto.secureEnclaveEcKeyAgreement(keyBlob, otherKey, unlockData)
    }

    override suspend fun getKeyInfo(alias: String): KeyInfo {
        val (_, keyInfo) = loadKey(alias)
        return keyInfo
    }

    override suspend fun getKeyInvalidated(alias: String): Boolean {
        return false
    }
}