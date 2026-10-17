package org.multipaz.mdoc.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import org.multipaz.context.applicationContext
import org.multipaz.context.initializeApplication

/**
 * Android actual for [CombinedNfcServiceBase] extending [HostApduService].
 */
actual abstract class CombinedNfcServiceBase actual constructor() : HostApduService() {
    actual override fun onCreate() {
        super.onCreate()
        applicationContext?.let {
            initializeApplication(it)
        }
    }

    actual override fun onDestroy() {
        super.onDestroy()
    }

    actual abstract fun processCommandApdu(encodedCommandApdu: ByteArray): ByteArray?

    final override fun processCommandApdu(encodedCommandApdu: ByteArray?, extras: Bundle?): ByteArray? {
        if (encodedCommandApdu == null) return null
        return processCommandApdu(encodedCommandApdu)
    }

    actual abstract override fun onDeactivated(reason: Int)
}

private fun vibrate(pattern: List<Int>) {
    try {
        val vibrator = applicationContext.getSystemService(Vibrator::class.java)
        vibrator?.let {
            val vibrationEffect = VibrationEffect.createWaveform(pattern.map { ms -> ms.toLong() }.toLongArray(), -1)
            it.vibrate(vibrationEffect)
        }
    } catch (_: Throwable) {
        // Ignore if applicationContext is not initialized (e.g. in unit tests)
    }
}

internal actual fun vibrateMdocNfcError() {
    vibrate(listOf(0, 500))
}

internal actual fun vibrateMdocNfcSuccess() {
    vibrate(listOf(0, 100, 50, 100))
}
