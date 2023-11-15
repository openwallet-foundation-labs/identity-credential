package com.android.identity.wallet.support.androidkeystore

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AndroidAuthKeyCurveState(
    val isEnabled: Boolean = true,
    val authCurve: AndroidAuthKeyCurveOption = AndroidAuthKeyCurveOption.P_256
) : Parcelable