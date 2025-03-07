package org.multipaz_credential.wallet.ui.prompt.biometric

import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.multipaz.securearea.UserAuthenticationType
import org.multipaz_credential.wallet.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Show the Biometric prompt
 *
 * Async function that shows the Biometric Prompt and returns the result as a [Boolean]
 * indicating whether authentication was successful, or raises/throws an Exception, such as
 * [IllegalStateException], if an error prevented the Biometric Prompt from showing.
 *
 * @param activity the [FragmentActivity] hosting the authentication prompt.
 * @param title the title for the authentication prompt.
 * @param subtitle the subtitle for the authentication prompt.
 * @param cryptoObject a crypto object to be associated with this authentication.
 * @param userAuthenticationTypes the set of allowed user authentication types, must contain at
 *                                least one element.
 * @param requireConfirmation option to require explicit user confirmation after a passive biometric.
 * @return a [Boolean] indicating whether biometric authentication was successful.
 * @throws Exception if there were errors showing the prompt.
 */
suspend fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    cryptoObject: BiometricPrompt.CryptoObject?,
    userAuthenticationTypes: Set<UserAuthenticationType>,
    requireConfirmation: Boolean,
): Boolean = suspendCancellableCoroutine { continuation ->
    // wrap around the [showBiometricPrompt] function signature with callbacks to return true,
    // false or raise an Exception
    showBiometricPrompt(
        activity = activity,
        title = title,
        subtitle = subtitle,
        cryptoObject = cryptoObject,
        userAuthenticationTypes = userAuthenticationTypes,
        requireConfirmation = requireConfirmation,
        onSuccess = {
            continuation.resume(true)
        },
        onCanceled = {
            continuation.resume(false)
        },
        onError = {
            continuation.resumeWithException(it)
        }
    )
}

/**
 * Prompts user for authentication, and calls the provided functions when authentication is
 * complete. Biometric authentication will be offered first if both [UserAuthenticationType.LSKF]
 * and [UserAuthenticationType.BIOMETRIC] are allowed.
 *
 * @param activity the [FragmentActivity] hosting the authentication prompt
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
                "userAuthenticationTypes must contain at least one authentication type"
            )
        )
    }

    BiometricPrompt(
        activity = activity,
        title = title,
        subtitle = subtitle,
        cryptoObject = cryptoObject,
        userAuthenticationTypes = userAuthenticationTypes,
        requireConfirmation = requireConfirmation,
        onSuccess = onSuccess,
        onCanceled = onCanceled,
        onError = onError
    ).authenticate()
}

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
private class BiometricPrompt(
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

    private var biometricAuthCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(
            errorCode: Int,
            errorString: CharSequence
        ) {
            super.onAuthenticationError(errorCode, errorString)
            if (setOf(
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_UNABLE_TO_PROCESS
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

        override fun onAuthenticationSucceeded(
            result: BiometricPrompt.AuthenticationResult
        ) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
        }
    }

    private var androidBiometricPrompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        biometricAuthCallback
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
            if (lskfOnNegativeBtn) resourceString(R.string.biometric_prompt_negative_btn_lskf)
            else resourceString(R.string.biometric_prompt_negative_btn_no_lskf)

        val biometricPromptInfo = PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeTxt)
            .setConfirmationRequired(requireConfirmation)
            .build()

        if (cryptoObject != null) {
            androidBiometricPrompt.authenticate(biometricPromptInfo, cryptoObject!!)
        } else {
            androidBiometricPrompt.authenticate(biometricPromptInfo)
        }
    }

    private fun authenticateLskf() {
        lskfOnNegativeBtn = false

        val lskfPromptInfo = PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .setConfirmationRequired(requireConfirmation)
            .build()

        if (cryptoObject != null) {
            androidBiometricPrompt.authenticate(lskfPromptInfo, cryptoObject!!)
        } else {
            androidBiometricPrompt.authenticate(lskfPromptInfo)
        }
    }

    private fun resourceString(id: Int, vararg text: String): String {
        return activity.applicationContext.resources.getString(id, *text)
    }
}
