package com.android.mdl.app.document

import android.graphics.Bitmap
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
@Entity(tableName = "document")
data class Document(
    @ColumnInfo(name = "doc_type") val docType: String,
    @PrimaryKey @ColumnInfo(name = "identity_credential_name") val identityCredentialName: String,
    @ColumnInfo(name = "user_visible_name") var userVisibleName: String, // Name displayed in UI, e.g. “P HIN”
    @ColumnInfo(name = "user_visible_document_background") val userVisibleDocumentBackground: Bitmap?,
    @ColumnInfo(name = "hardware_backed") val hardwareBacked: Boolean, // cf. blurb in IdentityCredentialStore docs
    @ColumnInfo(name = "server_url") val serverUrl: String? = null,
    @ColumnInfo(name = "provisioning_code") val provisioningCode: String? = null,
    @ColumnInfo(name = "date_provisioned") val dateProvisioned: Calendar = Calendar.getInstance(),
    @ColumnInfo(name = "date_check_for_update") var dateCheckForUpdate: Calendar? = null
) : Parcelable