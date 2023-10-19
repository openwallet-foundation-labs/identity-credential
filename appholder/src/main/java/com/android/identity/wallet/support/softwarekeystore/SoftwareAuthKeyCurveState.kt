package com.android.identity.wallet.support.softwarekeystore

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SoftwareAuthKeyCurveState(
    val isEnabled: Boolean = true,
    val authCurve: SoftwareAuthKeyCurveOption = SoftwareAuthKeyCurveOption.P256
) : Parcelable