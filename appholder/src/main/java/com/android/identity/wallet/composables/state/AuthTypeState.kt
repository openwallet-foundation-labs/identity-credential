package com.android.identity.wallet.composables.state

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AuthTypeState(
    val isEnabled: Boolean = true,
    val canBeModified: Boolean = false
) : Parcelable