package com.android.identity.wallet.support.softwarekeystore

import android.os.Parcelable
import com.android.identity.crypto.EcCurve
import kotlinx.parcelize.Parcelize

@Parcelize
enum class SoftwareAuthKeyCurveOption : Parcelable {
    P256,
    P384,
    P521,
    BrainPoolP256R1,
    BrainPoolP320R1,
    BrainPoolP384R1,
    BrainPoolP512R1,
    Ed25519,
    Ed448,
    X25519,
    X448,
    ;

    fun toEcCurve(): EcCurve =
        when (this) {
            P256 -> EcCurve.P256
            P384 -> EcCurve.P384
            P521 -> EcCurve.P521
            BrainPoolP256R1 -> EcCurve.BRAINPOOLP256R1
            BrainPoolP320R1 -> EcCurve.BRAINPOOLP320R1
            BrainPoolP384R1 -> EcCurve.BRAINPOOLP384R1
            BrainPoolP512R1 -> EcCurve.BRAINPOOLP512R1
            Ed25519 -> EcCurve.ED25519
            Ed448 -> EcCurve.ED448
            X25519 -> EcCurve.X25519
            X448 -> EcCurve.X448
        }
}
