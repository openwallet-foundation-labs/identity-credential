package com.ul.ims.gmdl.appholder.document

import android.graphics.Bitmap

data class Document(
    val docType: String,
    val identityCredentialName: String,
    val userVisibleName: String, // Name displayed in UI, e.g. “P HIN”
    val userVisibleDocumentBackground: Bitmap,
    val hardwareBacked: Boolean // cf. blurb in IdentityCredentialStore docs
)