package com.android.identity.issuance.evidence

import com.android.identity.securearea.PassphraseConstraints

/**
 * Evidence type for asking the user to create a PIN.
 *
 * @param message message formatted as markdown
 * @param verifyMessage message formatted as markdown
 * @param assets images that can be referenced in markdown, type (PNG, JPEG, or SVG) determined by the extension
 * @param length Length of the PIN
 */
data class EvidenceRequestCreatePassphrase(
    val passphraseConstraints: PassphraseConstraints,
    val message: String,
    val verifyMessage: String,
    val assets: Map<String, ByteArray>,
) : EvidenceRequest()
