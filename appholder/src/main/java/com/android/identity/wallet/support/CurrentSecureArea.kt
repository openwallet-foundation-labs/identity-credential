package com.android.identity.wallet.support

import android.os.Parcelable
import com.android.identity.securearea.SecureArea
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class CurrentSecureArea(
    @IgnoredOnParcel val secureArea: SecureArea,
    val identifier: String = secureArea.identifier,
    val displayName: String = secureArea.displayName,
) : Parcelable
