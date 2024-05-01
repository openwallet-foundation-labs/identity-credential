package com.android.identity_credential.wallet

import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.android.identity.android.securearea.UserAuthenticationType

/**
 * Prompts user for authentication, and calls the provided functions when authentication is
 * complete. Biometric authentication will be offered first if both [UserAuthenticationType.LSKF]
 * and [UserAuthenticationType.BIOMETRIC] are allowed.
 *
 * @param activity the activity hosting the authentication prompt
 * @param title the title for the authentication prompt
 * @param subtitle the subtitle for the authentication prompt
 * @param cryptoObject a crypto object to be associated with this authentication
 * @param userAuthenticationTypes the set of allowed user authentication types, must contain at
 *                                least one element
 * @param requireConfirmation option to require explicit user confirmation after a passive biometric
 * @param onSuccess the function which will be called when the user successfully authenticates
 * @param onCanceled the function which will be called when the user cancels
 * @param onError the function which will be called when there is an unexpected error in the user
 *                authentication process - a throwable will be passed into this function
 */
fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    cryptoObject: BiometricPrompt.CryptoObject?,
    userAuthenticationTypes: Set<UserAuthenticationType>,
    requireConfirmation: Boolean,
    onSuccess: () -> Unit,
    onCanceled: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    if (userAuthenticationTypes.isEmpty()) {
        onError.invoke(
            IllegalStateException(
                "userAuthenticationTypes must contain at least one authentication type",
            ),
        )
    }

    BiometricUserAuthPrompt(
        activity = activity,
        title = title,
        subtitle = subtitle,
        cryptoObject = cryptoObject,
        userAuthenticationTypes = userAuthenticationTypes,
        requireConfirmation = requireConfirmation,
        onSuccess = onSuccess,
        onCanceled = onCanceled,
        onError = onError,
    ).authenticate()
}

private class BiometricUserAuthPrompt(
    private val activity: FragmentActivity,
    private val title: String,
    private val subtitle: String,
    private val cryptoObject: BiometricPrompt.CryptoObject?,
    private val userAuthenticationTypes: Set<UserAuthenticationType>,
    private val requireConfirmation: Boolean,
    private val onSuccess: () -> Unit,
    private val onCanceled: () -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    companion object {
        private const val TAG = "BiometricPromptViewModel"
    }

    private var lskfOnNegativeBtn: Boolean = false

    private var biometricAuthCallback =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(
                errorCode: Int,
                errorString: CharSequence,
            ) {
                super.onAuthenticationError(errorCode, errorString)
                if (setOf(
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
                    ).contains(errorCode) && lskfOnNegativeBtn
                ) {
                    // if no delay is injected, then biometric prompt's auth callbacks would not be called
                    Handler(Looper.getMainLooper()).postDelayed({
                        authenticateLskf()
                    }, 100)
                } else if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    onCanceled.invoke()
                } else {
                    onError.invoke(IllegalStateException(errorString.toString()))
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        }

    private var biometricPrompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            biometricAuthCallback,
        )

    fun authenticate() {
        lskfOnNegativeBtn = userAuthenticationTypes.contains(UserAuthenticationType.LSKF)

        if (userAuthenticationTypes.contains(UserAuthenticationType.BIOMETRIC)) {
            authenticateBiometric()
        } else {
            authenticateLskf()
        }
    }

    private fun authenticateBiometric() {
        val negativeTxt =
            if (lskfOnNegativeBtn) {
                resourceString(R.string.biometric_prompt_negative_btn_lskf)
            } else {
                resourceString(R.string.biometric_prompt_negative_btn_no_lskf)
            }

        val biometricPromptInfo =
            PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeTxt)
                .setConfirmationRequired(requireConfirmation)
                .build()

        if (cryptoObject != null) {
            biometricPrompt.authenticate(biometricPromptInfo, cryptoObject!!)
        } else {
            biometricPrompt.authenticate(biometricPromptInfo)
        }
    }

    private fun authenticateLskf() {
        lskfOnNegativeBtn = false

        val lskfPromptInfo =
            PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .setConfirmationRequired(requireConfirmation)
                .build()

        if (cryptoObject != null) {
            biometricPrompt.authenticate(lskfPromptInfo, cryptoObject!!)
        } else {
            biometricPrompt.authenticate(lskfPromptInfo)
        }
    }

    private fun resourceString(
        id: Int,
        vararg text: String,
    ): String {
        return activity.applicationContext.resources.getString(id, *text)
    }
}
