package org.multipaz.util

import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.X509CertChain
import org.multipaz.device.AndroidKeystoreSecurityLevel
import kotlin.time.Instant

internal actual suspend fun validateAndroidKeyAttestationPlatform(
    chain: X509CertChain,
    challenge: ByteString?,
    requireGmsAttestation: Boolean,
    requireVerifiedBootGreen: Boolean,
    requireKeyMintSecurityLevel: AndroidKeystoreSecurityLevel,
    requireAppSignatureCertificateDigests: Set<ByteString>,
    requireAppPackages: Set<String>,
    validateAt: Instant,
    validateCaValidity: Boolean,
    checkRevocation: Boolean,
    revokedSerials: Set<String>?
): ByteString = validateAndroidKeyAttestationCommon(
    chain = chain,
    challenge = challenge,
    requireGmsAttestation = requireGmsAttestation,
    requireVerifiedBootGreen = requireVerifiedBootGreen,
    requireKeyMintSecurityLevel = requireKeyMintSecurityLevel,
    requireAppSignatureCertificateDigests = requireAppSignatureCertificateDigests,
    requireAppPackages = requireAppPackages,
    validateAt = validateAt,
    validateCaValidity = validateCaValidity,
    revokedSerials = revokedSerials
)
