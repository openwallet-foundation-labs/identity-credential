package com.android.identity_credential.wallet

import android.graphics.Bitmap
import com.android.identity.documenttype.MdocDataElement
import kotlinx.io.bytestring.ByteString

data class ReaderDataElement(
    // Null if the data element isn't known
    val mdocDataElement: MdocDataElement?,

    val value: ByteString,

    // Only set DocumentAttributeType.Picture
    val bitmap: Bitmap?,
)
