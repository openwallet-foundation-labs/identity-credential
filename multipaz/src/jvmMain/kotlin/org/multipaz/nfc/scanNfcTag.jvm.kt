package org.multipaz.nfc

actual suspend fun<T: Any> scanNfcTag(
    message: String,
    tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit
    ) -> T?,
): T {
    throw NotImplementedError("scanNfcTag is not available for JVM")
}
