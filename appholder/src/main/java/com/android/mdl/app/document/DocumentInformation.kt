package com.android.mdl.app.document

data class DocumentInformation(
    val userVisibleName: String,
    val docType: String,
    val dateProvisioned: String,
    val selfSigned: Boolean,
    val documentColor: Int,
    val maxUsagesPerKey: Int,
    val authKeys: List<KeyData>
) {

    data class KeyData(
        val alias: String,
        val validFrom: String,
        val validUntil: String,
        val issuerDataBytesCount: Int,
        val usagesCount: Int
    )
}

