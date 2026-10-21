package org.multipaz.util

import com.android.keyattestation.verifier.AttestationApplicationId
import com.android.keyattestation.verifier.AttestationPackageInfo
import com.android.keyattestation.verifier.AttributeConstraint
import com.android.keyattestation.verifier.GoogleTrustAnchors
import com.android.keyattestation.verifier.IgnoredConstraint
import com.android.keyattestation.verifier.InstantSource
import com.android.keyattestation.verifier.KeyDescription
import com.android.keyattestation.verifier.LogHook
import com.android.keyattestation.verifier.Origin
import com.android.keyattestation.verifier.SecurityLevel
import com.android.keyattestation.verifier.SecurityLevelConstraint
import com.android.keyattestation.verifier.SoftwareRoot
import com.android.keyattestation.verifier.VerificationResult
import com.android.keyattestation.verifier.VerifiedBootState
import com.android.keyattestation.verifier.Verifier
import com.android.keyattestation.verifier.VerifyRequestLog
import com.android.keyattestation.verifier.challengecheckers.ChallengeMatcher
import com.android.keyattestation.verifier.constraintConfig
import com.android.keyattestation.verifier.getGoogleRevocationStatusFromWeb
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.javaX509Certificates
import org.multipaz.device.AndroidKeystoreSecurityLevel
import java.math.BigInteger
import java.security.cert.TrustAnchor
import kotlin.time.Instant

