package org.multipaz.mdoc.vical

import kotlin.time.Instant

/**
 * The data in a VICAL according to ISO/IEC 18013-5:2021.
 *
 * @property version the version of the VICAL data structure, e.g. "1.0".
 * @property vicalProvider the provider of the VICAL.
 * @property date the date it was generated.
 * @property nextUpdate the date an update is expected to be available, if available.
 * @property vicalIssueID the issue of the VICAL, unique and monotonically increasing, if available.
 * @property certificateInfos the certificates in the VICAL.
 */
data class Vical(
    val version: String,
    val vicalProvider: String,
    val date: Instant,
    val nextUpdate: Instant?,
    val vicalIssueID: Long?,
    val certificateInfos: List<VicalCertificateInfo>
)