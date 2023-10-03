package com.android.identity.wallet.document

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
sealed class SecureAreaImplementationState : Parcelable {

    @Serializable
    object Android : SecureAreaImplementationState()

    @Serializable
    object BouncyCastle : SecureAreaImplementationState()
}