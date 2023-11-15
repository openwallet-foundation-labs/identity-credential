package com.android.identity.wallet.document

import com.android.identity.wallet.support.CurrentSecureArea

data class DocumentInformation(
    val userVisibleName: String,
    val docName: String,
    val docType: String,
    val dateProvisioned: String,
    val selfSigned: Boolean,
    val documentColor: Int,
    val maxUsagesPerKey: Int,
    val lastTimeUsed: String,
    val mDocAuthOption: String,
    val currentSecureArea: CurrentSecureArea,
    val authKeys: List<KeyData>
) {

    data class KeyData(
        val alias: String,
        val validFrom: String,
        val validUntil: String,
        val issuerDataBytesCount: Int,
        val usagesCount: Int,
        val keyPurposes: Int,
        val ecCurve: Int,
        val isHardwareBacked: Boolean
    )
}

