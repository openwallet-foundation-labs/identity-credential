package org.multipaz_credential.wallet

import kotlinx.datetime.Instant

data class ReaderDocument(
    val docType: String,
    val msoValidFrom: Instant,
    val msoValidUntil: Instant,
    val msoSigned: Instant,
    val msoExpectedUpdate: Instant?,
    val namespaces: List<ReaderNamespace>,
    val infoTexts: List<String>,
    val warningTexts: List<String>,
)
