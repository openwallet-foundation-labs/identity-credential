package org.multipaz.legacyprovisioning.evidence

class EvidenceRequestOpenid4Vp(
    val originUri: String,
    val request: String,
    val cancelText: String? = null
): EvidenceRequest()