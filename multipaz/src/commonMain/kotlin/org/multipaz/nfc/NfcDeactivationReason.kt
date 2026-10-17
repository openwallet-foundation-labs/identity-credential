package org.multipaz.nfc

/**
 * Reason why an NFC link was deactivated.
 */
enum class NfcDeactivationReason {
    /**
     * Deactivated due to the RF link being lost.
     */
    LINK_LOSS,

    /**
     * Deactivated due to a different AID being selected.
     */
    DESELECTED,
}
