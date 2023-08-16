/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity.testapp

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.android.identity.securearea.SecureArea
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.securearea.SecureArea.KEY_PURPOSE_AGREE_KEY
import com.android.identity.securearea.SecureArea.KEY_PURPOSE_SIGN
import com.android.identity.util.Logger
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException

class MainActivity : AppCompatActivity() {
    private lateinit var otherKeyPairForEcdh: KeyPair
    private lateinit var secureArea: AndroidKeystoreSecureArea
    private lateinit var storage: AndroidStorageEngine
    private val TAG = "MainActivity"

    private fun doUserAuthAndTryAgain(keyAlias : String,
                                      keyPurpose: Int,
                                      unlockData: AndroidKeystoreSecureArea.KeyUnlockData,
                                      forceLskf: Boolean) {

        var cryptoObject : BiometricPrompt.CryptoObject? = null
        var title = ""

        if (keyPurpose == KEY_PURPOSE_SIGN) {
            title = "Unlock to sign with key"
            cryptoObject = unlockData.getCryptoObjectForSigning(SecureArea.ALGORITHM_ES256)
        } else if (keyPurpose == KEY_PURPOSE_AGREE_KEY) {
            title = "Unlock to ECDH with key"
            // TODO: Note that getCryptoObjectForEcdh() will throw in the auth-per-op case
            //  because AOSP is currently lacks a CryptoObject constructor which takes a
            //  KeyAgreement object. See b/282058146.
            try {
                cryptoObject = unlockData.cryptoObjectForKeyAgreement
            } catch (e: Exception) {
                Toast.makeText(applicationContext, e.message, Toast.LENGTH_LONG).show()
                return
            }
        }

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authentication required")
            .setSubtitle(title)
        if (forceLskf) {
            // TODO: this works only on Android 11 or later but for now this is fine
            //   as this is just a reference/test app and this path is only hit if
            //   the user actually presses the "Use LSKF" button.  Longer term, we should
            //   fall back to using KeyGuard which will work on all Android versions.
            promptInfoBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            val canUseBiometricAuth = BiometricManager
                .from(applicationContext)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
            if (canUseBiometricAuth) {
                promptInfoBuilder.setNegativeButtonText("Use LSKF")
            } else {
                promptInfoBuilder.setDeviceCredentialAllowed(true)
            }
        }

        val biometricPromptInfo = promptInfoBuilder.build()
        val biometricPrompt = BiometricPrompt(this,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Logger.d(TAG, "onAuthenticationError $errorCode $errString")
                        // "Use LSKF"...  without this delay, the prompt won't work correctly
                        Handler(Looper.getMainLooper()).postDelayed({
                            doUserAuthAndTryAgain(keyAlias, keyPurpose, unlockData, true)
                                                                    }, 100)
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Logger.d(TAG, "onAuthenticationSucceeded $result")

                    // Now try to use the key again...
                    if (keyPurpose == KEY_PURPOSE_SIGN) {
                        val derSignature = secureArea.sign(
                            keyAlias,
                            SecureArea.ALGORITHM_ES256,
                            "data".toByteArray(),
                            unlockData
                        )
                        Logger.dHex(
                            TAG,
                            "Made signature with key '$keyAlias' after authentication",
                            derSignature
                        )
                    } else if (keyPurpose == KEY_PURPOSE_AGREE_KEY) {
                        val sharedSecret = secureArea.keyAgreement(
                            keyAlias,
                            otherKeyPairForEcdh.public,
                            null)
                        Logger.dHex(TAG,
                            "ECDH with key '$keyAlias' without authentication",
                            sharedSecret)
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Logger.d(TAG, "onAuthenticationFailed")
                }
            })

