package com.android.identity.wallet.util

import android.graphics.Bitmap
import com.android.identity.credentialtype.CredentialAttributeType
import com.android.identity.credentialtype.IntegerOption
import com.android.identity.credentialtype.StringOption

data class Field(
    val id: Int,
    val label: String,
    val name: String,
    val fieldType: CredentialAttributeType,
    val value: Any?,
    val namespace: String? = null,
    val parentId: Int? = null,
    var stringOptions: List<StringOption>? = null,
    var integerOptions: List<IntegerOption>? = null
) {
    fun hasValue(): Boolean {
        return value != ""
    }

    fun getValueLong(): Long {
        return value?.toString()?.toLong() ?: 0
    }

    fun getValueString(): String {
        return value as String
    }

    fun getValueBoolean(): Boolean {
        return value as Boolean
    }

    fun getValueBitmap(): Bitmap {
        return value as Bitmap
    }
}