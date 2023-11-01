package com.android.mdl.appreader.settings

import com.android.mdl.appreader.issuerauth.vical.Vical
import java.time.Instant


data class VicalItem(
    val title: String,
    val vicalProvider: String,
    val date: Instant?,
    val vicalIssueID: Int?,
    val nextUpdate: Instant?,
    val certificateItems: List<CertificateItem>,
    val vical: Vical?
) {
}