package com.android.identity.issuance.evidence

data class EvidenceResponseNotificationPermission(
    val permissionGranted: Boolean,
) : EvidenceResponse()
