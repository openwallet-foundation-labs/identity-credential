package com.android.identity.wallet.support.androidkeystore

import android.os.Parcelable
import com.android.identity.crypto.EcCurve
import kotlinx.parcelize.Parcelize

@Parcelize
enum class AndroidAuthKeyCurveOption : Parcelable {
    P_256,
    Ed25519,
    X25519,
    ;

    fun toEcCurve(): EcCurve =
        when (this) {
            P_256 -> EcCurve.P256
            Ed25519 -> EcCurve.ED25519
            X25519 -> EcCurve.X25519
        }
}
