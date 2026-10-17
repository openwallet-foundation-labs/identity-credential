package org.multipaz.mdoc.nfc

/**
 * Web actual for [CombinedNfcServiceBase].
 */
actual abstract class CombinedNfcServiceBase actual constructor() {
    actual open fun onCreate() {}
    actual open fun onDestroy() {}
    actual abstract fun processCommandApdu(encodedCommandApdu: ByteArray): ByteArray?
    actual abstract fun onDeactivated(reason: Int)
    actual fun sendResponseApdu(responseApdu: ByteArray) {}
}

internal actual fun vibrateMdocNfcError() {}

internal actual fun vibrateMdocNfcSuccess() {}
