package com.android.identity.nfc

import com.android.identity.util.Logger
import com.android.identity.util.toKotlinError
import kotlinx.coroutines.CancellableContinuation
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

private class NfcTagReader<T> {

    private class DialogCanceledException : Throwable()

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
            val nsError = didInvalidateWithError
            if (nsError.domain == NFCErrorDomain &&
                nsError.code == NFCReaderSessionInvalidationErrorUserCanceled) {
                continuation?.resumeWithException(DialogCanceledException())
                continuation = null
            } else {
                continuation?.resumeWithException(nsError.toKotlinError())
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
            val tags = didDetectTags as List<NFCTagProtocol>
            // Currently we only consider the first tag. We might need to look at all tags.
            val tag = tags[0]
            session.connectToTag(tag, { error ->
                if (error != null) {
                    Logger.e(TAG, "Connection failed", error.toKotlinError())
                } else {
                    val isoTag = NfcIsoTagIos(tag as NFCISO7816TagProtocol)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val ret = tagInteractionFunc(
                                isoTag,
                                { message -> session.alertMessage = message }
                            )
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
            })
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
    ): T? {
        check(NFCTagReaderSession.readingAvailable == true) { "The device doesn't support NFC tag reading" }

        try {
            val ret = suspendCancellableCoroutine<T> { continuation ->
                this.continuation = continuation
                this.tagInteractionFunc = tagInteractionFunc
                session.setAlertMessage(alertMessage)
                session.beginSession()
            }
            session.invalidateSession()
            return ret
        } catch (e: DialogCanceledException) {
            return null
        } catch (e: Throwable) {
            session.invalidateSessionWithErrorMessage(e.message!!)
            throw e
        }
    }
}

actual suspend fun<T> scanNfcTag(
    message: String,
    tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit
    ) -> T?
): T? {
    val reader = NfcTagReader<T>()
    return reader.beginSession(message, tagInteractionFunc)
}
