package org.multipaz.wallet

import androidx.compose.runtime.Immutable
import org.multipaz.provisioning.CredentialFormat
import kotlinx.datetime.Instant

@Immutable
data class CredentialInfo(
    val format: CredentialFormat,

    val description: String,

    val usageCount: Int,

    val signedAt: Instant?,

    val validFrom: Instant?,

    val validUntil: Instant?,

    val expectedUpdate: Instant?,

    val replacementPending: Boolean,

    val details: Map<String, String>
)
