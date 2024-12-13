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

actual class NfcTagReader {

    private class DialogCanceledException : Throwable()

    actual companion object {
        private const val TAG = "NfcTagReader"

        actual val canReadWithoutDialog: Boolean = false
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
            Logger.i(TAG, "didBecomeActive")
        }

        override fun tagReaderSession(
            session: NFCTagReaderSession,
            didDetectTags: List<*>
        ) {
            Logger.i(TAG, "didDetectTags.size=${didDetectTags.size}")
            val tags = didDetectTags as List<NFCTagProtocol>
            val tag = tags[0]
            Logger.i(TAG, "Connecting to $tag")
            session.connectToTag(tag, { error ->
                if (error != null) {
                    Logger.e(TAG, "Connection failed", error.toKotlinError())
                } else {
                    val isoTag = tag as NFCISO7816TagProtocol
                    Logger.i(TAG, "Woot, connected ${isoTag.identifier}")
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            tagInteractionFunc(NfcIsoTagIos(isoTag))
                            Logger.i(TAG, "Barfooz")
                            continuation?.resume(true, null)
                            continuation = null
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

    private var continuation: CancellableContinuation<Boolean>? = null

    private lateinit var tagInteractionFunc: suspend (tag: NfcIsoTag) -> Unit

    actual suspend fun beginSession(
        showDialog: Boolean,
        alertMessage: String,
        tagInteractionFunc: suspend (tag: NfcIsoTag) -> Unit
    ): Boolean {
        require(showDialog == true) { "NFC tag reading dialog is always shown on iOS" }
        check(NFCTagReaderSession.readingAvailable == true) { "The device doesn't support NFC tag reading" }

        try {
            Logger.i(TAG, "Beginning session")
            suspendCancellableCoroutine<Boolean> { continuation ->
                this.continuation = continuation
                this.tagInteractionFunc = tagInteractionFunc
                session.setAlertMessage(alertMessage)
                session.beginSession()
            }
            Logger.i(TAG, "Foobarz")
            session.invalidateSession()
            Logger.i(TAG, "Session ended")
            return true
        } catch (e: DialogCanceledException) {
            Logger.i(TAG, "Session ended with user cancelation")
            return false
        } catch (e: Throwable) {
            session.invalidateSessionWithErrorMessage("Something went wrong: $e")
            Logger.i(TAG, "Session ended with error")
            throw e
        }
    }
}