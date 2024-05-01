package com.android.identity_credential.wallet

import android.graphics.Bitmap
import com.android.identity.issuance.IssuingAuthorityConfiguration

data class IssuerDisplayData(
    val configuration: IssuingAuthorityConfiguration,
    val issuerLogo: Bitmap,
)
