package com.android.identity.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import com.android.identity.ui.ScanNfcTagDialogIcon
import com.android.identity.ui.UiModelAndroid
import com.android.identity.util.AndroidContexts
import com.android.identity.util.Logger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

private class NfcTagReader<T> {

    private class DialogCanceledException : Throwable()

    companion object {
        private const val TAG = "NfcTagReader"

        private var disableReaderModeJob: Job? = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val nfcReaderModeListener = object : NfcAdapter.ReaderCallback {
        override fun onTagDiscovered(tag: Tag?) {
            if (tag == null) {
                Logger.w(TAG, "onTagDiscovered called with null tag")
                return
            }
            for (tech in tag.techList) {
                if (tech == IsoDep::class.java.name) {
                    val isoDep = IsoDep.get(tag)!!
                    isoDep.connect()
                    isoDep.timeout = 20 * 1000 // 20 seconds
                    // Note: onTagDiscovered() is called in a dedicated thread and we're not supposed
                    // to return until we're done interrogating the tag.
                    runBlocking {
                        Logger.i(TAG, "maxTransceiveLength: ${isoDep.maxTransceiveLength}")
                        val isoTag = NfcIsoTagAndroid(isoDep, currentCoroutineContext())
                        try {
                            val ret = tagInteractionFunc(
                                isoTag,
                                { message -> dialogMessage.value = message }
                            )
                            if (ret != null) {
                                continuation?.resume(ret, null)
                                continuation = null
                            }
                        } catch (e: NfcTagLostException) {
                            // This is to to properly handle emulated tags - such as on Android - which may be showing
                            // disambiguation UI if multiple applications have registered for the same AID.
                            dialogMessage.value = originalMessage
                        } catch (e: Throwable) {
                            continuation?.resumeWithException(e)
                            continuation = null
                        }
                    }
                }
            }
        }
    }

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(AndroidContexts.applicationContext)

    private var continuation: CancellableContinuation<T>? = null

    private lateinit var tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit
        ) -> T?

    private lateinit var originalMessage: String
    private val dialogMessage = MutableStateFlow<String>("")
    private val dialogIcon = MutableStateFlow<ScanNfcTagDialogIcon>(ScanNfcTagDialogIcon.READY_TO_SCAN)
    private lateinit var dialogMessageJob: Job

    private fun vibrate(pattern: List<Int>) {
        val vibrator = ContextCompat.getSystemService(AndroidContexts.applicationContext, Vibrator::class.java)
        val vibrationEffect = VibrationEffect.createWaveform(pattern.map { it.toLong() }.toLongArray(), -1)
        vibrator?.vibrate(vibrationEffect)
    }

    private fun vibrateError() {
        vibrate(listOf(0, 500))
    }

    private fun vibrateSuccess() {
        vibrate(listOf(0, 100, 50, 100))
    }

    suspend fun beginSession(
        message: String,
        tagInteractionFunc: suspend (
            tag: NfcIsoTag,
            updateMessage: (message: String) -> Unit
        ) -> T?,
    ): T? {
        if (adapter == null) {
            throw IllegalStateException("NFC is not supported on this device")
        }

        if (disableReaderModeJob != null) {
            disableReaderModeJob!!.cancel()
            disableReaderModeJob = null
        }

        originalMessage = message
        dialogMessage.value = message

        dialogMessageJob = CoroutineScope(currentCoroutineContext()).launch {
            UiModelAndroid.showScanNfcTagDialog(
                message = dialogMessage,
                icon = dialogIcon
            )
            continuation?.resumeWithException(DialogCanceledException())
        }

        val fragmentActivity = AndroidContexts.currentActivity ?: throw IllegalStateException("No current activity")
        this.tagInteractionFunc = tagInteractionFunc

        try {
            val ret = suspendCancellableCoroutine<T> { continuation ->
                this.continuation = continuation
                adapter.enableReaderMode(
                    fragmentActivity,
                    nfcReaderModeListener,
                    NfcAdapter.FLAG_READER_NFC_A + NfcAdapter.FLAG_READER_NFC_B
                            + NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK + NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    null
                )
            }
            dialogIcon.value = ScanNfcTagDialogIcon.SUCCESS
            vibrateSuccess()
            CoroutineScope(Dispatchers.IO).launch {
                delay(2.seconds)
                dialogMessageJob.cancel()
            }
            return ret
        } catch (_: DialogCanceledException) {
            dialogMessageJob.cancel()
            return null
        } catch (e: Throwable) {
            dialogIcon.value = ScanNfcTagDialogIcon.ERROR
            dialogMessage.value = e.message!!
            vibrateError()
            CoroutineScope(Dispatchers.IO).launch {
                delay(2.seconds)
                dialogMessageJob.cancel()
            }
            throw e
        } finally {
            // If we disable reader mode right away, it causes an additional APPLICATION SELECT to be
            // sent to the tag right as we're done with it. If the tag is an Android device with multiple
            // mdoc apps registered for the NDEF AID it causes a NFC disambig dialog to be displayed on top of the
            // consent dialog for the first selected application. This delay works around this problem.
            //
            disableReaderModeJob = CoroutineScope(Dispatchers.IO).launch {
                delay(5.seconds)
                adapter.disableReaderMode(fragmentActivity)
                disableReaderModeJob = null
            }
        }
    }
}

actual suspend fun<T> scanNfcTag(
    message: String,
    tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit
    ) -> T?,
): T? {
    val reader = NfcTagReader<T>()
    return reader.beginSession(message, tagInteractionFunc)
}