        if (cryptoObject != null) {
            biometricPrompt.authenticate(biometricPromptInfo, cryptoObject)
        } else {
            biometricPrompt.authenticate(biometricPromptInfo)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val keySignNoAuth = "key_sign_no_auth"
        val keySignTimeout10Sec = "key_sign_timeout_10_sec"
        val keySignTimeoutNone = "key_sign_timeout_none"
        val keyEcdhNoAuth = "key_ecdh_no_auth"
        val keyEcdhTimeout10Sec = "key_ecdh_timeout_10_sec"
        val keyEcdhTimeoutNone = "key_ecdh_timeout_none"

        otherKeyPairForEcdh = try {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256)
            kpg.generateKeyPair()
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError("Unexpected exception", e)
        }

        storage = AndroidStorageEngine.Builder(applicationContext,
            File(applicationContext.dataDir, "ic-testing"))
            .build()
        secureArea =
            AndroidKeystoreSecureArea(
                applicationContext,
                storage
            )

        // Specify that either LSKF or Biometric Auth can be used. This is guaranteed to work on
        // any Android device that this app can run out (API 24 or higher).
        //
        val authType = (AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_LSKF
                + AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_BIOMETRIC)

        // Create EC signing keys. This is guaranteed to work on any Android device
        // that this app can run out (API 24 or higher).
        //
        secureArea.createKey(keySignNoAuth,
            AndroidKeystoreSecureArea.CreateKeySettings.Builder("challenge".toByteArray()).build())

        val keyguardManager: KeyguardManager =
            applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        // Can only use auth-bound keys with a lockscreen
        if (keyguardManager.isDeviceSecure) {
            secureArea.createKey(
                keySignTimeout10Sec,
                AndroidKeystoreSecureArea.CreateKeySettings.Builder("challenge".toByteArray())
                    .setUserAuthenticationRequired(true, 10 * 1000, authType)
                    .build()
            )
            secureArea.createKey(
                keySignTimeoutNone,
                AndroidKeystoreSecureArea.CreateKeySettings.Builder("challenge".toByteArray())
                    .setUserAuthenticationRequired(true, 0, authType)
                    .build()
            )
        } else {
            Logger.w(TAG, "No lock screen, skipping signing buttons for auth-bound keys")
            (findViewById(R.id.button_sign_auth_timeout_10_sec) as Button).isEnabled = false
            (findViewById(R.id.button_sign_auth_timeout_0) as Button).isEnabled = false
        }

