package com.android.identity.wallet.document

data class DocumentInformation(
    val userVisibleName: String,
    val docName: String,
    val docType: String,
    val dateProvisioned: String,
    val selfSigned: Boolean,
    val documentColor: Int,
    val maxUsagesPerKey: Int,
    val lastTimeUsed: String,
    val authKeys: List<KeyData>
) {

    data class KeyData(
        val counter: Int,
        val validFrom: String,
        val validUntil: String,
        val domain: String,
        val issuerDataBytesCount: Int,
        val usagesCount: Int,
        val keyPurposes: Int,
        val ecCurve: Int,
        val isHardwareBacked: Boolean,
        val secureAreaDisplayName: String
    )
}

