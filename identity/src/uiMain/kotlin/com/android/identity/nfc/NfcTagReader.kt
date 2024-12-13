package com.android.identity.nfc

expect class NfcTagReader() {

    companion object {
        /**
         * If `false` NFC tag reading always involve showing a UI dialog when [beginSession] is called.
         */
        val canReadWithoutDialog: Boolean
    }

    /**
     * Shows a dialog prompting the user to scan a NFC tag.
     *
     * @param showDialog whether to show a UI dialog. On some platforms a dialog is always shown so this
     * must to `true` if [canReadWithoutDialog] is `false`.
     * @param alertMessage the message to show in the dialog
     * @param tagInteractionFunc this is called when a tag is in the field.
     * @return `false` if the user canceled the dialog, `true` otherwise
     * @throws Throwable exceptions thrown in [tagInteractionFunc] are rethrown.
     */
    suspend fun beginSession(
        showDialog: Boolean,
        alertMessage: String,
        tagInteractionFunc: suspend (tag: NfcIsoTag) -> Unit
    ): Boolean

    // TODO: add endSession()
}