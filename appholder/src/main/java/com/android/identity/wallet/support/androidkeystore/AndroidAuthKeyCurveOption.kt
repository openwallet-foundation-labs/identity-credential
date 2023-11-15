package com.android.identity.wallet.support.androidkeystore

import android.os.Parcelable
import com.android.identity.securearea.SecureArea
import kotlinx.parcelize.Parcelize

@Parcelize
enum class AndroidAuthKeyCurveOption : Parcelable {
    P_256, Ed25519, X25519;

    fun toEcCurve(): Int {
        return when (this) {
            P_256 -> SecureArea.EC_CURVE_P256
            Ed25519 -> SecureArea.EC_CURVE_ED25519
            X25519 -> SecureArea.EC_CURVE_X25519
        }
    }
}