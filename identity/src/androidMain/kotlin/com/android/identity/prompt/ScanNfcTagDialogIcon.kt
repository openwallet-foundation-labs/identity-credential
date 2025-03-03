package com.android.identity.prompt

/**
 * Constants used to convey which icon to show when using [UiViewAndroid.showScanNfcTagDialog].
 */
enum class ScanNfcTagDialogIcon {
    /** The user should initiate the NFC scanning procedure. */
    READY_TO_SCAN,

    /** Indication of success and the user can move their device out of the field. */
    SUCCESS,

    /** Indication that an error occurred. */
    ERROR,
}
