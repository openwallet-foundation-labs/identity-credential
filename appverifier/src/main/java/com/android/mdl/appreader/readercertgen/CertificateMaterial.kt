package com.android.mdl.appreader.readercertgen

import java.math.BigInteger
import java.util.Date
import java.util.Optional

interface CertificateMaterial {
    fun serialNumber(): BigInteger
    fun startDate(): Date
    fun endDate(): Date
    fun keyUsage(): Int
    fun extendedKeyUsage(): Optional<String>
    fun pathLengthConstraint(): Int

    companion object {
        const val PATHLENGTH_NOT_A_CA = -1
    }
}