package org.multipaz.certext

import org.multipaz.cbor.annotation.CborSerializable

/**
 * Data identifying a particular signed-in Google Account.
 *
 * This may appear in [MultipazExtension] in leaf certificates in certificate chains used
 * for reader authentication according to e.g. ISO/IEC 18013-5:2021 or OpenID4VP. If it does
 * it means that the verifier requesting the data is using a reader where they are signed into
 * the Google account described in this data.
 *
 * The CDDL is defined as:
 * ```
 * GoogleAccount = {
 *   "id": tstr,
 *   ? "emailAddress": tstr,
 *   ? "displayName": tstr,
 *   ? "profilePictureUri": tstr
 * }
 * ```
 *
 * As with any other data, wallets should not blindly trust this data and should only e.g.
 * display it to the end user in the consent prompt after examining the reader certificate
 * chain, checking that the root CA is in a trust list, and that they trust whoever
 * generated this data to have checked (by using the appropriate Sign In with Google APIs)
 * that it's for this particular signed-in Google account.
 *
 * @property id the Google Account Identifier.
 * @property emailAddress the user's email address.
 * @property displayName the user's name.
 * @property profilePictureUri an URI pointing to the profile picture for the user.
 */
@CborSerializable(schemaHash = "7VrvNoArF_EU_LofYHBhcQnVb1kTsWs2zV22kx_K6Rc")
data class GoogleAccount(
    val id: String,
    val emailAddress: String? = null,
    val displayName: String? = null,
    val profilePictureUri: String? = null
) {
    companion object
}