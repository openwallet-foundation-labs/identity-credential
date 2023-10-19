package com.android.identity.wallet.composables.state

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MdocAuthOption(
    val isEnabled: Boolean = true,
    val mDocAuthentication: MdocAuthStateOption = MdocAuthStateOption.ECDSA
) : Parcelable