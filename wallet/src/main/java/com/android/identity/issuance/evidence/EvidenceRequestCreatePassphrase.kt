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
data class EvidenceRequestCreatePassphrase (
    // TODO: use PassphraseConstraints instead when cbor-processor is fixed
    val passphraseMinLength: Int,
    val passphraseMaxLength: Int,
    val passphraseRequireNumerical: Boolean,
    val message: String,
    val verifyMessage: String,
    val assets: Map<String, ByteArray>,
) : EvidenceRequest()