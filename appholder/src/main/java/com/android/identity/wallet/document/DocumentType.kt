package com.android.identity.wallet.document

import android.os.Parcelable
import com.android.identity.wallet.util.DocumentData
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class DocumentType(val value: String) : Parcelable {
    object MDL : DocumentType(DocumentData.MDL_DOCTYPE)
    object MVR : DocumentType(DocumentData.MVR_DOCTYPE)
    object MICOV : DocumentType(DocumentData.MICOV_DOCTYPE)
    object EUPID : DocumentType(DocumentData.EU_PID_DOCTYPE)
}