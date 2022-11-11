package com.android.mdl.app.document

import android.graphics.Bitmap
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.Calendar

@Parcelize
@Entity(tableName = "document")
data class Document(
    @ColumnInfo(name = "doc_type") val docType: String,
    @PrimaryKey @ColumnInfo(name = "identity_credential_name") val identityCredentialName: String,
    @ColumnInfo(name = "user_visible_name") var userVisibleName: String, // Name displayed in UI, e.g. “P HIN”
    @ColumnInfo(name = "user_visible_document_background") val userVisibleDocumentBackground: Bitmap?,
    @ColumnInfo(name = "hardware_backed") val hardwareBacked: Boolean, // cf. blurb in IdentityCredentialStore docs
    @ColumnInfo(name = "self_signed") val selfSigned: Boolean, // dummy credential in app created
    @ColumnInfo(name = "user_authentication") val userAuthentication: Boolean, // uses user authentication
    @ColumnInfo(name = "number_mso") var numberMso: Int,
    @ColumnInfo(name = "max_use_mso") var maxUseMso: Int,
    @ColumnInfo(name = "server_url") val serverUrl: String? = null,
    @ColumnInfo(name = "provisioning_code") val provisioningCode: String? = null,
    @ColumnInfo(name = "date_provisioned") val dateProvisioned: Calendar = Calendar.getInstance(),
    @ColumnInfo(name = "date_check_for_update") var dateCheckForUpdate: Calendar? = null,
    @ColumnInfo(name = "date_refresh_auth_keys") var dateRefreshAuthKeys: Calendar? = null,
    @ColumnInfo(name = "card_art") var cardArt: Int = 0
) : Parcelable