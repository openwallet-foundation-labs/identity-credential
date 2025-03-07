package org.multipaz.issuance.evidence

import org.multipaz.securearea.PassphraseConstraints
import kotlinx.io.bytestring.ByteString

/**
 * Evidence type for asking the user to create a PIN/passphrase.
 *
 * @param passphraseConstraints constraints for the PIN/passphrase.
 * @param message message formatted as markdown.
 * @param verifyMessage message formatted as markdown.
 * @param assets images that can be referenced in markdown, type (PNG, JPEG, or SVG) determined by the extension.
 */
data class EvidenceRequestCreatePassphrase (
    val passphraseConstraints: PassphraseConstraints,
    val message: String,
    val verifyMessage: String,
    val assets: Map<String, ByteString>,
) : EvidenceRequest()