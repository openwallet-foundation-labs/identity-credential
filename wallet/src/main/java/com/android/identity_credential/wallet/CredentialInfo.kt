package com.android.identity_credential.wallet

import androidx.compose.runtime.Immutable
import com.android.identity.issuance.CredentialFormat
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
