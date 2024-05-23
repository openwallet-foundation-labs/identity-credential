package com.android.identity_credential.wallet.presentation

import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import com.android.identity.android.securearea.AndroidKeystoreKeyInfo
import com.android.identity.android.securearea.AndroidKeystoreKeyUnlockData
import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.crypto.Algorithm
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.securearea.KeyUnlockData
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.showBiometricPrompt
import com.android.identity_credential.wallet.ui.destination.consentprompt.ConsentPromptData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Extension function for performing an MDL Presentation from a FragmentActivity.
 * Can be called from a compose context via LaunchedEffect().
 * Returns a PresentationResult object that contains the result of performing the Presentation -
 * either bytes to send to the requesting verifier party or an Exception if something didn't go as
 * expected (one of the prompts failed due to user cancellation or an error was encountered).
 *
 * This asynchronous extension function defines asynchronous functions for each of the prompts,
 * as well as for generating the response bytes to send after all the prompts succeeded,
 * and runs them in a synchronous fashion defining the order in which the prompts should be shown
 * to the user after a prompt succeeds.
 *
 * Each prompt function returns a PromptResult instance which defines the resulting state of the prompt.
 * These functions are asynchronous but use a semaphore (Waiter) to pause the async coroutine from
 * returning the final state until the prompt's callback functions are called (from the prompt) and
 * provide the resulting state and resume running the coroutine that ultimately returns.
 */
