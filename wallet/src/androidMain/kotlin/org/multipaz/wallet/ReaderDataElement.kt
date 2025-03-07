package org.multipaz_credential.wallet

import android.graphics.Bitmap
import org.multipaz.documenttype.MdocDataElement

data class ReaderDataElement(
    // Null if the data element isn't known
    val mdocDataElement: MdocDataElement?,

    val value: ByteArray,

    // Only set DocumentAttributeType.Picture
    val bitmap: Bitmap?,
)