        // Create EC ECDH keys. Note that this is only supported on Android 12
        // and later so we need to handle it not being available.
        //
        try {
            secureArea.createKey(
                keyEcdhNoAuth,
                AndroidKeystoreSecureArea.CreateKeySettings.Builder("challenge".toByteArray())
                    .setKeyPurposes(AndroidKeystoreSecureArea.KEY_PURPOSE_AGREE_KEY)
                    .build())
        } catch (e: IllegalArgumentException) {
            Logger.w(TAG, "Error creating key $keyEcdhNoAuth", e)
            (findViewById(R.id.button_ecdh_no_auth) as Button).isEnabled = false
        }
        // Can only use auth-bound keys with a lockscreen
        if (keyguardManager.isDeviceSecure) {
            try {
                secureArea.createKey(
                    keyEcdhTimeout10Sec,
                    AndroidKeystoreSecureArea.CreateKeySettings.Builder("challenge".toByteArray())
                        .setKeyPurposes(AndroidKeystoreSecureArea.KEY_PURPOSE_AGREE_KEY)
                        .setUserAuthenticationRequired(true, 10 * 1000, authType)
                        .build()
                )
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "Error creating key $keyEcdhTimeout10Sec", e)
                (findViewById(R.id.button_ecdh_auth_timeout_10_sec) as Button).isEnabled = false
            }
            try {
                secureArea.createKey(
                    keyEcdhTimeoutNone,
                    AndroidKeystoreSecureArea.CreateKeySettings.Builder("challenge".toByteArray())
                        .setKeyPurposes(AndroidKeystoreSecureArea.KEY_PURPOSE_AGREE_KEY)
                        .setUserAuthenticationRequired(true, 0, authType)
                        .build()
                )
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "Error creating key $keyEcdhTimeoutNone", e)
                (findViewById(R.id.button_ecdh_auth_timeout_0) as Button).isEnabled = false
            }
        } else {
            Logger.w(TAG, "No lock screen, skipping ECDH buttons for auth-bound keys")
            (findViewById(R.id.button_ecdh_auth_timeout_10_sec) as Button).isEnabled = false
            (findViewById(R.id.button_ecdh_auth_timeout_0) as Button).isEnabled = false
        }

        (findViewById(R.id.button_sign_no_auth) as Button).setOnClickListener {
            val derSignature = secureArea.sign(keySignNoAuth,
                SecureArea.ALGORITHM_ES256,
                "data".toByteArray(),
                null)
            Logger.dHex(TAG,
                "Made signature with key '$keySignNoAuth' without authentication",
                derSignature)
        }

        (findViewById(R.id.button_sign_auth_timeout_10_sec) as Button).setOnClickListener {
            try {
                val derSignature = secureArea.sign(
                    keySignTimeout10Sec,
                    SecureArea.ALGORITHM_ES256,
                    "data".toByteArray(),
                    null
                )
                Logger.dHex(TAG,
                    "Made signature with key '$keySignTimeout10Sec' without authentication",
                    derSignature)
            } catch (e: SecureArea.KeyLockedException) {
                val unlockData = AndroidKeystoreSecureArea.KeyUnlockData(keySignTimeout10Sec)
                doUserAuthAndTryAgain(keySignTimeout10Sec, KEY_PURPOSE_SIGN, unlockData, false)
            }
        }

        (findViewById(R.id.button_sign_auth_timeout_0) as Button).setOnClickListener {
            try {
                val derSignature = secureArea.sign(
                    keySignTimeoutNone,
                    SecureArea.ALGORITHM_ES256,
                    "data".toByteArray(),
                    null
                )
                Logger.eHex(TAG,
                    "Made signature with key '$keySignTimeoutNone' without "
                            + "authentication (should never happen)",
                    derSignature)
            } catch (e: SecureArea.KeyLockedException) {
                val unlockData = AndroidKeystoreSecureArea.KeyUnlockData(keySignTimeoutNone)
                doUserAuthAndTryAgain(keySignTimeoutNone, KEY_PURPOSE_SIGN, unlockData, false)
            }
        }

        (findViewById(R.id.button_ecdh_no_auth) as Button).setOnClickListener {
            val sharedSecret = secureArea.keyAgreement(keyEcdhNoAuth,
                otherKeyPairForEcdh.public,
                null)
            Logger.dHex(TAG,
                "ECDH with key '$keySignNoAuth' without authentication",
                sharedSecret)
        }

        (findViewById(R.id.button_ecdh_auth_timeout_10_sec) as Button).setOnClickListener {
            try {
                val sharedSecret = secureArea.keyAgreement(keyEcdhTimeout10Sec,
                    otherKeyPairForEcdh.public,
                    null)
                Logger.dHex(TAG,
                    "ECDH with key '$keySignNoAuth' without authentication",
                    sharedSecret)
            } catch (e: SecureArea.KeyLockedException) {
                val unlockData = AndroidKeystoreSecureArea.KeyUnlockData(keyEcdhTimeout10Sec)
                doUserAuthAndTryAgain(keyEcdhTimeout10Sec, KEY_PURPOSE_AGREE_KEY, unlockData, false)
            }
        }

        (findViewById(R.id.button_ecdh_auth_timeout_0) as Button).setOnClickListener {
            try {
                val sharedSecret = secureArea.keyAgreement(keyEcdhTimeoutNone,
                    otherKeyPairForEcdh.public,
                    null)
                Logger.dHex(TAG,
                    "ECDH with key '$keySignNoAuth' without authentication",
                    sharedSecret)
            } catch (e: SecureArea.KeyLockedException) {
                val unlockData = AndroidKeystoreSecureArea.KeyUnlockData(keyEcdhTimeoutNone)
                doUserAuthAndTryAgain(keyEcdhTimeoutNone, KEY_PURPOSE_AGREE_KEY, unlockData, false)
            }
        }

    }
}