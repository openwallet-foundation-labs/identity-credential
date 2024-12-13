package com.android.identity.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.android.identity.util.AndroidInitializer
import com.android.identity.util.Logger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

actual class NfcTagReader {

    private class DialogCanceledException : Throwable()

    actual companion object {
        private const val TAG = "NfcTagReader"

        actual val canReadWithoutDialog: Boolean = true
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
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            tagInteractionFunc(NfcIsoTagAndroid(isoDep))
                            continuation?.resume(true, null)
                            continuation = null
                        } catch (e: Throwable) {
                            continuation?.resumeWithException(e)
                            continuation = null
                        }
                    }
                }
            }
        }
    }

    private var continuation: CancellableContinuation<Boolean>? = null

    private lateinit var tagInteractionFunc: suspend (tag: NfcIsoTag) -> Unit

    actual suspend fun beginSession(
        showDialog: Boolean,
        alertMessage: String,
        tagInteractionFunc: suspend (tag: NfcIsoTag) -> Unit
    ): Boolean {
        val fragmentActivity = AndroidInitializer.currentActivity ?: throw IllegalStateException("No current activity")
        val dialog = if (showDialog) {
            val fragment = NfcTagReaderModalBottomSheet(
                alertText = alertMessage,
                onDismissed = {
                    continuation?.resumeWithException(DialogCanceledException())
                }
            )
            fragment.show(
                fragmentActivity.supportFragmentManager,
                "nfc_tag_reader_modal_bottom_sheet"
            )
            fragment
        } else {
            null
        }
        this.tagInteractionFunc = tagInteractionFunc

        val adapter = NfcAdapter.getDefaultAdapter(AndroidInitializer.applicationContext)
        try {
            suspendCancellableCoroutine<Boolean> { continuation ->
                this.continuation = continuation
                adapter.enableReaderMode(
                    fragmentActivity,
                    nfcReaderModeListener,
                    NfcAdapter.FLAG_READER_NFC_A + NfcAdapter.FLAG_READER_NFC_B
                            + NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK + NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    null
                )
            }
            return true
        } catch (_: DialogCanceledException) {
            return false
        } finally {
            adapter.disableReaderMode(fragmentActivity)
            dialog?.dismiss()
        }
    }
}

