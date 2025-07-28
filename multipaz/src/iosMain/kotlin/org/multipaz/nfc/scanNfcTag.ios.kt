package org.multipaz.nfc

import org.multipaz.prompt.PromptDismissedException
import org.multipaz.util.Logger
import org.multipaz.util.toKotlinError
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreNFC.NFCISO7816TagProtocol
import platform.CoreNFC.NFCPollingISO14443
import platform.CoreNFC.NFCTagProtocol
import platform.CoreNFC.NFCTagReaderSession
import platform.CoreNFC.NFCTagReaderSessionDelegateProtocol
import platform.Foundation.NSError
import platform.CoreNFC.NFCErrorDomain
import platform.CoreNFC.NFCReaderSessionInvalidationErrorUserCanceled
import platform.darwin.NSObject
import kotlin.coroutines.resumeWithException

actual val nfcTagScanningSupported = true

actual val nfcTagScanningSupportedWithoutDialog: Boolean = false

private class NfcTagReader<T> {

    companion object {
        private const val TAG = "NfcTagReader"
    }

    val session: NFCTagReaderSession

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tagReaderSessionDelegate = object : NSObject(), NFCTagReaderSessionDelegateProtocol {
        override fun tagReaderSession(
            session: NFCTagReaderSession,
            didInvalidateWithError: NSError
        ) {
            if (didInvalidateWithError.domain == NFCErrorDomain &&
                didInvalidateWithError.code == NFCReaderSessionInvalidationErrorUserCanceled) {
                continuation?.resumeWithException(PromptDismissedException())
                continuation = null
            } else {
                continuation?.resumeWithException(didInvalidateWithError.toKotlinError())
                continuation = null
            }
        }

        override fun tagReaderSessionDidBecomeActive(
            session: NFCTagReaderSession
        ) {
        }

        override fun tagReaderSession(
            session: NFCTagReaderSession,
            didDetectTags: List<*>
        ) {
            // Currently we only consider the first tag. We might need to look at all tags.
            val tag = didDetectTags[0] as NFCTagProtocol
            session.connectToTag(tag) { error ->
                if (error != null) {
                    Logger.e(TAG, "Connection failed", error.toKotlinError())
                } else {
                    val isoTag = NfcIsoTagIos(tag as NFCISO7816TagProtocol)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val ret = tagInteractionFunc(isoTag) { message -> session.alertMessage = message }
                            if (ret != null) {
                                continuation?.resume(ret, null)
                                continuation = null
                            } else {
                                session.restartPolling()
                            }
                        } catch (e: Throwable) {
                            continuation?.resumeWithException(e)
                            continuation = null
                        }
                    }
                }
            }
        }
    }

    init {
        session = NFCTagReaderSession(
            pollingOption = NFCPollingISO14443,
            delegate = tagReaderSessionDelegate,
            queue = null,
        )
    }

    private var continuation: CancellableContinuation<T>? = null

    private lateinit var tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit
    ) -> T?

    suspend fun beginSession(
        alertMessage: String,
        tagInteractionFunc: suspend (
            tag: NfcIsoTag,
            updateMessage: (message: String) -> Unit
        ) -> T?
    ): T {
        check(NFCTagReaderSession.readingAvailable) { "The device doesn't support NFC tag reading" }
        try {
            val ret = suspendCancellableCoroutine { continuation ->
                this.continuation = continuation
                this.tagInteractionFunc = tagInteractionFunc
                session.setAlertMessage(alertMessage)
                session.beginSession()
            }
            session.invalidateSession()
            return ret
        } catch (e: CancellationException) {
            session.invalidateSessionWithErrorMessage("Dialog was canceled")
            throw e
        } catch (e: Throwable) {
            e.message?.let { session.invalidateSessionWithErrorMessage(it) } ?: session.invalidateSession()
            throw e
        }
    }
}

actual suspend fun<T: Any> scanNfcTag(
    message: String?,
    tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit
    ) -> T?
): T {
    require(message != null) { "Cannot not show the NFC tag scanning dialog on iOS" }
    val reader = NfcTagReader<T>()
    return reader.beginSession(message, tagInteractionFunc)
}
