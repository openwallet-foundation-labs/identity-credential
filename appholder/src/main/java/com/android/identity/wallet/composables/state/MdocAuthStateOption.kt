package com.android.identity.wallet.composables.state

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class MdocAuthStateOption : Parcelable {
    ECDSA, MAC
}