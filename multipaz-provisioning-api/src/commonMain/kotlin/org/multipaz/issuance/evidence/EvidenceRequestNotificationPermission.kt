package org.multipaz.issuance.evidence

import kotlinx.io.bytestring.ByteString

/**
 * A request to the user for permission to send notifications to them.
 *
 * If the wallet application already has this permission, this is a no-op. Otherwise this
 * will show the appropriate UI for obtaining this permission which may include explaining
 * the rationale behind this to the user. The user will be given the choice to grant the
 * permission (in this case an OS-level permission request dialog is shown) or to not
 * grant the permission.
 *
 * @param permissionNotGrantedMessage message to show, formatted as markdown.
 * @param assets images that can be referenced in markdown, type (PNG, JPEG, or SVG) determined
 * by the extension.
 * @param grantPermissionButtonText text to show for the button for granting permission, for
 * example "Grant Permission".
 * @param continueWithoutPermissionButtonText text to show for the button for not granting
 * permission, for example "No Thanks"
 */
data class EvidenceRequestNotificationPermission(
    val permissionNotGrantedMessage: String,
    val assets: Map<String, ByteString>,
    val grantPermissionButtonText: String,
    val continueWithoutPermissionButtonText: String,
) : EvidenceRequest()