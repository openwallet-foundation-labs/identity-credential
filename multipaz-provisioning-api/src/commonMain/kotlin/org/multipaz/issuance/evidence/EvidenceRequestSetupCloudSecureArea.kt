package org.multipaz.issuance.evidence

import org.multipaz.securearea.PassphraseConstraints
import org.multipaz.securearea.SecureArea
import kotlinx.io.bytestring.ByteString

/**
 * Evidence type for asking the user to select a PIN/passphrase for a Cloud Secure Area.
 *
 * If the cloud secure area has already been configured (it's possible the same CSA is used by
 * other documents from the same issuer) this is a no-op.
 *
 * @param cloudSecureAreaIdentifier the [SecureArea.identifier] for the Cloud Secure Area to use.
 * @param passphraseConstraints the constraints for the PIN/passphrase.
 * @param message message shown to user when asking them to select a PIN/passphrase, as markdown.
 * @param verifyMessage message shown to the user when asking them to verify the PIN/passphrase
 * they just entered, as markdown.
 * @param assets images that can be referenced in markdown, type (PNG, JPEG, or SVG) determined by the extension.
 */
data class EvidenceRequestSetupCloudSecureArea (
    val cloudSecureAreaIdentifier: String,
    val passphraseConstraints: PassphraseConstraints,
    val message: String,
    val verifyMessage: String,
    val assets: Map<String, ByteString>,
) : EvidenceRequest()