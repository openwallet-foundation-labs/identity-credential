package org.multipaz.device

import kotlinx.io.bytestring.ByteString

/**
 * Data necessary to validate a [DeviceAttestation] object.
 */
data class DeviceAttestationValidationData(
    /**
     * Value of the `challange` parameter passed to [DeviceCheck.generateAttestation] method.
     */
    val attestationChallenge: ByteString,

    /**
     * Whether a release build is required on iOS. When `false`, both debug and release builds
     * are accepted.
     */
    val iosReleaseBuild: Boolean,

    /**
     * iOS app identifier that consists of a team id followed by a dot and app bundle name. If
     * `null`, any app identifier is accepted.
     *
     * On iOS this is the primary method of ensuring the the app that generated a given
     * [DeviceAttestation] is legitimate, as team id is tied to your team.
     *
     * It must not be `null` if [iosReleaseBuild] is `true`
     */
    val iosAppIdentifier: String?,

    /**
     * Ensure that the private key in the Android attestation is certified as legitimate using the
     * Google root private key.
     */
    val androidGmsAttestation: Boolean,

    /**
     * Require Android clients to be in verified boot state "green".
     */
    val androidVerifiedBootGreen: Boolean,

    /**
     * Allowed list of Android applications. Each element is the bytes of the SHA-256 of
     * a signing certificate, see the
     * [Signature](https://developer.android.com/reference/android/content/pm/Signature) class in
     * the Android SDK for details. If empty, allow any app.
     */
    val androidAppSignatureCertificateDigests: List<ByteString>,
)