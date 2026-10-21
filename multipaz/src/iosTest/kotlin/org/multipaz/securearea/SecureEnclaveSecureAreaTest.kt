package org.multipaz.securearea

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborMap
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.prompt.IosPromptModel
import org.multipaz.prompt.Reason
import org.multipaz.securearea.SecureEnclaveSecureArea.BiometryState
import org.multipaz.storage.ephemeral.EphemeralStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecureEnclaveSecureAreaTest {

    @Test
    fun testGetKeyInvalidatedNonExistentKeyThrows() = runTest {
        val storage = EphemeralStorage()
        val secureArea = SecureEnclaveSecureArea.create(storage)
        assertFailsWith<IllegalArgumentException> {
            secureArea.getKeyInvalidated("nonExistentKey")
        }
    }

    @Test
    fun testNonBiometryCurrentSetKeysNotInvalidatedWhenDomainStateChanges() = runTest {
        val storage = EphemeralStorage()
        val state1 = "domain-state-1".encodeToByteString()
        val state2 = "domain-state-2".encodeToByteString()
        var currentBiometryState: BiometryState = BiometryState.Enrolled(state1)

        val secureArea = SecureEnclaveSecureArea(
            storageTable = storage.getTable(SecureEnclaveSecureArea.tableSpec),
            partitionId = "default",
            getBiometryState = { currentBiometryState }
        )

        val settingsList = listOf(
            SecureEnclaveCreateKeySettings.Builder()
                .setAlgorithm(Algorithm.ESP256)
                .setUserAuthenticationRequired(false, emptySet())
                .build(),
            SecureEnclaveCreateKeySettings.Builder()
                .setAlgorithm(Algorithm.ESP256)
                .setUserAuthenticationRequired(true, setOf(SecureEnclaveUserAuthType.USER_PRESENCE))
                .build(),
            SecureEnclaveCreateKeySettings.Builder()
                .setAlgorithm(Algorithm.ESP256)
                .setUserAuthenticationRequired(true, setOf(SecureEnclaveUserAuthType.DEVICE_PASSCODE))
                .build(),
            SecureEnclaveCreateKeySettings.Builder()
                .setAlgorithm(Algorithm.ESP256)
                .setUserAuthenticationRequired(true, setOf(SecureEnclaveUserAuthType.BIOMETRY_ANY))
                .build()
        )

        val pubKey = Crypto.createEcPrivateKey(EcCurve.P256).use { it.publicKey }
        val aliases = settingsList.mapIndexed { index, settings ->
            val alias = "key_$index"
            secureArea.insertKey(alias, settings, byteArrayOf(1, 2, 3), pubKey)
            assertFalse(secureArea.getKeyInvalidated(alias))
            alias
        }

        // Changing biometric enrollment or removing biometrics does not invalidate these keys
        currentBiometryState = BiometryState.Enrolled(state2)
        for (alias in aliases) {
            assertFalse(secureArea.getKeyInvalidated(alias))
        }

        currentBiometryState = BiometryState.NotEnrolled
        for (alias in aliases) {
            assertFalse(secureArea.getKeyInvalidated(alias))
        }
    }

    @Test
    fun testBiometryCurrentSetKeyInvalidatedWhenDomainStateChanges() = runTest {
        val storage = EphemeralStorage()
        val state1 = "domain-state-1".encodeToByteString()
        val state2 = "domain-state-2".encodeToByteString()
        var currentBiometryState: BiometryState = BiometryState.Enrolled(state1)

        val secureArea = SecureEnclaveSecureArea(
            storageTable = storage.getTable(SecureEnclaveSecureArea.tableSpec),
            partitionId = "default",
            getBiometryState = { currentBiometryState }
        )

        val pubKey = Crypto.createEcPrivateKey(EcCurve.P256).use { it.publicKey }
        secureArea.insertKey(
            "signKey",
            SecureEnclaveCreateKeySettings.Builder()
                .setAlgorithm(Algorithm.ESP256)
                .setUserAuthenticationRequired(
                    true,
                    setOf(SecureEnclaveUserAuthType.BIOMETRY_CURRENT_SET)
                )
                .build(),
            byteArrayOf(1, 2, 3),
            pubKey
        )
        secureArea.insertKey(
            "kaKey",
            SecureEnclaveCreateKeySettings.Builder()
                .setAlgorithm(Algorithm.ECDH_P256)
                .setUserAuthenticationRequired(
                    true,
                    setOf(
                        SecureEnclaveUserAuthType.DEVICE_PASSCODE,
                        SecureEnclaveUserAuthType.BIOMETRY_CURRENT_SET
                    )
                )
                .build(),
            byteArrayOf(4, 5, 6),
            pubKey
        )

        // Initially valid
        assertFalse(secureArea.getKeyInvalidated("signKey"))
        assertFalse(secureArea.getKeyInvalidated("kaKey"))

        // Temporary lockout (BiometryState.Unavailable) must NOT report keys as invalidated
        currentBiometryState = BiometryState.Unavailable
        assertFalse(secureArea.getKeyInvalidated("signKey"))
        assertFalse(secureArea.getKeyInvalidated("kaKey"))

        // Biometric enrollment changes -> invalidated
        currentBiometryState = BiometryState.Enrolled(state2)
        assertTrue(secureArea.getKeyInvalidated("signKey"))
        assertTrue(secureArea.getKeyInvalidated("kaKey"))

        // getKeyInfo must still succeed on invalidated keys
        assertEquals("signKey", secureArea.getKeyInfo("signKey").alias)
        assertEquals("kaKey", secureArea.getKeyInfo("kaKey").alias)

        // Cryptographic operations must throw KeyInvalidatedException
        assertFailsWith<KeyInvalidatedException> {
            secureArea.sign("signKey", "hello".encodeToByteArray())
        }
        Crypto.createEcPrivateKey(EcCurve.P256).use { otherKey ->
            assertFailsWith<KeyInvalidatedException> {
                secureArea.keyAgreement("kaKey", otherKey.publicKey)
            }
        }

        // Biometrics completely unenrolled -> still invalidated
        currentBiometryState = BiometryState.NotEnrolled
        assertTrue(secureArea.getKeyInvalidated("signKey"))
        assertTrue(secureArea.getKeyInvalidated("kaKey"))
    }

    @Test
    fun testBiometryCurrentSetKeyCreatedWhenNotEnrolledIsInvalidated() = runTest {
        val storage = EphemeralStorage()
        var currentBiometryState: BiometryState = BiometryState.NotEnrolled

        val secureArea = SecureEnclaveSecureArea(
            storageTable = storage.getTable(SecureEnclaveSecureArea.tableSpec),
            partitionId = "default",
            getBiometryState = { currentBiometryState }
        )

        val pubKey = Crypto.createEcPrivateKey(EcCurve.P256).use { it.publicKey }
        secureArea.insertKey(
            "keyCreatedWithoutBiometrics",
            SecureEnclaveCreateKeySettings.Builder()
                .setAlgorithm(Algorithm.ESP256)
                .setUserAuthenticationRequired(
                    true,
                    setOf(SecureEnclaveUserAuthType.BIOMETRY_CURRENT_SET)
                )
                .build(),
            byteArrayOf(1, 2, 3),
            pubKey
        )

        assertTrue(secureArea.getKeyInvalidated("keyCreatedWithoutBiometrics"))

        // Even if biometrics are enrolled later, the key remains invalidated
        currentBiometryState = BiometryState.Enrolled("domain-state-1".encodeToByteString())
        assertTrue(secureArea.getKeyInvalidated("keyCreatedWithoutBiometrics"))
        assertFailsWith<KeyInvalidatedException> {
            secureArea.sign("keyCreatedWithoutBiometrics", "hello".encodeToByteArray())
        }
    }

    @Test
    fun testLegacyMetadataWithoutBiometryDomainState() = runTest {
        val storage = EphemeralStorage()
        val table = storage.getTable(SecureEnclaveSecureArea.tableSpec)
        val secureArea = SecureEnclaveSecureArea(
            storageTable = table,
            partitionId = "default",
            getBiometryState = { BiometryState.Enrolled("domain-state-new".encodeToByteString()) }
        )

        // Create a key with Unavailable biometry state to simulate legacy metadata (biometryDomainState = null)
        val legacySecureArea = SecureEnclaveSecureArea(
            storageTable = table,
            partitionId = "default",
            getBiometryState = { BiometryState.Unavailable }
        )
        val pubKey = Crypto.createEcPrivateKey(EcCurve.P256).use { it.publicKey }
        legacySecureArea.insertKey(
            "legacyKey",
            SecureEnclaveCreateKeySettings.Builder()
                .setAlgorithm(Algorithm.ESP256)
                .setUserAuthenticationRequired(
                    true,
                    setOf(SecureEnclaveUserAuthType.BIOMETRY_CURRENT_SET)
                )
                .build(),
            byteArrayOf(1, 2, 3),
            pubKey
        )

        // Verify biometryDomainState is absent in CBOR and getKeyInvalidated returns false
        val rawCbor = table.get("legacyKey", "default")!!.toByteArray()
        val map = Cbor.decode(rawCbor) as CborMap
        assertFalse(map.hasKey("biometryDomainState"))
        assertFalse(secureArea.getKeyInvalidated("legacyKey"))
    }

    @Test
    fun testDefaultKeyUnlockDataProviderUsesPromptModel() = runTest {
        val storage = EphemeralStorage()
        val secureArea = SecureEnclaveSecureArea.create(storage)
        val pubKey = Crypto.createEcPrivateKey(EcCurve.P256).use { it.publicKey }
        secureArea.insertKey(
            "authKey",
            SecureEnclaveCreateKeySettings.Builder()
                .setAlgorithm(Algorithm.ESP256)
                .setUserAuthenticationRequired(
                    true,
                    setOf(SecureEnclaveUserAuthType.USER_PRESENCE)
                )
                .build(),
            byteArrayOf(1, 2, 3),
            pubKey
        )

        // Without PromptModel in context, falls back to IosPromptModel.defaultConvertToHumanReadable
        val expectedReason = Reason.HumanReadable(
            title = "Sign credential request",
            subtitle = "Confirm with Face ID",
            requireConfirmation = false
        )
        val unlockDataList1 = secureArea.unlockKey("authKey", expectedReason)
        assertEquals(1, unlockDataList1.size)
        assertTrue(unlockDataList1[0] is SecureEnclaveKeyUnlockData)

        // With custom PromptModel in context, invokes PromptModel.toHumanReadable
        var capturedReason: Reason? = null
        val customPromptModel = IosPromptModel.Builder { reason, _ ->
            capturedReason = reason
            Reason.HumanReadable(
                title = "Custom Title",
                subtitle = "",
                requireConfirmation = false
            )
        }.build()
        val unlockDataList2 = withContext(customPromptModel) {
            secureArea.unlockKey("authKey", expectedReason)
        }
        assertEquals(1, unlockDataList2.size)
        assertTrue(unlockDataList2[0] is SecureEnclaveKeyUnlockData)
        assertEquals(expectedReason, capturedReason)
    }
}
