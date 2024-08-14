package com.android.identity_credential.wallet

import android.graphics.Bitmap
import com.android.identity.documenttype.MdocDataElement

data class ReaderDataElement(
    // Null if the data element isn't known
    val mdocDataElement: MdocDataElement?,

    val value: ByteArray,

    // Only set DocumentAttributeType.Picture
    val bitmap: Bitmap?,
)
