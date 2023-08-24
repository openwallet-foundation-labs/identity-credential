package com.android.mdl.app.document

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class DocumentColor(val value: Int) : Parcelable {
    object Green : DocumentColor(0)
    object Yellow : DocumentColor(1)
    object Blue : DocumentColor(2)
    object Red : DocumentColor(3)
}