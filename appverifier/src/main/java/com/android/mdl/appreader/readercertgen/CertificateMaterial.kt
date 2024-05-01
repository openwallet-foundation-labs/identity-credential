package com.android.mdl.appreader.readercertgen

import java.math.BigInteger
import java.util.Date
import java.util.Optional

data class CertificateMaterial(
    val serialNumber: BigInteger,
    val startDate: Date,
    val endDate: Date,
    val keyUsage: Int,
    val extendedKeyUsage: Optional<String>,
    val pathLengthConstraint: Int,
) {
    companion object {
        const val PATHLENGTH_NOT_A_CA = -1
    }
}
