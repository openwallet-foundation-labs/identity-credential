package com.android.identity.wallet.selfsigned

import android.graphics.Bitmap
import android.os.Parcelable
import com.android.identity.wallet.support.CurrentSecureArea
import com.android.identity.wallet.util.Field
import com.android.identity.wallet.support.SecureAreaSupportState
import kotlinx.parcelize.Parcelize

data class SelfSignedDocumentData(
    val provisionInfo: ProvisionInfo,
    val fields: List<Field>
) {
    private fun getField(name: String): Field {
        return fields.first { it.name == name }
    }

    fun getValueLong(name: String): Long {
        return (getField(name).value as String).toLong()
    }

    fun getValueString(name: String): String {
        return getField(name).value as String
    }

    fun getValueBoolean(name: String): Boolean {
        return getField(name).value == "true"
    }

    fun getValueBitmap(name: String): Bitmap {
        return getField(name).value as Bitmap
    }
}

@Parcelize
data class ProvisionInfo(
    val docType: String,
    var docName: String,
    var docColor: Int,
    val currentSecureArea: CurrentSecureArea,
    val secureAreaSupportState: SecureAreaSupportState,
    val validityInDays: Int,
    val minValidityInDays: Int,
    val numberMso: Int,
    val maxUseMso: Int
) : Parcelable