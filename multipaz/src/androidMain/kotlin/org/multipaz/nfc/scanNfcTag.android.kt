package org.multipaz.nfc

import org.multipaz.prompt.AndroidPromptModel
import org.multipaz.prompt.NfcDialogParameters
import kotlin.coroutines.coroutineContext

actual val nfcTagSupportsScanningWithoutDialog: Boolean = true

actual suspend fun<T: Any> scanNfcTag(
    message: String?,
    tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit
    ) -> T?,
): T {
    val promptModel = AndroidPromptModel.get(coroutineContext)
    val result = promptModel.scanNfcPromptModel.displayPrompt(
        NfcDialogParameters(message, tagInteractionFunc)
    )
    @Suppress("UNCHECKED_CAST")
    return result as T
}


