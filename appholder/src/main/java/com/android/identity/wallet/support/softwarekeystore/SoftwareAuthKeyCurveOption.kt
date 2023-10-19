package com.android.identity.wallet.support.softwarekeystore

import android.os.Parcelable
import com.android.identity.securearea.SecureArea
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
    X448;

    fun toEcCurve(): Int {
        return when (this) {
            P256 -> SecureArea.EC_CURVE_P256
            P384 -> SecureArea.EC_CURVE_P384
            P521 -> SecureArea.EC_CURVE_P521
            BrainPoolP256R1 -> SecureArea.EC_CURVE_BRAINPOOLP256R1
            BrainPoolP320R1 -> SecureArea.EC_CURVE_BRAINPOOLP320R1
            BrainPoolP384R1 -> SecureArea.EC_CURVE_BRAINPOOLP384R1
            BrainPoolP512R1 -> SecureArea.EC_CURVE_BRAINPOOLP512R1
            Ed25519 -> SecureArea.EC_CURVE_ED25519
            Ed448 -> SecureArea.EC_CURVE_ED448
            X25519 -> SecureArea.EC_CURVE_X25519
            X448 -> SecureArea.EC_CURVE_X448
        }
    }
}