suspend fun FragmentActivity.presentInPresentationActivity(
    consentPromptData: ConsentPromptData,
    documentTypeRepository: DocumentTypeRepository,
    transferHelper: TransferHelper,
): PresentationResult {
    // a semaphore used to pause and resume a coroutine as needed
    val waiter = Waiter()

    // semaphore helper function to pause coroutine from running
    fun CoroutineScope.pauseCoroutine() = run {
        launch {
            waiter.doWait()
        }
    }

    // semaphore helper function to resume coroutine from waiting
    fun CoroutineScope.resumeCoroutine() = run {
        launch {
            waiter.doNotify()
        }
    }

    /**
     * Show Consent Prompt function and return user's choice, Confirm or Cancel as a PromptResult class,
     * PromptSuccess or PromptFail (with "PromptCanceledException") respectively.
     *
     * @return an instance of PromptResult containing the resulting state from user's actions, either
     * a [PromptResult.PromptSuccess] if user tapped on "OK" or [PromptResult.PromptFail] if user
     * tapped on "Cancel".
     */
    suspend fun showConsentPrompt(): PromptResult {
        // default response is user tapping on Cancel
        var promptResponse: PromptResult = PromptResult.PromptFail()
        // new instance of the ConsentPrompt bottom sheet dialog fragment but not shown yet
        val consentPromptDialog =
            ConsentPromptDialog(consentPromptData = consentPromptData, documentTypeRepository)
        // use deferred to reference the async coroutine that is paused immediately after defining the
        // listener, and is resumed once the user performs an action on the prompt (which extracts
        // the resulting state and returns it)
        val deferredResponse: Deferred<Unit> = CoroutineScope(Dispatchers.Main).async {
            // define the listener responsible for resuming the coroutine once user performs an action
            consentPromptDialog.setResponseListener(object :
                ConsentPromptDialog.PromptResponseListener {
                override fun onConfirm() {
                    promptResponse = PromptResult.PromptSuccess
                    resumeCoroutine()
                }

                override fun onCancel() {
                    promptResponse = PromptResult.PromptFail(PromptCanceledException())
                    resumeCoroutine()
                }
            })
            // show Consent Prompt but don't allow this coroutine to terminate until we have user's input
            consentPromptDialog.show(supportFragmentManager, null)
            pauseCoroutine()
        }
        // pause and wait for coroutine to be resumed from the callbacks
        deferredResponse.await()
        // return the result extracted from the callbacks
        return promptResponse
    }


    /**
     * Show the Biometrics Prompt for a given MdocCredential that failed to produce response bytes
     * because of a locked authorization key.
     *
     * @param credential the MdocCredential that was attempted to be used to generate the response
     * bytes but encountered a locked authorization key.
     * @return an instance of PromptResult defining the state of the Biometrics prompt result, either
     * a [PromptResult.PromptFail] if user cancelled or an error was encountered or a
     * [PromptResult.UnlockedKey] containing key unlock data if the user was successful at
     * authenticating with biometrics.
     */
    suspend fun showBiometricsPrompt(credential: MdocCredential): PromptResult {
        val keyInfo = credential.secureArea.getKeyInfo(credential.alias)
        var userAuthenticationTypes = emptySet<UserAuthenticationType>()
        if (keyInfo is AndroidKeystoreKeyInfo) {
            userAuthenticationTypes = keyInfo.userAuthenticationTypes
        }

        val unlockData = AndroidKeystoreKeyUnlockData(credential.alias)
        val cryptoObject = unlockData.getCryptoObjectForSigning(Algorithm.ES256)

        // default response is user tapping on Cancel
        var promptResponse: PromptResult = PromptResult.PromptFail()
        // use deferred to reference the async coroutine that is paused immediately after showing
        // Biometrics prompt and is resumed once the user succeeds or fails authentication or an
        // error is encountered
        val deferredResponse: Deferred<Unit> = CoroutineScope(Dispatchers.Main).async {
            showBiometricPrompt(
                activity = this@presentInPresentationActivity,
                title = applicationContext.resources.getString(R.string.presentation_biometric_prompt_title),
                subtitle = applicationContext.resources.getString(R.string.presentation_biometric_prompt_subtitle),
                cryptoObject = cryptoObject,
                userAuthenticationTypes = userAuthenticationTypes,
                requireConfirmation = false,
                onCanceled = {
                    promptResponse = PromptResult.PromptFail(PromptCanceledException())
                    resumeCoroutine()
                },
                onSuccess = {
                    promptResponse = PromptResult.UnlockedKey(credential, unlockData)
                    resumeCoroutine()
                },
                onError = { exception ->
                    promptResponse = PromptResult.PromptFail(exception)
                    resumeCoroutine()
                },
            )
            pauseCoroutine()
        }
        // pause and wait for coroutine to be resumed from the callbacks
        deferredResponse.await()
        // return the result extracted from the callbacks
        return promptResponse
    }

    /**
     * Attempt to generate the response bytes to send to verifying party using the specified credential
     * and key unlock data. Returns the generated bytes if the key unlock data is valid or an object
     * defining that the authorization key is locked (and thus should initiate a biometrics prompt to
     * get valid key unlock data)
     *
     * @param credential the MdocCredential used to generate the key unlock data.
     * @param keyUnlockData the KeyUnlockData after a successful biometrics authentication.
     * @return the resulting state of trying to generate the response bytes, either a [PromptResult.SuccessfulResponse]
     * with the response bytes or [PromptResult.LockedKey] with the MdocCredential that yields to having
     * the authorization key locked.
     */
    suspend fun tryGenerateResponseBytes(
        credential: MdocCredential? = null,
        keyUnlockData: KeyUnlockData? = null
    ): PromptResult {
        // default response is PromptFail(), which, if returned infers the coroutine never ran
        var promptResponse: PromptResult = PromptResult.PromptFail()

        // use deferred to reference the async coroutine that is paused immediately after trying to
        // finish processing the request and generating the response bytes and is resumed on successful
        // generation of bytes or if the authorization key is found to be locked
        val deferredResponse: Deferred<Unit> = CoroutineScope(Dispatchers.Default).async {
            transferHelper.finishProcessingRequest(
                requestedDocType = consentPromptData.docType,
                credentialId = consentPromptData.credentialId,
                documentRequest = consentPromptData.documentRequest,
                keyUnlockData = keyUnlockData,
                onFinishedProcessing = {
                    promptResponse = PromptResult.SuccessfulResponse(it)
                    resumeCoroutine()
                },
                onAuthenticationKeyLocked = {
                    promptResponse = PromptResult.LockedKey(it)
                    resumeCoroutine()
                },
                credential = credential
            )
            // pause the coroutine from ending immediately
            pauseCoroutine()
        }
        // pause and wait for coroutine to be resumed from the callbacks
        deferredResponse.await()
        // return the result extracted from the callbacks
        return promptResponse
    }


    /** Start logic for showing the prompts in a sequential order **/


    // final bytes to be sent once all prompts are successful
    var responseBytes: ByteArray? = null
    // credential used to unlock authorization key
    var credential: MdocCredential? = null
    // data used to unlock the authorization key
    var keyUnlockData: KeyUnlockData? = null

    // result re-used amongst prompts results and response generation attempts
    var result = showConsentPrompt()
    // if consent prompt failed it's because of a Cancel (and not a thrown Exception)
    if (result is PromptResult.PromptFail) {
        return PresentationResult(exception = result.exception)
    }

    // consent prompt was successful
    // try generate the response bytes, if authorization key is locked then show biometrics prompt
    // and loop until a PromptFail is returned or a SuccessfulResponse with generated bytes
    while (result !is PromptResult.SuccessfulResponse) {
        // try to generate a response
        result = tryGenerateResponseBytes(credential = credential, keyUnlockData = keyUnlockData)

        // we got a locked key, show Biometrics Prompt
        if (result is PromptResult.LockedKey) {
            result = showBiometricsPrompt(result.credential)
        }

        // a Fail is returned, return the exception as the presentation result (and exit the loop)
        if (result is PromptResult.PromptFail) {
            return PresentationResult(exception = result.exception)
        } else if (result is PromptResult.UnlockedKey) {
            // successful unlock of authorization key, extract the credential and key unlock data
            // (to retry generating response bytes on next loop)
            credential = result.credential
            keyUnlockData = result.keyUnlockData
        }
    }

    // at this point result is guaranteed to be SuccessfulResponse, extract the response bytes
    if (result.responseBytes == null) {
        return PresentationResult(exception = IllegalStateException("Expected non-null Response Bytes following the successful generation of response bytes"))
    }
    responseBytes = result.responseBytes

    // (add passphrase prompt logic here)

    return PresentationResult(responseBytes = responseBytes)
}


