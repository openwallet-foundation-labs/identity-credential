package com.android.identity_credential.mrtd

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.Handler
import android.util.Log
import net.sf.scuba.smartcards.CardService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Connects to an NFC-equipped passport/card and attempts to read [MrtdNfcData] from it.
 */
class MrtdNfcScanner(private val mActivity: Activity) {
    private var nfcAdapter: NfcAdapter = NfcAdapter.getDefaultAdapter(mActivity)

    companion object {
        private const val TAG = "MrtdNfcScanner"
    }

    /**
     * Connects to NFC card/passport and attempts to read [MrtdNfcData] in background.
     */
    suspend fun <ResultT>scanCard(
        mrzData: MrtdMrzData,
        reader: MrtdNfcReader<ResultT>,
        onStatus: (MrtdNfc.Status) -> Unit,
    ): ResultT {
        val options = Bundle()
        val executor = Executors.newFixedThreadPool(1)!!
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 300)
        return suspendCoroutine { continuation ->
            val callback = NfcAdapter.ReaderCallback { tag ->
                readUsingTag(tag, mrzData, executor, reader, onStatus,
                    { err ->
                        nfcAdapter.disableReaderMode(mActivity)
                        if (!executor.isShutdown) {
                            executor.shutdown()
                            continuation.resumeWithException(err)
                        }
                    },
                    { result ->
                        nfcAdapter.disableReaderMode(mActivity)
                        if (!executor.isShutdown) {
                            executor.shutdown()
                            continuation.resume(result)
                        }
                    }
                )
            }
            nfcAdapter.disableReaderMode(mActivity)
            nfcAdapter.enableReaderMode(
                mActivity,
                callback,
                NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or
                        NfcAdapter.FLAG_READER_NFC_V or
                        NfcAdapter.FLAG_READER_NFC_BARCODE or
                        NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                options
            )
        }
    }

    private fun <ResultT>readUsingTag(
        tag: Tag,
        mrzData: MrtdMrzData,
        executor: ExecutorService,
        reader: MrtdNfcReader<ResultT>,
        onStatus: (MrtdNfc.Status) -> Unit,
        onError: (Exception) -> Unit,
        onResult: (ResultT) -> Unit
    ) {
        for (tech in tag.techList) {
            if (tech.equals("android.nfc.tech.IsoDep")) {
                Log.i(TAG, "Got IsoDep")
                val mainHandler = Handler(mActivity.mainLooper)
                executor.submit {
                    try {
                        val statusCb: (MrtdNfc.Status) -> Unit = { status ->
                            mainHandler.post {
                                onStatus(status)
                            }
                        }
                        val cardService = CardService.getInstance(IsoDep.get(tag))
                        Log.i(TAG, "Got card service")
                        val nfcReader = MrtdNfcChipAccess(false)  // TODO: enable mac?
                        val passportService = nfcReader.open(cardService, mrzData, statusCb)
                        val result = reader.read(cardService, passportService, statusCb)
                        mainHandler.post {
                            onResult(result)
                        }
                    } catch (err: Exception) {
                        Log.i(TAG, "Error: $err")
                        mainHandler.post {
                            onError(err)
                        }
                    }
                }
                break
            }
        }
    }
}