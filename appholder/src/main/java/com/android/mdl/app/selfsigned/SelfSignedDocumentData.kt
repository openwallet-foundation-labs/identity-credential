package com.android.mdl.app.selfsigned

import android.graphics.Bitmap
import android.os.Parcelable
import com.android.mdl.app.document.SecureAreaImplementationState
import com.android.mdl.app.selfsigned.AddSelfSignedScreenState.AndroidAuthKeyCurveOption
import com.android.mdl.app.selfsigned.AddSelfSignedScreenState.MdocAuthStateOption
import com.android.mdl.app.util.Field
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
    val secureAreaImplementationStateType: SecureAreaImplementationState,
    val userAuthentication: Boolean,
    val userAuthenticationTimeoutSeconds: Int,
    val allowLskfUnlocking: Boolean,
    val allowBiometricUnlocking: Boolean,
    val useStrongBox: Boolean,
    val mDocAuthenticationOption: MdocAuthStateOption,
    val androidAuthKeyCurveOption: AndroidAuthKeyCurveOption,
    val validityInDays: Int,
    val minValidityInDays: Int,
    val passphrase: String?,
    val numberMso: Int,
    val maxUseMso: Int
) : Parcelable