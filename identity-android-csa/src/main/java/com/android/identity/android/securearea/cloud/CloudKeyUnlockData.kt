package com.android.identity.android.securearea.cloud

import androidx.biometric.BiometricPrompt
import com.android.identity.securearea.KeyUnlockData
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Signature
import java.security.spec.InvalidKeySpecException

/**
 * A class to provide information used for unlocking a Cloud Secure Area key.
 *
 * @param alias the alias of the key to unlock.
 */
class CloudKeyUnlockData(
    private val cloudSecureArea: CloudSecureArea,
    private val alias: String
) : KeyUnlockData {
    var cryptoObject_: BiometricPrompt.CryptoObject? = null

    /**
     * The passphrase used to unlock the key or `null` if a passphrase isn't required.
     */
    var passphrase: String? = null

    /**
     * The [BiometricPrompt.CryptoObject] for unlocking the key if user authentication is needed.
     *
     * This can be used with [BiometricPrompt] to unlock the key. On successful authentication,
     * this object should be passed to the [CloudSecureArea.sign] or [CloudSecureArea.keyAgreement]
     * methods.
     *
     * Note that a [BiometricPrompt.CryptoObject] is returned only if the key is
     * configured to require authentication for every use of the key, that is, when the
     * key was created with a zero timeout as per
     * [CloudCreateKeySettings.Builder.setUserAuthenticationRequired]. If this is not the
     * cass `null` is returned.
     */
    val cryptoObject: BiometricPrompt.CryptoObject?
        get() {
            if (cryptoObject_ != null) {
                return cryptoObject_
            } else try {
                val ks = KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                val localKeyAlias = cloudSecureArea.getLocalKeyAlias(alias)
                val entry = ks.getEntry(localKeyAlias, null)
                    ?: throw IllegalArgumentException("No entry for alias")
                val privateKey = (entry as KeyStore.PrivateKeyEntry).privateKey
                val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
                try {
                    val keyInfo = factory.getKeySpec(
                        privateKey,
                        android.security.keystore.KeyInfo::class.java
                    )
                    if (keyInfo.userAuthenticationValidityDurationSeconds > 0) {
                        // Key is not auth-per-op, no CryptoObject required.
                        return null
                    }
                } catch (e: InvalidKeySpecException) {
                    throw IllegalStateException("Given key is not an Android Keystore key", e)
                }
                val signature = Signature.getInstance("SHA256withECDSA")
                signature.initSign(privateKey)
                cryptoObject_ = BiometricPrompt.CryptoObject(signature)
                return cryptoObject_
            } catch (e: Exception) {
                throw IllegalStateException("Unexpected exception", e)
            }
        }
 }