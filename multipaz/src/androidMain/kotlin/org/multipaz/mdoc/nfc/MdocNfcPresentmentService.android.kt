package org.multipaz.mdoc.nfc

import android.os.VibrationEffect
import android.os.Vibrator
import org.multipaz.context.applicationContext

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