/**
 * Semaphore used to make a coroutine pause or run as needed. Suspend functions are guaranteed to
 * execute the "pausing" or "resuming" functionality, they are prefixed with "do" and they need to
 * run from within a coroutine. Non-suspend functions are not guaranteed to pause or resume the default
 * coroutine they're run on, and are appropriately prefixed with "try".
 */
@JvmInline
value class Waiter(private val channel: Channel<Unit> = Channel(0)) {
    /**
     * Sleep or pause the coroutine from running.
     */
    suspend fun doWait() {
        channel.receive()
    }

    /**
     * Awaken or resume the coroutine from pausing/sleeping.
     */
    suspend fun doNotify() {
        channel.send(Unit)
    }

    /**
     * Try to pause the default coroutine.
     */
    fun tryWait() {
        channel.tryReceive()
    }

    /**
     * Try to resume the default coroutine.
     */
    fun tryNotify() {
        channel.trySend(Unit)
    }
}

/**
 * Object defining the ultimate state of performing an MDL Presentation. This object is returned from
 * the extension function [presentInPresentationActivity] that is executed from inside a [FragmentActivity]
 * either during composition via [LaunchedEffect] or directly.
 *
 * @param responseBytes the generated bytes to send to the requesting party after all prompts have succeeded.
 * @param exception if a prompt's state returns an unexpected result, such as user tapping on Cancel
 * (via the PromptCanceledException) or an error was encountered that threw an Exception.
 */
data class PresentationResult(
    val responseBytes: ByteArray? = null,
    val exception: Throwable? = null
)

/**
 * Exception thrown from a Prompt when user cancels the prompt
 */
class PromptCanceledException : Exception()

/**
 * PromptResult class defines the different return types of the prompts used during an MDL Presentation.
 */
sealed class PromptResult {

    /**
     * Prompt returned "success" with no additional data.
     */
    data object PromptSuccess : PromptResult()

    /**
     * Prompt failed to succeed due to numerous reasons, such as user canceling (PromptCanceledException)
     * or encountering an error when running.
     */
    data class PromptFail(
        val exception: Throwable? = null
    ) : PromptResult()

    /**
     * A locked key blocked the prompt from succeeding for the specified MdocCredential.
     */
    data class LockedKey(
        val credential: MdocCredential
    ) : PromptResult()

    /**
     * An unlocked key was provided for the specified MdocCredential and key unlock data.
     */
    data class UnlockedKey(
        val credential: MdocCredential,
        val keyUnlockData: KeyUnlockData
    ) : PromptResult()

    /**
     * The response bytes were generated successfully.
     */
    data class SuccessfulResponse(
        val responseBytes: ByteArray? = null
    ) : PromptResult()
}
