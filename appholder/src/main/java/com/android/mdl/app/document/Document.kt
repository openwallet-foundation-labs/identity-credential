package com.android.mdl.app.document

import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Document(
    val docType: String,
    val identityCredentialName: String,
    val userVisibleName: String, // Name displayed in UI, e.g. “P HIN”
    val userVisibleDocumentBackground: Bitmap?,
    val hardwareBacked: Boolean // cf. blurb in IdentityCredentialStore docs
) : Parcelable