private const val TAG = "validateAndroidKeyAttestation"

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
): ByteString {
    val rawJavaCerts = chain.javaX509Certificates
    check(rawJavaCerts.isNotEmpty()) {
        "Android key attestation certificate chain is empty"
    }

    val javaCerts = if (rawJavaCerts.last().subjectX500Principal != rawJavaCerts.last().issuerX500Principal) {
        val matchingAnchor = GoogleTrustAnchors().firstOrNull {
            it.trustedCert?.subjectX500Principal == rawJavaCerts.last().issuerX500Principal
        }?.trustedCert
        if (matchingAnchor != null) {
            rawJavaCerts + matchingAnchor
        } else {
            rawJavaCerts
        }
    } else {
        rawJavaCerts
    }

    val trustAnchorsSource: () -> Set<TrustAnchor> = if (requireGmsAttestation) {
        GoogleTrustAnchors
    } else {
        {
            buildSet {
                addAll(GoogleTrustAnchors())
                SoftwareRoot.SOFTWARE_ROOTS.forEach { add(TrustAnchor(it, null)) }
                add(TrustAnchor(javaCerts.last(), null))
            }
        }
    }

    val revokedSerialsSource: () -> Set<String> = when {
        revokedSerials != null -> {
            val normalized = revokedSerials.map { it.lowercase() }.toSet()
            ({ normalized })
        }
        checkRevocation -> ::getGoogleRevocationStatusFromWeb
        else -> ({ emptySet() })
    }

    val expectedAppId = if (requireAppPackages.isEmpty() && requireAppSignatureCertificateDigests.isEmpty()) {
        null
    } else {
        AttestationApplicationId(
            packages = requireAppPackages.map {
                AttestationPackageInfo(it, BigInteger.ZERO)
            }.toSet(),
            signatures = requireAppSignatureCertificateDigests.map {
                com.google.protobuf.ByteString.copyFrom(it.toByteArray())
            }.toSet()
        )
    }

    val config = constraintConfig {
        allowSoftwareRoot = !requireGmsAttestation
        keyOrigin {
            AttributeConstraint.STRICT("Origin", Origin.GENERATED) {
                if (requireKeyMintSecurityLevel == AndroidKeystoreSecurityLevel.SOFTWARE) {
                    it.hardwareEnforced.origin ?: it.softwareEnforced.origin
                } else {
                    it.hardwareEnforced.origin
                }
            }
        }
        securityLevel {
            when (requireKeyMintSecurityLevel) {
                AndroidKeystoreSecurityLevel.STRONG_BOX ->
                    SecurityLevelConstraint.STRICT(SecurityLevel.STRONG_BOX)
                AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT ->
                    SecurityLevelConstraint.NOT_SOFTWARE
                AndroidKeystoreSecurityLevel.SOFTWARE ->
                    SecurityLevelConstraint.CONSISTENT
            }
        }
        if (!requireVerifiedBootGreen) {
            rootOfTrust { IgnoredConstraint }
        }
    }

    val verifier = Verifier(
        trustAnchorsSource = trustAnchorsSource,
        revokedSerialsSource = revokedSerialsSource,
        instantSource = InstantSource { java.time.Instant.ofEpochMilli(validateAt.toEpochMilliseconds()) },
        expectedAttestationApplicationId = expectedAppId,
        constraintConfig = config
    )

    val challengeChecker = challenge?.let { ChallengeMatcher(it.toByteArray()) }

    var parsedKeyDescription: KeyDescription? = null
    val logHook = object : LogHook {
        override fun createRequestLog(): VerifyRequestLog = object : VerifyRequestLog {
            override fun logKeyDescription(keyDescription: KeyDescription) {
                parsedKeyDescription = keyDescription
            }

            override fun logInfoMessage(infoMessage: String) {
                Logger.d(TAG, infoMessage)
            }
        }
    }

    val result = verifier.verify(
        chain = javaCerts,
        challengeChecker = challengeChecker,
        log = logHook
    )

    when (result) {
        is VerificationResult.Success -> {
            if (requireVerifiedBootGreen) {
                check(result.verifiedBootState == VerifiedBootState.VERIFIED) {
                    "Verified boot state is not GREEN (was ${result.verifiedBootState})"
                }
            }
            val actualSecurityLevel = when (result.securityLevel) {
                SecurityLevel.SOFTWARE -> AndroidKeystoreSecurityLevel.SOFTWARE
                SecurityLevel.TRUSTED_ENVIRONMENT -> AndroidKeystoreSecurityLevel.TRUSTED_ENVIRONMENT
                SecurityLevel.STRONG_BOX -> AndroidKeystoreSecurityLevel.STRONG_BOX
            }
            check(actualSecurityLevel >= requireKeyMintSecurityLevel) {
                "KeyMint security level is $actualSecurityLevel but must be at least $requireKeyMintSecurityLevel"
            }

            val signatures = parsedKeyDescription
                ?.softwareEnforced
                ?.attestationApplicationId
                ?.signatures
                ?.toList()
                .orEmpty()
            Logger.d(
                TAG,
                "Accepting Android client with ${signatures.size} signing certificates digests"
            )
            signatures.forEachIndexed { n, digest ->
                Logger.d(TAG, "Digest $n: ${digest.toByteArray().toBase64Url()}")
            }

            return ByteString(result.challenge.toByteArray())
        }
        is VerificationResult.ChallengeMismatch -> {
            throw IllegalStateException("Challenge didn't match what was expected")
        }
        is VerificationResult.PathValidationFailure -> {
            throw IllegalStateException(
                "Android key attestation certificate chain is invalid: ${result.cause.message}",
                result.cause
            )
        }
        is VerificationResult.ChainParsingFailure -> {
            throw IllegalStateException(
                "Error parsing Android key attestation certificate chain: ${result.cause.message}",
                result.cause
            )
        }
        is VerificationResult.ExtensionParsingFailure -> {
            throw IllegalStateException(
                "Error parsing Android Attestation Extension: ${result.cause.message}",
                result.cause
            )
        }
        is VerificationResult.ConstraintViolation -> {
            throw IllegalStateException(
                "Android key attestation constraint violation (${result.constraintLabel}): ${result.cause}"
            )
        }
        is VerificationResult.SoftwareAttestationUnsupported -> {
            throw IllegalStateException("Software attestation is not supported")
        }
    }
}
